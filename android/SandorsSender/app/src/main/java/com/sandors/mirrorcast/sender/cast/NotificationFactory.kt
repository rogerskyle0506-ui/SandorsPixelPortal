package com.sandors.mirrorcast.sender.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sandors.mirrorcast.sender.R

object NotificationFactory {
    const val ID = 42
    private const val CHANNEL = "sandors_cast"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setSound(null, null)
        channel.description = context.getString(R.string.channel_name)
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        session: MediaSessionCompat,
        playing: Boolean,
        quality: String,
    ): Notification {
        ensureChannel(context)
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                0L,
                if (playing) 1f else 0f,
            )
            .build()
        session.setPlaybackState(state)

        val pauseAction = if (playing) {
            action(context, CastForegroundService.ACTION_PAUSE, android.R.drawable.ic_media_pause, R.string.action_pause, 1)
        } else {
            action(context, CastForegroundService.ACTION_RESUME, android.R.drawable.ic_media_play, R.string.action_play, 1)
        }
        val nextAction = action(
            context,
            CastForegroundService.ACTION_NEXT,
            android.R.drawable.ic_media_next,
            R.string.action_next,
            2,
        )
        val stopAction = action(
            context,
            CastForegroundService.ACTION_STOP,
            android.R.drawable.ic_menu_close_clear_cancel,
            R.string.action_stop,
            3,
        )
        val text = context.getString(if (playing) R.string.notif_casting else R.string.notif_paused, quality)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cast)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(pauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun action(
        context: Context,
        action: String,
        icon: Int,
        label: Int,
        request: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, CastForegroundService::class.java).setAction(action)
        val pending = PendingIntent.getService(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, context.getString(label), pending)
    }
}
