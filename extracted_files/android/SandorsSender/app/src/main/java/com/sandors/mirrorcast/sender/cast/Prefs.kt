package com.sandors.mirrorcast.sender.cast

import android.content.Context
import java.util.UUID

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("sandors", Context.MODE_PRIVATE)

    val host: String? get() = prefs.getString(KEY_HOST, null)
    val port: Int get() = prefs.getInt(KEY_PORT, 47821)
    val name: String? get() = prefs.getString(KEY_NAME, null)

    fun savePeer(host: String, port: Int, name: String) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun installId(): String {
        val existing = prefs.getString(KEY_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, created).apply()
        return created
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_NAME = "name"
        private const val KEY_ID = "id"
    }
}
