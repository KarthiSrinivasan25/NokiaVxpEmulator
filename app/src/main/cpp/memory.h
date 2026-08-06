#pragma once

#include <unicorn/unicorn.h>
#include <cstdint>
#include <cstddef>

// Unicorn (via its embedded QEMU) requires mapped regions to be 4KB-page
// aligned in both base address and size. VXP header-derived offsets won't
// generally satisfy that on their own, so vxp_map_region() aligns down/up
// as needed - callers just pass the "logical" base/size they want visible.

// Maps [base, base+size) with the given UC_PROT_* perms bitmask, then
// (if initialData != nullptr) writes initialData at the *unaligned*
// base address. Returns false and logs the Unicorn error on failure.
bool vxp_map_region(uc_engine* uc, uint64_t base, uint64_t size, uint32_t perms,
                     const uint8_t* initialData, size_t initialLen);

bool vxp_write_memory(uc_engine* uc, uint64_t address, const uint8_t* data, size_t len);

bool vxp_read_memory(uc_engine* uc, uint64_t address, uint8_t* outBuffer, size_t len);

// Rounds size up to Unicorn's required page granularity (4KB).
uint64_t vxp_align_size_to_page(uint64_t size);
