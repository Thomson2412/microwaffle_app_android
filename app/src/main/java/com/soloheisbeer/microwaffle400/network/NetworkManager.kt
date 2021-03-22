package com.soloheisbeer.microwaffle400.network

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

interface StatusUpdateInterface {
    fun onStatusUpdate(status: JSONObject)
}

interface ConnectionUpdateInterface {
    fun connectedToMicrowave()
    fun disconnectedToMicrowave()
}

object NetworkManager {

    private const val microURL = "http://192.168.178.146"
//    private const val microURL = "http://192.168.178.10:3030"
    private lateinit var socket: Socket
    private var statusUpdateCallbacks = ArrayList<StatusUpdateInterface>()
    private var connectionUpdateCallbacks = ArrayList<ConnectionUpdateInterface>()

    var isConnected = false

    init {
        try {
            socket = IO.socket(microURL)
            socket.on("connect", OnConnected)
            socket.on("disconnect", OnDisconnected)
            socket.on("statusUpdate", OnStatusUpdate)
        } catch (e: URISyntaxException) {
        }
    }

    object OnConnected : Emitter.Listener {
        override fun call(vararg args: Any?) {
            isConnected = true
            synchronized(connectionUpdateCallbacks) {
                for (cuc in connectionUpdateCallbacks) {
                    cuc.connectedToMicrowave()
                }
            }
        }
    }

    object OnDisconnected : Emitter.Listener {
        override fun call(vararg args: Any?) {
            isConnected = false
            synchronized(connectionUpdateCallbacks) {
                for (cuc in connectionUpdateCallbacks) {
                    cuc.disconnectedToMicrowave()
                }
            }
        }
    }

    object OnStatusUpdate : Emitter.Listener {
        override fun call(vararg args: Any?) {
            val data = args[0] as JSONObject
            synchronized(statusUpdateCallbacks) {
                for (suc in statusUpdateCallbacks) {
                    suc.onStatusUpdate(data)
                }
            }
        }
    }

    fun connectToMicrowave() {
        if (!socket.connected())
            socket.connect()
    }

    fun startMicrowave(timeInSeconds: Int) {
        socket.emit("start", timeInSeconds)
    }

    fun pauseMicrowave() {
        socket.emit("pause")
    }

    fun stopMicrowave() {
        socket.emit("stop")
    }

    fun addTimeToMicrowave(timeInSeconds: Int) {
        socket.emit("addTime", timeInSeconds)
    }

    fun sendStatusUpdateRequest() {
        socket.emit("getStatus")
    }

    fun cleanUp() {
        socket.disconnect()
        socket.off("statusUpdate", OnStatusUpdate)
    }

    fun addStatusUpdateCallback(suc: StatusUpdateInterface) {
        synchronized(statusUpdateCallbacks) {
            statusUpdateCallbacks.add(suc)
        }
    }

    fun removeStatusUpdateCallback(suc: StatusUpdateInterface) {
        synchronized(statusUpdateCallbacks) {
            statusUpdateCallbacks.remove(suc)
        }
    }

    fun addConnectionUpdateCallback(cuc: ConnectionUpdateInterface) {
        synchronized(connectionUpdateCallbacks) {
            connectionUpdateCallbacks.add(cuc)
        }
    }

    fun removeConnectionUpdateCallback(cuc: ConnectionUpdateInterface) {
        synchronized(connectionUpdateCallbacks) {
            connectionUpdateCallbacks.remove(cuc)
        }
    }
}