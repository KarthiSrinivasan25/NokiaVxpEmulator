package com.nokia.vxp.utils

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

object Logger {

    var enabled = true
    private const val GLOBAL_TAG = "VXP"

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    private fun send(level: String, tag: String, msg: String) {
        val text = "$level/$GLOBAL_TAG-$tag: $msg"

        listeners.forEach {
            it(text)
        }
    }

    fun d(tag: String, msg: String) {
        if (!enabled) return
        Log.d("$GLOBAL_TAG/$tag", msg)
        send("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (!enabled) return
        Log.i("$GLOBAL_TAG/$tag", msg)
        send("I", tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (!enabled) return
        Log.w("$GLOBAL_TAG/$tag", msg, t)
        send("W", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!enabled) return
        Log.e("$GLOBAL_TAG/$tag", msg, t)
        send("E", tag, msg)
    }
}