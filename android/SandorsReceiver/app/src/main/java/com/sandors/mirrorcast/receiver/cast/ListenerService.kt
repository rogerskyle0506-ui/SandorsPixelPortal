package com.sandors.mirrorcast.receiver.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.sandors.mirrorcast.receiver.R
import com.sandors.mirrorcast.receiver.net.NsdAdvertiser
import com.sandors.mirrorcast.receiver.net.Reassembler
import com.sandors.mirrorcast.receiver.net.Wire
import com.sandors.mirrorcast.receiver.ui.MainActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.lang.ref.WeakReference

class ListenerService : Service() {
    private val engine = DecodeEngine()
    private lateinit var advertiser: NsdAdvertiser
    private var control: ServerSocket? = null
    private var video: DatagramSocket? = null
    private var audio: DatagramSocket? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var multicast: WifiManager.MulticastLock? = null
    @Volatile private var running = false
    @Volatile private var client: Socket? = null
    lateinit var serviceLabel: String
        private set

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceLabel = stickName()
        startForeground(NOTIF_ID, notification(getString(R.string.notif_listening)))
        acquireLocks()
        video = bindUdp(Wire.VIDEO_PORT)
        audio = bindUdp(Wire.AUDIO_PORT)
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(Wire.CONTROL_PORT))
        control = server
        advertiser = NsdAdvertiser(this)
        advertiser.start(server.localPort, serviceLabel)
        running = true
        ref = WeakReference(this)
        Thread { acceptLoop(server) }.apply { name = "sandors-accept"; start() }
        Thread { videoLoop(video!!) }.apply { name = "sandors-video"; start() }
        Thread { audioLoop(audio!!) }.apply { name = "sandors-audio"; start() }
        UiBus.emit("ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        ref = null
        try {
            advertiser.stop()
        } catch (_: Exception) {
        }
        try {
            client?.close()
        } catch (_: Exception) {
        }
        try {
            control?.close()
        } catch (_: Exception) {
        }
        try {
            video?.close()
        } catch (_: Exception) {
        }
        try {
            audio?.close()
        } catch (_: Exception) {
        }
        engine.release()
        releaseLocks()
        super.onDestroy()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            if (client != null && client?.isClosed == false) {
                reject(socket)
                continue
            }
            client = socket
            Thread { handle(socket) }.apply { name = "sandors-client"; start() }
        }
    }

    private fun reject(socket: Socket) {
        try {
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            writer.write(JSONObject().put("t", "busy").toString())
            writer.write("\n")
            writer.flush()
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 180_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            fun send(message: JSONObject) {
                synchronized(writer) {
                    writer.write(message.toString())
                    writer.write("\n")
                    writer.flush()
                }
            }
            val hello = JSONObject(reader.readLine() ?: return)
            if (hello.optString("t") != "hello") return
            val pin = randomPin()
            showPin(pin)
            updateNotification(getString(R.string.notif_pairing))
            send(JSONObject().put("t", "pin_required"))
            var left = 3
            var authed = false
            while (left > 0 && running) {
                val line = reader.readLine() ?: return
                val message = JSONObject(line)
                if (message.optString("t") == "stop") return
                if (message.optString("t") != "pin") continue
                if (message.optString("pin") == pin) {
                    authed = true
                    send(
                        JSONObject()
                            .put("t", "auth_ok")
                            .put("videoPort", video?.localPort ?: Wire.VIDEO_PORT)
                            .put("audioPort", audio?.localPort ?: Wire.AUDIO_PORT),
                    )
                    engine.paused = false
                    UiBus.emit("casting")
                    updateNotification(getString(R.string.notif_casting))
                    break
                }
                left -= 1
                send(JSONObject().put("t", "auth_fail").put("left", left))
            }
            if (!authed) return
            socket.soTimeout = 8_000
            while (running && !socket.isClosed) {
                val line = try {
                    reader.readLine()
                } catch (_: SocketTimeoutException) {
                    continue
                } ?: break
                val message = JSONObject(line)
                when (message.optString("t")) {
                    "ping" -> send(JSONObject().put("t", "pong"))
                    "stop" -> break
                    "pause" -> {
                        engine.paused = true
                        UiBus.emit("paused")
                    }
                    "resume" -> {
                        engine.paused = false
                        UiBus.emit("casting")
                    }
                    "volume" -> engine.setVolume(message.optInt("v", 80))
                    "config" -> applyConfig(message)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "client ended", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            if (client == socket) {
                client = null
                engine.paused = true
                UiBus.emit("idle")
                if (running) updateNotification(getString(R.string.notif_listening))
            }
        }
    }

    private fun applyConfig(message: JSONObject) {
        val sps = decode(message.optString("sps"))
        val pps = decode(message.optString("pps"))
        if (sps == null || pps == null) return
        engine.onConfig(message.optInt("w", 1920), message.optInt("h", 1080), sps, pps)
    }

    private fun decode(value: String): ByteArray? {
        if (value.isBlank()) return null
        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun showPin(pin: String) {
        val extras = Bundle().apply { putString("pin", pin) }
        UiBus.emit("pin", extras)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("event", "pin")
            putExtra("pin", pin)
        }
        startActivity(intent)
    }

    private fun videoLoop(socket: DatagramSocket) {
        val reassembler = Reassembler { kind, flags, timestampMs, data ->
            when (kind) {
                Wire.KIND_CONFIG -> {
                    val sets = com.sandors.mirrorcast.receiver.net.Nal.parameterSets(listOf(data))
                    val sps = sets.first
                    val pps = sets.second
                    if (sps != null && pps != null) engine.onConfig(0, 0, sps, pps)
                }
                Wire.KIND_VIDEO -> engine.onVideo(data, flags and Wire.FLAG_KEYFRAME != 0, timestampMs)
            }
        }
        val buffer = ByteArray(1600)
        val packet = DatagramPacket(buffer, buffer.size)
        while (running) {
            try {
                socket.receive(packet)
                reassembler.push(packet.data, packet.length)
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun audioLoop(socket: DatagramSocket) {
        val reassembler = Reassembler { kind, _, _, data ->
            if (kind == Wire.KIND_AUDIO) engine.onAudio(data)
        }
        val buffer = ByteArray(1600)
        val packet = DatagramPacket(buffer, buffer.size)
        while (running) {
            try {
                socket.receive(packet)
                reassembler.push(packet.data, packet.length)
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun bindUdp(port: Int): DatagramSocket {
        return try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
                receiveBufferSize = 4 * 1024 * 1024
            }
        } catch (_: Exception) {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
                receiveBufferSize = 4 * 1024 * 1024
            }
        }
    }

    private fun stickName(): String {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "0000"
        return "Sandors-" + id.takeLast(4).uppercase()
    }

    private fun randomPin(): String = SecureRandom().nextInt(10_000).toString().padStart(4, '0')

    private fun acquireLocks() {
        val power = getSystemService(PowerManager::class.java)
        wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sandors:receiver").apply {
            setReferenceCounted(false)
            acquire(8 * 60 * 60 * 1000L)
        }
        val manager = applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        wifi = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "sandors:receiver-wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
        multicast = manager.createMulticastLock("sandors-adv").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (_: Exception) {
        }
        try {
            if (wifi?.isHeld == true) wifi?.release()
        } catch (_: Exception) {
        }
        try {
            if (multicast?.isHeld == true) multicast?.release()
        } catch (_: Exception) {
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cast)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val TAG = "SandorsRecv"
        private const val CHANNEL = "sandors_receiver"
        private const val NOTIF_ID = 7
        @Volatile private var ref: WeakReference<ListenerService>? = null

        fun attachSurface(surface: Surface) {
            ref?.get()?.engine?.setSurface(surface)
        }

        fun detachSurface() {
            ref?.get()?.engine?.setSurface(null)
        }

        fun label(fallback: String): String = ref?.get()?.serviceLabel ?: fallback
    }
}
