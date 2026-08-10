#pragma once

#include <jni.h>
#include <cstdint>

#include <unicorn/unicorn.h>

uint64_t vxp_install_dispatch_hook(
        JNIEnv* env,
        uc_engine* uc,
        jobject dispatcherGlobalRef
);

void vxp_remove_dispatch_hook(
        uc_engine* uc,
        uint64_t hookHandle
);