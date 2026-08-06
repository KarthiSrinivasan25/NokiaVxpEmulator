package com.nokia.vxp.utils

import android.util.Log

/**
 * Thin wrapper around android.util.Log so every module logs consistently
 * and we have one place to redirect output (e.g. into debug/LogConsole.kt
 * once the debug module exists).
 */
object Logger {

    var enabled = true
    private const val GLOBAL_TAG = "VXP"

    fun d(tag: String, msg: String) {
        if (enabled) Log.d("$GLOBAL_TAG/$tag", msg)
    }

    fun i(tag: String, msg: String) {
        if (enabled) Log.i("$GLOBAL_TAG/$tag", msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (enabled) Log.w("$GLOBAL_TAG/$tag", msg, t)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (enabled) Log.e("$GLOBAL_TAG/$tag", msg, t)
    }
}
