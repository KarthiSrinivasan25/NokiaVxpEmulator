package com.nokia.vxp.input

/**
 * Classic Nokia S40/MRE-era keypad keys. guestCode is what gets sent to
 * the guest via emulator.Emulator.sendKeyDown/Up (as EmulatorEvent's raw
 * Int keyCode) - once mre/VmInput exists it'll read this value out of
 * whatever the guest's input-polling syscall expects.
 *
 * IMPORTANT: Nokia's real MRE key code table was never published. The
 * digit/star/pound values below follow the common MIDP/J2ME convention
 * (ASCII values: '0'-'9' = 48-57, '*' = 42, '#' = 35) since VXP/MRE grew
 * out of that ecosystem - but the navigation/softkey/call/end codes are
 * placeholders and will likely need correcting once real captured input
 * traffic or a disassembled MRE key-handling routine confirms them.
 */
enum class NokiaKey(val label: String, val guestCode: Int) {
    SOFT_LEFT("LEFT SOFT", -6),
    SOFT_RIGHT("RIGHT SOFT", -7),
    CALL("CALL", -10),
    END("END", -11),
    UP("UP", -1),
    DOWN("DOWN", -2),
    LEFT("LEFT", -3),
    RIGHT("RIGHT", -4),
    SELECT("SELECT", -5),
    NUM0("0", 48),
    NUM1("1", 49),
    NUM2("2", 50),
    NUM3("3", 51),
    NUM4("4", 52),
    NUM5("5", 53),
    NUM6("6", 54),
    NUM7("7", 55),
    NUM8("8", 56),
    NUM9("9", 57),
    STAR("*", 42),
    POUND("#", 35);

    companion object {
        fun fromGuestCode(code: Int): NokiaKey? = values().firstOrNull { it.guestCode == code }
    }
}
