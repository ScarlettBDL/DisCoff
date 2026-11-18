package com.example.discordtoolkit.discord

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DUser(val id: String, val name: String, val avatar: String?, val bot: Boolean) {
    val avatarUrl: String
        get() = if (avatar != null) {
            "https://cdn.discordapp.com/avatars/$id/$avatar.png?size=64"
        } else {
            val index = ((id.toLongOrNull() ?: 0L) shr 22) % 6
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
}

data class DGuild(val id: String, val name: String, val icon: String?) {
    val iconUrl: String?
        get() = if (icon != null) "https://cdn.discordapp.com/icons/$id/$icon.png?size=128" else null

    val initials: String
        get() = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

data class DChannel(
    val id: String,
    val guildId: String?,
    val name: String,
    val type: Int,
    val parentId: String?,
    val position: Int
) {
    val isText: Boolean get() = type == 0 || type == 5
    val isVoice: Boolean get() = type == 2 || type == 13
    val isCategory: Boolean get() = type == 4
}

data class Attachment(
    val id: String,
    val filename: String,
    val url: String,
    val contentType: String?,
    val width: Int?,
    val height: Int?
) {
    val isImage: Boolean
        get() = contentType?.startsWith("image/") == true ||
            filename.lowercase().let {
                it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                    it.endsWith(".gif") || it.endsWith(".webp")
            }

    val isVideo: Boolean
        get() = contentType?.startsWith("video/") == true || isVideoFile(filename)
}

fun isVideoFile(nameOrUrl: String): Boolean {
    val path = nameOrUrl.substringBefore('?').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") ||
        path.endsWith(".m4v") || path.endsWith(".mkv")
}

/** Rol creado en un servidor. */
data class DRole(val id: String, val name: String) {
    /** Así se menciona un rol en un mensaje de Discord. */
    val mention: String get() = "<@&$id>"
}

data class Reaction(val emojiName: String, val emojiId: String?, val count: Int, val me: Boolean) {
    /** Formato que pide la API de Discord en la URL de la reacción. */
    val apiEmoji: String get() = if (emojiId != null) "$emojiName:$emojiId" else emojiName
    val customUrl: String? get() = if (emojiId != null) "https://cdn.discordapp.com/emojis/$emojiId.png?size=32" else null
}

data class EmbedField(val name: String, val value: String, val inline: Boolean)

data class Embed(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val color: Int?,
    /** Solo si el embed trae un archivo de video reproducible directo (mp4, webm…), como los gifs de Tenor. */
    val videoUrl: String? = null,
    /** El link del título (la `url` del embed). */
    val linkUrl: String? = null,
    val authorName: String? = null,
    val authorIconUrl: String? = null,
    val authorUrl: String? = null,
    val thumbnailUrl: String? = null,
    val footerText: String? = null,
    val footerIconUrl: String? = null,
    /** Fecha del embed ya formateada para mostrar. */
    val timeLabel: String? = null,
    val fields: List<EmbedField> = emptyList()
)

/** Un rol de un servidor, con lo necesario para pintar el nombre de sus miembros. */
data class RoleInfo(
    val id: String,
    val name: String,
    val color: Int,
    val position: Int,
    /** Roles de integraciones/bots: no se pueden dar ni quitar a mano. */
    val managed: Boolean = false
)

/** Lo que Discord sabe de un miembro dentro de un server. */
data class MemberInfo(val roles: List<String>, val nick: String?, val timeoutUntilMs: Long?) {
    fun isTimedOut(now: Long = System.currentTimeMillis()): Boolean = timeoutUntilMs != null && timeoutUntilMs > now
}

/** Un usuario baneado y el motivo (si se dejó uno). */
data class BanEntry(val user: DUser, val reason: String?)

data class ReplySnippet(val authorName: String, val content: String)

data class Message(
    val id: String,
    val channelId: String,
    val guildId: String?,
    val author: DUser,
    val rawContent: String,
    val content: String,
    val epochMs: Long,
    val timeLabel: String,
    val edited: Boolean,
    val attachments: List<Attachment>,
    val reactions: List<Reaction>,
    val embeds: List<Embed>,
    val reply: ReplySnippet?,
    /** Identificador que mandamos al enviar; Discord lo devuelve y sirve para reemplazar el mensaje pendiente. */
    val nonce: String? = null,
    /** true mientras el mensaje se muestra localmente y todavía no confirmó Discord. */
    val pending: Boolean = false
)

/** Un DM conocido por el bot. Discord no deja listar los DMs de un bot, por eso se guardan localmente. */
data class DmEntry(
    val channelId: String,
    val userId: String,
    val name: String,
    val avatar: String? = null
) {
    val avatarUrl: String get() = DUser(userId, name, avatar, false).avatarUrl
}

fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    val arr = this ?: return emptyList()
    val out = ArrayList<T>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        out.add(transform(obj))
    }
    return out
}

// SimpleDateFormat es caro de crear y no es thread-safe: uno por hilo, y las horas se calculan al parsear.
private val isoFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f
    }
}

private val labelFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
}

fun parseIso(iso: String): Long {
    try {
        return isoFormat.get()!!.parse(iso.take(19))?.time ?: 0L
    } catch (e: Exception) {
        return 0L
    }
}

fun timeLabel(epochMs: Long): String {
    if (epochMs == 0L) return ""
    return labelFormat.get()!!.format(Date(epochMs))
}

object Parse {
    private val customEmoji = Regex("<a?:(\\w+):\\d+>")
    private val channelMention = Regex("<#\\d+>")

