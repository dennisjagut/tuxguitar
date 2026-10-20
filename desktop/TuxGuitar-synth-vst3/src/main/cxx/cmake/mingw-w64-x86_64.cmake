# Cross compile the VST3 client for Windows x86_64 with mingw-w64 (posix thread model, required for std::thread)
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

set(MINGW_PREFIX x86_64-w64-mingw32)

find_program(MINGW_CXX NAMES ${MINGW_PREFIX}-g++-posix ${MINGW_PREFIX}-g++ REQUIRED)
find_program(MINGW_CC NAMES ${MINGW_PREFIX}-gcc-posix ${MINGW_PREFIX}-gcc REQUIRED)
find_program(MINGW_RC NAMES ${MINGW_PREFIX}-windres)

set(CMAKE_C_COMPILER ${MINGW_CC})
set(CMAKE_CXX_COMPILER ${MINGW_CXX})
if(MINGW_RC)
	set(CMAKE_RC_COMPILER ${MINGW_RC})
endif()

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
