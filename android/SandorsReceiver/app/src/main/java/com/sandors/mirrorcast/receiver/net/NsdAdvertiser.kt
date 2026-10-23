package com.sandors.mirrorcast.receiver.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            Log.i(TAG, "advertising ${info.serviceName}")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "register failed $errorCode")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
    }

    fun start(port: Int, name: String) {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = Wire.SERVICE_TYPE
            this.port = port
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        try {
            nsd.unregisterService(listener)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "SandorsAdv"
    }
}
