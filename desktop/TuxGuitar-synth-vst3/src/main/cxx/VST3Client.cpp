// TuxGuitar VST3 client.
//
// Hosts one VST3 audio module class for the TuxGuitar synthesizer, see TuxGuitar-synth-vst3 (VST3RemoteInstance).
//
//   tuxguitar-synth-vst3 <sessionId> <serverPort> <blockSize> <sampleRate> <modulePath> <classId>
//   tuxguitar-synth-vst3 --scan <modulePath>
//
// The protocol is little endian, this client only targets little endian (x86_64) hosts.

#include <cstring>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include <windows.h>
#include <ole2.h>
#include <shellapi.h>

#include "VST3Instance.h"
#include "VST3Loader.h"
#include "VST3Log.h"
#include "VST3Socket.h"
#include "VST3UI.h"
#include "pluginterfaces/base/ipluginbase.h"

using namespace Steinberg;

namespace {

// Command ids, must match app.tuxguitar.player.impl.midiport.vst3.remote.command.*
enum Command
{
	kGetState = 1,
	kSetState = 2,
	kGetBusInfo = 3,
	kProcess = 4,
	kOpenUI = 5,
	kCloseUI = 6,
	kFocusUI = 7,
	kIsUIOpen = 8,
	kIsUIAvailable = 9,
};

std::string toUtf8 (const std::wstring& value)
{
	int length = WideCharToMultiByte (CP_UTF8, 0, value.c_str (), -1, nullptr, 0, nullptr, nullptr);
	if (length <= 0)
		return std::string ();

	std::string result (static_cast<std::size_t> (length), '\0');
	WideCharToMultiByte (CP_UTF8, 0, value.c_str (), -1, &result[0], length, nullptr, nullptr);
	result.resize (static_cast<std::size_t> (length - 1));
	return result;
}

std::string toString (const char8* value, std::size_t maxLength)
{
	std::string result (value, strnlen (value, maxLength));
	// tabs and line breaks are separators of the scan output
	for (char& c : result)
	{
		if (c == '\t' || c == '\r' || c == '\n')
			c = ' ';
	}
	return result;
}

std::vector<std::wstring> getArguments ()
{
	std::vector<std::wstring> arguments;
	int count = 0;
	LPWSTR* values = CommandLineToArgvW (GetCommandLineW (), &count);
	if (values != nullptr)
	{
		arguments.assign (values, values + count);
		LocalFree (values);
	}
	return arguments;
}

// Prints one line per audio module class: CLASS<TAB>cid<TAB>name<TAB>vendor<TAB>subCategories
int scanModule (const std::wstring& path)
{
	std::string error;
	std::unique_ptr<VST3Loader> loader = VST3Loader::load (path, error);
	if (!loader)
	{
		VST3_LOG ("Could not load %s: %s", toUtf8 (path).c_str (), error.c_str ());
		return 1;
	}

	IPluginFactory* factory = loader->getFactory ();
	PFactoryInfo factoryInfo;
	factory->getFactoryInfo (&factoryInfo);

	FUnknownPtr<IPluginFactory2> factory2 (factory);
	for (int32 i = 0; i < factory->countClasses (); i++)
	{
		PClassInfo info;
		if (factory->getClassInfo (i, &info) != kResultOk || std::strncmp (info.category, kVstAudioEffectClass, PClassInfo::kCategorySize) != 0)
			continue;

		std::string name = toString (info.name, PClassInfo::kNameSize);
		std::string vendor = toString (factoryInfo.vendor, PFactoryInfo::kNameSize);
		std::string subCategories;

		PClassInfo2 info2;
		if (factory2 && factory2->getClassInfo2 (i, &info2) == kResultOk)
		{
			name = toString (info2.name, PClassInfo::kNameSize);
			subCategories = toString (info2.subCategories, PClassInfo2::kSubCategoriesSize);
			if (info2.vendor[0] != 0)
				vendor = toString (info2.vendor, PClassInfo2::kVendorSize);
		}

		char8 cid[33] = {0};
		FUID::fromTUID (info.cid).toString (cid);

		std::printf ("CLASS\t%s\t%s\t%s\t%s\n", cid, name.c_str (), vendor.c_str (), subCategories.c_str ());
	}
	std::fflush (stdout);
	return 0;
}

bool readEvents (VST3Socket& socket, std::vector<VST3HostEvent>& events)
{
	int32_t count = 0;
	if (!socket.readInt (count) || count < 0)
		return false;

	events.resize (static_cast<std::size_t> (count));
	for (VST3HostEvent& event : events)
	{
		if (!socket.readInt (event.type) || !socket.readInt (event.data1) || !socket.readInt (event.data2))
			return false;
	}
	return true;
}

bool sendBool (VST3Socket& socket, bool value)
{
	VST3Packet packet;
	packet.putBool (value);
	return socket.write (packet.data ().data (), packet.data ().size ());
}

// Runs on the socket thread until the connection is closed by TuxGuitar.
void processCommands (VST3Socket& socket, VST3Instance& instance, VST3UI& ui)
{
	std::size_t blockSize = static_cast<std::size_t> (instance.getBlockSize ());
	std::vector<float> inputs (static_cast<std::size_t> (instance.getInputChannels ()) * blockSize);
	std::vector<float> outputs (static_cast<std::size_t> (instance.getOutputChannels ()) * blockSize);
	std::vector<VST3HostEvent> events;

	bool running = true;
	while (running)
	{
		int32_t command = 0;
		if (!socket.readInt (command))
			break;

		switch (command)
		{
			case kProcess:
			{
				running = (readEvents (socket, events) && (inputs.empty () || socket.read (inputs.data (), inputs.size () * sizeof (float))));
				if (running)
				{
					instance.process (events, inputs.data (), outputs.data ());
					ui.requestOutputParameters ();

					VST3Packet packet;
					for (float sample : outputs)
						packet.putFloat (sample);
					packet.putBool (instance.consumeDirty ());
					running = socket.write (packet.data ().data (), packet.data ().size ());
				}
				break;
			}
			case kGetState:
			{
				std::vector<uint8_t> componentState;
				std::vector<uint8_t> controllerState;
				ui.invoke ([&] () { instance.getState (componentState, controllerState); });

				VST3Packet packet;
				packet.putBytes (componentState);
				packet.putBytes (controllerState);
				running = socket.write (packet.data ().data (), packet.data ().size ());
				break;
			}
			case kSetState:
			{
				std::vector<uint8_t> componentState;
				std::vector<uint8_t> controllerState;
				running = (socket.readBytes (componentState) && socket.readBytes (controllerState));
				if (running)
				{
					bool result = false;
					ui.invoke ([&] () { result = instance.setState (componentState, controllerState); });
					running = sendBool (socket, result);
				}
				break;
			}
			case kGetBusInfo:
			{
				VST3Packet packet;
				packet.putInt (instance.getInputChannels ());
				packet.putInt (instance.getOutputChannels ());
				running = socket.write (packet.data ().data (), packet.data ().size ());
				break;
			}
			case kOpenUI:
			case kCloseUI:
			case kFocusUI:
			case kIsUIOpen:
			case kIsUIAvailable:
			{
				bool result = false;
				ui.invoke ([&] () {
					switch (command)
					{
						case kOpenUI: result = ui.open (); break;
						case kCloseUI: result = ui.close (); break;
						case kFocusUI: result = ui.focus (); break;
						case kIsUIOpen: result = ui.isOpen (); break;
						default: result = ui.isAvailable (); break;
					}
				});
				running = sendBool (socket, result);
				break;
			}
			default:
				VST3_LOG ("Unknown command %d", command);
				running = false;
				break;
		}
	}
}

} // namespace

