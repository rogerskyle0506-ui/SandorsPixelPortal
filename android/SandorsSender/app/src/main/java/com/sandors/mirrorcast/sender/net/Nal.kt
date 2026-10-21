package com.sandors.mirrorcast.sender.net

/**
 * Pulls SPS (type 7) and PPS (type 8) out of either Annex-B or AVCC codec config.
 * Pixel encoders emit both, depending on the chip.
 */
object Nal {
    fun parameterSets(buffers: List<ByteArray>): Pair<ByteArray?, ByteArray?> {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        for (buf in buffers) {
            for (nal in annexB(buf)) {
                when (typeOf(nal)) {
                    7 -> sps = stripStart(nal)
                    8 -> pps = stripStart(nal)
                }
            }
        }
        return sps to pps
    }

    fun prependParameterSets(accessUnit: ByteArray, sps: ByteArray?, pps: ByteArray?): ByteArray {
        if (sps == null || pps == null) return accessUnit
        if (typeOf(accessUnit) == 7) return accessUnit
        return configAnnexB(sps, pps) + accessUnit
    }

    fun configAnnexB(sps: ByteArray, pps: ByteArray): ByteArray = withStart(sps) + withStart(pps)

    fun withStart(nal: ByteArray): ByteArray {
        if (hasStart(nal)) return nal
        val out = ByteArray(nal.size + 4)
        out[3] = 1
        System.arraycopy(nal, 0, out, 4, nal.size)
        return out
    }

    private fun annexB(csd: ByteArray): List<ByteArray> {
        if (csd.isEmpty()) return emptyList()
        if ((csd[0].toInt() and 0xFF) == 1 && csd.size > 7) return parseAvcc(csd)
        return splitStartCodes(csd)
    }

    private fun splitStartCodes(data: ByteArray): List<ByteArray> {
        val starts = ArrayList<Int>()
        var i = 0
        while (i + 3 < data.size) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    starts.add(i)
                    i += 3
                    continue
                }
                if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    starts.add(i)
                    i += 4
                    continue
                }
            }
            i++
        }
        if (starts.isEmpty()) return listOf(data)
        val out = ArrayList<ByteArray>(starts.size)
        for (s in starts.indices) {
            val begin = starts[s]
            val end = if (s + 1 < starts.size) starts[s + 1] else data.size
            if (end > begin) out.add(data.copyOfRange(begin, end))
        }
        return out
    }

    private fun parseAvcc(csd: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var i = 5
        if (i >= csd.size) return out
        val numSps = csd[i].toInt() and 0x1F
        i += 1
        repeat(numSps) {
            if (i + 2 > csd.size) return out
            val len = ((csd[i].toInt() and 0xFF) shl 8) or (csd[i + 1].toInt() and 0xFF)
            i += 2
            if (len <= 0 || i + len > csd.size) return out
            out.add(withStart(csd.copyOfRange(i, i + len)))
            i += len
        }
        if (i >= csd.size) return out
        val numPps = csd[i].toInt() and 0xFF
        i += 1
        repeat(numPps) {
            if (i + 2 > csd.size) return out
            val len = ((csd[i].toInt() and 0xFF) shl 8) or (csd[i + 1].toInt() and 0xFF)
            i += 2
            if (len <= 0 || i + len > csd.size) return out
            out.add(withStart(csd.copyOfRange(i, i + len)))
            i += len
        }
        return out
    }

    fun typeOf(nal: ByteArray): Int {
        var i = 0
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 0.toByte() && nal[3] == 1.toByte()) {
            i = 4
        } else if (nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 1.toByte()) {
            i = 3
        }
        if (i >= nal.size) return -1
        return nal[i].toInt() and 0x1F
    }

    private fun stripStart(nal: ByteArray): ByteArray {
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 0.toByte() && nal[3] == 1.toByte()) {
            return nal.copyOfRange(4, nal.size)
        }
        if (nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 1.toByte()) {
            return nal.copyOfRange(3, nal.size)
        }
        return nal
    }

    private fun hasStart(nal: ByteArray): Boolean {
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 0.toByte() && nal[3] == 1.toByte()) return true
        return nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 1.toByte()
    }
}
