#include "VST3Instance.h"

#include <algorithm>
#include <cstring>

#include "VST3Log.h"
#include "pluginterfaces/vst/ivstevents.h"
#include "pluginterfaces/vst/ivstmessage.h"
#include "pluginterfaces/vst/ivstmidicontrollers.h"
#include "pluginterfaces/vst/vstspeaker.h"
#include "public.sdk/source/common/memorystream.h"
#include "public.sdk/source/vst/utility/stringconvert.h"

using namespace Steinberg;
using namespace Steinberg::Vst;

namespace {

class TuxGuitarHostApplication : public HostApplication
{
public:
	tresult PLUGIN_API getName (String128 name) override
	{
		return StringConvert::convert ("TuxGuitar", name) ? kResultTrue : kInternalError;
	}
};

std::vector<uint8_t> toBytes (MemoryStream& stream)
{
	const uint8_t* data = reinterpret_cast<const uint8_t*> (stream.getData ());
	return std::vector<uint8_t> (data, data + stream.getSize ());
}

int32_t getMainBusChannels (IAudioProcessor* processor, BusDirection direction, int32 busCount)
{
	SpeakerArrangement arrangement = 0;
	if (busCount > 0 && processor->getBusArrangement (direction, 0, arrangement) == kResultTrue)
		return SpeakerArr::getChannelCount (arrangement);
	return 0;
}

} // namespace

VST3Instance::VST3Instance (int32_t blockSize, double sampleRate)
: blockSize (blockSize)
, sampleRate (sampleRate)
, eventList (512)
{
}

VST3Instance::~VST3Instance ()
{
	close ();
}

bool VST3Instance::open (IPluginFactory* factory, const std::string& cid, std::string& error)
{
	FUID classId;
	if (!classId.fromString (cid.c_str ()))
	{
		error = "Invalid class id: " + cid;
		return false;
	}

	hostApplication = owned (new TuxGuitarHostApplication ());

	FUnknownPtr<IPluginFactory3> factory3 (factory);
	if (factory3)
		factory3->setHostContext (hostApplication);

	for (int32 i = 0; i < factory->countClasses (); i++)
	{
		PClassInfo info;
		if (factory->getClassInfo (i, &info) == kResultOk && FUID::fromTUID (info.cid) == classId)
			name = info.name;
	}

	IComponent* componentPtr = nullptr;
	if (factory->createInstance (classId.toTUID (), IComponent::iid, reinterpret_cast<void**> (&componentPtr)) != kResultOk || componentPtr == nullptr)
	{
		error = "Could not create the component " + cid;
		return false;
	}
	component = owned (componentPtr);

	if (component->initialize (hostApplication) != kResultOk)
	{
		error = "Could not initialize the component";
		component = nullptr;
		return false;
	}

	processor = FUnknownPtr<IAudioProcessor> (component.get ());
	if (!processor)
	{
		error = "The component is not an audio processor";
		return false;
	}

	// the controller is either part of the component (single component effect) or a separate class
	controller = FUnknownPtr<IEditController> (component.get ());
	singleComponent = (controller.get () != nullptr);
	if (!controller)
	{
		TUID controllerClassId;
		IEditController* controllerPtr = nullptr;
		if (component->getControllerClassId (controllerClassId) == kResultTrue &&
		    factory->createInstance (controllerClassId, IEditController::iid, reinterpret_cast<void**> (&controllerPtr)) == kResultOk && controllerPtr != nullptr)
		{
			controller = owned (controllerPtr);
			if (controller->initialize (hostApplication) != kResultOk)
			{
				VST3_LOG ("Could not initialize the edit controller");
				controller = nullptr;
			}
		}
	}

	if (controller)
	{
		controller->setComponentHandler (this);

		if (!singleComponent)
		{
			FUnknownPtr<IConnectionPoint> componentConnection (component.get ());
			FUnknownPtr<IConnectionPoint> controllerConnection (controller.get ());
			if (componentConnection && controllerConnection)
			{
				componentConnection->connect (controllerConnection);
				controllerConnection->connect (componentConnection);
			}

			// synchronize the controller with the initial processor state
			MemoryStream stream;
			if (component->getState (&stream) == kResultOk)
			{
				stream.seek (0, IBStream::kIBSeekSet, nullptr);
				controller->setComponentState (&stream);
			}
		}
		readMidiMappings ();
	}

	if (!setupBuses ())
	{
		error = "No supported audio bus arrangement";
		return false;
	}

	processContext.sampleRate = sampleRate;
	processContext.tempo = 120.0;
	processContext.timeSigNumerator = 4;
	processContext.timeSigDenominator = 4;
	processContext.state = ProcessContext::kPlaying | ProcessContext::kTempoValid | ProcessContext::kTimeSigValid;

	startProcessing ();
	return true;
}

