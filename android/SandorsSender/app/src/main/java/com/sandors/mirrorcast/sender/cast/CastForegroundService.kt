package com.sandors.mirrorcast.sender.cast

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.media.VolumeProviderCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Base64
import android.util.Log
import android.view.Surface
import com.sandors.mirrorcast.sender.net.AudioCapturer
import com.sandors.mirrorcast.sender.net.H264Encoder
import com.sandors.mirrorcast.sender.net.Nal
import com.sandors.mirrorcast.sender.net.Packetizer
import com.sandors.mirrorcast.sender.net.Wire
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class CastForegroundService : Service() {
    private val stopping = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var encoder: H264Encoder? = null
    private var audio: AudioCapturer? = null
    private var videoSocket: DatagramSocket? = null
    private var audioSocket: DatagramSocket? = null
    private var videoTx: Packetizer? = null
    private var audioTx: Packetizer? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var session: MediaSessionCompat? = null
    private var volumeProvider: VolumeProviderCompat? = null
    @Volatile private var streaming = false
    private var quality = Quality.P1080
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_NEXT -> cycleQuality()
            ACTION_STOP -> stopCast()
            ACTION_VOLUME -> applyVolume(intent.getIntExtra(EXTRA_VOLUME, CastBus.volume))
            else -> if (!streaming) startCast(intent)
        }
        return START_NOT_STICKY
    }

    private fun startCast(intent: Intent?) {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        stopping.set(false)
        quality = Quality.fromLabel(CastBus.quality)
        val mediaSession = MediaSessionCompat(this, "SandorsMirrorcast")
        mediaSession.isActive = true
        session = mediaSession
        volumeProvider = object : VolumeProviderCompat(
            VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
            100,
            CastBus.volume,
        ) {
            override fun onSetVolumeTo(volume: Int) {
                applyVolume(volume)
            }

            override fun onAdjustVolume(direction: Int) {
                applyVolume(CastBus.volume + direction * 5)
            }
        }
        mediaSession.setPlaybackToRemote(volumeProvider)
        val notification = NotificationFactory.build(this, mediaSession, playing = true, quality.label)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NotificationFactory.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NotificationFactory.ID, notification)
        }
        if (resultCode == 0 || data == null || SessionHolder.session == null) {
            stopCast(Phase.ERROR, "Screen capture was not granted.")
            return
        }
        acquireLocks()
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val proj = manager.getMediaProjection(resultCode, data)
            projection = proj
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCast()
                }
            }, main)
            openSockets()
            SessionHolder.session?.setIdleTimeout()
            streaming = true
            startPipeline()
            startControlLoops()
            CastBus.update(Phase.CASTING, SessionHolder.peerName)
            CastTileService.requestUpdate(this)
            sendJson(JSONObject().put("t", "volume").put("v", CastBus.volume))
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            stopCast(Phase.ERROR, e.message ?: "Cast failed")
        }
    }

    private fun openSockets() {
        val address = InetAddress.getByName(SessionHolder.host.substringBefore('%'))
        videoSocket = DatagramSocket().apply { sendBufferSize = 2 * 1024 * 1024 }
        audioSocket = DatagramSocket().apply { sendBufferSize = 512 * 1024 }
        videoTx = Packetizer(videoSocket!!, address, SessionHolder.videoPort)
        audioTx = Packetizer(audioSocket!!, address, SessionHolder.audioPort)
    }

    private fun startPipeline() {
        val preset = quality
        val enc = H264Encoder(
            preset.width,
            preset.height,
            preset.bitrate,
            preset.fps,
            object : H264Encoder.Sink {
                override fun onConfig(buffers: List<ByteArray>) = publishConfig(buffers)
                override fun onAccessUnit(data: ByteArray, key: Boolean, ptsUs: Long) {
                    if (!streaming || CastBus.phase == Phase.PAUSED) return
                    val flags = if (key) Wire.FLAG_KEYFRAME else 0
                    val payload = if (key) Nal.prependParameterSets(data, sps, pps) else data
                    videoTx?.sendFrame(Wire.KIND_VIDEO, flags, ptsUs / 1000L, payload)
                }
            },
        )
        enc.start()
        encoder = enc
        val surface: Surface = enc.inputSurface ?: error("Encoder did not create a surface")
        display = projection?.createVirtualDisplay(
            "SandorsCast",
            preset.width,
            preset.height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null,
        )
        val projectionToken = projection ?: return
        try {
            val capturer = AudioCapturer(projectionToken, object : AudioCapturer.Sink {
                override fun onPcm(pcm: ByteArray, ptsMs: Long) {
                    if (!streaming || CastBus.phase == Phase.PAUSED) return
                    audioTx?.sendFrame(Wire.KIND_AUDIO, 0, ptsMs, pcm)
                }
            })
            capturer.start()
            audio = capturer
        } catch (e: Exception) {
            Log.w(TAG, "audio capture unavailable", e)
        }
    }

    private fun publishConfig(buffers: List<ByteArray>) {
        val sets = Nal.parameterSets(buffers)
        val sequence = sets.first ?: return
        val picture = sets.second ?: return
        sps = sequence
        pps = picture
        sendJson(
            JSONObject()
                .put("t", "config")
                .put("w", quality.width)
                .put("h", quality.height)
                .put("fps", quality.fps)
                .put("sps", Base64.encodeToString(sequence, Base64.NO_WRAP))
                .put("pps", Base64.encodeToString(picture, Base64.NO_WRAP)),
        )
        videoTx?.sendFrame(Wire.KIND_CONFIG, Wire.FLAG_CONFIG, 0L, Nal.configAnnexB(sequence, picture))
    }

    private fun startControlLoops() {
        val control = SessionHolder.session ?: return
        Thread {
            try {
                while (streaming) {
                    val message = control.read() ?: break
                    if (message.optString("t") == "stop") {
                        main.post { stopCast() }
                        break
                    }
                }
            } catch (_: Exception) {
                if (streaming) main.post { stopCast() }
            }
        }.apply {
            name = "sandors-ctrl"
            start()
        }
        Thread {
            while (streaming) {
                try {
                    Thread.sleep(2_000)
                    if (!streaming) break
                    sendJson(JSONObject().put("t", "ping"))
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            name = "sandors-ping"
            start()
        }
    }

    private fun sendJson(message: JSONObject) {
        try {
            SessionHolder.session?.send(message)
        } catch (e: Exception) {
            Log.w(TAG, "control send failed", e)
            if (streaming) main.post { stopCast() }
        }
    }

    private fun applyVolume(value: Int) {
        CastBus.setVolume(value)
        volumeProvider?.currentVolume = CastBus.volume
        if (streaming) sendJson(JSONObject().put("t", "volume").put("v", CastBus.volume))
    }

    private fun pause() {
        if (!streaming) return
        display?.setSurface(null)
        CastBus.update(Phase.PAUSED, SessionHolder.peerName)
        sendJson(JSONObject().put("t", "pause"))
        refreshNotification()
        CastTileService.requestUpdate(this)
    }

    private fun resume() {
        if (!streaming) return
        encoder?.inputSurface?.let { display?.setSurface(it) }
        encoder?.requestSync()
        CastBus.update(Phase.CASTING, SessionHolder.peerName)
        sendJson(JSONObject().put("t", "resume"))
        refreshNotification()
        CastTileService.requestUpdate(this)
    }

    private fun cycleQuality() {
        if (!streaming) return
        quality = quality.next()
        CastBus.setQuality(quality.label)
        restartPipeline()
        refreshNotification()
    }

    private fun restartPipeline() {
        releasePipeline()
        startPipeline()
        encoder?.requestSync()
    }

    private fun releasePipeline() {
        try {
            display?.release()
        } catch (_: Exception) {
        }
        display = null
        try {
            audio?.stop()
        } catch (_: Exception) {
        }
        audio = null
        try {
            encoder?.stop()
        } catch (_: Exception) {
        }
        encoder = null
    }

    private fun refreshNotification() {
        val mediaSession = session ?: return
        val playing = CastBus.phase != Phase.PAUSED
        val notification = NotificationFactory.build(this, mediaSession, playing, quality.label)
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NotificationFactory.ID, notification)
    }

    private fun acquireLocks() {
        try {
            val power = getSystemService(PowerManager::class.java)
            wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sandors:cast").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake lock unavailable", e)
        }
        try {
            val manager = applicationContext.getSystemService(WifiManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifi = manager.createWifiLock(mode, "sandors:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "wifi lock unavailable", e)
        }
    }

    private fun stopCast(end: Phase = Phase.IDLE, detail: String = "") {
        if (!stopping.compareAndSet(false, true)) return
        streaming = false
        try {
            SessionHolder.session?.send(JSONObject().put("t", "stop"))
        } catch (_: Exception) {
        }
        releasePipeline()
        val proj = projection
        projection = null
        try {
            proj?.stop()
        } catch (_: Exception) {
        }
        try {
            videoSocket?.close()
        } catch (_: Exception) {
        }
        try {
            audioSocket?.close()
        } catch (_: Exception) {
        }
        SessionHolder.closeQuietly()
        try {
            session?.isActive = false
            session?.release()
        } catch (_: Exception) {
        }
        session = null
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (_: Exception) {
        }
        try {
            if (wifi?.isHeld == true) wifi?.release()
        } catch (_: Exception) {
        }
        CastBus.update(end, detail)
        CastTileService.requestUpdate(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopping.get()) stopCast()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SandorsCast"
        const val ACTION_START = "com.sandors.mirrorcast.START"
        const val ACTION_PAUSE = "com.sandors.mirrorcast.PAUSE"
        const val ACTION_RESUME = "com.sandors.mirrorcast.RESUME"
        const val ACTION_NEXT = "com.sandors.mirrorcast.NEXT"
        const val ACTION_STOP = "com.sandors.mirrorcast.STOP"
        const val ACTION_VOLUME = "com.sandors.mirrorcast.VOLUME"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val EXTRA_VOLUME = "volume"
    }
}
