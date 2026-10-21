package com.sandors.mirrorcast.sender.net

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log

/**
 * Captures other apps' playback through the projection token. No microphone.
 * Apps that opt out of capture are skipped; the cast continues with video only.
 */
class AudioCapturer(
    private val projection: MediaProjection,
    private val sink: Sink,
) {
    interface Sink {
        fun onPcm(pcm: ByteArray, ptsMs: Long)
    }

    private var record: AudioRecord? = null
    @Volatile private var running = false
    private var samplesSent = 0L

    fun start() {
        val rate = 48_000
        val channel = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioRecord.getMinBufferSize(rate, channel, encoding)
        val chunk = 3_840
        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(rate)
            .setChannelMask(channel)
            .build()
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build()
        val audio = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(min, chunk * 4))
            .setAudioPlaybackCaptureConfig(capture)
            .build()
        record = audio
        audio.startRecording()
        running = true
        Thread({
            val buffer = ByteArray(chunk)
            while (running) {
                val read = audio.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val slice = if (read == buffer.size) buffer.clone() else buffer.copyOf(read)
                val frames = read / 4
                val pts = samplesSent * 1000L / rate
                samplesSent += frames
                sink.onPcm(slice, pts)
            }
        }, "sandors-audio").apply {
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }
        record = null
        Log.d(TAG, "audio stopped")
    }

    companion object {
        private const val TAG = "SandorsAudio"
    }
}
