package com.sandors.mirrorcast.sender.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class Packetizer(
    private val socket: DatagramSocket,
    private val address: InetAddress,
    private val port: Int,
) {
    private val sequence = AtomicInteger(0)

    fun sendFrame(kind: Int, flags: Int, timestampMs: Long, payload: ByteArray) {
        val max = Wire.MAX_PAYLOAD
        val count = if (payload.isEmpty()) 1 else (payload.size + max - 1) / max
        if (count > 400) return
        val frameSeq = sequence.getAndIncrement()
        var offset = 0
        for (frag in 0 until count) {
            val len = if (payload.isEmpty()) 0 else minOf(max, payload.size - offset)
            val packet = ByteArray(Wire.HEADER_SIZE + len)
            val buf = ByteBuffer.wrap(packet)
            buf.putInt(Wire.MAGIC)
            buf.put(Wire.VERSION.toByte())
            buf.put(kind.toByte())
            buf.put(flags.toByte())
            buf.put(0)
            buf.putInt(frameSeq)
            buf.putInt((timestampMs and 0xffffffffL).toInt())
            buf.putShort(frag.toShort())
            buf.putShort(count.toShort())
            if (len > 0) buf.put(payload, offset, len)
            socket.send(DatagramPacket(packet, packet.size, address, port))
            offset += len
        }
    }
}
