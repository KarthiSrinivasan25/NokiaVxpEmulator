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
    set(VXP_HAVE_UNICORN TRUE)
else()
    message(WARNING
        "external/unicorn not found at ${UNICORN_DIR} - "
        "clone https://github.com/unicorn-engine/unicorn into external/unicorn "
        "before building. memory.cpp/unicorn_bridge.cpp/jni_bridge.cpp depend on "
        "<unicorn/unicorn.h> and will be EXCLUDED from this build until then; "
        "only the native-lib.cpp scaffold will be compiled.")
    set(VXP_HAVE_UNICORN FALSE)
endif()

# ---------------------------------------------------------------------------
# Our native sources. The Unicorn-dependent files are only compiled once
# external/unicorn is actually present (see above), so a fresh checkout
# without the submodule still configures and builds successfully.
# ---------------------------------------------------------------------------
set(VXP_SOURCES native-lib.cpp)

if(VXP_HAVE_UNICORN)
    list(APPEND VXP_SOURCES
        jni_bridge.cpp
        unicorn_bridge.cpp
        memory.cpp
        cpu_bridge.cpp
        jni_cpu_bridge.cpp
        vm_dispatch_bridge.cpp
        jni_mre_bridge.cpp
    )
endif()

add_library(
        vxpnative
        SHARED
        ${VXP_SOURCES}
)

if(VXP_HAVE_UNICORN)
    target_include_directories(
            vxpnative
            PRIVATE
            ${UNICORN_DIR}/include
    )
endif()

find_library(log-lib log)
find_library(android-lib android)

if(VXP_HAVE_UNICORN AND TARGET unicorn)
    target_link_libraries(vxpnative unicorn ${log-lib} ${android-lib})
else()
    target_link_libraries(vxpnative ${log-lib} ${android-lib})
endif()
