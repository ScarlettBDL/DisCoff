package com.example.discordtoolkit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.discordtoolkit.discord.BanEntry
import com.example.discordtoolkit.discord.DUser
import com.example.discordtoolkit.discord.MemberInfo

/**
 * Estado de la pantalla de moderación. Son campos de Compose directos (no pasan por un StateFlow)
 * para que escribir en los campos de texto no tenga retraso ni mueva el cursor.
 */
class ModState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<DUser>>(emptyList())
    var searching by mutableStateOf(false)

    var target by mutableStateOf<DUser?>(null)

    /** Datos del miembro en el server; null si no está (ver [memberLoaded]). */
    var member by mutableStateOf<MemberInfo?>(null)
    var memberLoaded by mutableStateOf(false)

    /** Motivo opcional que queda en el registro de auditoría de Discord. */
    var reason by mutableStateOf("")
    var busy by mutableStateOf(false)

    /** Lista de baneados del server; null mientras no se cargó. */
    var bans by mutableStateOf<List<BanEntry>?>(null)
    var loadingBans by mutableStateOf(false)

    fun clearTarget() {
        target = null
        member = null
        memberLoaded = false
    }

    fun reset() {
        query = ""
        results = emptyList()
        searching = false
        clearTarget()
        reason = ""
        busy = false
        bans = null
        loadingBans = false
    }
}
