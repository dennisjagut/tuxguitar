#ifndef _VST3_INSTANCE_H
#define _VST3_INSTANCE_H

#include <atomic>
#include <cstdint>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "pluginterfaces/base/ipluginbase.h"
#include "pluginterfaces/base/smartpointer.h"
#include "pluginterfaces/vst/ivstaudioprocessor.h"
#include "pluginterfaces/vst/ivstcomponent.h"
#include "pluginterfaces/vst/ivsteditcontroller.h"
#include "pluginterfaces/vst/ivstprocesscontext.h"
#include "public.sdk/source/vst/hosting/eventlist.h"
#include "public.sdk/source/vst/hosting/hostclasses.h"
#include "public.sdk/source/vst/hosting/parameterchanges.h"
#include "public.sdk/source/vst/hosting/processdata.h"

// Event types, must match app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3Event
enum VST3EventType
{
	kVST3NoteOn = 1,
	kVST3NoteOff = 2,
	kVST3PitchBend = 3,
	kVST3ControlChange = 4,
};

struct VST3HostEvent
{
	int32_t type;
	int32_t data1;
	int32_t data2;
};

// Host side of one VST3 audio module class: component (processor) + edit controller.
//
// Threading: everything except process() and the IComponentHandler edit callbacks runs on the UI thread.
// process() runs on the socket thread, which acts as the audio thread.
class VST3Instance : public Steinberg::Vst::IComponentHandler, public Steinberg::Vst::IComponentHandler2
{
public:
	VST3Instance (int32_t blockSize, double sampleRate);
	virtual ~VST3Instance ();

	bool open (Steinberg::IPluginFactory* factory, const std::string& cid, std::string& error);
	void close ();

	int32_t getInputChannels () const { return inputChannels; }
	int32_t getOutputChannels () const { return outputChannels; }
	int32_t getBlockSize () const { return blockSize; }

	Steinberg::Vst::IEditController* getController () const { return controller.get (); }
	const std::string& getName () const { return name; }

	// UI thread
	bool getState (std::vector<uint8_t>& componentState, std::vector<uint8_t>& controllerState);
	bool setState (const std::vector<uint8_t>& componentState, const std::vector<uint8_t>& controllerState);
	void applyOutputParameters ();

	// audio (socket) thread. inputs / outputs hold one block per channel, channel after channel.
	void process (const std::vector<VST3HostEvent>& events, const float* inputs, float* outputs);

	// true once after the plugin reported a change (parameter edit, preset load...)
	bool consumeDirty () { return dirty.exchange (false); }

	//--- IComponentHandler
	Steinberg::tresult PLUGIN_API beginEdit (Steinberg::Vst::ParamID id) override;
	Steinberg::tresult PLUGIN_API performEdit (Steinberg::Vst::ParamID id, Steinberg::Vst::ParamValue valueNormalized) override;
	Steinberg::tresult PLUGIN_API endEdit (Steinberg::Vst::ParamID id) override;
	Steinberg::tresult PLUGIN_API restartComponent (Steinberg::int32 flags) override;

	//--- IComponentHandler2
	Steinberg::tresult PLUGIN_API setDirty (Steinberg::TBool state) override;
	Steinberg::tresult PLUGIN_API requestOpenEditor (Steinberg::FIDString name) override;
	Steinberg::tresult PLUGIN_API startGroupEdit () override;
	Steinberg::tresult PLUGIN_API finishGroupEdit () override;

	//--- FUnknown, the instance lives as long as the process: no reference counting
	Steinberg::tresult PLUGIN_API queryInterface (const Steinberg::TUID iid, void** obj) override;
	Steinberg::uint32 PLUGIN_API addRef () override { return 1; }
	Steinberg::uint32 PLUGIN_API release () override { return 1; }

private:
	struct ParameterValue
	{
		Steinberg::Vst::ParamID id;
		Steinberg::Vst::ParamValue value;
	};

	bool setupBuses ();
	void startProcessing ();
	void stopProcessing ();
	void readMidiMappings ();
	void queueParameter (Steinberg::Vst::ParamID id, Steinberg::Vst::ParamValue value);

	int32_t blockSize;
	double sampleRate;
	std::string name;

	Steinberg::IPtr<Steinberg::Vst::HostApplication> hostApplication;
	Steinberg::IPtr<Steinberg::Vst::IComponent> component;
	Steinberg::IPtr<Steinberg::Vst::IAudioProcessor> processor;
	Steinberg::IPtr<Steinberg::Vst::IEditController> controller;
	bool singleComponent {false};
	bool active {false};
	bool processing {false};

	int32_t inputChannels {0};
	int32_t outputChannels {0};
	bool hasEventInput {false};

	std::map<int32_t, Steinberg::Vst::ParamID> midiMappings;

	// recursive: some plugins call restartComponent from within process
	std::recursive_mutex processMutex;
	Steinberg::Vst::HostProcessData processData;
	Steinberg::Vst::ProcessContext processContext {};
	Steinberg::Vst::EventList eventList;
	Steinberg::Vst::ParameterChanges inputChanges;
	Steinberg::Vst::ParameterChanges outputChanges;

	std::mutex parameterMutex;
	std::vector<ParameterValue> pendingParameters;
	std::vector<ParameterValue> outputParameters;

	std::atomic<bool> dirty {false};
	std::atomic<int32_t> activeEdits {0};
};

#endif
