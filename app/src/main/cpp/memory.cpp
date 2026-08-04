#include "memory.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr uint64_t VXP_PAGE_SIZE = 0x1000; // 4KB, required by Unicorn/QEMU

static uint64_t align_down(uint64_t addr) {
    return addr & ~(VXP_PAGE_SIZE - 1);
}

uint64_t vxp_align_size_to_page(uint64_t size) {
    if (size == 0) return VXP_PAGE_SIZE;
    return (size + (VXP_PAGE_SIZE - 1)) & ~(VXP_PAGE_SIZE - 1);
}

bool vxp_map_region(uc_engine* uc, uint64_t base, uint64_t size, uint32_t perms,
                     const uint8_t* initialData, size_t initialLen) {
    if (uc == nullptr || size == 0) {
        LOGE("vxp_map_region: invalid engine or zero size");
        return false;
    }

    const uint64_t alignedBase = align_down(base);
    // Extra bytes lost by aligning the base downward must be added back
    // before we align the size up, or the mapping could end short of
    // where the caller expects [base, base+size) to actually end.
    const uint64_t frontSlack = base - alignedBase;
    const uint64_t alignedSize = vxp_align_size_to_page(size + frontSlack);

    uc_err err = uc_mem_map(uc, alignedBase, alignedSize, perms);
    if (err != UC_ERR_OK) {
        LOGE("uc_mem_map(base=0x%llx, size=0x%llx, perms=%u) failed: %s",
             (unsigned long long) alignedBase, (unsigned long long) alignedSize,
             perms, uc_strerror(err));
        return false;
    }

    if (initialData != nullptr && initialLen > 0) {

    if (initialLen > size) {
        LOGE("initial data larger than mapped region: data=%zu size=0x%llx",
             initialLen,
             (unsigned long long)size);
        return false;
    }

    err = uc_mem_write(uc, base, initialData, initialLen);

    if (err != UC_ERR_OK) {
        LOGE("uc_mem_write(addr=0x%llx, len=%zu) during map failed: %s",
             (unsigned long long) base,
             initialLen,
             uc_strerror(err));
        return false;
    }
}

    LOGI("Mapped region: logicalBase=0x%llx size=0x%llx (aligned base=0x%llx size=0x%llx) perms=%u",
         (unsigned long long) base, (unsigned long long) size,
         (unsigned long long) alignedBase, (unsigned long long) alignedSize, perms);
    return true;
}

bool vxp_write_memory(uc_engine* uc, uint64_t address, const uint8_t* data, size_t len) {
    if (uc == nullptr || data == nullptr || len == 0) return false;

    uc_err err = uc_mem_write(uc, address, data, len);
    if (err != UC_ERR_OK) {
        LOGE("uc_mem_write(addr=0x%llx, len=%zu) failed: %s",
             (unsigned long long) address, len, uc_strerror(err));
        return false;
    }
    return true;
}

bool vxp_read_memory(uc_engine* uc, uint64_t address, uint8_t* outBuffer, size_t len) {
    if (uc == nullptr || outBuffer == nullptr || len == 0) return false;

    uc_err err = uc_mem_read(uc, address, outBuffer, len);
    if (err != UC_ERR_OK) {
        LOGE("uc_mem_read(addr=0x%llx, len=%zu) failed: %s",
             (unsigned long long) address, len, uc_strerror(err));
        return false;
    }
    return true;
}
