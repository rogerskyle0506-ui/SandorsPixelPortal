package com.sandors.mirrorcast.receiver.net

import java.nio.ByteBuffer

class Reassembler(
    private val onFrame: (kind: Int, flags: Int, timestampMs: Long, data: ByteArray) -> Unit,
) {
    private class Slot(
        val kind: Int,
        val flags: Int,
        val timestampMs: Long,
        val count: Int,
        val parts: Array<ByteArray?>,
        var got: Int,
        val bornNs: Long,
    )

    private val slots = HashMap<Long, Slot>()

    fun push(packet: ByteArray, length: Int) {
        if (length < Wire.HEADER_SIZE) return
        val buf = ByteBuffer.wrap(packet, 0, length)
        if (buf.int != Wire.MAGIC) return
        val version = buf.get().toInt() and 0xFF
        if (version != Wire.VERSION) return
        val kind = buf.get().toInt() and 0xFF
        val flags = buf.get().toInt() and 0xFF
        buf.get()
        val frameSeq = buf.int
        val timestampMs = buf.int.toLong() and 0xffffffffL
        val frag = buf.short.toInt() and 0xFFFF
        val count = buf.short.toInt() and 0xFFFF
        if (count <= 0 || count > 400 || frag >= count) return
        val payloadLen = length - Wire.HEADER_SIZE
        val payload = ByteArray(payloadLen)
        if (payloadLen > 0) buf.get(payload)

        val key = (kind.toLong() shl 32) xor (frameSeq.toLong() and 0xffffffffL)
        val now = System.nanoTime()
        val slot = slots.getOrPut(key) {
            Slot(kind, flags, timestampMs, count, arrayOfNulls(count), 0, now)
        }
        if (slot.count != count) return
        if (slot.parts[frag] == null) {
            slot.parts[frag] = payload
            slot.got += 1
        }
        if (slot.got == slot.count) {
            var total = 0
            for (part in slot.parts) total += part?.size ?: return
            val out = ByteArray(total)
            var cursor = 0
            for (part in slot.parts) {
                val bytes = part ?: return
                System.arraycopy(bytes, 0, out, cursor, bytes.size)
                cursor += bytes.size
            }
            slots.remove(key)
            onFrame(slot.kind, slot.flags, slot.timestampMs, out)
        }
        if (slots.size > 8) {
            val it = slots.entries.iterator()
            while (it.hasNext()) {
                if (now - it.next().value.bornNs > 50_000_000L) it.remove()
            }
        }
    }
}
