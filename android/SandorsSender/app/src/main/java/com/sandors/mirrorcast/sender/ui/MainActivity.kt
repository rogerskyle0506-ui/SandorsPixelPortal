package com.sandors.mirrorcast.sender.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.sandors.mirrorcast.sender.R
import com.sandors.mirrorcast.sender.cast.CastBus
import com.sandors.mirrorcast.sender.cast.CastForegroundService
import com.sandors.mirrorcast.sender.cast.Phase
import com.sandors.mirrorcast.sender.cast.Prefs
import com.sandors.mirrorcast.sender.cast.SessionHolder
import com.sandors.mirrorcast.sender.databinding.ActivityMainBinding
import com.sandors.mirrorcast.sender.net.ControlSession
import com.sandors.mirrorcast.sender.net.NsdDiscovery
import com.sandors.mirrorcast.sender.net.Wire
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var discovery: NsdDiscovery
    private val devices = LinkedHashMap<String, NsdDiscovery.Device>()
    private val pinBuffer = StringBuilder()
    private var pairGen = 0

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            Thread { SessionHolder.sendStopAndClose() }.start()
            CastBus.update(Phase.IDLE, getString(R.string.status_cancelled))
            return@registerForActivityResult
        }
        val service = Intent(this, CastForegroundService::class.java).apply {
            action = CastForegroundService.ACTION_START
            putExtra(CastForegroundService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(CastForegroundService.EXTRA_DATA, data)
        }
        ContextCompat.startForegroundService(this, service)
    }

    private val busListener: (Phase) -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        discovery = NsdDiscovery(
            this,
            onFound = { device ->
                runOnUiThread {
                    devices[device.name] = device
                    inflateDevices()
                    binding.status.setText(R.string.status_found)
                }
            },
            onLost = { name ->
                runOnUiThread {
                    devices.remove(name)
                    inflateDevices()
                }
            },
        )
        binding.scanButton.setOnClickListener { startScan() }
        binding.cancelPin.setOnClickListener { cancelPair() }
        buildKeypad()
        CastBus.add(busListener)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        showCached()
        render()
        if (intent.getBooleanExtra(EXTRA_FROM_TILE, false)) startScan()
    }

    override fun onDestroy() {
        CastBus.remove(busListener)
        discovery.stop()
        super.onDestroy()
    }

    private fun startScan() {
        devices.clear()
        showCached()
        inflateDevices()
        binding.status.setText(R.string.status_scanning)
        discovery.start()
    }

    private fun showCached() {
        val host = prefs.host ?: return
        val name = prefs.name ?: host
        devices.putIfAbsent("cached:$host", NsdDiscovery.Device(name, host, prefs.port, cached = true))
    }

    private fun inflateDevices() {
        val list = binding.deviceList
        list.removeAllViews()
        if (devices.isEmpty()) {
            val empty = TextView(this).apply {
                setText(R.string.empty_devices)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted))
                textSize = 15f
                setPadding(0, dp(12), 0, 0)
            }
            list.addView(empty)
            return
        }
        for (device in devices.values) {
            val row = layoutInflater.inflate(R.layout.item_device, list, false)
            row.findViewById<TextView>(R.id.deviceName).text = device.name
            row.findViewById<TextView>(R.id.deviceHost).text = if (device.cached) {
                getString(R.string.cached_host, device.host)
            } else {
                device.host
            }
            row.findViewById<TextView>(R.id.deviceAction).setText(
                if (device.cached) R.string.reconnect else R.string.pair,
            )
            row.setOnClickListener { beginPair(device) }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = dp(10)
            list.addView(row, params)
        }
    }

    private fun beginPair(device: NsdDiscovery.Device) {
        if (CastBus.phase == Phase.CONNECTING || CastBus.phase == Phase.NEED_PIN) return
        val gen = ++pairGen
        pinBuffer.clear()
        paintPin()
        binding.pinError.text = ""
        binding.pinTarget.text = getString(R.string.pin_target, device.name)
        CastBus.update(Phase.CONNECTING, device.name)
        Thread {
            try {
                val session = ControlSession(device.host, device.port)
                session.connect()
                session.send(
                    JSONObject()
                        .put("t", "hello")
                        .put("v", 1)
                        .put("name", Build.MODEL ?: "Pixel")
                        .put("id", prefs.installId()),
                )
                val challenge = session.read()
                if (gen != pairGen) {
                    session.close()
                    return@Thread
                }
                val type = challenge?.optString("t")
                if (type != "pin_required") {
                    session.close()
                    val message = if (type == "busy") {
                        "That Stick is already in a cast."
                    } else {
                        getString(R.string.status_refused)
                    }
                    failPair(message)
                    return@Thread
                }
                SessionHolder.hold(session, device.host, device.port, device.name)
                CastBus.update(Phase.NEED_PIN, device.name)
            } catch (e: Exception) {
                if (gen == pairGen) failPair(e.message ?: getString(R.string.status_refused))
            }
        }.start()
    }

    private fun failPair(message: String) {
        SessionHolder.closeQuietly()
        CastBus.update(Phase.ERROR, message)
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun buildKeypad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.forEach { key ->
            val button = Button(this).apply {
                text = key
                isAllCaps = false
                textSize = 22f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ink))
                setBackgroundResource(R.drawable.bg_key)
                ViewCompat.setBackgroundTintList(this, null)
                stateListAnimator = null
                minHeight = dp(56)
                isEnabled = key.isNotEmpty()
                setOnClickListener { onKey(key) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(56)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            binding.keypad.addView(button, params)
        }
    }

    private fun onKey(key: String) {
        if (CastBus.phase != Phase.NEED_PIN) return
        if (key == "⌫") {
            if (pinBuffer.isNotEmpty()) pinBuffer.deleteAt(pinBuffer.lastIndex)
            paintPin()
            return
        }
        if (pinBuffer.length >= 4) return
        pinBuffer.append(key)
        paintPin()
        if (pinBuffer.length == 4) submitPin(pinBuffer.toString())
    }

    private fun paintPin() {
        val row = binding.pinRow
        if (row.childCount != 4) {
            row.removeAllViews()
            repeat(4) {
                val box = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 28f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.yellow))
                    setBackgroundResource(R.drawable.bg_key)
                }
                val params = LinearLayout.LayoutParams(0, dp(64), 1f)
                params.marginEnd = dp(8)
                row.addView(box, params)
            }
        }
        for (i in 0 until 4) {
            (row.getChildAt(i) as TextView).text = if (i < pinBuffer.length) pinBuffer[i].toString() else ""
        }
    }

    private fun submitPin(pin: String) {
        val gen = pairGen
        binding.pinError.text = ""
        Thread {
            try {
                val session = SessionHolder.session ?: return@Thread
                session.send(JSONObject().put("t", "pin").put("pin", pin))
                val reply = session.read()
                if (gen != pairGen) return@Thread
                when (reply?.optString("t")) {
                    "auth_ok" -> {
                        SessionHolder.videoPort = reply.optInt("videoPort", Wire.VIDEO_PORT)
                        SessionHolder.audioPort = reply.optInt("audioPort", Wire.AUDIO_PORT)
                        prefs.savePeer(SessionHolder.host, SessionHolder.port, SessionHolder.peerName)
                        runOnUiThread { requestProjection() }
                    }
                    "auth_fail" -> {
                        val left = reply.optInt("left", 0)
                        runOnUiThread {
                            pinBuffer.clear()
                            paintPin()
                            if (left <= 0) {
                                SessionHolder.closeQuietly()
                                binding.pinError.setText(R.string.pin_locked)
                                CastBus.update(Phase.IDLE, getString(R.string.pin_locked))
                            } else {
                                binding.pinError.text = getString(R.string.pin_wrong, left)
                            }
                        }
                    }
                    else -> failPair(getString(R.string.status_refused))
                }
            } catch (e: Exception) {
                if (gen == pairGen) failPair(e.message ?: getString(R.string.status_refused))
            }
        }.start()
    }

    private fun requestProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun cancelPair() {
        pairGen += 1
        pinBuffer.clear()
        Thread { SessionHolder.sendStopAndClose() }.start()
        CastBus.update(Phase.IDLE, getString(R.string.status_idle))
    }

    private fun render() {
        val phase = CastBus.phase
        val pinVisible = phase == Phase.NEED_PIN || phase == Phase.CONNECTING
        val live = phase == Phase.CASTING || phase == Phase.PAUSED
        binding.groupDiscover.visibility = if (pinVisible || live) View.GONE else View.VISIBLE
        binding.groupPin.visibility = if (pinVisible) View.VISIBLE else View.GONE
        binding.groupLive.visibility = if (live) View.VISIBLE else View.GONE
        when (phase) {
            Phase.IDLE -> binding.status.setText(R.string.status_idle)
            Phase.CONNECTING -> binding.status.text = getString(R.string.status_connecting, CastBus.detail)
            Phase.NEED_PIN -> binding.status.setText(R.string.status_need_pin)
            Phase.CASTING -> {
                binding.status.text = getString(R.string.status_casting, CastBus.detail)
                binding.liveTitle.text = getString(R.string.status_casting, CastBus.detail)
                binding.liveDetail.text = getString(R.string.live_detail, CastBus.quality)
            }
            Phase.PAUSED -> {
                binding.status.setText(R.string.status_paused)
                binding.liveTitle.setText(R.string.status_paused)
                binding.liveDetail.text = getString(R.string.live_detail, CastBus.quality)
            }
            Phase.ERROR -> binding.status.text = CastBus.detail
        }
        if (!pinVisible && !live && binding.deviceList.childCount == 0) inflateDevices()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_FROM_TILE = "from_tile"
    }
}
