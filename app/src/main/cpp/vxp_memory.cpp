#include "vxp_memory.h"
#include <cstring>

namespace {
inline uint64_t pageIndexOf(uint64_t address) { return address / VxpMemory::PAGE_SIZE; }
inline uint64_t alignDown(uint64_t addr) { return addr & ~(VxpMemory::PAGE_SIZE - 1); }
} // namespace

VxpMemory::Page* VxpMemory::findPage(uint64_t address) const {
    auto it = pages_.find(pageIndexOf(address));
    return it == pages_.end() ? nullptr : it->second.get();
}

VxpMemory::Page* VxpMemory::getOrCreatePage(uint64_t pageIndex) {
    auto it = pages_.find(pageIndex);
    if (it != pages_.end()) return it->second.get();
    auto page = std::make_unique<Page>();
    Page* raw = page.get();
    pages_.emplace(pageIndex, std::move(page));
    return raw;
}

bool VxpMemory::mapRegion(uint64_t base, uint64_t size, uint32_t perms,
                           const uint8_t* initialData, size_t initialLen) {
    if (size == 0) return false;

    const uint64_t alignedBase = alignDown(base);
    const uint64_t frontSlack = base - alignedBase;
    const uint64_t alignedSize = alignSizeToPage(size + frontSlack);

    uint64_t firstPage = pageIndexOf(alignedBase);
    uint64_t pageCount = alignedSize / PAGE_SIZE;
    for (uint64_t i = 0; i < pageCount; i++) {
        Page* page = getOrCreatePage(firstPage + i);
        page->perms = perms;
    }

    if (initialData != nullptr && initialLen > 0) {
        // Bypasses permission checks - matches uc_mem_write's "backdoor" semantics.
        if (!apiWrite(base, initialData, initialLen)) return false;
    }
    return true;
}

bool VxpMemory::apiRead(uint64_t address, uint8_t* out, size_t len) const {
    for (size_t i = 0; i < len; i++) {
        Page* page = findPage(address + i);
        out[i] = page ? page->data[(address + i) % PAGE_SIZE] : 0;
        if (page == nullptr) return false;
    }
    return true;
}

bool VxpMemory::apiWrite(uint64_t address, const uint8_t* data, size_t len) {
    for (size_t i = 0; i < len; i++) {
        uint64_t addr = address + i;
        Page* page = getOrCreatePage(pageIndexOf(addr));
        // apiWrite is used both for initial segment loading (into pages we
        // just created, with whatever perms mapRegion set) and generic
        // debug/loader writes - if a caller writes to a genuinely
        // never-mapped address this silently creates a RW page rather
        // than failing, which matches how a loader populating fresh
        // memory ahead of mapping metadata would expect this to behave.
        if (page->perms == 0) page->perms = VXP_PROT_READ | VXP_PROT_WRITE;
        page->data[addr % PAGE_SIZE] = data[i];
    }
    return true;
}

bool VxpMemory::accessChecked(uint32_t address, uint8_t* out, const uint8_t* in, size_t len,
                               uint32_t requiredPerm, ArmFault unmappedFault, ArmFault protFault,
                               ArmFault* fault) {
    for (size_t i = 0; i < len; i++) {
        uint64_t addr = static_cast<uint64_t>(address) + i;
        Page* page = findPage(addr);
        if (page == nullptr) { *fault = unmappedFault; return false; }
        if ((page->perms & requiredPerm) == 0) { *fault = protFault; return false; }
        if (out != nullptr) out[i] = page->data[addr % PAGE_SIZE];
        if (in != nullptr) page->data[addr % PAGE_SIZE] = in[i];
    }
    return true;
}

bool VxpMemory::read(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) {
    return accessChecked(address, out, nullptr, len, VXP_PROT_READ,
                          ArmFault::ReadUnmapped, ArmFault::ReadProt, fault);
}

bool VxpMemory::write(uint32_t address, const uint8_t* data, size_t len, ArmFault* fault) {
    return accessChecked(address, nullptr, data, len, VXP_PROT_WRITE,
                          ArmFault::WriteUnmapped, ArmFault::WriteProt, fault);
}

bool VxpMemory::fetch(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) {
    return accessChecked(address, out, nullptr, len, VXP_PROT_EXEC,
                          ArmFault::FetchUnmapped, ArmFault::FetchProt, fault);
}
