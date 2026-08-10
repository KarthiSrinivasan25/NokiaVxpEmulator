// app/src/main/cpp/jni_cpu_bridge.cpp

#include <jni.h>
#include <stdint.h>

#include "unicorn/unicorn.h"

// -----------------------------------------------------------------------------
// This file intentionally contains NO JNI function definitions.
//
// All JNI entry points are implemented in cpu_bridge.cpp.
//
// Do NOT add:
//
//   Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister
//   Java_com_nokia_vxp_cpu_Executor_nativeRun
//   Java_com_nokia_vxp_cpu_Executor_nativeStep
//   Java_com_nokia_vxp_cpu_Executor_nativeStop
//
// here.
//
// Having them in both cpu_bridge.cpp and this file causes:
//     ld.lld: error: duplicate symbol
// -----------------------------------------------------------------------------

extern "C" {

uc_err vxp_set_register(
        uc_engine* uc,
        int regId,
        uint32_t value);

uc_err vxp_run(
        uc_engine* uc,
        uint64_t start,
        uint64_t end,
        uint64_t timeout,
        uint64_t count);

uc_err vxp_step(
        uc_engine* uc);

uc_err vxp_stop(
        uc_engine* uc);

}