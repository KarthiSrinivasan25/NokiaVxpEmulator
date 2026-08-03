package com.nokia.vxp.debug

import com.nokia.vxp.cpu.CpuState
import com.nokia.vxp.cpu.Flags
import com.nokia.vxp.cpu.Registers

/**
 * Formats a CpuState register snapshot into human-readable lines for a
 * future debug UI (or just logcat). Split into two steps: capturing the
 * snapshot (needs a real CpuState, so untestable in a plain JVM test -
 * same native-backed limitation CpuState itself has) and formatting it
 * (pure, fully testable via formatSnapshot() directly).
 */
object RegisterViewer {

    fun captureAndFormat(cpuState: CpuState): List<String> = formatSnapshot(cpuState.snapshot())

    fun formatSnapshot(snapshot: Map<Registers, Long>): List<String> {
        val lines = mutableListOf<String>()

        for (row in Registers.generalPurpose.chunked(4)) {
            lines += row.joinToString("   ") { reg ->
                "%-3s 0x%08X".format(reg.name, snapshot[reg] ?: 0L)
            }
        }

        val sp = snapshot[Registers.SP] ?: 0L
        val lr = snapshot[Registers.LR] ?: 0L
        val pc = snapshot[Registers.PC] ?: 0L
        lines += "SP  0x%08X   LR  0x%08X   PC  0x%08X".format(sp, lr, pc)

        val cpsr = snapshot[Registers.CPSR] ?: 0L
        lines += "CPSR 0x%08X  [%s]".format(cpsr, Flags.describe(cpsr))

        return lines
    }
}
