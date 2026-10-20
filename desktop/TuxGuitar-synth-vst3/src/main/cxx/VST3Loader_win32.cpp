#include "VST3Loader.h"

#include <windows.h>

namespace {

using InitModuleFunc = bool (PLUGIN_API*) ();
using ExitModuleFunc = bool (PLUGIN_API*) ();

// A bundle is a folder: <name>.vst3/Contents/x86_64-win/<name>.vst3
std::wstring resolveModuleFile (const std::wstring& path)
{
	DWORD attributes = GetFileAttributesW (path.c_str ());
	if (attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_DIRECTORY))
	{
		std::wstring folder = path;
		while (!folder.empty () && (folder.back () == L'\\' || folder.back () == L'/'))
			folder.pop_back ();

		std::wstring::size_type separator = folder.find_last_of (L"\\/");
		std::wstring fileName = (separator == std::wstring::npos ? folder : folder.substr (separator + 1));

		return folder + L"\\Contents\\x86_64-win\\" + fileName;
	}
	return path;
}

std::string lastErrorMessage ()
{
	DWORD code = GetLastError ();
	char* buffer = nullptr;
	FormatMessageA (FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
	                nullptr, code, 0, reinterpret_cast<LPSTR> (&buffer), 0, nullptr);
	std::string message = (buffer != nullptr ? buffer : "unknown error");
	if (buffer != nullptr)
		LocalFree (buffer);
	return message;
}

} // namespace

std::unique_ptr<VST3Loader> VST3Loader::load (const std::wstring& path, std::string& error)
{
	std::wstring moduleFile = resolveModuleFile (path);

	HMODULE handle = LoadLibraryW (moduleFile.c_str ());
	if (handle == nullptr)
	{
		error = "LoadLibrary failed: " + lastErrorMessage ();
		return nullptr;
	}

	std::unique_ptr<VST3Loader> loader (new VST3Loader ());
	loader->module = handle;

	auto getFactory = reinterpret_cast<GetFactoryProc> (reinterpret_cast<void*> (GetProcAddress (handle, "GetPluginFactory")));
	if (getFactory == nullptr)
	{
		error = "The module does not export 'GetPluginFactory'";
		return nullptr;
	}

	// InitDll is optional
	auto initDll = reinterpret_cast<InitModuleFunc> (reinterpret_cast<void*> (GetProcAddress (handle, "InitDll")));
	if (initDll != nullptr && !initDll ())
	{
		error = "Calling 'InitDll' failed";
		return nullptr;
	}

	loader->factory = Steinberg::owned (getFactory ());
	if (!loader->factory)
	{
		error = "'GetPluginFactory' returned nullptr";
		return nullptr;
	}
	return loader;
}

VST3Loader::~VST3Loader ()
{
	factory = nullptr;
	if (module != nullptr)
	{
		HMODULE handle = static_cast<HMODULE> (module);

		// ExitDll is optional
		auto exitDll = reinterpret_cast<ExitModuleFunc> (reinterpret_cast<void*> (GetProcAddress (handle, "ExitDll")));
		if (exitDll != nullptr)
			exitDll ();

		FreeLibrary (handle);
	}
}
