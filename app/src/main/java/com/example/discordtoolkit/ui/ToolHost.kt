package com.example.discordtoolkit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.discord.timeLabel
import com.example.discordtoolkit.ui.theme.DcColors

/** Qué herramienta extra está abierta y en qué server. */
data class ToolRoute(val id: String, val guildId: String)

/** Decide qué pantalla mostrar para una herramienta abierta con [ToolRoute]. */
@Composable
fun ToolHost(state: UiState, vm: AppViewModel) {
    val route = state.tool ?: return
    BackHandler { vm.closeTool() }
    when (route.id) {
        "bulk" -> BulkScreen(state, vm, route.guildId)
        "clear" -> ClearScreen(state, vm, route.guildId)
        "audit" -> AuditScreen(state, vm, route.guildId)
        "modlog" -> ModLogScreen(state, vm, route.guildId)
        else -> ToolScaffold("Herramienta", null, vm::closeTool) {
            Text("Esta herramienta todavía no está disponible.", color = DcColors.TextMuted)
        }
    }
}

/** Marco común de las herramientas: cabecera con botón de volver y cuerpo con scroll. */
@Composable
internal fun ToolScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DcColors.Panel)
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = DcColors.TextPrimary, fontSize = 30.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = DcColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, color = DcColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DcColors.Border)
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun guildName(state: UiState, guildId: String): String? = state.guilds.firstOrNull { it.id == guildId }?.name

/** Fila con radio para elegir una opción dentro de una sección. */
@Composable
internal fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DcColors.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DcColors.TextMuted, fontSize = 13.sp, modifier = Modifier.width(90.dp))
        Text(value, color = DcColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("▾", color = DcColors.TextMuted)
    }
}

// ---------------------------------------------------------------- Acciones masivas

private enum class BulkDialog { ACTION, DURATION, ROLE, CONFIRM }

