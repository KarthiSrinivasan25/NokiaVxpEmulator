#include "unicorn_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


namespace {

uc_hook g_invalidWriteHook = 0;


bool onInvalidWrite(
        uc_engine* uc,
        uc_mem_type type,
        uint64_t address,
        int size,
        int64_t value,
        void* userData
) {

    LOGE(
        "UNMAPPED WRITE:"
        " addr=0x%llx"
        " size=%d"
        " value=0x%llx",
        (unsigned long long) address,
        size,
        (unsigned long long) value
    );


    // Read CPU registers at fault time
    uint32_t pc = 0;
    uint32_t sp = 0;
    uint32_t lr = 0;


    uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &pc
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_SP,
        &sp
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_LR,
        &lr
    );


    LOGE(
        "CPU STATE:"
        " PC=0x%x"
        " SP=0x%x"
        " LR=0x%x",
        pc,
        sp,
        lr
    );


    // false = Unicorn should stop with UC_ERR_WRITE_UNMAPPED
    return false;
}



bool onInvalidFetch(
        uc_engine* uc,
        uc_mem_type type,
        uint64_t address,
        int size,
        int64_t value,
        void* userData
) {

    LOGE(
        "UNMAPPED FETCH:"
        " addr=0x%llx",
        (unsigned long long) address
    );


    uint32_t pc = 0;

    uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &pc
    );


    LOGE(
        "FETCH PC=0x%x",
        pc
    );


    return false;
}

}



uc_engine* vxp_create_arm_engine() {

    uc_engine* uc = nullptr;


    uc_err err = uc_open(
            UC_ARCH_ARM,
            UC_MODE_ARM,
            &uc
    );


    if(err != UC_ERR_OK) {

        LOGE(
            "uc_open failed: %s",
            uc_strerror(err)
        );

        return nullptr;
    }



    /*
     * Debug hooks
     *
     * These show the exact address that causes:
     * UC_ERR_WRITE_UNMAPPED
     * UC_ERR_FETCH_UNMAPPED
     */


    err = uc_hook_add(
            uc,
            &g_invalidWriteHook,
            UC_HOOK_MEM_WRITE_UNMAPPED,
            (void*) onInvalidWrite,
            nullptr,
            1,
            0
    );


    if(err != UC_ERR_OK) {

        LOGE(
            "Write hook failed: %s",
            uc_strerror(err)
        );
    }



    uc_hook fetchHook;


    err = uc_hook_add(
            uc,
            &fetchHook,
            UC_HOOK_MEM_FETCH_UNMAPPED,
            (void*) onInvalidFetch,
            nullptr,
            1,
            0
    );


    if(err != UC_ERR_OK) {

        LOGE(
            "Fetch hook failed: %s",
            uc_strerror(err)
        );
    }



    LOGI(
        "Unicorn ARM engine created (lib version %u.%u)",
        UC_VERSION_MAJOR,
        UC_VERSION_MINOR
    );


    return uc;
}





void vxp_destroy_engine(uc_engine* uc) {

    if(uc == nullptr) {
        return;
    }


    uc_err err = uc_close(uc);


    if(err != UC_ERR_OK) {

        LOGE(
            "uc_close failed: %s",
            uc_strerror(err)
        );

    } else {

        LOGI(
            "Unicorn engine destroyed"
        );
    }
}