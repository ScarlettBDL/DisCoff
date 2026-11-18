package com.example.discordtoolkit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.discordtoolkit.discord.Embed
import com.example.discordtoolkit.discord.EmbedField
import com.example.discordtoolkit.discord.timeLabel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Un campo (nombre + valor) del embed que se está armando. */
class EmbedFieldDraft(val key: Long) {
    var name by mutableStateOf("")
    var value by mutableStateOf("")
    var inline by mutableStateOf(false)
}

/**
 * La plantilla del embed con todo lo que Discord permite. Los campos son estado de Compose directo
 * (no pasan por un StateFlow) para que escribir no tenga retraso ni mueva el cursor.
 */
class EmbedDraft {
    companion object {
        const val MAX_TITLE = 256
        const val MAX_DESCRIPTION = 4096
        const val MAX_AUTHOR = 256
        const val MAX_FOOTER = 2048
        const val MAX_FIELD_NAME = 256
        const val MAX_FIELD_VALUE = 1024
        const val MAX_FIELDS = 25
        const val MAX_TOTAL = 6000
        const val MAX_CONTENT = 2000
        const val MAX_URL = 2000
    }

    /** Servidor al que pertenece el borrador; al abrir la herramienta en otro se empieza de cero. */
    var guildId: String? = null
    var channelId by mutableStateOf<String?>(null)

    /** Si no es null, se está editando ese mensaje ya enviado en vez de armar uno nuevo. */
    var editingMessageId by mutableStateOf<String?>(null)

    var content by mutableStateOf("")

    var authorName by mutableStateOf("")
    var authorUrl by mutableStateOf("")
    var authorIconUrl by mutableStateOf("")

    var title by mutableStateOf("")
    var titleUrl by mutableStateOf("")
    var description by mutableStateOf("")
    var color by mutableIntStateOf(0)

    var thumbnailUrl by mutableStateOf("")
    var imageUrl by mutableStateOf("")

    val fields = mutableStateListOf<EmbedFieldDraft>()

    var footerText by mutableStateOf("")
    var footerIconUrl by mutableStateOf("")
    var timestamp by mutableStateOf(false)

    private var counter = 0L

    fun addField() {
        if (fields.size >= MAX_FIELDS) return
        counter++
        fields.add(EmbedFieldDraft(counter))
    }

    /** Vacía todo menos el servidor y el canal elegidos. */
    fun clear() {
        content = ""
        authorName = ""
        authorUrl = ""
        authorIconUrl = ""
        title = ""
        titleUrl = ""
        description = ""
        color = 0
        thumbnailUrl = ""
        imageUrl = ""
        fields.clear()
        footerText = ""
        footerIconUrl = ""
        timestamp = false
    }

    fun reset() {
        clear()
        channelId = null
        editingMessageId = null
    }

    /** Carga un embed ya enviado para poder editarlo. */
    fun loadFromEmbed(e: Embed, text: String) {
        clear()
        content = text
        authorName = e.authorName.orEmpty()
        authorUrl = e.authorUrl.orEmpty()
        authorIconUrl = e.authorIconUrl.orEmpty()
        title = e.title.orEmpty()
        titleUrl = e.linkUrl.orEmpty()
        description = e.description.orEmpty()
        color = e.color ?: 0
        thumbnailUrl = e.thumbnailUrl.orEmpty()
        imageUrl = e.imageUrl.orEmpty()
        footerText = e.footerText.orEmpty()
        footerIconUrl = e.footerIconUrl.orEmpty()
        timestamp = e.timeLabel != null
        e.fields.take(MAX_FIELDS).forEach { f ->
            addField()
            fields.last().also {
                it.name = f.name
                it.value = f.value
                it.inline = f.inline
            }
        }
    }

    /** El borrador completo (texto + embed) para guardarlo como plantilla. */
    fun toTemplate(): JSONObject {
        val o = toJson()
        o.remove("timestamp")
        o.put("ts", timestamp)
        o.put("content", content)
        return o
    }

