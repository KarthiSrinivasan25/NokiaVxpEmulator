#include "unicorn_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

uc_engine* vxp_create_arm_engine() {
    uc_engine* uc = nullptr;

    uc_err err = uc_open(
        UC_ARCH_ARM,
        UC_MODE_ARM | UC_MODE_THUMB,
        &uc
    );

    if (err != UC_ERR_OK) {
        LOGE("uc_open failed: %s", uc_strerror(err));
        return nullptr;
    }

    LOGI("Unicorn ARM/THUMB engine created");

    return uc;
}

void vxp_destroy_engine(uc_engine* uc) {
    if (uc == nullptr) {
        return;
    }
    uc_err err = uc_close(uc);
    if (err != UC_ERR_OK) {
        LOGE("uc_close failed: %s", uc_strerror(err));
    } else {
        LOGI("Unicorn engine destroyed");
    }
}
