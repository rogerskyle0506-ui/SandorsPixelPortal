package com.sandors.mirrorcast.sender.cast

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sandors.mirrorcast.sender.ui.MainActivity

class CastTileService : TileService() {
    private val listener: (Phase) -> Unit = { refresh() }

    override fun onStartListening() {
        CastBus.add(listener)
        refresh()
    }

    override fun onStopListening() {
        CastBus.remove(listener)
    }

    override fun onClick() {
        val active = CastBus.phase == Phase.CASTING || CastBus.phase == Phase.PAUSED
        if (active) {
            startService(
                Intent(this, CastForegroundService::class.java)
                    .setAction(CastForegroundService.ACTION_STOP),
            )
            return
        }
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FROM_TILE, true)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                1,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = CastBus.phase == Phase.CASTING || CastBus.phase == Phase.PAUSED
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Sandors"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (CastBus.phase) {
                Phase.CASTING -> "Casting"
                Phase.PAUSED -> "Paused"
                else -> "Mirrorcast"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, CastTileService::class.java))
        }
    }
}
