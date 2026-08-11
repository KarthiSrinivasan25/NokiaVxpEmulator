cmake_minimum_required(VERSION 3.22.1)
project("nokiavxpemulator")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# CPU emulation backend: a custom ARMv4T (ARM7TDMI-class) fetch-decode-
# execute interpreter (arm_cpu.cpp/.h), backed by our own sparse paged
# guest address space (vxp_memory.cpp/.h) and glued together for JNI via
# cpu_bridge.cpp/.h. No external CPU emulation library (Unicorn/QEMU) is
# used or required - this always builds, on every platform, with nothing
# beyond the NDK's own toolchain.
# ---------------------------------------------------------------------------
set(VXP_SOURCES
        native-lib.cpp
        arm_cpu.cpp
        vxp_memory.cpp
        cpu_bridge.cpp
        jni_bridge.cpp
        jni_cpu_bridge.cpp
        vm_dispatch_bridge.cpp
        jni_mre_bridge.cpp
        fault_diagnostics.cpp
)

add_library(
        vxpnative
        SHARED
        ${VXP_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(vxpnative ${log-lib} ${android-lib})