    fun loadTemplate(o: JSONObject) {
        clear()
        content = o.optString("content")
        title = o.optString("title")
        titleUrl = o.optString("url")
        description = o.optString("description")
        color = o.optInt("color", 0)
        timestamp = o.optBoolean("ts", false)
        o.optJSONObject("author")?.let {
            authorName = it.optString("name")
            authorUrl = it.optString("url")
            authorIconUrl = it.optString("icon_url")
        }
        thumbnailUrl = o.optJSONObject("thumbnail")?.optString("url").orEmpty()
        imageUrl = o.optJSONObject("image")?.optString("url").orEmpty()
        o.optJSONObject("footer")?.let {
            footerText = it.optString("text")
            footerIconUrl = it.optString("icon_url")
        }
        val arr = o.optJSONArray("fields")
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), MAX_FIELDS)) {
                val f = arr.optJSONObject(i) ?: continue
                addField()
                fields.last().also {
                    it.name = f.optString("name")
                    it.value = f.optString("value")
                    it.inline = f.optBoolean("inline", false)
                }
            }
        }
    }

    private fun nz(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    /** true si el embed (sin contar el mensaje de texto) no tiene nada cargado. */
    fun isEmbedEmpty(): Boolean =
        nz(title) == null && nz(description) == null && nz(authorName) == null &&
            nz(thumbnailUrl) == null && nz(imageUrl) == null && nz(footerText) == null &&
            fields.isEmpty()

    /** Caracteres que Discord cuenta para el límite de 6000 del embed. */
    fun totalChars(): Int {
        var total = title.trim().length + description.trim().length +
            authorName.trim().length + footerText.trim().length
        fields.forEach { total += it.name.trim().length + it.value.trim().length }
        return total
    }

    /** El embed como lo vería el chat, para la vista previa. */
    fun toEmbed(): Embed = Embed(
        title = nz(title),
        description = nz(description),
        imageUrl = nz(imageUrl),
        imageWidth = null,
        imageHeight = null,
        color = if (color != 0) color else null,
        linkUrl = nz(titleUrl),
        authorName = nz(authorName),
        authorIconUrl = nz(authorIconUrl),
        authorUrl = nz(authorUrl),
        thumbnailUrl = nz(thumbnailUrl),
        footerText = nz(footerText),
        footerIconUrl = nz(footerIconUrl),
        timeLabel = if (timestamp) timeLabel(System.currentTimeMillis()) else null,
        fields = fields.map { EmbedField(it.name.trim(), it.value.trim(), it.inline) }
    )

    private fun isHttp(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /** Devuelve un texto con el problema, o null si el embed se puede mandar. */
    fun validate(): String? {
        if (isEmbedEmpty()) return "El embed está vacío: cargá al menos un título, descripción, campo o imagen"
        if (totalChars() > MAX_TOTAL) return "El embed supera los $MAX_TOTAL caracteres en total (${totalChars()})"

        val urls = listOf(
            "del título" to titleUrl,
            "del autor" to authorUrl,
            "del ícono del autor" to authorIconUrl,
            "de la miniatura" to thumbnailUrl,
            "de la imagen" to imageUrl,
            "del ícono del pie" to footerIconUrl
        )
        for ((label, value) in urls) {
            val url = value.trim()
            if (url.isNotEmpty() && !isHttp(url)) return "La URL $label tiene que empezar con http:// o https://"
        }

        if (nz(authorName) == null && (nz(authorUrl) != null || nz(authorIconUrl) != null)) {
            return "El autor necesita un nombre"
        }
        if (nz(footerText) == null && nz(footerIconUrl) != null) return "El pie necesita un texto"

        fields.forEachIndexed { index, f ->
            if (nz(f.name) == null || nz(f.value) == null) {
                return "El campo ${index + 1} necesita nombre y valor"
            }
        }
        return null
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    /** El embed en el formato de la API de Discord. */
    fun toJson(): JSONObject {
        val o = JSONObject()
        nz(title)?.let { o.put("title", it) }
        nz(titleUrl)?.let { o.put("url", it) }
        nz(description)?.let { o.put("description", it) }
        if (color != 0) o.put("color", color)

        val authorNameValue = nz(authorName)
        if (authorNameValue != null) {
            val a = JSONObject().put("name", authorNameValue)
            nz(authorUrl)?.let { a.put("url", it) }
            nz(authorIconUrl)?.let { a.put("icon_url", it) }
            o.put("author", a)
        }

        nz(thumbnailUrl)?.let { o.put("thumbnail", JSONObject().put("url", it)) }
        nz(imageUrl)?.let { o.put("image", JSONObject().put("url", it)) }

        if (fields.isNotEmpty()) {
            val arr = JSONArray()
            fields.forEach {
                arr.put(
                    JSONObject()
                        .put("name", it.name.trim())
                        .put("value", it.value.trim())
                        .put("inline", it.inline)
                )
            }
            o.put("fields", arr)
        }

        val footerValue = nz(footerText)
        if (footerValue != null) {
            val f = JSONObject().put("text", footerValue)
            nz(footerIconUrl)?.let { f.put("icon_url", it) }
            o.put("footer", f)
        }

        if (timestamp) o.put("timestamp", isoNow())
        return o
    }
}
