package com.sandors.mirrorcast.sender.cast

import android.content.Intent
import com.sandors.mirrorcast.sender.net.ControlSession
import com.sandors.mirrorcast.sender.net.Wire
import org.json.JSONObject

object SessionHolder {
    @Volatile var session: ControlSession? = null
    @Volatile var host: String = ""
    @Volatile var port: Int = Wire.CONTROL_PORT
    @Volatile var peerName: String = ""
    @Volatile var videoPort: Int = Wire.VIDEO_PORT
    @Volatile var audioPort: Int = Wire.AUDIO_PORT
    @Volatile var captureCode: Int = 0
    @Volatile var captureIntent: Intent? = null

    fun hold(session: ControlSession, host: String, port: Int, name: String) {
        this.session = session
        this.host = host
        this.port = port
        this.peerName = name
    }

    fun holdCapture(code: Int, data: Intent) {
        captureCode = code
        captureIntent = data
    }

    fun clearCapture() {
        captureCode = 0
        captureIntent = null
    }

    fun sendStopAndClose() {
        try {
            session?.send(JSONObject().put("t", "stop"))
        } catch (_: Exception) {
        }
        closeQuietly()
    }

    fun closeQuietly() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        clearCapture()
    }
}