bool VST3Instance::setupBuses ()
{
	int32 audioInputs = component->getBusCount (kAudio, kInput);
	int32 audioOutputs = component->getBusCount (kAudio, kOutput);
	if (audioOutputs <= 0)
		return false;

	// keep the current arrangement of the auxiliary buses, those are deactivated below
	std::vector<SpeakerArrangement> inputArrangements (static_cast<std::size_t> (std::max (audioInputs, 0)));
	std::vector<SpeakerArrangement> outputArrangements (static_cast<std::size_t> (audioOutputs));
	for (int32 i = 0; i < audioInputs; i++)
		processor->getBusArrangement (kInput, i, inputArrangements[static_cast<std::size_t> (i)]);
	for (int32 i = 0; i < audioOutputs; i++)
		processor->getBusArrangement (kOutput, i, outputArrangements[static_cast<std::size_t> (i)]);

	// prefer stereo, amp simulators often only accept a mono input
	const SpeakerArrangement candidates[][2] = {
		{SpeakerArr::kStereo, SpeakerArr::kStereo},
		{SpeakerArr::kMono, SpeakerArr::kStereo},
		{SpeakerArr::kMono, SpeakerArr::kMono},
	};
	for (const auto& candidate : candidates)
	{
		if (audioInputs > 0)
			inputArrangements[0] = candidate[0];
		outputArrangements[0] = candidate[1];

		if (processor->setBusArrangements (inputArrangements.data (), audioInputs, outputArrangements.data (), audioOutputs) == kResultTrue)
			break;
	}

	// whatever was accepted, use the arrangement reported by the plugin
	inputChannels = getMainBusChannels (processor.get (), kInput, audioInputs);
	outputChannels = getMainBusChannels (processor.get (), kOutput, audioOutputs);
	if (outputChannels <= 0)
		return false;

	for (int32 i = 0; i < audioInputs; i++)
		component->activateBus (kAudio, kInput, i, i == 0);
	for (int32 i = 0; i < audioOutputs; i++)
		component->activateBus (kAudio, kOutput, i, i == 0);

	int32 eventInputs = component->getBusCount (kEvent, kInput);
	hasEventInput = (eventInputs > 0);
	for (int32 i = 0; i < eventInputs; i++)
		component->activateBus (kEvent, kInput, i, i == 0);

	return true;
}

void VST3Instance::readMidiMappings ()
{
	// [UI-thread & Connected]: TuxGuitar sends pitch bend and controllers as MIDI, VST3 expects parameters
	FUnknownPtr<IMidiMapping> midiMapping (controller.get ());
	if (midiMapping)
	{
		for (int32 controllerNumber = 0; controllerNumber <= kPitchBend; controllerNumber++)
		{
			ParamID id = kNoParamId;
			if (midiMapping->getMidiControllerAssignment (0, 0, static_cast<CtrlNumber> (controllerNumber), id) == kResultTrue)
				midiMappings[controllerNumber] = id;
		}
	}
}

void VST3Instance::startProcessing ()
{
	std::lock_guard<std::recursive_mutex> guard (processMutex);

	ProcessSetup setup {};
	setup.processMode = kRealtime;
	setup.symbolicSampleSize = kSample32;
	setup.maxSamplesPerBlock = blockSize;
	setup.sampleRate = sampleRate;
	processor->setupProcessing (setup);

	processData.prepare (*component.get (), blockSize, kSample32);
	processData.numSamples = blockSize;
	processData.processContext = &processContext;
	processData.inputParameterChanges = &inputChanges;
	processData.outputParameterChanges = &outputChanges;
	processData.inputEvents = (hasEventInput ? &eventList : nullptr);

	active = (component->setActive (true) == kResultOk);
	processing = (processor->setProcessing (true) == kResultOk);
}

