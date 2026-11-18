package com.example.discordtoolkit.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Cliente REST de Discord (API v10) para un bot. */
class DiscordApi(private val token: String) {
    class ApiException(val code: Int, message: String) : Exception(message)

    private class Raw(val code: Int, val body: String)

    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun request(path: String): Request.Builder =
        Request.Builder()
            .url(BASE + path)
            .header("Authorization", "Bot $token")
            .header("User-Agent", "DiscordBot (https://github.com/ScarlettBDL/DisCoff, 0.2.0)")

    private fun errorText(raw: Raw): String {
        val msg = try {
            JSONObject(raw.body).optString("message", "")
        } catch (e: Exception) {
            ""
        }
        return when (raw.code) {
            401 -> "Token inválido"
            403 -> "Sin permisos: " + msg.ifEmpty { "Missing Access" }
            else -> msg.ifEmpty { "Error ${raw.code}" }
        }
    }

    /** Ejecuta la request y reintenta si Discord responde 429 (rate limit). */
    private suspend fun execute(build: () -> Request): String {
        var attempts = 0
        while (true) {
            val raw = withContext(Dispatchers.IO) {
                client.newCall(build()).execute().use { Raw(it.code, it.body?.string().orEmpty()) }
            }
            if (raw.code == 429 && attempts < 3) {
                attempts++
                val seconds = try {
                    JSONObject(raw.body).optDouble("retry_after", 1.0)
                } catch (e: Exception) {
                    1.0
                }
                delay((seconds * 1000).toLong() + 100)
                continue
            }
            if (raw.code !in 200..299) throw ApiException(raw.code, errorText(raw))
            return raw.body
        }
    }

    suspend fun getMe(): DUser {
        val body = execute { request("/users/@me").get().build() }
        return Parse.user(JSONObject(body))
    }

    suspend fun getGuilds(): List<DGuild> {
        val body = execute { request("/users/@me/guilds?limit=200").get().build() }
        return JSONArray(body).mapObjects { Parse.guild(it) }
    }

    suspend fun getChannels(guildId: String): List<DChannel> {
        val body = execute { request("/guilds/$guildId/channels").get().build() }
        return JSONArray(body).mapObjects { Parse.channel(it, guildId) }
    }

    /** Devuelve los mensajes del más viejo al más nuevo. */
    suspend fun getMessages(channelId: String, limit: Int = 50): List<Message> {
        val body = execute { request("/channels/$channelId/messages?limit=$limit").get().build() }
        val arr = JSONArray(body)
        val list = ArrayList<Message>()
        for (i in 0 until arr.length()) {
            try {
                list.add(Parse.message(arr.getJSONObject(i)))
            } catch (e: Exception) {
                // mensaje raro que no se puede leer: se saltea
            }
        }
        return list.reversed()
    }

    suspend fun getMessage(channelId: String, messageId: String): Message {
        val body = execute { request("/channels/$channelId/messages/$messageId").get().build() }
        return Parse.message(JSONObject(body))
    }

    private fun replyRef(messageId: String): JSONObject =
        JSONObject().put("message_id", messageId).put("fail_if_not_exists", false)

    suspend fun sendMessage(channelId: String, content: String, replyTo: String?): Message {
        val payload = JSONObject().put("content", content)
        if (replyTo != null) payload.put("message_reference", replyRef(replyTo))
        val body = execute {
            request("/channels/$channelId/messages")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        }
        return Parse.message(JSONObject(body))
    }

    suspend fun sendWithFile(
        channelId: String,
        content: String,
        replyTo: String?,
        bytes: ByteArray,
        filename: String,
        mime: String?
    ): Message {
        val payload = JSONObject()
        if (content.isNotEmpty()) payload.put("content", content)
        if (replyTo != null) payload.put("message_reference", replyRef(replyTo))
        val fileType = (mime ?: "application/octet-stream").toMediaTypeOrNull()
        val body = execute {
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", payload.toString())
                .addFormDataPart("files[0]", filename, bytes.toRequestBody(fileType))
                .build()
            request("/channels/$channelId/messages").post(multipart).build()
        }
        return Parse.message(JSONObject(body))
    }

    suspend fun editMessage(channelId: String, messageId: String, content: String): Message {
        val payload = JSONObject().put("content", content)
        val body = execute {
            request("/channels/$channelId/messages/$messageId")
                .patch(payload.toString().toRequestBody(jsonType))
                .build()
        }
        return Parse.message(JSONObject(body))
    }

    suspend fun deleteMessage(channelId: String, messageId: String) {
        execute { request("/channels/$channelId/messages/$messageId").delete().build() }
    }

    private fun encodeEmoji(emoji: String): String = URLEncoder.encode(emoji, "UTF-8")

    suspend fun addReaction(channelId: String, messageId: String, emoji: String) {
        val path = "/channels/$channelId/messages/$messageId/reactions/${encodeEmoji(emoji)}/@me"
        execute { request(path).put("".toRequestBody()).build() }
    }

    suspend fun removeReaction(channelId: String, messageId: String, emoji: String) {
        val path = "/channels/$channelId/messages/$messageId/reactions/${encodeEmoji(emoji)}/@me"
        execute { request(path).delete().build() }
    }

    /** Abre (o recupera) el canal de DM con un usuario. Solo funciona con usuarios que comparten server con el bot. */
    suspend fun openDm(userId: String): DmEntry {
        val payload = JSONObject().put("recipient_id", userId)
        val body = execute {
            request("/users/@me/channels")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        }
        val o = JSONObject(body)
        val recipient = o.optJSONArray("recipients")?.optJSONObject(0)
        val name = if (recipient != null) Parse.user(recipient).name else userId
        return DmEntry(channelId = o.getString("id"), userId = userId, name = name)
    }

    companion object {
        private const val BASE = "https://discord.com/api/v10"
    }
}
