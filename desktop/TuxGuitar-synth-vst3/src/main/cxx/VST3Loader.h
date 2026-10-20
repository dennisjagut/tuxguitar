#ifndef _VST3_LOADER_H
#define _VST3_LOADER_H

#include <memory>
#include <string>

#include "pluginterfaces/base/ipluginbase.h"
#include "pluginterfaces/base/smartpointer.h"

// Loads a .vst3 module (bundle folder or single file) and exposes its plugin factory.
class VST3Loader
{
public:
	static std::unique_ptr<VST3Loader> load (const std::wstring& path, std::string& error);

	~VST3Loader ();

	Steinberg::IPluginFactory* getFactory () const { return factory.get (); }

private:
	VST3Loader () = default;

	void* module {nullptr};
	Steinberg::IPtr<Steinberg::IPluginFactory> factory;
};

#endif
