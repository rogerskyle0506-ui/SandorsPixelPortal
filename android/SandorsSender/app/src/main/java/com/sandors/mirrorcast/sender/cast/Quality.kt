package com.sandors.mirrorcast.sender.cast

enum class Quality(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
) {
    P720("720p30", 1280, 720, 30, 4_000_000),
    P1080("1080p30", 1920, 1080, 30, 8_000_000),
    P1080_60("1080p60", 1920, 1080, 60, 12_000_000),
    ;

    fun next(): Quality = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromLabel(label: String): Quality = entries.firstOrNull { it.label == label } ?: P1080
    }
}