int main ()
{
	std::vector<std::wstring> arguments = getArguments ();

	if (arguments.size () == 3 && arguments[1] == L"--scan")
		return scanModule (arguments[2]);

	if (arguments.size () != 7)
	{
		VST3_LOG ("Usage: tuxguitar-synth-vst3 <sessionId> <serverPort> <blockSize> <sampleRate> <modulePath> <classId>");
		VST3_LOG ("       tuxguitar-synth-vst3 --scan <modulePath>");
		return 2;
	}

	int32_t sessionId = std::stoi (arguments[1]);
	int serverPort = std::stoi (arguments[2]);
	int32_t blockSize = std::stoi (arguments[3]);
	double sampleRate = std::stod (arguments[4]);
	std::wstring modulePath = arguments[5];
	std::string classId = toUtf8 (arguments[6]);

	SetProcessDPIAware ();
	OleInitialize (nullptr);

	int exitCode = 0;
	{
		std::string error;
		std::unique_ptr<VST3Loader> loader = VST3Loader::load (modulePath, error);
		if (!loader)
		{
			VST3_LOG ("Could not load %s: %s", toUtf8 (modulePath).c_str (), error.c_str ());
			OleUninitialize ();
			return 1;
		}

		VST3Instance instance (blockSize, sampleRate);
		VST3UI ui (instance);
		VST3Socket socket;

		if (!instance.open (loader->getFactory (), classId, error))
		{
			VST3_LOG ("Could not open %s: %s", classId.c_str (), error.c_str ());
			exitCode = 1;
		}
		else if (!ui.create ())
		{
			VST3_LOG ("Could not create the message window");
			exitCode = 1;
		}
		else if (!socket.connect (serverPort))
		{
			VST3_LOG ("Could not connect to port %d", serverPort);
			exitCode = 1;
		}
		else
		{
			VST3Packet session;
			session.putInt (sessionId);
			socket.write (session.data ().data (), session.data ().size ());

			std::thread commandThread ([&] () {
				processCommands (socket, instance, ui);
				ui.quit ();
			});

			ui.run ();

			socket.close ();
			commandThread.join ();
		}

		ui.close ();
		instance.close ();
	}

	OleUninitialize ();
	return exitCode;
}
