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
        "manage" -> ManageScreen(state, vm, route.guildId)
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

// ---------------------------------------------------------------- Roles y canales

private sealed interface ManageDialog {
    data class EditRole(val role: com.example.discordtoolkit.discord.RoleInfo) : ManageDialog
    data class DeleteRole(val role: com.example.discordtoolkit.discord.RoleInfo) : ManageDialog
    data class EditChannel(val channel: com.example.discordtoolkit.discord.DChannel) : ManageDialog
    data class DeleteChannel(val channel: com.example.discordtoolkit.discord.DChannel) : ManageDialog
    data object NewChannel : ManageDialog
}

@Composable
fun ManageScreen(state: UiState, vm: AppViewModel, guildId: String) {
    val mg = vm.manager
    var dialog by remember { mutableStateOf<ManageDialog?>(null) }

    val roles = remember(state.guildRoles, guildId) {
        (state.guildRoles[guildId] ?: emptyMap()).values
            .filter { it.id != guildId }
            .sortedByDescending { it.position }
    }
    val channels = remember(state.channels, guildId) {
        (state.channels[guildId] ?: emptyList()).sortedWith(compareBy({ it.type != 4 }, { it.position }))
    }

    ToolScaffold("Roles y canales", guildName(state, guildId), vm::closeTool) {
        Text(
            "Renombrá, recoloreá, ordená y borrá roles; creá, renombrá y borrá canales. El bot necesita “Gestionar roles” / “Gestionar canales”, y los roles solo se pueden tocar si están por debajo del suyo.",
            color = DcColors.TextMuted,
            fontSize = 13.sp
        )
        val result = mg.result
        if (result != null) {
            Text(
                result,
                color = if (result.startsWith("✗")) DcColors.Danger else DcColors.TextPrimary,
                fontSize = 14.sp
            )
        }
        Section("Roles (${roles.size})") {
            if (roles.isEmpty()) Text("No hay roles cargados.", color = DcColors.TextMuted, fontSize = 13.sp)
            roles.forEachIndexed { index, role ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DcColors.Panel)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (role.color != 0) androidx.compose.ui.graphics.Color(0xFF000000.toInt() or role.color) else DcColors.Offline
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        role.name,
                        color = DcColors.TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!role.managed) {
                        TextButton(onClick = { mg.moveRole(guildId, roles, index, true) }, enabled = !mg.busy && index > 0) { Text("⬆") }
                        TextButton(onClick = { mg.moveRole(guildId, roles, index, false) }, enabled = !mg.busy && index < roles.lastIndex) { Text("⬇") }
                        TextButton(onClick = { dialog = ManageDialog.EditRole(role) }, enabled = !mg.busy) { Text("✎") }
                        TextButton(onClick = { dialog = ManageDialog.DeleteRole(role) }, enabled = !mg.busy) { Text("🗑") }
                    } else {
                        Text("de un bot", color = DcColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
        }
        Section("Canales (${channels.size})") {
            Button(onClick = { dialog = ManageDialog.NewChannel }, enabled = !mg.busy, modifier = Modifier.fillMaxWidth()) {
                Text("+ Crear canal")
            }
            channels.forEach { ch ->
                val icon = when {
                    ch.isCategory -> "📁"
                    ch.isVoice -> "🔊"
                    else -> "#"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DcColors.Panel)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$icon ${ch.name}",
                        color = DcColors.TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { dialog = ManageDialog.EditChannel(ch) }, enabled = !mg.busy) { Text("✎") }
                    TextButton(onClick = { dialog = ManageDialog.DeleteChannel(ch) }, enabled = !mg.busy) { Text("🗑") }
                }
            }
        }
    }

    when (val d = dialog) {
        is ManageDialog.EditRole -> {
            var name by remember(d.role.id) { mutableStateOf(d.role.name) }
            var color by remember(d.role.id) { mutableStateOf(d.role.color) }
            var picking by remember { mutableStateOf(false) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = DcColors.Surface,
                title = { Text("Editar rol") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormField("Nombre", name, { name = it }, 100)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Color", color = DcColors.TextMuted, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (color != 0) androidx.compose.ui.graphics.Color(0xFF000000.toInt() or color) else DcColors.Offline
                                    )
                                    .clickable { picking = true }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialog = null
                            mg.editRole(guildId, d.role, name, color)
                        },
                        enabled = name.isNotBlank()
                    ) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
            )
            if (picking) {
                ColorPickerDialog(initial = color, onDismiss = { picking = false }, onPick = { color = it; picking = false })
            }
        }
        is ManageDialog.DeleteRole -> ConfirmDialog(
            title = "Borrar rol",
            text = "Se borra “${d.role.name}” y se le saca a todos los que lo tengan.",
            confirmLabel = "Borrar",
            onConfirm = { dialog = null; mg.deleteRole(guildId, d.role) },
            onDismiss = { dialog = null }
        )
        is ManageDialog.EditChannel -> {
            var name by remember(d.channel.id) { mutableStateOf(d.channel.name) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = DcColors.Surface,
                title = { Text("Renombrar canal") },
                text = { FormField("Nombre", name, { name = it }, 100) },
                confirmButton = {
                    TextButton(
                        onClick = { dialog = null; mg.renameChannel(guildId, d.channel, name) },
                        enabled = name.isNotBlank()
                    ) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
            )
        }
        is ManageDialog.DeleteChannel -> ConfirmDialog(
            title = "Borrar canal",
            text = "Se borra “${d.channel.name}” con todos sus mensajes. No se puede deshacer.",
            confirmLabel = "Borrar",
            onConfirm = { dialog = null; mg.deleteChannel(guildId, d.channel) },
            onDismiss = { dialog = null }
        )
        ManageDialog.NewChannel -> {
            var name by remember { mutableStateOf("") }
            var typeIdx by remember { mutableStateOf(0) }
            val types = listOf("Texto" to 0, "Voz" to 2, "Categoría" to 4)
            val cats = channels.filter { it.isCategory }
            var catIdx by remember { mutableStateOf(-1) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = DcColors.Surface,
                title = { Text("Crear canal") },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormField("Nombre", name, { name = it }, 100)
                        Text("Tipo", color = DcColors.TextMuted, fontSize = 13.sp)
                        types.forEachIndexed { i, t ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { typeIdx = i },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(selected = typeIdx == i, onClick = { typeIdx = i })
                                Text(t.first, color = DcColors.TextPrimary)
                            }
                        }
                        if (types[typeIdx].second != 4 && cats.isNotEmpty()) {
                            Text("Categoría", color = DcColors.TextMuted, fontSize = 13.sp)
                            (listOf<com.example.discordtoolkit.discord.DChannel?>(null) + cats).forEachIndexed { i, c ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { catIdx = i - 1 },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(selected = catIdx == i - 1, onClick = { catIdx = i - 1 })
                                    Text(c?.name ?: "Ninguna", color = DcColors.TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialog = null
                            mg.createChannel(guildId, name, types[typeIdx].second, cats.getOrNull(catIdx)?.id)
                        },
                        enabled = name.isNotBlank()
                    ) { Text("Crear") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
            )
        }
        null -> Unit
    }
}
