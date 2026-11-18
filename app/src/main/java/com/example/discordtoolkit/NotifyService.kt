package com.example.discordtoolkit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.discordtoolkit.data.Storage
import com.example.discordtoolkit.discord.Gateway
import com.example.discordtoolkit.discord.Parse
import org.json.JSONObject

/** Qué está viendo el usuario ahora; el servicio no avisa de mensajes del chat que ya tiene abierto. */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false

    @Volatile
    var openChannelId: String? = null
}

/**
 * Mantiene una conexión al Gateway en segundo plano (con una notificación fija, como exige Android)
 * y avisa de los mensajes directos y de las menciones al bot.
 */
class NotifyService : Service() {
    private var gateway: Gateway? = null

    @Volatile
    private var myId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val ongoing = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Discord Toolkit")
            .setContentText("Conectado para avisarte de mensajes y menciones")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(STATUS_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(STATUS_ID, ongoing)
        }

        val token = Storage(this).token()
        if (token == null) {
            stopSelf()
            return
        }
        gateway = Gateway(token, object : Gateway.Listener {
            override fun onStatus(text: String) {}

            override fun onDispatch(type: String, data: JSONObject) {
                try {
                    when (type) {
                        "READY" -> myId = data.optJSONObject("user")?.optString("id")
                        "MESSAGE_CREATE" -> onMessage(data)
                    }
                } catch (e: Exception) {
                    // un evento ilegible no debe tirar abajo el servicio
                }
            }

            override fun onFatal(text: String) {
                stopSelf()
            }
        }).also { it.start() }
    }

    private fun onMessage(d: JSONObject) {
        val me = myId ?: return
        val m = Parse.message(d)
        if (m.author.id == me || m.author.bot) return
        val isDm = d.optString("guild_id", "").isEmpty()
        val mentioned = d.optJSONArray("mentions")?.let { arr ->
            (0 until arr.length()).any { arr.optJSONObject(it)?.optString("id") == me }
        } == true
        if (!isDm && !mentioned) return
        if (AppVisibility.foreground && AppVisibility.openChannelId == m.channelId) return

        val text = m.content.ifBlank {
            when {
                m.attachments.isNotEmpty() -> "[adjunto]"
                m.embeds.isNotEmpty() -> "[embed]"
                else -> "[mensaje]"
            }
        }
        val title = if (isDm) m.author.name else "${m.author.name} te mencionó"
        val n = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(500)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        try {
            manager.notify(m.channelId.hashCode(), n)
        } catch (e: SecurityException) {
            // sin permiso de notificaciones: no se avisa
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Conexión", NotificationManager.IMPORTANCE_MIN)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Mensajes y menciones", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        gateway?.stop()
        gateway = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_STATUS = "status"
        private const val CHANNEL_MESSAGES = "messages"
        private const val STATUS_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NotifyService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotifyService::class.java))
        }
    }
}
