#include "VST3UI.h"

#include <string>

#include <windows.h>

#include "VST3Instance.h"
#include "VST3Log.h"
#include "pluginterfaces/gui/iplugviewcontentscalesupport.h"

using namespace Steinberg;

namespace {

const wchar_t* MESSAGE_WINDOW_CLASS = L"TuxGuitarVST3MessageWindow";
const wchar_t* EDITOR_WINDOW_CLASS = L"TuxGuitarVST3EditorWindow";

const UINT WM_VST3_INVOKE = WM_APP + 1;
const UINT WM_VST3_OUTPUT_PARAMETERS = WM_APP + 2;

LRESULT CALLBACK messageWindowProc (HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam)
{
	VST3UI* ui = reinterpret_cast<VST3UI*> (GetWindowLongPtrW (hwnd, GWLP_USERDATA));
	if (ui != nullptr)
		return static_cast<LRESULT> (ui->onMessageWindowEvent (message, wParam, lParam));
	return DefWindowProcW (hwnd, message, wParam, lParam);
}

LRESULT CALLBACK editorWindowProc (HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam)
{
	VST3UI* ui = reinterpret_cast<VST3UI*> (GetWindowLongPtrW (hwnd, GWLP_USERDATA));
	if (ui != nullptr)
		return static_cast<LRESULT> (ui->onEditorWindowEvent (message, wParam, lParam));
	return DefWindowProcW (hwnd, message, wParam, lParam);
}

std::wstring toWide (const std::string& value)
{
	int length = MultiByteToWideChar (CP_UTF8, 0, value.c_str (), -1, nullptr, 0);
	if (length <= 0)
		return std::wstring ();

	std::wstring result (static_cast<std::size_t> (length), L'\0');
	MultiByteToWideChar (CP_UTF8, 0, value.c_str (), -1, &result[0], length);
	result.resize (static_cast<std::size_t> (length - 1));
	return result;
}

DWORD editorWindowStyle (bool resizable)
{
	DWORD style = WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN;
	if (!resizable)
		style &= ~static_cast<DWORD> (WS_THICKFRAME | WS_MAXIMIZEBOX);
	return style;
}

} // namespace

VST3UI::VST3UI (VST3Instance& instance)
: instance (instance)
{
}

VST3UI::~VST3UI ()
{
	close ();
	if (messageWindow != nullptr)
		DestroyWindow (static_cast<HWND> (messageWindow));
}

bool VST3UI::create ()
{
	HINSTANCE hInstance = GetModuleHandleW (nullptr);

	WNDCLASSEXW messageClass {};
	messageClass.cbSize = sizeof (messageClass);
	messageClass.lpfnWndProc = messageWindowProc;
	messageClass.hInstance = hInstance;
	messageClass.lpszClassName = MESSAGE_WINDOW_CLASS;
	RegisterClassExW (&messageClass);

	WNDCLASSEXW editorClass {};
	editorClass.cbSize = sizeof (editorClass);
	editorClass.style = CS_HREDRAW | CS_VREDRAW;
	editorClass.lpfnWndProc = editorWindowProc;
	editorClass.hInstance = hInstance;
	editorClass.hCursor = LoadCursor (nullptr, IDC_ARROW);
	editorClass.hbrBackground = reinterpret_cast<HBRUSH> (COLOR_WINDOW + 1);
	editorClass.lpszClassName = EDITOR_WINDOW_CLASS;
	RegisterClassExW (&editorClass);

	HWND hwnd = CreateWindowExW (0, MESSAGE_WINDOW_CLASS, L"", 0, 0, 0, 0, 0, HWND_MESSAGE, nullptr, hInstance, nullptr);
	if (hwnd == nullptr)
		return false;

	SetWindowLongPtrW (hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR> (this));
	messageWindow = hwnd;
	return true;
}

void VST3UI::run ()
{
	MSG msg;
	while (GetMessageW (&msg, nullptr, 0, 0) > 0)
	{
		TranslateMessage (&msg);
		DispatchMessageW (&msg);
	}
}

void VST3UI::quit ()
{
	PostMessageW (static_cast<HWND> (messageWindow), WM_CLOSE, 0, 0);
}

void VST3UI::invoke (const std::function<void ()>& function)
{
	// SendMessage blocks until the UI thread executed the function
	SendMessageW (static_cast<HWND> (messageWindow), WM_VST3_INVOKE, 0, reinterpret_cast<LPARAM> (&function));
}

void VST3UI::requestOutputParameters ()
{
	if (!outputParametersPending.exchange (true))
		PostMessageW (static_cast<HWND> (messageWindow), WM_VST3_OUTPUT_PARAMETERS, 0, 0);
}

std::intptr_t VST3UI::onMessageWindowEvent (unsigned int message, std::uintptr_t wParam, std::intptr_t lParam)
{
	HWND hwnd = static_cast<HWND> (messageWindow);
	switch (message)
	{
		case WM_VST3_INVOKE:
			(*reinterpret_cast<const std::function<void ()>*> (lParam)) ();
			return 0;
		case WM_VST3_OUTPUT_PARAMETERS:
			outputParametersPending = false;
			instance.applyOutputParameters ();
			return 0;
		case WM_CLOSE:
			close ();
			PostQuitMessage (0);
			return 0;
	}
	return DefWindowProcW (hwnd, message, static_cast<WPARAM> (wParam), static_cast<LPARAM> (lParam));
}

