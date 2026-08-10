#include <jni.h>
#include <cstdint>

#include <unicorn/unicorn.h>


// Functions implemented by cpu_bridge.cpp.
uint32_t vxp_get_register(
    uc_engine* uc,
    int reg
);

void vxp_set_register(
    uc_engine* uc,
    int reg,
    uint32_t value
);

uc_err vxp_run(
    uc_engine* uc,
    uint64_t start,
    uint64_t end,
    uint64_t timeout,
    uint64_t count
);

uc_err vxp_step(
    uc_engine* uc
);

uc_err vxp_stop(
    uc_engine* uc
);


// ============================================================================
// CpuState.nativeGetRegister
// ============================================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeGetRegister(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle,
    jint reg
) {
    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        return 0;
    }

    return static_cast<jint>(
        vxp_get_register(
            uc,
            static_cast<int>(reg)
        )
    );
}


// ============================================================================
// CpuState.nativeSetRegister
// ============================================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle,
    jint reg,
    jint value
) {
    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        return;
    }

    vxp_set_register(
        uc,
        static_cast<int>(reg),
        static_cast<uint32_t>(value)
    );
}


// ============================================================================
// Executor.nativeRun
// ============================================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeRun(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle,
    jlong start,
    jlong end,
    jlong timeout,
    jlong count
) {
    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        return static_cast<jint>(UC_ERR_HANDLE);
    }

    uc_err err = vxp_run(
        uc,
        static_cast<uint64_t>(start),
        static_cast<uint64_t>(end),
        static_cast<uint64_t>(timeout),
        static_cast<uint64_t>(count)
    );

    return static_cast<jint>(err);
}


// ============================================================================
// Executor.nativeStep
// ============================================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStep(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle
) {
    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        return static_cast<jint>(UC_ERR_HANDLE);
    }

    return static_cast<jint>(
        vxp_step(uc)
    );
}


// ============================================================================
// Executor.nativeStop
// ============================================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStop(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle
) {
    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        return static_cast<jint>(UC_ERR_HANDLE);
    }

    return static_cast<jint>(
        vxp_stop(uc)
    );
}