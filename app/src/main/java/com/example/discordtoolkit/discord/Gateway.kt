package com.example.discordtoolkit.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Conexión al Gateway de Discord para recibir eventos en vivo (mensajes, reacciones, etc.).
 * Los callbacks del Listener llegan desde un hilo de red, no del hilo principal.
 */
class Gateway(private val token: String, private val listener: Listener) {
    interface Listener {
        fun onStatus(text: String)
        fun onDispatch(type: String, data: JSONObject)
        fun onFatal(text: String)
    }

    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var seq: Int? = null

    @Volatile
    private var stopped = false

    private var heartbeatJob: Job? = null

    fun start() {
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        heartbeatJob?.cancel()
        ws?.close(1000, "bye")
        ws = null
        scope.cancel()
    }

    private fun connect() {
        if (stopped) return
        listener.onStatus("Conectando…")
        seq = null
        val request = Request.Builder().url(URL).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleClose(webSocket, code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleClose(webSocket, -1)
            }
        })
    }

    private fun handle(webSocket: WebSocket, text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        when (json.optInt("op", -1)) {
            0 -> {
                if (!json.isNull("s")) seq = json.optInt("s")
                val type = json.optString("t")
                val data = json.optJSONObject("d") ?: JSONObject()
                if (type == "READY") listener.onStatus("Conectado")
                listener.onDispatch(type, data)
            }
            1 -> sendHeartbeat(webSocket)
            7, 9 -> webSocket.close(4000, "reconnect")
            10 -> {
                val interval = json.optJSONObject("d")?.optLong("heartbeat_interval", 41250L) ?: 41250L
                startHeartbeat(webSocket, interval)
                identify(webSocket)
            }
        }
    }

    private fun handleClose(webSocket: WebSocket, code: Int) {
        if (stopped || webSocket !== ws) return
        heartbeatJob?.cancel()
        val fatal = when (code) {
            4004 -> "Token inválido: Discord rechazó la conexión del bot"
            4013, 4014 -> "Intents no permitidos: activá MESSAGE CONTENT INTENT en el Developer Portal (Bot)"
            else -> null
        }
        if (fatal != null) {
            stopped = true
            listener.onFatal(fatal)
            return
        }
        listener.onStatus("Reconectando…")
        scope.launch {
            delay(3000)
            connect()
        }
    }

    private fun sendHeartbeat(webSocket: WebSocket) {
        webSocket.send("{\"op\":1,\"d\":${seq ?: "null"}}")
    }

    private fun startHeartbeat(webSocket: WebSocket, interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay((interval * Math.random()).toLong())
            while (isActive) {
                sendHeartbeat(webSocket)
                delay(interval)
            }
        }
    }

    private fun identify(webSocket: WebSocket) {
        val properties = JSONObject()
            .put("os", "android")
            .put("browser", "discoff")
            .put("device", "discoff")
        val data = JSONObject()
            .put("token", token)
            .put("intents", INTENTS)
            .put("properties", properties)
        webSocket.send(JSONObject().put("op", 2).put("d", data).toString())
    }

    companion object {
        private const val URL = "wss://gateway.discord.gg/?v=10&encoding=json"

        // GUILDS | GUILD_MESSAGES | GUILD_MESSAGE_REACTIONS | DIRECT_MESSAGES | DIRECT_MESSAGE_REACTIONS | MESSAGE_CONTENT
        private const val INTENTS = (1 shl 0) or (1 shl 9) or (1 shl 10) or (1 shl 12) or (1 shl 13) or (1 shl 15)
    }
}
