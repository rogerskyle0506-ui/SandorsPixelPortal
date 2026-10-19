package com.sandors.mirrorcast.receiver.cast

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import com.sandors.mirrorcast.receiver.net.Nal
import com.sandors.mirrorcast.receiver.net.Wire
import java.nio.ByteBuffer
import java.util.ArrayDeque

/**
 * Hardware H.264 decode straight onto a Surface, plus a low-latency PCM track.
 * ExoPlayer is intentionally not used: its demuxer and default buffers add
 * hundreds of milliseconds, which is the wrong trade for a mirror.
 */
class DecodeEngine {
    private val lock = Any()
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var width = 1920
    private var height = 1080
    private var configured = false
    private var needKey = true
    private var gain = 0.8f
    @Volatile var paused = false

    private val pending = ArrayDeque<ByteArray>()
    private val pendingKey = ArrayDeque<Boolean>()
    private val pendingPts = ArrayDeque<Long>()
    private val info = MediaCodec.BufferInfo()

    fun setSurface(next: Surface?) {
        synchronized(lock) {
            surface = next
            if (next == null) releaseCodec() else maybeConfigure()
        }
    }

    fun onConfig(w: Int, h: Int, sequence: ByteArray, picture: ByteArray) {
        synchronized(lock) {
            if (w > 0 && h > 0) {
                width = w
                height = h
            }
            sps = sequence
            pps = picture
            releaseCodec()
            maybeConfigure()
        }
    }

    fun onVideo(data: ByteArray, key: Boolean, ptsMs: Long) {
        synchronized(lock) {
            if (paused) {
                needKey = true
                return
            }
            sniff(data)
            if (!configured) {
                while (pending.size >= 12) {
                    pending.removeFirst()
                    pendingKey.removeFirst()
                    pendingPts.removeFirst()
                }
                pending.addLast(data)
                pendingKey.addLast(key)
                pendingPts.addLast(ptsMs)
                maybeConfigure()
                return
            }
            val idr = key || Nal.typeOf(data) == 5 || Nal.typeOf(data) == 7
            if (needKey && !idr) return
            needKey = false
            queue(data, idr, ptsMs)
        }
    }

    fun onAudio(pcm: ByteArray) {
        if (paused) return
        ensureAudio()
        val audio = track ?: return
        var offset = 0
        while (offset < pcm.size) {
            val wrote = audio.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            if (wrote <= 0) break
            offset += wrote
            if (offset < pcm.size) break
        }
    }

    fun setVolume(value: Int) {
        gain = value.coerceIn(0, 100) / 100f
        track?.setVolume(gain)
    }

    fun release() {
        synchronized(lock) {
            releaseCodec()
            surface = null
        }
        releaseAudio()
    }

    private fun sniff(data: ByteArray) {
        val sets = Nal.parameterSets(listOf(data))
        if (sets.first != null) sps = sets.first
        if (sets.second != null) pps = sets.second
    }

    private fun maybeConfigure() {
        val target = surface ?: return
        val sequence = sps ?: return
        val picture = pps ?: return
        if (configured) return
        val decoder = openDecoder(target, sequence, picture) ?: return
        codec = decoder
        configured = true
        needKey = true
        ensureAudio()
        while (pending.isNotEmpty()) {
            val frame = pending.removeFirst()
            val key = pendingKey.removeFirst()
            val pts = pendingPts.removeFirst()
            val idr = key || Nal.typeOf(frame) == 5 || Nal.typeOf(frame) == 7
            if (needKey && !idr) continue
            needKey = false
            queue(frame, idr, pts)
        }
    }

    private fun openDecoder(target: Surface, sequence: ByteArray, picture: ByteArray): MediaCodec? {
        return try {
            configure(target, sequence, picture, lowLatency = true)
        } catch (first: Exception) {
            Log.w(TAG, "low-latency configure failed", first)
            try {
                configure(target, sequence, picture, lowLatency = false)
            } catch (second: Exception) {
                Log.e(TAG, "decoder configure failed", second)
                null
            }
        }
    }

    private fun configure(
        target: Surface,
        sequence: ByteArray,
        picture: ByteArray,
        lowLatency: Boolean,
    ): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sequence))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(picture))
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1_500_000)
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        if (lowLatency && Build.VERSION.SDK_INT >= 23) {
            format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        }
        if (lowLatency && Build.VERSION.SDK_INT >= 30) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        decoder.configure(format, target, null, 0)
        decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        decoder.start()
        return decoder
    }

    private fun queue(data: ByteArray, key: Boolean, ptsMs: Long) {
        val decoder = codec ?: return
        val payload = Nal.withStart(data)
        try {
            val index = decoder.dequeueInputBuffer(0)
            if (index < 0) return
            val input = decoder.getInputBuffer(index) ?: return
            if (payload.size > input.capacity()) return
            input.clear()
            input.put(payload)
            val flags = if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            decoder.queueInputBuffer(index, 0, payload.size, ptsMs * 1000L, flags)
            while (true) {
                val out = decoder.dequeueOutputBuffer(info, 0)
                if (out >= 0) decoder.releaseOutputBuffer(out, info.size > 0)
                else break
            }
        } catch (e: Exception) {
            Log.w(TAG, "queue failed", e)
            releaseCodec()
        }
    }

    private fun ensureAudio() {
        if (track != null) return
        val rate = 48_000
        val mask = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioTrack.getMinBufferSize(rate, mask, encoding).coerceAtLeast(3_840 * 2)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(rate)
                    .setChannelMask(mask)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(min)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audio.setVolume(gain)
        audio.play()
        track = audio
    }

    private fun releaseCodec() {
        configured = false
        pending.clear()
        pendingKey.clear()
        pendingPts.clear()
        val current = codec
        codec = null
        try {
            current?.stop()
        } catch (_: Exception) {
        }
        try {
            current?.release()
        } catch (_: Exception) {
        }
    }

    private fun releaseAudio() {
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    companion object {
        private const val TAG = "SandorsDec"
        const val FLAG_KEY = Wire.FLAG_KEYFRAME
    }
}
