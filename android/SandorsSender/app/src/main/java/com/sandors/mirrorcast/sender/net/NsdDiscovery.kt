package com.sandors.mirrorcast.sender.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class NsdDiscovery(
    context: Context,
    private val onFound: (Device) -> Unit,
    private val onLost: (String) -> Unit,
) {
    data class Device(
        val name: String,
        val host: String,
        val port: Int,
        val cached: Boolean = false,
    )

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val appContext = context.applicationContext
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val resolver = Executors.newSingleThreadExecutor()
    private val modernCallbacks = ArrayList<Any>()
    private var resolving = false

    fun start() {
        stop()
        val wifi = appContext.getSystemService(WifiManager::class.java)
        multicast = wifi.createMulticastLock("sandors-discover").apply {
            setReferenceCounted(false)
            acquire()
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("sandorscast")) return
                enqueue(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                onLost(service.serviceName)
            }
        }
        listener = discovery
        try {
            nsd.discoverServices(Wire.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (e: Exception) {
            Log.e(TAG, "discoverServices", e)
        }
    }

    fun stop() {
        val current = listener
        listener = null
        if (current != null) {
            try {
                nsd.stopServiceDiscovery(current)
            } catch (_: Exception) {
            }
        }
        synchronized(this) {
            pending.clear()
            resolving = false
        }
        if (Build.VERSION.SDK_INT >= 34) dropModern()
        try {
            multicast?.release()
        } catch (_: Exception) {
        }
        multicast = null
    }

    @Synchronized
    private fun enqueue(info: NsdServiceInfo) {
        pending.add(info)
        if (!resolving) {
            resolving = true
            resolveNext()
        }
    }

    private fun resolveNext() {
        val next = synchronized(this) { if (pending.isEmpty()) null else pending.removeFirst() }
        if (next == null) {
            synchronized(this) { resolving = false }
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) resolveModern(next) else resolveLegacy(next)
        } catch (e: Exception) {
            Log.w(TAG, "resolve threw", e)
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacy(info: NsdServiceInfo) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                publish(resolved)
                resolveNext()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "resolve failed $errorCode")
                resolveNext()
            }
        })
    }

    @RequiresApi(34)
    private fun resolveModern(info: NsdServiceInfo) {
        val done = AtomicBoolean(false)
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.w(TAG, "service info failed $errorCode")
                finish(null)
            }

            override fun onServiceUpdated(updated: NsdServiceInfo) {
                finish(updated)
            }

            override fun onServiceLost() {
                finish(null)
            }

            override fun onServiceInfoCallbackUnregistered() {}

            private fun finish(updated: NsdServiceInfo?) {
                if (!done.compareAndSet(false, true)) return
                if (updated != null) publish(updated)
                try {
                    nsd.unregisterServiceInfoCallback(this)
                } catch (_: Exception) {
                }
                synchronized(this@NsdDiscovery) { modernCallbacks.remove(this) }
                resolveNext()
            }
        }
        synchronized(this) { modernCallbacks.add(callback) }
        nsd.registerServiceInfoCallback(info, resolver, callback)
    }

    @RequiresApi(34)
    private fun dropModern() {
        val copy = synchronized(this) { modernCallbacks.toList().also { modernCallbacks.clear() } }
        for (callback in copy) {
            try {
                nsd.unregisterServiceInfoCallback(callback as NsdManager.ServiceInfoCallback)
            } catch (_: Exception) {
            }
        }
    }

    private fun publish(resolved: NsdServiceInfo) {
        val host = hostOf(resolved) ?: return
        onFound(Device(resolved.serviceName, host, resolved.port))
    }

    private fun hostOf(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            val addresses = info.hostAddresses
            val picked = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
            if (picked != null) return picked.hostAddress?.substringBefore('%')
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress?.substringBefore('%')
    }

    companion object {
        private const val TAG = "SandorsNsd"
    }
}
