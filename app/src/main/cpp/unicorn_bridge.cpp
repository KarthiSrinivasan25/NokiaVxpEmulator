#include "unicorn_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

uc_engine* vxp_create_arm_engine(bool thumb) {
    uc_engine* uc = nullptr;

    uc_mode mode = thumb ? UC_MODE_THUMB : UC_MODE_ARM;

    uc_err err = uc_open(UC_ARCH_ARM, mode, &uc);

    if (err != UC_ERR_OK) {
        LOGE("uc_open failed: %s", uc_strerror(err));
        return nullptr;
    }

    LOGI("Unicorn engine created (%s mode)", thumb ? "THUMB" : "ARM");

    return uc;
}

void vxp_destroy_engine(uc_engine* uc) {
    if (!uc)
        return;

    uc_err err = uc_close(uc);

    if (err != UC_ERR_OK) {
        LOGE("uc_close failed: %s", uc_strerror(err));
    } else {
        LOGI("Unicorn engine destroyed");
    }
}