@Composable
fun BulkScreen(state: UiState, vm: AppViewModel, guildId: String) {
    val ad = vm.admin
    var action by remember { mutableStateOf(BulkAction.BAN) }
    var durationIdx by remember { mutableStateOf(2) }
    var roleId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<BulkDialog?>(null) }

    val roles = remember(state.guildRoles, guildId) {
        (state.guildRoles[guildId] ?: emptyMap()).values
            .filter { it.id != guildId && !it.managed }
            .sortedByDescending { it.position }
    }
    val roleName = roles.firstOrNull { it.id == roleId }?.name
    val count = ad.parseIds(ad.bulkIds).size
    val needsRole = action == BulkAction.ADD_ROLE || action == BulkAction.REMOVE_ROLE
    val canRun = !ad.bulkBusy && count > 0 && (!needsRole || roleId != null)

    ToolScaffold("Acciones masivas", guildName(state, guildId), vm::closeTool) {
        Section("IDs de usuario") {
            FormField("Pegá los IDs (separados por espacio, coma o renglón)", ad.bulkIds, { ad.bulkIds = it }, 4000, singleLine = false, minLines = 4)
            Text(
                if (count == 0) "Todavía no hay IDs válidos" else "$count usuarios detectados",
                color = DcColors.TextMuted,
                fontSize = 12.sp
            )
        }
        Section("Acción") {
            PickRow("Acción", action.label) { dialog = BulkDialog.ACTION }
            if (action == BulkAction.MUTE) {
                PickRow("Duración", timeoutOptions[durationIdx].first) { dialog = BulkDialog.DURATION }
            }
            if (needsRole) {
                PickRow("Rol", roleName ?: "Elegir…") { dialog = BulkDialog.ROLE }
            }
            FormField("Motivo (opcional)", ad.bulkReason, { ad.bulkReason = it }, 512)
        }
        Button(
            onClick = { dialog = BulkDialog.CONFIRM },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ad.bulkBusy) "Procesando ${ad.bulkDone} de ${ad.bulkTotal}…" else "Ejecutar")
        }
        if (ad.bulkReport.isNotEmpty()) {
            Section("Resultado") {
                ad.bulkReport.forEach {
                    Text(
                        it,
                        color = if (it.startsWith("✗")) DcColors.Danger else DcColors.TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    when (dialog) {
        BulkDialog.ACTION -> ChoiceDialog(
            title = "Acción",
            description = "Se aplica a todos los IDs de la lista.",
            options = BulkAction.values().mapIndexed { i, a -> a.label to i.toLong() },
            defaultIndex = action.ordinal,
            confirmLabel = "Elegir",
            onConfirm = { action = BulkAction.values()[it.toInt()]; dialog = null },
            onDismiss = { dialog = null }
        )
        BulkDialog.DURATION -> ChoiceDialog(
            title = "Duración del mute",
            description = "Discord permite hasta 28 días.",
            options = timeoutOptions.mapIndexed { i, o -> o.first to i.toLong() },
            defaultIndex = durationIdx,
            confirmLabel = "Elegir",
            onConfirm = { durationIdx = it.toInt(); dialog = null },
            onDismiss = { dialog = null }
        )
        BulkDialog.ROLE -> ChoiceDialog(
            title = "Rol",
            description = if (roles.isEmpty()) "No hay roles disponibles." else "El bot solo puede dar roles por debajo del suyo.",
            options = roles.mapIndexed { i, r -> r.name to i.toLong() },
            defaultIndex = roles.indexOfFirst { it.id == roleId }.coerceAtLeast(0),
            confirmLabel = "Elegir",
            onConfirm = { roleId = roles.getOrNull(it.toInt())?.id; dialog = null },
            onDismiss = { dialog = null }
        )
        BulkDialog.CONFIRM -> ConfirmDialog(
            title = "${action.label} a $count usuarios",
            text = "Esto se aplica uno por uno y queda en el registro de moderación.",
            confirmLabel = "Ejecutar",
            onConfirm = {
                dialog = null
                ad.runBulk(guildId, action, timeoutOptions[durationIdx].second, roleId, roleName)
            },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------- Limpiar mensajes

@Composable
fun ClearScreen(state: UiState, vm: AppViewModel, guildId: String) {
    val ad = vm.admin
    val channels = remember(state.channels, guildId) {
        (state.channels[guildId] ?: emptyList()).filter { it.isText }
    }
    var channelId by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("50") }
    val count = countText.toIntOrNull()?.coerceIn(1, 100) ?: 0
    val channel = channels.firstOrNull { it.id == channelId }

    ToolScaffold("Limpiar mensajes", guildName(state, guildId), vm::closeTool) {
        Text(
            "Borra mensajes recientes de un canal. Mira los últimos 100; los de menos de 14 días se borran en bloque y los más viejos de a uno. El bot necesita “Gestionar mensajes”.",
            color = DcColors.TextMuted,
            fontSize = 13.sp
        )
        Section("Qué borrar") {
            PickRow("Canal", channel?.let { "# ${it.name}" } ?: "Elegir…") { picking = true }
            FormField("Cantidad (1 a 100)", countText, { countText = it.filter { c -> c.isDigit() } }, 3)
            FormField("Solo de este usuario (ID, opcional)", userId, { userId = it.filter { c -> c.isDigit() } }, 20)
        }
        Button(
            onClick = { confirm = true },
            enabled = channel != null && count > 0 && !ad.clearBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (ad.clearBusy) "Borrando…" else "Borrar") }
        val result = ad.clearResult
        if (result != null) {
            Text(
                result,
                color = if (result.startsWith("✗")) DcColors.Danger else DcColors.TextPrimary,
                fontSize = 14.sp
            )
        }
    }

    if (picking) {
        ChoiceDialog(
            title = "Canal",
            description = "Elegí el canal que querés limpiar.",
            options = channels.mapIndexed { i, c -> "# ${c.name}" to i.toLong() },
            defaultIndex = channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0),
            confirmLabel = "Elegir",
            onConfirm = { channelId = channels.getOrNull(it.toInt())?.id; picking = false },
            onDismiss = { picking = false }
        )
    }
    if (confirm && channel != null) {
        ConfirmDialog(
            title = "Borrar $count mensajes",
            text = "En # ${channel.name}" + if (userId.isNotBlank()) ", solo del usuario $userId." else ". Esto no se puede deshacer.",
            confirmLabel = "Borrar",
            onConfirm = {
                confirm = false
                ad.clearMessages(guildId, channel.id, channel.name, userId.ifBlank { null }, count)
            },
            onDismiss = { confirm = false }
        )
    }
}

// ---------------------------------------------------------------- Auditoría

@Composable
fun AuditScreen(state: UiState, vm: AppViewModel, guildId: String) {
    val ad = vm.admin
    LaunchedEffect(guildId) { ad.loadAudit(guildId) }
    val rows = ad.audit

    ToolScaffold("Registro de auditoría", guildName(state, guildId), vm::closeTool) {
        Text(
            "Las últimas 100 acciones del servidor. El bot necesita “Ver registro de auditoría”.",
            color = DcColors.TextMuted,
            fontSize = 13.sp
        )
        TextButton(onClick = { ad.loadAudit(guildId) }, enabled = !ad.auditLoading) {
            Text(if (ad.auditLoading) "Cargando…" else "Actualizar")
        }
        when {
            rows == null -> Unit
            rows.isEmpty() && !ad.auditLoading -> Text("No hay registros (o falta el permiso).", color = DcColors.TextMuted)
            else -> rows.forEach { row ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DcColors.Surface)
                        .padding(12.dp)
                ) {
                    Text(
                        row.actor + " · " + row.action,
                        color = DcColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (row.target.isNotEmpty()) {
                        Text("Sobre: ${row.target}", color = DcColors.TextMuted, fontSize = 12.sp)
                    }
                    if (!row.reason.isNullOrBlank()) {
                        Text("Motivo: ${row.reason}", color = DcColors.TextMuted, fontSize = 12.sp)
                    }
                    Text(timeLabel(row.time), color = DcColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Registro de moderación

@Composable
fun ModLogScreen(state: UiState, vm: AppViewModel, guildId: String) {
    val ad = vm.admin
    LaunchedEffect(Unit) { ad.loadLog() }
    val rows = ad.log.filter { it.guildId == guildId }
    var confirm by remember { mutableStateOf(false) }

    ToolScaffold("Registro de moderación", guildName(state, guildId), vm::closeTool) {
        Text(
            "Lo que hiciste con esta app (ban, mute, limpiezas, etc.). Se guarda cifrado en tu teléfono.",
            color = DcColors.TextMuted,
            fontSize = 13.sp
        )
        if (rows.isEmpty()) {
            Text("Todavía no hay acciones registradas.", color = DcColors.TextMuted)
        } else {
            TextButton(onClick = { confirm = true }) { Text("Vaciar registro de este servidor") }
            rows.forEach { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DcColors.Surface)
                        .padding(12.dp)
                ) {
                    Text(
                        e.action + " · " + e.targetName,
                        color = DcColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (e.targetName != e.targetId && e.targetId != "-") {
                        Text(e.targetId, color = DcColors.TextMuted, fontSize = 11.sp)
                    }
                    if (!e.reason.isNullOrBlank()) {
                        Text("Motivo: ${e.reason}", color = DcColors.TextMuted, fontSize = 12.sp)
                    }
                    Text(timeLabel(e.time), color = DcColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Vaciar registro",
            text = "Se borra el historial guardado de este servidor.",
            confirmLabel = "Vaciar",
            onConfirm = { confirm = false; ad.clearLog(guildId) },
            onDismiss = { confirm = false }
        )
    }
}
