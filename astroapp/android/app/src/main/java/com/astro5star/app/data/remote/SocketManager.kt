package com.astro5star.app.data.remote

import android.util.Log
import com.astro5star.app.MainActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null

    fun init() {
        if (socket != null) return

        try {
            val opts = IO.Options()
            opts.transports = arrayOf("websocket", "polling")
            socket = IO.socket(MainActivity.SERVER_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected: ${socket?.id()}")
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error", e)
        }
    }

    fun registerUser(userId: String) {
        val data = JSONObject()
        data.put("userId", userId)
        socket?.emit("register", data)
    }

    fun getSocket(): Socket? {
        return socket
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
