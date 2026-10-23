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
    }

    private val bus: (String, Bundle) -> Unit = { event, extras ->
        applyEvent(event, extras.getString("pin"))
    }

    override fun onStart() {
        super.onStart()
        UiBus.listener = bus
        applyEvent(UiBus.lastEvent, UiBus.lastPin)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val event = intent.getStringExtra("event")
        if (event != null) applyEvent(event, intent.getStringExtra("pin"))
        else applyEvent(UiBus.lastEvent, UiBus.lastPin)
    }

    override fun onStop() {
        if (UiBus.listener === bus) UiBus.listener = null
        super.onStop()
    }

    private fun applyEvent(event: String?, pin: String?) {
        when (event) {
            "pin" -> showPin(pin ?: return)
            "armed" -> showArmed()
            "casting" -> showLive(paused = false)
            "paused" -> showLive(paused = true)
            "ready", "idle" -> showIdle()
        }
    }

    private fun showIdle() {
        binding.idleTitle.setText(R.string.idle_title)
        binding.idleGroup.visibility = View.VISIBLE
        binding.pinGroup.visibility = View.GONE
        binding.liveChip.visibility = View.GONE
        binding.idleBody.text = getString(R.string.idle_body, ListenerService.label(getString(R.string.brand)))
    }

    private fun showArmed() {
        binding.idleTitle.setText(R.string.armed_title)
        binding.idleBody.setText(R.string.armed_body)
        binding.idleGroup.visibility = View.VISIBLE
        binding.pinGroup.visibility = View.GONE
        binding.liveChip.visibility = View.GONE
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
