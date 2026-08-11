#pragma once

#include "arm_cpu.h"
#include <cstdint>
#include <cstddef>
#include <unordered_map>
#include <memory>

// Permission bits, deliberately using the same values MemoryManager.kt's
// Kotlin side already passes down (UC_PROT_* had READ=1, WRITE=2, EXEC=4 -
// kept identical here so nothing on the Kotlin/JNI side needs to change).
enum VxpProt : uint32_t {
    VXP_PROT_NONE = 0,
    VXP_PROT_READ = 1,
    VXP_PROT_WRITE = 2,
    VXP_PROT_EXEC = 4,
    VXP_PROT_ALL = VXP_PROT_READ | VXP_PROT_WRITE | VXP_PROT_EXEC,
};


// Sparse, page-granular guest address space. Real VXP/MRE modules only
// ever map a handful of regions (code segment, data segment, heap, stack),
// so a hash map keyed by page index is simple and plenty fast - no need
// for Unicorn/QEMU's TLB machinery for an interpret-only CPU like this one.
class VxpMemory : public ArmMemoryBus {
public:
    // Do not call this PAGE_SIZE: Android's sys/user.h defines PAGE_SIZE
    // as a preprocessor macro.
    static constexpr uint64_t VXP_PAGE_SIZE = 0x1000;

    // Maps [base, base+size) with the given permissions, aligned exactly
    // like the old vxp_map_region() did (align base down, extend size to
    // cover the resulting slack, then round up to a page). If initialData
    // is non-null, it's written at the *unaligned* base address - this
    // write bypasses permission checks (mirrors Unicorn's uc_mem_write
    // semantics: a direct API write is a "backdoor" DMA-style access, not
    // a guest CPU access, so it's not gated by the region's own perms).
    bool mapRegion(uint64_t base, uint64_t size, uint32_t perms,
                   const uint8_t* initialData, size_t initialLen);

    // Direct API read/write - same "backdoor" semantics as above (used by
    // MemoryManager.kt's read/write JNI calls, not by guest instructions).
    bool apiRead(uint64_t address, uint8_t* out, size_t len) const;
    bool apiWrite(uint64_t address, const uint8_t* data, size_t len);

    static uint64_t alignSizeToPage(uint64_t size) {
        if (size == 0) return VXP_PAGE_SIZE;
        return (size + (VXP_PAGE_SIZE - 1)) & ~(VXP_PAGE_SIZE - 1);
    }

    // ArmMemoryBus - guest CPU-issued accesses, which DO respect permissions.
    bool read(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) override;
    bool write(uint32_t address, const uint8_t* data, size_t len, ArmFault* fault) override;
    bool fetch(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) override;

private:
    struct Page {
        uint32_t perms = 0;
        std::unique_ptr<uint8_t[]> data;

        Page() : data(new uint8_t[VXP_PAGE_SIZE]()) {}
    };

    Page* findPage(uint64_t address) const;
    Page* getOrCreatePage(uint64_t pageIndex);

    // mutable so the const apiRead/read paths can lazily look up without
    // needing two near-identical const/non-const map accessors.
    mutable std::unordered_map<uint64_t, std::unique_ptr<Page>> pages_;

    bool accessChecked(uint32_t address, uint8_t* out, const uint8_t* in, size_t len,
                       uint32_t requiredPerm, ArmFault unmappedFault, ArmFault protFault,
                       ArmFault* fault);
};
