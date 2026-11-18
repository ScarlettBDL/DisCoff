package com.example.discordtoolkit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.discordtoolkit.discord.DChannel
import com.example.discordtoolkit.discord.DiscordApi
import com.example.discordtoolkit.discord.RoleInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Editar y borrar roles y canales de un server, y crear canales. */
class Manager(
    private val scope: CoroutineScope,
    private val api: () -> DiscordApi?,
    private val describe: (Exception) -> String,
    private val onRoles: (String, List<RoleInfo>) -> Unit,
    private val onChannels: (String, List<DChannel>) -> Unit
) {
    var busy by mutableStateOf(false)

    /** Último resultado ("✓ …" o "✗ …") para mostrarlo en pantalla. */
    var result by mutableStateOf<String?>(null)

    private fun exec(guildId: String, refreshRoles: Boolean, refreshChannels: Boolean, okText: String, block: suspend (DiscordApi) -> Unit) {
        val a = api() ?: return
        if (busy) return
        busy = true
        result = null
        scope.launch {
            try {
                block(a)
                if (refreshRoles) onRoles(guildId, a.getRoles(guildId))
                if (refreshChannels) onChannels(guildId, a.getChannels(guildId))
                result = "✓ $okText"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = "✗ " + describe(e)
            } finally {
                busy = false
            }
        }
    }

    fun editRole(guildId: String, role: RoleInfo, name: String, color: Int) =
        exec(guildId, true, false, "Rol actualizado") { it.editRole(guildId, role.id, name.trim(), color) }

    fun deleteRole(guildId: String, role: RoleInfo) =
        exec(guildId, true, false, "Rol “${role.name}” borrado") { it.deleteRole(guildId, role.id) }

    /** Sube o baja un rol una posición, intercambiándolo con el vecino. [sorted] va de arriba hacia abajo. */
    fun moveRole(guildId: String, sorted: List<RoleInfo>, index: Int, up: Boolean) {
        val other = if (up) index - 1 else index + 1
        if (index !in sorted.indices || other !in sorted.indices) return
        val a = sorted[index]
        val b = sorted[other]
        exec(guildId, true, false, "Rol movido") {
            it.moveRoles(guildId, listOf(a.id to b.position, b.id to a.position))
        }
    }

    fun createChannel(guildId: String, name: String, type: Int, parentId: String?) =
        exec(guildId, false, true, "Canal creado") { it.createChannel(guildId, name.trim(), type, parentId) }

    fun renameChannel(guildId: String, channel: DChannel, name: String) =
        exec(guildId, false, true, "Canal renombrado") { it.renameChannel(channel.id, name.trim()) }

    fun deleteChannel(guildId: String, channel: DChannel) =
        exec(guildId, false, true, "Canal “${channel.name}” borrado") { it.deleteChannel(channel.id) }
}
