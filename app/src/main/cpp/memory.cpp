#include "memory.h"

#include <android/log.h>
#include <unicorn/unicorn.h>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


static constexpr uint64_t VXP_PAGE_SIZE  = 0x1000;


static uint64_t align_down(uint64_t address) {
    return address & ~(VXP_PAGE_SIZE  - 1);
}


static uint64_t align_up(uint64_t value) {

    if (value == 0) {
        return VXP_PAGE_SIZE ;
    }

    return (value + VXP_PAGE_SIZE  - 1) &
           ~(VXP_PAGE_SIZE  - 1);
}


uint64_t vxp_align_size_to_page(uint64_t size) {
    return align_up(size);
}


bool vxp_map_region(
        uc_engine* uc,
        uint64_t base,
        uint64_t size,
        uint32_t perms,
        const uint8_t* initialData,
        size_t initialLen) {

    if (uc == nullptr) {

        LOGE(
            "vxp_map_region FAILED: uc == NULL"
        );

        return false;
    }

    if (size == 0) {

        LOGE(
            "vxp_map_region FAILED: size == 0 "
            "base=0x%llx",
            (unsigned long long)base
        );

        return false;
    }


    const uint64_t alignedBase =
        align_down(base);

    const uint64_t frontSlack =
        base - alignedBase;

    const uint64_t alignedSize =
        align_up(size + frontSlack);


    LOGI(
        "Attempting memory map:"
        " logicalBase=0x%llx"
        " logicalSize=0x%llx"
        " alignedBase=0x%llx"
        " alignedSize=0x%llx"
        " perms=%u",
        (unsigned long long)base,
        (unsigned long long)size,
        (unsigned long long)alignedBase,
        (unsigned long long)alignedSize,
        perms
    );


    uc_err err =
        uc_mem_map(
            uc,
            alignedBase,
            alignedSize,
            perms
        );


    if (err != UC_ERR_OK) {

        LOGE("========================================");
        LOGE("          MEMORY MAP FAILED");
        LOGE("========================================");

        LOGE(
            "Requested base : 0x%08llx",
            (unsigned long long)base
        );

        LOGE(
            "Requested size : 0x%08llx",
            (unsigned long long)size
        );

        LOGE(
            "Aligned base   : 0x%08llx",
            (unsigned long long)alignedBase
        );

        LOGE(
            "Aligned size   : 0x%08llx",
            (unsigned long long)alignedSize
        );

        LOGE(
            "Permissions    : %u",
            perms
        );

        LOGE(
            "Unicorn error  : %d (%s)",
            err,
            uc_strerror(err)
        );

        LOGE("========================================");

        return false;
    }


    LOGI(
        "Memory mapped successfully:"
        " 0x%08llx - 0x%08llx",
        (unsigned long long)alignedBase,
        (unsigned long long)
            (alignedBase + alignedSize)
    );


    if (initialData != nullptr &&
        initialLen > 0) {

        if (initialLen > alignedSize - frontSlack) {

            LOGE(
                "Initial data too large:"
                " initialLen=%zu"
                " available=%llu",
                initialLen,
                (unsigned long long)
                    (alignedSize - frontSlack)
            );

            return false;
        }


        err =
            uc_mem_write(
                uc,
                base,
                initialData,
                initialLen
            );


        if (err != UC_ERR_OK) {

            LOGE(
                "Initial memory write FAILED:"
                " address=0x%08llx"
                " length=%zu"
                " error=%s",
                (unsigned long long)base,
                initialLen,
                uc_strerror(err)
            );

            return false;
        }
    }


    return true;
}


bool vxp_write_memory(
        uc_engine* uc,
        uint64_t address,
        const uint8_t* data,
        size_t len) {

    if (uc == nullptr ||
        data == nullptr ||
        len == 0) {

        return false;
    }


    uc_err err =
        uc_mem_write(
            uc,
            address,
            data,
            len
        );


    if (err != UC_ERR_OK) {

        LOGE(
            "WRITE FAILED:"
            " address=0x%08llx"
            " size=%zu"
            " error=%s",
            (unsigned long long)address,
            len,
            uc_strerror(err)
        );

        return false;
    }

    return true;
}


bool vxp_read_memory(
        uc_engine* uc,
        uint64_t address,
        uint8_t* outBuffer,
        size_t len) {

    if (uc == nullptr ||
        outBuffer == nullptr ||
        len == 0) {

        return false;
    }


    uc_err err =
        uc_mem_read(
            uc,
            address,
            outBuffer,
            len
        );


    if (err != UC_ERR_OK) {

        LOGE(
            "READ FAILED:"
            " address=0x%08llx"
            " size=%zu"
            " error=%s",
            (unsigned long long)address,
            len,
            uc_strerror(err)
        );

        return false;
    }

    return true;
}