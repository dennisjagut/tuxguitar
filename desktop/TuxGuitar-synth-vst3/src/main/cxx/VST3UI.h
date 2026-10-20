#ifndef _VST3_UI_H
#define _VST3_UI_H

#include <atomic>
#include <cstdint>
#include <functional>

#include "pluginterfaces/base/smartpointer.h"
#include "pluginterfaces/gui/iplugview.h"

class VST3Instance;

// Owns the UI thread (the process main thread): runs the message loop, executes the commands that
// VST3 requires on the UI thread and shows the plugin editor in its own top level window.
class VST3UI : public Steinberg::IPlugFrame
{
public:
	explicit VST3UI (VST3Instance& instance);
	virtual ~VST3UI ();

	bool create ();
	void run ();

	// thread safe
	void quit ();
	void invoke (const std::function<void ()>& function);
	void requestOutputParameters ();

	// UI thread
	bool isAvailable ();
	bool isOpen () const;
	bool open ();
	bool close ();
	bool focus ();

	//--- IPlugFrame
	Steinberg::tresult PLUGIN_API resizeView (Steinberg::IPlugView* view, Steinberg::ViewRect* newSize) override;

	//--- FUnknown, the UI lives as long as the process: no reference counting
	Steinberg::tresult PLUGIN_API queryInterface (const Steinberg::TUID iid, void** obj) override;
	Steinberg::uint32 PLUGIN_API addRef () override { return 1; }
	Steinberg::uint32 PLUGIN_API release () override { return 1; }

	// window procedures
	std::intptr_t onMessageWindowEvent (unsigned int message, std::uintptr_t wParam, std::intptr_t lParam);
	std::intptr_t onEditorWindowEvent (unsigned int message, std::uintptr_t wParam, std::intptr_t lParam);

private:
	void resizeWindow (int width, int height);

	VST3Instance& instance;
	void* messageWindow {nullptr};
	void* editorWindow {nullptr};
	Steinberg::IPtr<Steinberg::IPlugView> view;
	int availability {-1};
	bool resizing {false};
	std::atomic<bool> outputParametersPending {false};
};

#endif
