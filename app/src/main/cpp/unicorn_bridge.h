#pragma once

#include <unicorn/unicorn.h>

// Creates a fresh Unicorn engine configured for ARM.
// UC_MODE_ARM (not Thumb-only) is used because real ARM7/ARM9-class
// firmware mixes ARM and Thumb instruction streams via BX-style mode
// switches at runtime; Unicorn handles that switching internally once
// the CPU module starts executing code, so we don't track it ourselves
// here at engine-creation time.
// Returns nullptr on failure (check logcat tag "VxpNative" for uc_strerror()).
uc_engine* vxp_create_arm_engine();

// Safe to call with nullptr.
void vxp_destroy_engine(uc_engine* uc);
