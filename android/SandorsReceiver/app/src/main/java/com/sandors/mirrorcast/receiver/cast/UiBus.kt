package com.sandors.mirrorcast.receiver.cast

import android.os.Bundle
import android.os.Handler
import android.os.Looper

object UiBus {
    var listener: ((String, Bundle) -> Unit)? = null
    @Volatile var lastEvent: String = "idle"
    @Volatile var lastPin: String? = null
    private val main = Handler(Looper.getMainLooper())

    fun emit(event: String, extras: Bundle = Bundle()) {
        lastEvent = event
        extras.getString("pin")?.let { lastPin = it }
        val snapshot = Bundle(extras)
        main.post { listener?.invoke(event, snapshot) }
    }
}