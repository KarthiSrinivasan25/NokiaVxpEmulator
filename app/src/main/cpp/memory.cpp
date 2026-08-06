#include "memory.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr uint64_t kPageSize = 0x1000; // 4KB, required by Unicorn/QEMU

static uint64_t align_down(uint64_t addr) {
    return addr & ~(kPageSize - 1);
}

uint64_t vxp_align_size_to_page(uint64_t size) {
    if (size == 0) return kPageSize;
    return (size + (kPageSize - 1)) & ~(kPageSize - 1);
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
        err = uc_mem_write(uc, base, initialData, initialLen);
        if (err != UC_ERR_OK) {
            LOGE("uc_mem_write(addr=0x%llx, len=%zu) during map failed: %s",
                 (unsigned long long) base, initialLen, uc_strerror(err));
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

namespace {
bool vxp_on_invalid_mem(uc_engine* uc, uc_mem_type type, uint64_t address,
                         int size, int64_t value, void* /*userData*/) {
    uint32_t pc = 0;
    uc_reg_read(uc, UC_ARM_REG_PC, &pc);

    const char* kind =
        (type == UC_MEM_WRITE_UNMAPPED || type == UC_MEM_WRITE_PROT) ? "WRITE" :
        (type == UC_MEM_READ_UNMAPPED  || type == UC_MEM_READ_PROT)  ? "READ"  :
        (type == UC_MEM_FETCH_UNMAPPED || type == UC_MEM_FETCH_PROT) ? "FETCH" : "?";

    LOGE("MEM FAULT: %s addr=0x%llx size=%d value=0x%llx at PC=0x%08x",
         kind, (unsigned long long) address, size,
         (unsigned long long) value, pc);

    return false; // don't suppress the fault - just record it before it propagates
}
} // namespace

uint64_t vxp_install_fault_logger(uc_engine* uc) {
    if (uc == nullptr) return 0;
    uc_hook hook;
    uc_err err = uc_hook_add(
        uc, &hook, UC_HOOK_MEM_INVALID,
        reinterpret_cast<void*>(&vxp_on_invalid_mem), nullptr, 1, 0);
    if (err != UC_ERR_OK) {
        LOGE("uc_hook_add(MEM_INVALID) failed: %s", uc_strerror(err));
        return 0;
    }
    return static_cast<uint64_t>(hook);
}

void vxp_remove_fault_logger(uc_engine* uc, uint64_t hookHandle) {
    if (uc == nullptr || hookHandle == 0) return;
    uc_hook_del(uc, static_cast<uc_hook>(hookHandle));
}