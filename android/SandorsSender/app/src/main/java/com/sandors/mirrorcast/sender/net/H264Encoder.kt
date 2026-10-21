package com.sandors.mirrorcast.sender.net

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface

/**
 * Hardware H.264 encoder fed by a virtual-display surface.
 * Baseline, no B-frames, realtime priority. API 30+ asks for low-latency mode.
 */
class H264Encoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val fps: Int,
    private val sink: Sink,
) {
    interface Sink {
        fun onConfig(buffers: List<ByteArray>)
        fun onAccessUnit(data: ByteArray, key: Boolean, ptsUs: Long)
    }

    private var codec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set
    @Volatile private var running = false
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start() {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            encoder.configure(format(full = true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (first: Exception) {
            Log.w(TAG, "profile configure failed, retrying plain", first)
            encoder.configure(format(full = false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        inputSurface = encoder.createInputSurface()
        encoder.start()
        codec = encoder
        running = true
        Thread(::drain, "sandors-h264").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun requestSync() {
        try {
            val bundle = Bundle()
            bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            codec?.setParameters(bundle)
        } catch (e: Exception) {
            Log.w(TAG, "sync request failed", e)
        }
    }

    fun stop() {
        running = false
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
        try {
            inputSurface?.release()
        } catch (_: Exception) {
        }
        inputSurface = null
    }

    private fun format(full: Boolean): MediaFormat {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        format.setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
        )
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
        if (full) {
            format.setInteger(
                MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
            )
            format.setInteger(
                MediaFormat.KEY_LEVEL,
                MediaCodecInfo.CodecProfileLevel.AVCLevel41,
            )
            if (Build.VERSION.SDK_INT >= 29) {
                format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
            }
            if (Build.VERSION.SDK_INT >= 30) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }
        return format
    }

    private fun drain() {
        val encoder = codec ?: return
        while (running) {
            val index = try {
                encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "encoder stopped", e)
                break
            }
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val fmt = encoder.outputFormat
                    val buffers = ArrayList<ByteArray>(2)
                    fmt.getByteBuffer("csd-0")?.let { buffers.add(toBytes(it)) }
                    fmt.getByteBuffer("csd-1")?.let { buffers.add(toBytes(it)) }
                    if (buffers.isNotEmpty()) sink.onConfig(buffers)
                }
                index >= 0 -> {
                    val output = encoder.getOutputBuffer(index)
                    if (output != null && bufferInfo.size > 0) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        val bytes = ByteArray(bufferInfo.size)
                        output.get(bytes)
                        val config = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val key = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        if (config) sink.onConfig(listOf(bytes))
                        else sink.onAccessUnit(bytes, key, bufferInfo.presentationTimeUs)
                    }
                    try {
                        encoder.releaseOutputBuffer(index, false)
                    } catch (_: IllegalStateException) {
                        break
                    }
                }
            }
        }
    }

    private fun toBytes(buffer: java.nio.ByteBuffer): ByteArray {
        val dup = buffer.duplicate()
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    companion object {
        private const val TAG = "SandorsEnc"
    }
}
