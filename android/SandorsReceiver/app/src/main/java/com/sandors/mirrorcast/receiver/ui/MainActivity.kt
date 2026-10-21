package com.sandors.mirrorcast.receiver.ui

import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sandors.mirrorcast.receiver.R
import com.sandors.mirrorcast.receiver.cast.ListenerService
import com.sandors.mirrorcast.receiver.cast.UiBus
import com.sandors.mirrorcast.receiver.databinding.ActivityReceiverBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReceiverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                ListenerService.attachSurface(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                ListenerService.detachSurface()
            }
        })
        ContextCompat.startForegroundService(this, Intent(this, ListenerService::class.java))
        showIdle()
    }

    override fun onStart() {
        super.onStart()
        UiBus.listener = { event, extras -> applyEvent(event, extras.getString("pin")) }
        applyEvent(intent.getStringExtra("event") ?: "idle", intent.getStringExtra("pin"))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyEvent(intent.getStringExtra("event") ?: "idle", intent.getStringExtra("pin"))
    }

    override fun onStop() {
        if (UiBus.listener != null) UiBus.listener = null
        super.onStop()
    }

    private fun applyEvent(event: String?, pin: String?) {
        when (event) {
            "pin" -> showPin(pin ?: return)
            "casting" -> showLive(paused = false)
            "paused" -> showLive(paused = true)
            "ready", "idle" -> showIdle()
        }
    }

    private fun showIdle() {
        binding.idleGroup.visibility = View.VISIBLE
        binding.pinGroup.visibility = View.GONE
        binding.liveChip.visibility = View.GONE
        binding.idleBody.text = getString(R.string.idle_body, ListenerService.label(getString(R.string.brand)))
    }

    private fun showPin(pin: String) {
        binding.pinDigits.text = pin
        binding.idleGroup.visibility = View.GONE
        binding.pinGroup.visibility = View.VISIBLE
        binding.liveChip.visibility = View.GONE
    }

    private fun showLive(paused: Boolean) {
        binding.idleGroup.visibility = View.GONE
        binding.pinGroup.visibility = View.GONE
        binding.liveChip.visibility = View.VISIBLE
        binding.liveChip.setText(if (paused) R.string.paused_chip else R.string.live_chip)
        binding.liveChip.setTextColor(
            ContextCompat.getColor(this, if (paused) R.color.blue else R.color.yellow),
        )
    }
}
