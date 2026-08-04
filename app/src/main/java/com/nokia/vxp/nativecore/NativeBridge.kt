package com.nokia.vxp.nativecore

/**
 * Thin JNI bridge to the native (C++) emulator core in app/src/main/cpp.
 *
 * This scaffold only proves the JNI link end-to-end (library loads,
 * a native method can be called and returns a value). Real emulation
 * entry points (memory map setup, Unicorn context creation, VXP module
 * loading, the run loop) will be added here as their corresponding
 * modules (memory/, loader/, emulator/) are implemented.
 */
object NativeBridge {

    private var loaded = false
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("vxpnative")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message
            loaded = false
        }
    }

    val isLoaded: Boolean get() = loaded
    val lastLoadError: String? get() = loadError

    /** Returns a human-readable version/build string from the native side. */
    external fun getNativeVersion(): String

    /**
     * One-time native init hook. Currently a no-op on the C++ side
     * (see native-lib.cpp) - real Unicorn/memory setup lands here later.
     */
    external fun nativeInit(): Boolean
}

