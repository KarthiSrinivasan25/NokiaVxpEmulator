cmake_minimum_required(VERSION 3.22.1)
project("nokiavxpemulator")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Unicorn Engine (external/unicorn at repo root), built from source as a
# static lib. Restricted to the ARM target since VXP/MRE binaries run on
# ARM7/ARM9-class cores - this keeps build time and binary size down
# compared to building all of Unicorn's supported architectures.
# ---------------------------------------------------------------------------
set(UNICORN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/unicorn)

set(UNICORN_ARCH "arm" CACHE STRING "" FORCE)
set(UNICORN_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(UNICORN_INSTALL OFF CACHE BOOL "" FORCE)
set(UNICORN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(UNICORN_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)

if(EXISTS ${UNICORN_DIR}/CMakeLists.txt)
    add_subdirectory(${UNICORN_DIR} ${CMAKE_CURRENT_BINARY_DIR}/unicorn-build)
else()
    message(WARNING
        "external/unicorn not found at ${UNICORN_DIR} - "
        "clone https://github.com/unicorn-engine/unicorn into external/unicorn "
        "before building the CPU/emulator modules. native-lib scaffold will "
        "still build without it for now.")
endif()

# ---------------------------------------------------------------------------
# Our native sources. Only native-lib.cpp exists at this scaffolding stage;
# the rest are added to this list as each module (memory, loader, graphics,
# audio, input, mre, arm interpreter, unicorn bridge) gets implemented.
# ---------------------------------------------------------------------------
add_library(
        vxpnative
        SHARED
        native-lib.cpp
)

target_include_directories(
        vxpnative
        PRIVATE
        ${UNICORN_DIR}/include
)

find_library(log-lib log)
find_library(android-lib android)

if(TARGET unicorn)
    target_link_libraries(vxpnative unicorn ${log-lib} ${android-lib})
else()
    target_link_libraries(vxpnative ${log-lib} ${android-lib})
endif()
