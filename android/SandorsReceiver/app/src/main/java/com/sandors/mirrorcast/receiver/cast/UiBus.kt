package com.sandors.mirrorcast.receiver.cast

import android.os.Bundle
import android.os.Handler
import android.os.Looper

object UiBus {
    var listener: ((String, Bundle) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun emit(event: String, extras: Bundle = Bundle()) {
        main.post { listener?.invoke(event, extras) }
    }
}
