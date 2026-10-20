#ifndef _VST3_LOG_H
#define _VST3_LOG_H

#include <cstdio>

// stderr is forwarded to the TuxGuitar console by TGClientProcess
#define VST3_LOG(...) do { std::fprintf(stderr, "[tuxguitar-synth-vst3] " __VA_ARGS__); std::fprintf(stderr, "\n"); std::fflush(stderr); } while (0)

#endif
