package com.example.discordtoolkit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.discordtoolkit.data.Storage
import com.example.discordtoolkit.discord.DiscordApi
import com.example.discordtoolkit.discord.snowflakeMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ModLogEntry(
    val time: Long,
    val guildId: String,
    val action: String,
    val targetId: String,
    val targetName: String,
    val reason: String?
)

class AuditRow(val time: Long, val actor: String, val action: String, val target: String, val reason: String?)

enum class BulkAction(val label: String) {
    BAN("Banear"),
    KICK("Expulsar"),
    MUTE("Mutear (timeout)"),
    UNMUTE("Quitar mute"),
    UNBAN("Desbanear"),
    ADD_ROLE("Dar un rol"),
    REMOVE_ROLE("Quitar un rol")
}

/** Fecha "ahora + ms" en el formato ISO UTC que pide Discord para el timeout. */
fun isoIn(ms: Long): String {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return format.format(java.util.Date(System.currentTimeMillis() + ms))
}

/**
 * Herramientas de administración extra: registro de moderación local, acciones masivas,
 * limpieza de mensajes y registro de auditoría. El estado es de Compose directo para que los campos no se traben.
 */
class Admin(
    private val scope: CoroutineScope,
    private val api: () -> DiscordApi?,
    private val storage: Storage,
    private val botId: () -> String?,
    private val notify: (String) -> Unit,
    private val describe: (Exception) -> String
) {
    // ---------- Registro de moderación (guardado en el teléfono) ----------
    var log by mutableStateOf<List<ModLogEntry>>(emptyList())

    fun loadLog() {
        val id = botId() ?: return
        val raw = storage.blob("modlog_$id") ?: return
        log = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ModLogEntry(
                    o.optLong("t"),
                    o.optString("g"),
                    o.optString("a"),
                    o.optString("i"),
                    o.optString("n"),
                    if (o.isNull("r")) null else o.optString("r")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun record(guildId: String, action: String, targetId: String, targetName: String, reason: String?) {
        val id = botId() ?: return
        val entry = ModLogEntry(System.currentTimeMillis(), guildId, action, targetId, targetName, reason)
        log = (listOf(entry) + log).take(500)
        val arr = JSONArray()
        log.forEach {
            val o = JSONObject().put("t", it.time).put("g", it.guildId).put("a", it.action)
                .put("i", it.targetId).put("n", it.targetName)
            if (it.reason != null) o.put("r", it.reason)
            arr.put(o)
        }
        storage.saveBlob("modlog_$id", arr.toString())
    }

    fun clearLog(guildId: String) {
        val id = botId() ?: return
        log = log.filter { it.guildId != guildId }
        val arr = JSONArray()
        log.forEach {
            val o = JSONObject().put("t", it.time).put("g", it.guildId).put("a", it.action)
                .put("i", it.targetId).put("n", it.targetName)
            if (it.reason != null) o.put("r", it.reason)
            arr.put(o)
        }
        storage.saveBlob("modlog_$id", arr.toString())
    }

    // ---------- Acciones masivas ----------
    var bulkIds by mutableStateOf("")
    var bulkReason by mutableStateOf("")
    var bulkBusy by mutableStateOf(false)
    var bulkDone by mutableStateOf(0)
    var bulkTotal by mutableStateOf(0)
    var bulkReport by mutableStateOf<List<String>>(emptyList())

    fun parseIds(text: String): List<String> =
        Regex("\\d{15,20}").findAll(text).map { it.value }.distinct().toList()

    fun runBulk(guildId: String, action: BulkAction, durationMs: Long, roleId: String?, roleName: String?) {
        val a = api() ?: return
        if (bulkBusy) return
        val ids = parseIds(bulkIds)
        if (ids.isEmpty()) {
            notify("Pegá al menos un ID de usuario")
            return
        }
        val reason = bulkReason.trim().ifEmpty { null }
        bulkBusy = true
        bulkDone = 0
        bulkTotal = ids.size
        bulkReport = emptyList()
        scope.launch {
            val problems = ArrayList<String>()
            var ok = 0
            try {
                for (id in ids) {
                    try {
                        when (action) {
                            BulkAction.BAN -> a.banMember(guildId, id, 0, reason)
                            BulkAction.KICK -> a.kickMember(guildId, id, reason)
                            BulkAction.MUTE -> a.timeoutMember(guildId, id, isoIn(durationMs), reason)
                            BulkAction.UNMUTE -> a.timeoutMember(guildId, id, null, reason)
                            BulkAction.UNBAN -> a.unbanMember(guildId, id, reason)
                            BulkAction.ADD_ROLE -> a.addMemberRole(guildId, id, roleId ?: "", reason)
                            BulkAction.REMOVE_ROLE -> a.removeMemberRole(guildId, id, roleId ?: "", reason)
                        }
                        ok++
                        val label = if (roleName != null) "${action.label}: $roleName" else action.label
                        record(guildId, label, id, id, reason)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        problems.add("$id → ${describe(e)}")
                    }
                    bulkDone++
                }
            } finally {
                bulkReport = listOf("✓ $ok de ${ids.size} hechos") + problems.map { "✗ $it" }
                bulkBusy = false
            }
        }
    }

    // ---------- Limpiar mensajes ----------
    var clearBusy by mutableStateOf(false)
    var clearResult by mutableStateOf<String?>(null)

    /** Borra hasta [count] mensajes entre los últimos 100 del canal; si hay [userId], solo los de ese usuario. */
    fun clearMessages(guildId: String, channelId: String, channelName: String, userId: String?, count: Int) {
        val a = api() ?: return
        if (clearBusy) return
        clearBusy = true
        clearResult = null
        scope.launch {
            try {
                val fetched = a.getMessages(channelId, 100).asReversed()
                val picked = fetched
                    .filter { userId == null || it.author.id == userId }
                    .take(count)
                val limit = System.currentTimeMillis() - 13L * 24 * 3600 * 1000
                val recent = picked.filter { it.epochMs > limit }.map { it.id }
                val old = picked.filter { it.epochMs <= limit }.map { it.id }
                var deleted = 0
                if (recent.size >= 2) {
                    a.bulkDelete(channelId, recent)
                    deleted += recent.size
                } else {
                    for (id in recent) {
                        a.deleteMessage(channelId, id)
                        deleted++
                    }
                }
                for (id in old) {
                    a.deleteMessage(channelId, id)
                    deleted++
                }
                record(guildId, "Limpieza en #$channelName", userId ?: "-", userId ?: "todos", "$deleted mensajes")
                clearResult = if (deleted == 0) "No había mensajes para borrar" else "✓ Se borraron $deleted mensajes"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clearResult = "✗ " + describe(e)
            } finally {
                clearBusy = false
            }
        }
    }

    // ---------- Registro de auditoría ----------
    var audit by mutableStateOf<List<AuditRow>?>(null)
    var auditLoading by mutableStateOf(false)

    fun loadAudit(guildId: String) {
        val a = api() ?: return
        if (auditLoading) return
        auditLoading = true
        scope.launch {
            try {
                val data = a.getAuditLog(guildId)
                audit = data.entries.map {
                    AuditRow(
                        time = snowflakeMs(it.id),
                        actor = data.users[it.actorId]?.name ?: it.actorId,
                        action = auditActionName(it.action),
                        target = it.targetId?.let { t -> data.users[t]?.name ?: t } ?: "",
                        reason = it.reason
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                audit = emptyList()
                notify(describe(e))
            } finally {
                auditLoading = false
            }
        }
    }

    fun resetAll() {
        log = emptyList()
        bulkIds = ""
        bulkReason = ""
        bulkBusy = false
        bulkReport = emptyList()
        clearBusy = false
        clearResult = null
        audit = null
        auditLoading = false
    }

    private fun auditActionName(type: Int): String = when (type) {
        1 -> "Cambió el servidor"
        10 -> "Creó un canal"
        11 -> "Editó un canal"
        12 -> "Borró un canal"
        13, 14, 15 -> "Cambió permisos de un canal"
        20 -> "Expulsó"
        21 -> "Limpió miembros inactivos"
        22 -> "Baneó"
        23 -> "Desbaneó"
        24 -> "Editó un miembro (mute/apodo)"
        25 -> "Cambió roles de un miembro"
        26 -> "Movió un miembro de voz"
        27 -> "Desconectó un miembro de voz"
        28 -> "Agregó un bot"
        30 -> "Creó un rol"
        31 -> "Editó un rol"
        32 -> "Borró un rol"
        40 -> "Creó una invitación"
        41 -> "Actualizó una invitación"
        42 -> "Borró una invitación"
        50 -> "Creó un webhook"
        51 -> "Editó un webhook"
        52 -> "Borró un webhook"
        60 -> "Creó un emoji"
        61 -> "Editó un emoji"
        62 -> "Borró un emoji"
        72 -> "Borró mensajes"
        73 -> "Borró mensajes en masa"
        74 -> "Fijó un mensaje"
        75 -> "Desfijó un mensaje"
        80 -> "Creó una integración"
        90 -> "Creó un sticker"
        91 -> "Editó un sticker"
        92 -> "Borró un sticker"
        110 -> "Creó un hilo"
        111 -> "Editó un hilo"
        112 -> "Borró un hilo"
        else -> "Acción $type"
    }
}
