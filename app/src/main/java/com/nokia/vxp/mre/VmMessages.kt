package com.nokia.vxp.mre

/**
 * VM_MSG_* system event codes delivered to the guest's registered
 * handle_sysevt(VMINT message, VMINT param) callback (see
 * mre.SysEventRegistry / mre.VmSystem.callGuestFunction).
 *
 * Only VM_MSG_CREATE's value is actually confirmed - found consistently
 * in two independent sources describing real MRE app startup:
 * UstadMobile/ustadmobile-mre's README states the guest's vm_main()
 * registers its event/keyboard/pen handlers, after which
 * handle_sysevt(message=4, param=0) is called as the first real event.
 * No other VM_MSG_* values were confirmed during research for this
 * project - deliberately not guessing further ones here rather than
 * presenting fabricated constants as real.
 */
object VmMessages {
    const val VM_MSG_CREATE = 4
}