    fun user(o: JSONObject): DUser = DUser(
        id = o.optString("id"),
        name = o.str("global_name") ?: o.optString("username", "?"),
        avatar = o.str("avatar"),
        bot = o.optBoolean("bot", false)
    )

    fun guild(o: JSONObject): DGuild = DGuild(o.optString("id"), o.optString("name"), o.str("icon"))

    fun channel(o: JSONObject, guildId: String?): DChannel = DChannel(
        id = o.optString("id"),
        guildId = o.str("guild_id") ?: guildId,
        name = o.str("name") ?: "",
        type = o.optInt("type", 0),
        parentId = o.str("parent_id"),
        position = o.optInt("position", 0)
    )

    private fun attachment(o: JSONObject): Attachment = Attachment(
        id = o.optString("id"),
        filename = o.optString("filename", "archivo"),
        url = o.optString("url"),
        contentType = o.str("content_type"),
        width = if (o.isNull("width")) null else o.optInt("width"),
        height = if (o.isNull("height")) null else o.optInt("height")
    )

    private fun reaction(o: JSONObject): Reaction {
        val emoji = o.optJSONObject("emoji") ?: JSONObject()
        return Reaction(
            emojiName = emoji.str("name") ?: "?",
            emojiId = emoji.str("id"),
            count = o.optInt("count", 0),
            me = o.optBoolean("me", false)
        )
    }

    private fun embed(o: JSONObject): Embed {
        val type = o.optString("type", "rich")
        val video = o.optJSONObject("video")
        val image = o.optJSONObject("image")
        val thumb = o.optJSONObject("thumbnail")
        val videoUrl = video?.str("url")?.takeIf { isVideoFile(it) }
        // En los embeds de imagen/gif la "miniatura" es la imagen principal; en los demás es la miniatura chica.
        val thumbIsMain = type == "image" || type == "gifv" || videoUrl != null
        val main = if (thumbIsMain) (image ?: thumb) else image
        val author = o.optJSONObject("author")
        val footer = o.optJSONObject("footer")
        val timestamp = o.str("timestamp")
        return Embed(
            title = o.str("title"),
            description = o.str("description"),
            imageUrl = main?.str("url"),
            imageWidth = main?.intOrNull("width") ?: video?.intOrNull("width"),
            imageHeight = main?.intOrNull("height") ?: video?.intOrNull("height"),
            color = o.intOrNull("color"),
            videoUrl = videoUrl,
            linkUrl = o.str("url"),
            authorName = author?.str("name"),
            authorIconUrl = author?.str("icon_url"),
            authorUrl = author?.str("url"),
            thumbnailUrl = if (thumbIsMain) null else thumb?.str("url"),
            footerText = footer?.str("text"),
            footerIconUrl = footer?.str("icon_url"),
            timeLabel = if (timestamp != null) timeLabel(parseIso(timestamp)).ifEmpty { null } else null,
            fields = o.optJSONArray("fields").mapObjects {
                EmbedField(
                    name = it.optString("name", ""),
                    value = it.optString("value", ""),
                    inline = it.optBoolean("inline", false)
                )
            }
        )
    }

    fun roleInfo(o: JSONObject): RoleInfo = RoleInfo(
        id = o.optString("id"),
        name = o.optString("name", ""),
        color = o.optInt("color", 0),
        position = o.optInt("position", 0),
        managed = o.optBoolean("managed", false)
    )

    fun memberInfo(o: JSONObject): MemberInfo {
        val arr = o.optJSONArray("roles")
        val roles = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }
        val until = o.str("communication_disabled_until")?.let { parseIso(it) }?.takeIf { it > 0L }
        return MemberInfo(roles = roles, nick = o.str("nick"), timeoutUntilMs = until)
    }

    fun banEntry(o: JSONObject): BanEntry =
        BanEntry(user = user(o.getJSONObject("user")), reason = o.str("reason"))

    private fun cleanContent(raw: String, mentions: JSONArray?): String {
        var text = raw
        mentions.mapObjects { user(it) }.forEach { u ->
            text = text.replace("<@${u.id}>", "@${u.name}").replace("<@!${u.id}>", "@${u.name}")
        }
        text = customEmoji.replace(text) { ":${it.groupValues[1]}:" }
        return channelMention.replace(text, "#canal")
    }

    fun message(o: JSONObject): Message {
        val raw = o.optString("content", "")
        val ref = o.optJSONObject("referenced_message")
        val reply = if (ref != null) {
            val refAuthor = ref.optJSONObject("author")
            ReplySnippet(
                authorName = if (refAuthor != null) user(refAuthor).name else "?",
                content = ref.optString("content", "").replace("\n", " ").take(80)
            )
        } else {
            null
        }
        val epochMs = parseIso(o.optString("timestamp", ""))
        return Message(
            id = o.getString("id"),
            channelId = o.getString("channel_id"),
            guildId = o.str("guild_id"),
            author = user(o.getJSONObject("author")),
            rawContent = raw,
            content = cleanContent(raw, o.optJSONArray("mentions")),
            epochMs = epochMs,
            timeLabel = timeLabel(epochMs),
            edited = !o.isNull("edited_timestamp"),
            attachments = o.optJSONArray("attachments").mapObjects { attachment(it) },
            reactions = o.optJSONArray("reactions").mapObjects { reaction(it) },
            embeds = o.optJSONArray("embeds").mapObjects { embed(it) },
            reply = reply,
            nonce = o.str("nonce")
        )
    }
}
