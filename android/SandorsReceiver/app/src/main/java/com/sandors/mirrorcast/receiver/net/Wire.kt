package com.sandors.mirrorcast.receiver.net

object Wire {
    const val MAGIC = 0x534E4452
    const val VERSION = 1
    const val HEADER_SIZE = 20
    const val MAX_PAYLOAD = 1200
    const val KIND_VIDEO = 1
    const val KIND_AUDIO = 2
    const val KIND_CONFIG = 3
    const val FLAG_KEYFRAME = 1
    const val FLAG_CONFIG = 2
    const val CONTROL_PORT = 47821
    const val VIDEO_PORT = 47822
    const val AUDIO_PORT = 47823
    const val SERVICE_TYPE = "_sandorscast._tcp."
}
