#pragma once

#include <unicorn/unicorn.h>
#include <cstdint>

// Installs a diagnostic-only hook on [uc] that logs full detail about
// invalid memory accesses (unmapped read/write/fetch, protection
// violations) - the exact faulting address, access size, and current
// PC/SP/LR - before letting the fault propagate exactly as it did
// before this hook existed. ALWAYS returns false from the callback
// (never "handles" the access) - this exists purely so a bare error
// like "invalid memory write (UC_ERR_WRITE_UNMAPPED)" becomes
// traceable in logcat to an actual guest address and PC, instead of
// leaving no way to tell what the guest was even trying to do.
//
// Unlike mre/VmDispatcher's hook, this one does no JNI callback at all
// (pure C++ logging via __android_log_print), so it has no thread-
// affinity requirement - safe to install from any thread.
//
// Returns an opaque hook handle (0 on failure).
uint64_t vxp_install_fault_diagnostics_hook(uc_engine* uc);

void vxp_remove_fault_diagnostics_hook(uc_engine* uc, uint64_t hookHandle);
