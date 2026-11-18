package com.example.discordtoolkit.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.discordtoolkit.data.Storage
import com.example.discordtoolkit.discord.DChannel
import com.example.discordtoolkit.discord.DGuild
import com.example.discordtoolkit.discord.DUser
import com.example.discordtoolkit.discord.DiscordApi
import com.example.discordtoolkit.discord.DmEntry
import com.example.discordtoolkit.discord.Gateway
import com.example.discordtoolkit.discord.Message
import com.example.discordtoolkit.discord.Parse
import com.example.discordtoolkit.discord.Reaction
import com.example.discordtoolkit.discord.ReplySnippet
import com.example.discordtoolkit.discord.mapObjects
import com.example.discordtoolkit.discord.str
import com.example.discordtoolkit.discord.timeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

enum class Phase { INIT, LOGIN, READY }

data class ChannelRef(val id: String, val title: String, val isDm: Boolean)

data class UiState(
    val phase: Phase = Phase.INIT,
    val connecting: Boolean = false,
    val loginError: String? = null,
    val me: DUser? = null,
    val gatewayStatus: String = "Desconectado",
    val guilds: List<DGuild> = emptyList(),
    val selectedGuildId: String? = null,
    val channels: Map<String, List<DChannel>> = emptyMap(),
    val dms: List<DmEntry> = emptyList(),
    val openChannel: ChannelRef? = null,
    val messages: List<Message> = emptyList(),
    val loadingMessages: Boolean = false,
    val replyingTo: Message? = null,
    val editing: Message? = null,
    val sending: Boolean = false,
    val notice: String? = null,
    /** Pantalla principal: mostrar los DMs en vez de los canales del server. */
    val showDms: Boolean = false,
    /** Pantalla principal: mostrar la lista de herramientas del bot. */
    val showTools: Boolean = false,
    /** Si no es null, está abierta la herramienta de roles de ese server. */
    val rolesGuildId: String? = null,
    val creatingRoles: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: DiscordApi? = null
    private var gateway: Gateway? = null

    /** Roles que se están armando para crear. Es estado de Compose directo (ver RoleDraft). */
    val roleDrafts = mutableStateListOf<RoleDraft>()
    private var draftsGuildId: String? = null
    private var draftCounter = 0L

    private class Picked(val name: String, val mime: String?, val bytes: ByteArray)

    private val gatewayListener = object : Gateway.Listener {
        override fun onStatus(text: String) {
            _state.update { it.copy(gatewayStatus = text) }
        }

        override fun onDispatch(type: String, data: JSONObject) {
            try {
                handleDispatch(type, data)
            } catch (e: Exception) {
                // un evento que no se pudo leer no debe tirar abajo la conexión
            }
        }

        override fun onFatal(text: String) {
            _state.update { it.copy(gatewayStatus = "Desconectado", notice = text) }
        }
    }

    init {
        val saved = storage.token()
        if (saved == null) {
            _state.update { it.copy(phase = Phase.LOGIN) }
        } else {
            connect(saved, manual = false)
        }
    }

    // ---------- Sesión ----------

    fun login(raw: String) {
        val token = raw.trim().removePrefix("Bot ").trim()
        if (token.isEmpty()) {
            _state.update { it.copy(loginError = "Pegá el token del bot") }
            return
        }
        connect(token, manual = true)
    }

    private fun connect(token: String, manual: Boolean) {
        _state.update { it.copy(connecting = true, loginError = null) }
        viewModelScope.launch {
            try {
                val newApi = DiscordApi(token)
                val me = newApi.getMe()
                if (manual) storage.saveToken(token)
                api = newApi
                gateway?.stop()
                gateway = Gateway(token, gatewayListener).also { it.start() }
                _state.update {
                    it.copy(
                        phase = Phase.READY,
                        connecting = false,
                        me = me,
                        dms = storage.dms(me.id),
                        guilds = emptyList(),
                        channels = emptyMap(),
                        selectedGuildId = null
                    )
                }
                launchSafe { loadGuilds(newApi) }
                launchSafe { refreshDmProfiles(newApi) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is DiscordApi.ApiException && e.code == 401) storage.clearToken()
                _state.update {
                    it.copy(phase = Phase.LOGIN, connecting = false, loginError = friendly(e))
                }
            }
        }
    }

    fun logout() {
        gateway?.stop()
        gateway = null
        api = null
        roleDrafts.clear()
        draftsGuildId = null
        storage.clearToken()
        _state.value = UiState(phase = Phase.LOGIN)
    }

    private fun friendly(e: Exception): String = when {
        e is DiscordApi.ApiException && e.code == 401 -> "Token inválido"
        e is IOException -> "No hay conexión con Discord"
        else -> e.message ?: "Error desconocido"
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(friendly(e))
            }
        }
    }

    private fun notify(text: String) {
        _state.update { it.copy(notice = text) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ---------- Servers y canales ----------

    private suspend fun loadGuilds(api: DiscordApi) {
        val guilds = api.getGuilds()
        _state.update { s ->
            s.copy(guilds = guilds, selectedGuildId = s.selectedGuildId ?: guilds.firstOrNull()?.id)
        }
        val selected = _state.value.selectedGuildId
        if (selected != null) ensureChannels(api, selected)
    }

    private suspend fun ensureChannels(api: DiscordApi, guildId: String) {
        if (_state.value.channels.containsKey(guildId)) return
        val list = api.getChannels(guildId)
        _state.update { it.copy(channels = it.channels + (guildId to list)) }
    }

    fun showDms(show: Boolean) {
        _state.update { it.copy(showDms = show, showTools = false) }
    }

    fun showTools(show: Boolean) {
        _state.update { it.copy(showTools = show) }
    }

    fun selectGuild(id: String) {
        _state.update { it.copy(selectedGuildId = id, showDms = false, showTools = false) }
        launchSafe {
            val a = api ?: return@launchSafe
            ensureChannels(a, id)
        }
    }

    // ---------- Roles masivos ----------

    fun openRoles(guildId: String) {
        // Los borradores son de un server: si se abre otro, se empieza de cero.
        if (draftsGuildId != guildId) {
            roleDrafts.clear()
            draftsGuildId = guildId
        }
        _state.update { it.copy(rolesGuildId = guildId) }
    }

    fun closeRoles() {
        // Si se está creando, el proceso sigue y los borradores conservan el resultado al volver.
        _state.update { it.copy(rolesGuildId = null, showTools = true) }
    }

    fun addRoleDraft() {
        draftCounter++
        roleDrafts.add(RoleDraft(draftCounter))
    }

    fun removeRoleDraft(draft: RoleDraft) {
        roleDrafts.remove(draft)
    }

    /** Aplica el mismo campo de permisos a todos los roles que todavía no se crearon. */
    fun applyPermissionsToAll(permissions: Long) {
        roleDrafts.filter { it.isEditable }.forEach { it.permissions = permissions }
    }

    /** Crea de a uno, en orden, todos los roles pendientes (o que fallaron antes). */
    fun createRoles() {
        val guildId = draftsGuildId ?: return
        val a = api ?: return
        if (_state.value.creatingRoles) return
        val pending = roleDrafts.filter { it.status is RoleStatus.Draft || it.status is RoleStatus.Failed }
        if (pending.isEmpty()) return
        _state.update { it.copy(creatingRoles = true) }
        viewModelScope.launch {
            try {
                for (draft in pending) {
                    val name = draft.name.trim()
                    if (name.isEmpty()) {
                        draft.status = RoleStatus.Failed("Falta el nombre")
                        continue
                    }
                    draft.status = RoleStatus.Creating
                    try {
                        val role = a.createRole(guildId, name, draft.color, draft.permissions)
                        draft.status = RoleStatus.Created(role.id)
                    } catch (e: CancellationException) {
                        draft.status = RoleStatus.Draft
                        throw e
                    } catch (e: Exception) {
                        draft.status = RoleStatus.Failed(friendly(e))
                    }
                }
            } finally {
                _state.update { it.copy(creatingRoles = false) }
            }
        }
    }

    // ---------- Chat ----------

    fun openChannel(ref: ChannelRef) {
        _state.update {
            it.copy(
                openChannel = ref,
                messages = emptyList(),
                loadingMessages = true,
                replyingTo = null,
                editing = null
            )
        }
        launchSafe {
            val a = api ?: return@launchSafe
            try {
                val list = a.getMessages(ref.id)
                _state.update { s ->
                    if (s.openChannel?.id == ref.id) s.copy(messages = list, loadingMessages = false) else s
                }
            } catch (e: Exception) {
                _state.update { s ->
                    if (s.openChannel?.id == ref.id) s.copy(loadingMessages = false) else s
                }
                throw e
            }
        }
    }

    fun closeChannel() {
        _state.update {
            it.copy(openChannel = null, messages = emptyList(), replyingTo = null, editing = null)
        }
    }

    fun startReply(m: Message) {
        _state.update { it.copy(replyingTo = m, editing = null) }
    }

    fun startEdit(m: Message) {
        _state.update { it.copy(editing = m, replyingTo = null) }
    }

    fun cancelCompose() {
        _state.update { it.copy(replyingTo = null, editing = null) }
    }

    fun send(text: String) {
        val s = _state.value
        val channel = s.openChannel ?: return
        val content = text.trim()
        if (content.isEmpty()) return
        val editing = s.editing
        val reply = s.replyingTo
        _state.update { it.copy(replyingTo = null, editing = null) }

        if (editing != null) {
            launchSafe {
                val a = api ?: return@launchSafe
                upsert(a.editMessage(channel.id, editing.id, content))
            }
            return
        }

        // Se muestra el mensaje al instante (pendiente) y se reemplaza cuando Discord confirma.
        val nonce = newNonce()
        val me = s.me
        if (me != null) upsert(pendingMessage(nonce, channel.id, me, content, reply))
        launchSafe {
            val a = api ?: return@launchSafe
            try {
                val result = a.sendMessage(channel.id, content, reply?.id, nonce)
                upsert(result, replacesId = nonce)
            } catch (e: Exception) {
                removeMessages(channel.id, listOf(nonce))
                throw e
            }
        }
    }

    private var nonceCounter = 0

    private fun newNonce(): String {
        nonceCounter++
        return "d${System.currentTimeMillis()}$nonceCounter"
    }

    private fun pendingMessage(
        nonce: String,
        channelId: String,
        me: DUser,
        content: String,
        reply: Message?
    ): Message {
        val now = System.currentTimeMillis()
        return Message(
            id = nonce,
            channelId = channelId,
            guildId = null,
            author = me,
            rawContent = content,
            content = content,
            epochMs = now,
            timeLabel = timeLabel(now),
            edited = false,
            attachments = emptyList(),
            reactions = emptyList(),
            embeds = emptyList(),
            reply = if (reply != null) ReplySnippet(reply.author.name, reply.content.take(80)) else null,
            nonce = nonce,
            pending = true
        )
    }

    fun sendImage(uri: Uri, caption: String) {
        val channel = _state.value.openChannel ?: return
        val replyId = _state.value.replyingTo?.id
        _state.update { it.copy(replyingTo = null, editing = null, sending = true) }
        launchSafe {
            try {
                val a = api ?: return@launchSafe
                val picked = withContext(Dispatchers.IO) { readPicked(uri) }
                if (picked.bytes.size > 25 * 1024 * 1024) {
                    throw IllegalStateException("El archivo supera los 25 MB")
                }
                val result = a.sendWithFile(
                    channel.id, caption.trim(), replyId, picked.bytes, picked.name, picked.mime
                )
                upsert(result)
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    private fun readPicked(uri: Uri): Picked {
        val resolver = getApplication<Application>().contentResolver
        var name = "archivo"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                val value = cursor.getString(index)
                if (value != null) name = value
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No pude leer el archivo")
        return Picked(name, resolver.getType(uri), bytes)
    }

    fun toggleReaction(m: Message, emoji: String) {
        val mine = m.reactions.any { it.apiEmoji == emoji && it.me }
        // Se refleja al instante y después se confirma con Discord.
        applyReaction(m.channelId, m.id, emoji, byMe = true, add = !mine)
        launchSafe {
            val a = api ?: return@launchSafe
            try {
                if (mine) {
                    a.removeReaction(m.channelId, m.id, emoji)
                } else {
                    a.addReaction(m.channelId, m.id, emoji)
                }
            } catch (e: Exception) {
                try {
                    refresh(m.channelId, m.id)
                } catch (ignored: Exception) {
                    // si tampoco se puede refrescar, queda el error original
                }
                throw e
            }
        }
    }

    private fun splitEmoji(key: String): Pair<String, String?> {
        val index = key.lastIndexOf(':')
        return if (index > 0) Pair(key.substring(0, index), key.substring(index + 1)) else Pair(key, null)
    }

    /**
     * Cambia el contador de una reacción en el mensaje abierto. Para las reacciones del propio bot
     * es idempotente: aplicar dos veces el mismo cambio (local + evento del gateway) no suma doble.
     */
    private fun applyReaction(channelId: String, messageId: String, key: String, byMe: Boolean, add: Boolean) {
        _state.update { s ->
            if (s.openChannel?.id != channelId) return@update s
            val index = s.messages.indexOfFirst { it.id == messageId }
            if (index < 0) return@update s
            val msg = s.messages[index]
            val reactions = msg.reactions.toMutableList()
            val at = reactions.indexOfFirst { it.apiEmoji == key }
            if (add) {
                if (at >= 0) {
                    val r = reactions[at]
                    if (!byMe) {
                        reactions[at] = r.copy(count = r.count + 1)
                    } else if (!r.me) {
                        reactions[at] = r.copy(count = r.count + 1, me = true)
                    }
                } else {
                    val (name, id) = splitEmoji(key)
                    reactions.add(Reaction(name, id, 1, byMe))
                }
            } else if (at >= 0) {
                val r = reactions[at]
                val applies = !byMe || r.me
                if (applies) {
                    if (r.count <= 1) {
                        reactions.removeAt(at)
                    } else {
                        reactions[at] = r.copy(count = r.count - 1, me = if (byMe) false else r.me)
                    }
                }
            }
            val list = s.messages.toMutableList()
            list[index] = msg.copy(reactions = reactions)
            s.copy(messages = list)
        }
    }

    fun deleteMessage(m: Message) {
        launchSafe {
            val a = api ?: return@launchSafe
            a.deleteMessage(m.channelId, m.id)
            removeMessages(m.channelId, listOf(m.id))
        }
    }

    fun startDm(userId: String) {
        val id = userId.trim()
        if (id.isEmpty() || !id.all { it.isDigit() }) {
            notify("El ID de usuario son solo números")
            return
        }
        launchSafe {
            val a = api ?: return@launchSafe
            val entry = a.openDm(id)
            addDm(entry)
            openChannel(ChannelRef(entry.channelId, entry.name, true))
        }
    }

    private suspend fun refresh(channelId: String, messageId: String) {
        val a = api ?: return
        if (_state.value.openChannel?.id != channelId) return
        upsert(a.getMessage(channelId, messageId))
    }

    /** Agrega o reemplaza un mensaje. Si trae nonce, también saca el mensaje pendiente que lo originó. */
    private fun upsert(m: Message, replacesId: String? = null) {
        _state.update { s ->
            if (s.openChannel?.id != m.channelId) return@update s
            val pendingId = replacesId ?: m.nonce
            val base = if (pendingId != null && !m.pending) {
                s.messages.filter { it.id != pendingId }
            } else {
                s.messages
            }
            val index = base.indexOfFirst { it.id == m.id }
            val list = if (index >= 0) {
                base.toMutableList().also { it[index] = m }
            } else {
                (base + m).takeLast(300)
            }
            s.copy(messages = list)
        }
    }

    private suspend fun refreshDmProfiles(api: DiscordApi) {
        val me = _state.value.me ?: return
        for (dm in _state.value.dms) {
            val user = try {
                api.getUser(dm.userId)
            } catch (e: Exception) {
                continue
            }
            _state.update { s ->
                s.copy(
                    dms = s.dms.map {
                        if (it.channelId == dm.channelId) it.copy(name = user.name, avatar = user.avatar) else it
                    }
                )
            }
        }
        storage.saveDms(me.id, _state.value.dms)
    }

    private fun removeMessages(channelId: String, ids: List<String>) {
        _state.update { s ->
            if (s.openChannel?.id != channelId) s else s.copy(messages = s.messages.filter { it.id !in ids })
        }
    }

    private fun addDm(entry: DmEntry) {
        val me = _state.value.me ?: return
        if (_state.value.dms.any { it.channelId == entry.channelId }) return
        val list = _state.value.dms + entry
        _state.update { it.copy(dms = list) }
        storage.saveDms(me.id, list)
    }

    // ---------- Eventos del gateway ----------

    private fun handleDispatch(type: String, d: JSONObject) {
        when (type) {
            "MESSAGE_CREATE" -> {
                val m = Parse.message(d)
                val me = _state.value.me
                val isDm = d.str("guild_id") == null
                if (isDm && me != null && m.author.id != me.id) {
                    addDm(DmEntry(m.channelId, m.author.id, m.author.name, m.author.avatar))
                }
                upsert(m)
            }
            "MESSAGE_REACTION_ADD", "MESSAGE_REACTION_REMOVE" -> {
                val channelId = d.str("channel_id") ?: return
                val messageId = d.str("message_id") ?: return
                val emoji = d.optJSONObject("emoji") ?: return
                val name = emoji.str("name") ?: return
                val emojiId = emoji.str("id")
                val key = if (emojiId != null) "$name:$emojiId" else name
                val byMe = d.str("user_id") == _state.value.me?.id
                applyReaction(channelId, messageId, key, byMe, add = type == "MESSAGE_REACTION_ADD")
            }
            "MESSAGE_UPDATE", "MESSAGE_REACTION_REMOVE_ALL", "MESSAGE_REACTION_REMOVE_EMOJI" -> {
                val channelId = d.str("channel_id") ?: return
                val messageId = d.str("id") ?: d.str("message_id") ?: return
                if (_state.value.openChannel?.id == channelId) {
                    launchSafe { refresh(channelId, messageId) }
                }
            }
            "MESSAGE_DELETE" -> {
                val channelId = d.str("channel_id") ?: return
                val id = d.str("id") ?: return
                removeMessages(channelId, listOf(id))
            }
            "MESSAGE_DELETE_BULK" -> {
                val channelId = d.str("channel_id") ?: return
                val arr = d.optJSONArray("ids") ?: return
                val ids = (0 until arr.length()).map { arr.optString(it) }
                removeMessages(channelId, ids)
            }
            "GUILD_CREATE" -> {
                val g = Parse.guild(d)
                val channels = d.optJSONArray("channels").mapObjects { Parse.channel(it, g.id) }
                _state.update { s ->
                    s.copy(
                        guilds = if (s.guilds.any { it.id == g.id }) s.guilds else s.guilds + g,
                        channels = s.channels + (g.id to channels),
                        selectedGuildId = s.selectedGuildId ?: g.id
                    )
                }
            }
            "GUILD_DELETE" -> {
                if (d.optBoolean("unavailable", false)) return
                val id = d.str("id") ?: return
                _state.update { s ->
                    s.copy(
                        guilds = s.guilds.filter { it.id != id },
                        channels = s.channels - id,
                        selectedGuildId = if (s.selectedGuildId == id) null else s.selectedGuildId
                    )
                }
            }
            "CHANNEL_CREATE", "CHANNEL_UPDATE" -> {
                val ch = Parse.channel(d, null)
                val guildId = ch.guildId ?: return
                _state.update { s ->
                    val list = s.channels[guildId] ?: return@update s
                    val updated = if (list.any { it.id == ch.id }) {
                        list.map { if (it.id == ch.id) ch else it }
                    } else {
                        list + ch
                    }
                    s.copy(channels = s.channels + (guildId to updated))
                }
            }
            "CHANNEL_DELETE" -> {
                val ch = Parse.channel(d, null)
                val guildId = ch.guildId ?: return
                _state.update { s ->
                    val list = s.channels[guildId] ?: return@update s
                    s.copy(channels = s.channels + (guildId to list.filter { it.id != ch.id }))
                }
            }
        }
    }

    override fun onCleared() {
        gateway?.stop()
        super.onCleared()
    }
}
