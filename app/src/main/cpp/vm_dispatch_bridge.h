#pragma once

#include <unicorn/unicorn.h>
#include <jni.h>
#include <cstdint>

// Installs a guest-call trap on [uc]: whenever guest code attempts to
// execute (fetch) from an address we haven't mapped, this calls back
// into the given VmDispatcher Kotlin instance's
// onSyscallTrap(address, r0, r1, r2, r3): Long method.
//
// This stands in for "the guest just called an MRE OS API function".
// Real MRE binaries resolve vm_* calls against the phone's actual
// firmware (a fixed jump table or resolved relocations we don't have
// access to without a real firmware dump), so we don't know the real
// addresses those calls target. Trapping ANY unmapped-fetch and letting
// VmDispatcher decide (by address) whether it recognizes the call is a
// reasonable stand-in: if VmDispatcher has no handler registered for
// that address, we return false and let Unicorn raise the real fault,
// so genuine bugs still surface as crashes rather than being silently
// swallowed. See mre/VmDispatcher.kt for the full rationale and the
// registered (placeholder) address table.
//
// IMPORTANT - THREAD AFFINITY: [env] is cached for reuse inside the hook
// callback, which fires synchronously during uc_emu_start on whatever
// thread called it. This function MUST be called from the same thread
// that will later call Executor.run()/step() (i.e. call this from
// EmulatorLoop's own thread, not from wherever Runtime/MemoryManager
// were constructed) - JNIEnv pointers are strictly thread-local, and
// using one cached from a different thread will crash.
//
// Returns an opaque hook handle (0 on failure).
uint64_t vxp_install_dispatch_hook(JNIEnv* env, uc_engine* uc, jobject dispatcherGlobalRef);

void vxp_remove_dispatch_hook(uc_engine* uc, uint64_t hookHandle);
