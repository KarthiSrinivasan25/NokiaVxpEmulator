package com.nokia.vxp.nativecore

/**

* Thin JNI bridge to the native C++ emulator core.
*
* This object:
* * loads libvxpnative.so
* * verifies the native library through nativeInit()
* * exposes the native build/version string
*
* Per-VXP Unicorn engine creation is intentionally handled by
* MemoryManager/Runtime rather than here.
  */
  object NativeBridge {

  private var loaded = false
  private var initialized = false
  private var loadError: String? = null

  init {
  try {
  System.loadLibrary("vxpnative")
  loaded = true


       // Verify that the JNI bridge is callable.
       initialized = nativeInit()

       if (!initialized) {
           loadError = "nativeInit() returned false"
       }
   } catch (e: UnsatisfiedLinkError) {
       loaded = false
       initialized = false
       loadError = e.message
   } catch (e: Exception) {
       loaded = false
       initialized = false
       loadError = e.message
   }


  }

  /**

  * True when libvxpnative.so was loaded successfully.
    */
    val isLoaded: Boolean
    get() = loaded

  /**

  * True when the native initialization check completed successfully.
    */
    val isInitialized: Boolean
    get() = initialized

  /**

  * Error message from library loading or native initialization.
    */
    val lastLoadError: String?
    get() = loadError

  /**

  * Returns a human-readable native core/build description.
    */
    external fun getNativeVersion(): String

  /**

  * One-time native initialization check.
  *
  * The actual Unicorn engine is created per VXP Runtime/session,
  * not here.
    */
    external fun nativeInit(): Boolean
    }
