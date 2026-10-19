package com.sandors.mirrorcast.sender.net

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class ControlSession(private val host: String, private val port: Int) {
    private lateinit var socket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter

    fun connect() {
        val clean = host.substringBefore('%')
        socket = Socket()
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.connect(InetSocketAddress(clean, port), 3_000)
        socket.soTimeout = 180_000
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    }

    fun setIdleTimeout() {
        socket.soTimeout = 0
    }

    fun send(message: JSONObject) {
        synchronized(writer) {
            writer.write(message.toString())
            writer.write("\n")
            writer.flush()
        }
    }

    fun read(): JSONObject? {
        val line = reader.readLine() ?: return null
        if (line.isBlank()) return read()
        return JSONObject(line)
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
