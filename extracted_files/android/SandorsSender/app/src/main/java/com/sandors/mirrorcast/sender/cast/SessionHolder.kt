package com.sandors.mirrorcast.sender.cast

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

    fun hold(session: ControlSession, host: String, port: Int, name: String) {
        this.session = session
        this.host = host
        this.port = port
        this.peerName = name
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
    }
}
