package com.sandors.mirrorcast.receiver.cast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != QUICKBOOT) return
        ContextCompat.startForegroundService(context, Intent(context, ListenerService::class.java))
    }

    companion object {
        private const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
