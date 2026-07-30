package com.nokia.vxp.cpu

/**
 * ARM register identifiers used across the emulator. These are NOT
 * Unicorn's raw UC_ARM_REG_* values - those are resolved symbolically in
 * cpu_bridge.cpp (against the real <unicorn/unicorn.h>), so this enum
 * stays stable even if Unicorn's internal numbering ever changes.
 *
 * IMPORTANT: the `id` values here must exactly match the VxpRegisterId
 * enum in app/src/main/cpp/cpu_bridge.h. If you add a register on one
 * side, add it on the other.
 */
enum class Registers(val id: Int) {
    R0(0), R1(1), R2(2), R3(3), R4(4), R5(5), R6(6), R7(7),
    R8(8), R9(9), R10(10), R11(11), R12(12),
    SP(13), LR(14), PC(15),
    CPSR(16);

    companion object {
        fun fromId(id: Int): Registers = values().first { it.id == id }

        /** General-purpose registers only, excluding SP/LR/PC/CPSR - handy for register dump UIs. */
        val generalPurpose: List<Registers> = listOf(
            R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12
        )
    }
}