void VST3Instance::stopProcessing ()
{
	std::lock_guard<std::recursive_mutex> guard (processMutex);

	if (processing)
		processor->setProcessing (false);
	if (active)
		component->setActive (false);

	processing = false;
	active = false;
	processData.unprepare ();
}

void VST3Instance::close ()
{
	if (component)
	{
		stopProcessing ();

		if (controller)
		{
			controller->setComponentHandler (nullptr);
			if (!singleComponent)
			{
				FUnknownPtr<IConnectionPoint> componentConnection (component.get ());
				FUnknownPtr<IConnectionPoint> controllerConnection (controller.get ());
				if (componentConnection && controllerConnection)
				{
					componentConnection->disconnect (controllerConnection);
					controllerConnection->disconnect (componentConnection);
				}
				controller->terminate ();
			}
		}
		component->terminate ();
	}

	controller = nullptr;
	processor = nullptr;
	component = nullptr;
	hostApplication = nullptr;
}

bool VST3Instance::getState (std::vector<uint8_t>& componentState, std::vector<uint8_t>& controllerState)
{
	MemoryStream componentStream;
	if (component->getState (&componentStream) != kResultOk)
		return false;
	componentState = toBytes (componentStream);

	controllerState.clear ();
	if (controller && !singleComponent)
	{
		MemoryStream controllerStream;
		if (controller->getState (&controllerStream) == kResultOk)
			controllerState = toBytes (controllerStream);
	}
	return true;
}

bool VST3Instance::setState (const std::vector<uint8_t>& componentState, const std::vector<uint8_t>& controllerState)
{
	if (componentState.empty ())
		return false;

	std::vector<uint8_t> componentData (componentState);
	MemoryStream componentStream (componentData.data (), static_cast<TSize> (componentData.size ()));

	bool result = false;
	{
		std::lock_guard<std::recursive_mutex> guard (processMutex);
		result = (component->setState (&componentStream) == kResultOk);
	}

	if (controller && !singleComponent)
	{
		componentStream.seek (0, IBStream::kIBSeekSet, nullptr);
		controller->setComponentState (&componentStream);

		if (!controllerState.empty ())
		{
			std::vector<uint8_t> controllerData (controllerState);
			MemoryStream controllerStream (controllerData.data (), static_cast<TSize> (controllerData.size ()));
			controller->setState (&controllerStream);
		}
	}

	// the state was given by TuxGuitar, nothing new to report
	dirty = false;
	return result;
}

