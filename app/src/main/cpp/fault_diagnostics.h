#pragma once

#include "cpu_bridge.h"
#include <cstdint>

// Installs a diagnostic-only hook on [engine] that logs full detail about
// invalid memory accesses (unmapped read/write/fetch, protection
// violations) - the exact faulting address, access size, and current
// PC/SP/LR - before letting the fault propagate exactly as it did
// before this hook existed. This never "handles" the access (the custom
// interpreter has no such concept for a diagnostics-only hook - it always
// runs, and the real error still propagates back to Executor.kt via the
// return value of vxp_run()/vxp_step()); it exists purely so a bare error
// like "invalid memory write (unmapped)" becomes traceable in logcat to
// an actual guest address and PC, instead of leaving no way to tell what
// the guest was even trying to do.
//
// Unlike mre/VmDispatcher's hook, this one does no JNI callback at all
// (pure C++ logging via __android_log_print), so it has no thread-
// affinity requirement - safe to install from any thread.
//
// Returns an opaque hook handle (0 on failure/null engine).
uint64_t vxp_install_fault_diagnostics_hook(VxpEngine* engine);

void vxp_remove_fault_diagnostics_hook(VxpEngine* engine, uint64_t hookHandle);
