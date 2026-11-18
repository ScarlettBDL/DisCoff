package com.example.discordtoolkit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface RoleStatus {
    /** Todavía no se creó (se puede editar). */
    data object Draft : RoleStatus

    data object Creating : RoleStatus

    data class Created(val id: String) : RoleStatus {
        val mention: String get() = "<@&$id>"
    }

    data class Failed(val message: String) : RoleStatus
}

/**
 * Un rol que se está armando. Los campos son estado de Compose (no pasan por un StateFlow)
 * para que escribir el nombre no tenga retraso ni mueva el cursor.
 */
class RoleDraft(val key: Long) {
    var name by mutableStateOf("")
    var color by mutableIntStateOf(0)

    /** Campo de bits de permisos. 0 = permisos por defecto de @everyone. */
    var permissions by mutableLongStateOf(0L)
    var status by mutableStateOf<RoleStatus>(RoleStatus.Draft)

    /** true solo para el rol recién agregado, para abrirle el teclado una vez. */
    var focusPending by mutableStateOf(true)

    val isEditable: Boolean get() = status !is RoleStatus.Created && status !is RoleStatus.Creating
}