std::intptr_t VST3UI::onEditorWindowEvent (unsigned int message, std::uintptr_t wParam, std::intptr_t lParam)
{
	HWND hwnd = static_cast<HWND> (editorWindow);
	switch (message)
	{
		case WM_SIZE:
			if (view && !resizing && wParam != SIZE_MINIMIZED && view->canResize () == kResultTrue)
			{
				ViewRect rect (0, 0, LOWORD (lParam), HIWORD (lParam));
				view->onSize (&rect);
			}
			return 0;
		case WM_CLOSE:
			close ();
			return 0;
	}
	return DefWindowProcW (hwnd, message, static_cast<WPARAM> (wParam), static_cast<LPARAM> (lParam));
}

bool VST3UI::isAvailable ()
{
	if (availability < 0)
	{
		availability = 0;
		if (instance.getController () != nullptr)
		{
			IPtr<IPlugView> probe = owned (instance.getController ()->createView (Vst::ViewType::kEditor));
			if (probe && probe->isPlatformTypeSupported (kPlatformTypeHWND) == kResultTrue)
				availability = 1;
		}
	}
	return (availability == 1);
}

bool VST3UI::isOpen () const
{
	return (editorWindow != nullptr);
}

bool VST3UI::open ()
{
	if (isOpen ())
		return focus ();
	if (!isAvailable ())
		return false;

	view = owned (instance.getController ()->createView (Vst::ViewType::kEditor));
	if (!view || view->isPlatformTypeSupported (kPlatformTypeHWND) != kResultTrue)
	{
		view = nullptr;
		return false;
	}

	ViewRect size;
	view->getSize (&size);

	DWORD style = editorWindowStyle (view->canResize () == kResultTrue);
	RECT windowRect {0, 0, size.getWidth (), size.getHeight ()};
	AdjustWindowRectEx (&windowRect, style, FALSE, 0);

	std::wstring title = toWide (instance.getName ().empty () ? std::string ("VST3 Plugin") : instance.getName ());
	HWND hwnd = CreateWindowExW (0, EDITOR_WINDOW_CLASS, title.c_str (), style, CW_USEDEFAULT, CW_USEDEFAULT,
	                             windowRect.right - windowRect.left, windowRect.bottom - windowRect.top,
	                             nullptr, nullptr, GetModuleHandleW (nullptr), nullptr);
	if (hwnd == nullptr)
	{
		view = nullptr;
		return false;
	}
	SetWindowLongPtrW (hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR> (this));
	editorWindow = hwnd;

	view->setFrame (this);

	FUnknownPtr<IPlugViewContentScaleSupport> scaleSupport (view.get ());
	if (scaleSupport)
	{
		HDC screen = GetDC (nullptr);
		float scale = static_cast<float> (GetDeviceCaps (screen, LOGPIXELSX)) / 96.f;
		ReleaseDC (nullptr, screen);
		scaleSupport->setContentScaleFactor (scale);
	}

	if (view->attached (hwnd, kPlatformTypeHWND) != kResultOk)
	{
		VST3_LOG ("The plugin editor could not be attached");
		close ();
		return false;
	}

	// the size may change once the view is attached (content scale)
	if (view->getSize (&size) == kResultOk)
		resizeWindow (size.getWidth (), size.getHeight ());

	ShowWindow (hwnd, SW_SHOW);
	SetForegroundWindow (hwnd);
	return true;
}

bool VST3UI::close ()
{
	if (view)
	{
		view->removed ();
		view->setFrame (nullptr);
		view = nullptr;
	}
	if (editorWindow != nullptr)
	{
		HWND hwnd = static_cast<HWND> (editorWindow);
		editorWindow = nullptr;
		DestroyWindow (hwnd);
	}
	return true;
}

bool VST3UI::focus ()
{
	if (!isOpen ())
		return false;

	HWND hwnd = static_cast<HWND> (editorWindow);
	if (IsIconic (hwnd))
		ShowWindow (hwnd, SW_RESTORE);
	SetForegroundWindow (hwnd);
	return true;
}

void VST3UI::resizeWindow (int width, int height)
{
	HWND hwnd = static_cast<HWND> (editorWindow);
	RECT windowRect {0, 0, width, height};
	AdjustWindowRectEx (&windowRect, static_cast<DWORD> (GetWindowLongPtrW (hwnd, GWL_STYLE)), FALSE, 0);

	resizing = true;
	SetWindowPos (hwnd, nullptr, 0, 0, windowRect.right - windowRect.left, windowRect.bottom - windowRect.top, SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
	resizing = false;
}

tresult PLUGIN_API VST3UI::resizeView (IPlugView* resizedView, ViewRect* newSize)
{
	if (resizedView == nullptr || newSize == nullptr || resizedView != view.get () || !isOpen ())
		return kInvalidArgument;

	resizeWindow (newSize->getWidth (), newSize->getHeight ());
	resizedView->onSize (newSize);
	return kResultTrue;
}

tresult PLUGIN_API VST3UI::queryInterface (const TUID iid, void** obj)
{
	if (FUnknownPrivate::iidEqual (iid, IPlugFrame::iid) || FUnknownPrivate::iidEqual (iid, FUnknown::iid))
	{
		*obj = static_cast<IPlugFrame*> (this);
		return kResultOk;
	}
	*obj = nullptr;
	return kNoInterface;
}
