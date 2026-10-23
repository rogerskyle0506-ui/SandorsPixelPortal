package com.sandors.mirrorcast.sender.cast

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

enum class Phase { IDLE, CONNECTING, NEED_PIN, STARTING, CASTING, PAUSED, ERROR }

object CastBus {
    @Volatile var phase: Phase = Phase.IDLE
        private set
    @Volatile var detail: String = ""
        private set
    @Volatile var quality: String = Quality.P1080.label
        private set
    @Volatile var volume: Int = 80
        private set

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Phase) -> Unit>()

    fun add(listener: (Phase) -> Unit) {
        listeners.add(listener)
    }

    fun remove(listener: (Phase) -> Unit) {
        listeners.remove(listener)
    }

    fun update(next: Phase, detail: String = this.detail) {
        phase = next
        this.detail = detail
        dispatch()
    }

    fun setQuality(label: String) {
        quality = label
        dispatch()
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        dispatch()
    }

    private fun dispatch() {
        val snapshot = phase
        main.post { listeners.forEach { it(snapshot) } }
    }
}
