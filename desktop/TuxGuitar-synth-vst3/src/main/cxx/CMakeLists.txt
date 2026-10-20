cmake_minimum_required(VERSION 3.19)

project(tuxguitar-synth-vst3 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# VST3 SDK (MIT license). Only the submodules required for hosting are fetched.
set(VST3_SDK_TAG "v3.8.1_build_84" CACHE STRING "VST3 SDK git tag")
FetchContent_Declare(vst3sdk
	GIT_REPOSITORY https://github.com/steinbergmedia/vst3sdk.git
	GIT_TAG ${VST3_SDK_TAG}
	GIT_SHALLOW TRUE
	GIT_SUBMODULES base pluginterfaces public.sdk
	# the SDK cmake project is not used, the required SDK sources are compiled below
	SOURCE_SUBDIR do-not-add-subdirectory
)
FetchContent_MakeAvailable(vst3sdk)

set(VST3_SDK_SOURCES
	${vst3sdk_SOURCE_DIR}/pluginterfaces/base/conststringtable.cpp
	${vst3sdk_SOURCE_DIR}/pluginterfaces/base/coreiids.cpp
	${vst3sdk_SOURCE_DIR}/pluginterfaces/base/funknown.cpp
	${vst3sdk_SOURCE_DIR}/pluginterfaces/base/ustring.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/common/commonstringconvert.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/common/memorystream.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/hosting/eventlist.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/hosting/hostclasses.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/hosting/parameterchanges.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/hosting/pluginterfacesupport.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/hosting/processdata.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/utility/stringconvert.cpp
	${vst3sdk_SOURCE_DIR}/public.sdk/source/vst/vstinitiids.cpp
)

set(CLIENT_SOURCES
	VST3Client.cpp
	VST3Instance.cpp
	VST3Loader_win32.cpp
	VST3Socket_win32.cpp
	VST3UI_win32.cpp
)

add_executable(tuxguitar-synth-vst3 ${CLIENT_SOURCES} ${VST3_SDK_SOURCES})

target_include_directories(tuxguitar-synth-vst3 PRIVATE ${vst3sdk_SOURCE_DIR})
target_compile_definitions(tuxguitar-synth-vst3 PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX _WIN32_WINNT=0x0A00 WINVER=0x0A00 $<$<CONFIG:Release>:RELEASE=1> $<$<CONFIG:Debug>:DEVELOPMENT=1>)

if(MINGW)
	# no runtime dlls to ship next to the executable
	target_link_options(tuxguitar-synth-vst3 PRIVATE -static -static-libgcc -static-libstdc++)
endif()

target_link_libraries(tuxguitar-synth-vst3 PRIVATE ws2_32 ole32 uuid shell32 user32 gdi32)