void VST3Instance::process (const std::vector<VST3HostEvent>& events, const float* inputs, float* outputs)
{
	std::lock_guard<std::recursive_mutex> guard (processMutex);

	std::fill (outputs, outputs + (static_cast<std::size_t> (outputChannels) * static_cast<std::size_t> (blockSize)), 0.f);
	if (!processing)
		return;

	eventList.clear ();
	inputChanges.clearQueue ();
	outputChanges.clearQueue ();

	for (const VST3HostEvent& hostEvent : events)
	{
		if (hostEvent.type == kVST3NoteOn || hostEvent.type == kVST3NoteOff)
		{
			if (!hasEventInput)
				continue;

			Event event {};
			event.busIndex = 0;
			event.sampleOffset = 0;
			if (hostEvent.type == kVST3NoteOn && hostEvent.data2 > 0)
			{
				event.type = Event::kNoteOnEvent;
				event.noteOn.channel = 0;
				event.noteOn.pitch = static_cast<int16> (hostEvent.data1);
				event.noteOn.velocity = static_cast<float> (hostEvent.data2) / 127.f;
				event.noteOn.noteId = -1;
			}
			else
			{
				event.type = Event::kNoteOffEvent;
				event.noteOff.channel = 0;
				event.noteOff.pitch = static_cast<int16> (hostEvent.data1);
				event.noteOff.velocity = static_cast<float> (hostEvent.data2) / 127.f;
				event.noteOff.noteId = -1;
			}
			eventList.addEvent (event);
		}
		else
		{
			int32_t controllerNumber = (hostEvent.type == kVST3PitchBend ? static_cast<int32_t> (kPitchBend) : hostEvent.data1);
			double value = (hostEvent.type == kVST3PitchBend ? hostEvent.data1 / 16383.0 : hostEvent.data2 / 127.0);

			auto mapping = midiMappings.find (controllerNumber);
			if (mapping != midiMappings.end ())
				queueParameter (mapping->second, std::min (1.0, std::max (0.0, value)));
		}
	}

	{
		// parameter edits from the plugin editor
		std::lock_guard<std::mutex> parameterGuard (parameterMutex);
		for (const ParameterValue& parameter : pendingParameters)
		{
			int32 queueIndex = 0;
			int32 pointIndex = 0;
			IParamValueQueue* queue = inputChanges.addParameterData (parameter.id, queueIndex);
			if (queue != nullptr)
				queue->addPoint (0, parameter.value, pointIndex);
		}
		pendingParameters.clear ();
	}

	if (processData.numInputs > 0 && inputChannels > 0)
	{
		for (int32 c = 0; c < inputChannels && c < processData.inputs[0].numChannels; c++)
			std::memcpy (processData.inputs[0].channelBuffers32[c], inputs + (c * blockSize), sizeof (float) * static_cast<std::size_t> (blockSize));
	}

	processor->process (processData);

	if (processData.numOutputs > 0)
	{
		for (int32 c = 0; c < outputChannels && c < processData.outputs[0].numChannels; c++)
			std::memcpy (outputs + (c * blockSize), processData.outputs[0].channelBuffers32[c], sizeof (float) * static_cast<std::size_t> (blockSize));
	}

	processContext.projectTimeSamples += blockSize;

	// output parameters (meters, values changed by the processor) are applied to the controller on the UI thread
	int32 outputCount = outputChanges.getParameterCount ();
	if (outputCount > 0)
	{
		std::lock_guard<std::mutex> parameterGuard (parameterMutex);
		for (int32 i = 0; i < outputCount; i++)
		{
			IParamValueQueue* queue = outputChanges.getParameterData (i);
			int32 points = (queue != nullptr ? queue->getPointCount () : 0);
			int32 sampleOffset = 0;
			ParamValue value = 0;
			if (points > 0 && queue->getPoint (points - 1, sampleOffset, value) == kResultTrue)
				outputParameters.push_back ({queue->getParameterId (), value});
		}
	}
}

void VST3Instance::applyOutputParameters ()
{
	std::vector<ParameterValue> parameters;
	{
		std::lock_guard<std::mutex> parameterGuard (parameterMutex);
		parameters.swap (outputParameters);
	}
	if (controller)
	{
		for (const ParameterValue& parameter : parameters)
			controller->setParamNormalized (parameter.id, parameter.value);
	}
}

void VST3Instance::queueParameter (ParamID id, ParamValue value)
{
	std::lock_guard<std::mutex> parameterGuard (parameterMutex);
	pendingParameters.push_back ({id, value});
}

tresult PLUGIN_API VST3Instance::beginEdit (ParamID)
{
	activeEdits++;
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::performEdit (ParamID id, ParamValue valueNormalized)
{
	queueParameter (id, valueNormalized);

	// edits within a gesture are reported once, at endEdit
	if (activeEdits <= 0)
		dirty = true;
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::endEdit (ParamID)
{
	if (activeEdits > 0)
		activeEdits--;
	dirty = true;
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::restartComponent (int32 flags)
{
	if (flags & (kIoChanged | kLatencyChanged))
	{
		stopProcessing ();
		startProcessing ();
	}
	if (flags & (kParamValuesChanged | kReloadComponent))
		dirty = true;
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::setDirty (TBool state)
{
	if (state)
		dirty = true;
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::requestOpenEditor (FIDString)
{
	return kNotImplemented;
}

tresult PLUGIN_API VST3Instance::startGroupEdit ()
{
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::finishGroupEdit ()
{
	return kResultOk;
}

tresult PLUGIN_API VST3Instance::queryInterface (const TUID iid, void** obj)
{
	if (FUnknownPrivate::iidEqual (iid, IComponentHandler::iid) || FUnknownPrivate::iidEqual (iid, FUnknown::iid))
	{
		*obj = static_cast<IComponentHandler*> (this);
		return kResultOk;
	}
	if (FUnknownPrivate::iidEqual (iid, IComponentHandler2::iid))
	{
		*obj = static_cast<IComponentHandler2*> (this);
		return kResultOk;
	}
	*obj = nullptr;
	return kNoInterface;
}
