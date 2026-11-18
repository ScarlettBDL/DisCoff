package com.example.discordtoolkit.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.discordtoolkit.discord.DUser
import com.example.discordtoolkit.discord.timeLabel
import com.example.discordtoolkit.ui.theme.DcColors

private enum class ModDialog { BAN, SOFTBAN, KICK, TIMEOUT, UNBAN, NICK, ROLES, BANS }

/** Cuánto historial de mensajes se borra al banear. El valor está en segundos (Discord admite hasta 7 días). */
private val banDeleteOptions = listOf(
    "No borrar mensajes" to 0L,
    "Última hora" to 3_600L,
    "Últimas 6 horas" to 21_600L,
    "Últimas 12 horas" to 43_200L,
    "Últimas 24 horas" to 86_400L,
    "Últimos 3 días" to 259_200L,
    "Últimos 7 días" to 604_800L
)

/** Duración del mute (timeout), en milisegundos. Discord admite hasta 28 días. */
private val timeoutOptions = listOf(
    "60 segundos" to 60_000L,
    "5 minutos" to 300_000L,
    "10 minutos" to 600_000L,
    "1 hora" to 3_600_000L,
    "1 día" to 86_400_000L,
    "1 semana" to 604_800_000L,
    "28 días (máximo)" to 2_419_200_000L
)

@Composable
fun ModerationScreen(state: UiState, vm: AppViewModel) {
    val m = vm.mod
    val guildId = state.modGuildId
    val guild = state.guilds.firstOrNull { it.id == guildId }
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<ModDialog?>(null) }

    BackHandler { vm.closeModeration() }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val target = m.target
    val isSelf = target != null && target.id == state.me?.id
    val notMember = m.memberLoaded && m.member == null
    val timedOut = m.member?.isTimedOut() == true

    Column(
        Modifier
            .fillMaxSize()
            .background(DcColors.Panel)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Header
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
                    .clickable { vm.closeModeration() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = DcColors.TextPrimary, fontSize = 30.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Moderación", color = DcColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (guild != null) {
                    Text(
                        guild.name,
                        color = DcColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "El bot necesita los permisos correspondientes (banear, expulsar, aislar miembros, " +
                    "gestionar apodos o roles) y un rol más alto que el del usuario.",
                color = DcColors.TextMuted,
                fontSize = 13.sp
            )

            Section("Usuario") {
                FormField("ID de usuario o nombre", m.query, { m.query = it }, 100)
                Button(
                    onClick = vm::searchUsers,
                    enabled = !m.searching && m.query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (m.searching) "Buscando…" else "Buscar") }
                m.results.forEach { user ->
                    UserRow(user) { vm.selectTarget(user) }
                }
                if (target != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DcColors.AccentSoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = target.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                target.name,
                                color = DcColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(target.id, color = DcColors.TextMuted, fontSize = 12.sp)
                            val nick = m.member?.nick
                            if (nick != null) {
                                Text("Apodo: $nick", color = DcColors.TextMuted, fontSize = 12.sp)
                            }
                            val until = m.member?.timeoutUntilMs
                            if (timedOut && until != null) {
                                Text(
                                    "Muteado hasta ${timeLabel(until)}",
                                    color = DcColors.Danger,
                                    fontSize = 12.sp
                                )
                            }
                            if (notMember) {
                                Text(
                                    "No está en el servidor (solo se puede banear o desbanear)",
                                    color = DcColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelf) {
                                Text(
                                    "Este es el propio bot: no se puede moderar a sí mismo",
                                    color = DcColors.Danger,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { vm.clearTarget() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = DcColors.TextMuted)
                        }
                    }
                }
            }

            Section("Motivo (opcional, queda en el registro de auditoría)") {
                FormField("Motivo", m.reason, { m.reason = it }, 512)
            }

            if (target != null) {
                Section("Acciones") {
                    ActionItem("🔨", "Banear", "Lo echa y no lo deja volver", !isSelf && !m.busy, danger = true) {
                        dialog = ModDialog.BAN
                    }
                    ActionItem("🧹", "Softban", "Banea y desbanea para borrar sus mensajes", !isSelf && !m.busy) {
                        dialog = ModDialog.SOFTBAN
                    }
                    ActionItem("👢", "Expulsar", "Lo echa, pero puede volver con una invitación", !isSelf && !notMember && !m.busy) {
                        dialog = ModDialog.KICK
                    }
                    ActionItem("🔇", "Mutear (timeout)", "No puede escribir ni hablar por un tiempo", !isSelf && !notMember && !m.busy) {
                        dialog = ModDialog.TIMEOUT
                    }
                    if (timedOut) {
                        ActionItem("🔊", "Quitar mute", "Le saca el timeout ahora", !m.busy) {
                            vm.removeTimeout(toast)
                        }
                    }
                    ActionItem("🏷️", "Cambiar apodo", "En este servidor", !isSelf && !notMember && !m.busy) {
                        dialog = ModDialog.NICK
                    }
                    ActionItem("🎭", "Roles", "Darle o quitarle roles", !isSelf && !notMember && !m.busy) {
                        dialog = ModDialog.ROLES
                    }
                    ActionItem("✅", "Desbanear", "Lo saca de la lista de baneados", !m.busy) {
                        dialog = ModDialog.UNBAN
                    }
                }
            }

            Section("Baneados") {
                ActionItem("📋", "Ver lista de baneados", "Con el motivo y la opción de desbanear", !m.busy) {
                    dialog = ModDialog.BANS
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    // ---------- Diálogos ----------
    val name = target?.name ?: "el usuario"
    when (dialog) {
        ModDialog.BAN -> ChoiceDialog(
            title = "Banear a $name",
            description = "No podrá volver hasta que lo desbanees. Elegí cuánto de su historial se borra.",
            options = banDeleteOptions,
            defaultIndex = 0,
            confirmLabel = "Banear",
            onConfirm = { value ->
                dialog = null
                vm.ban(value.toInt(), toast)
            },
            onDismiss = { dialog = null }
        )
        ModDialog.SOFTBAN -> ChoiceDialog(
            title = "Softban a $name",
            description = "Se banea y se desbanea enseguida: se borran sus mensajes recientes y puede volver.",
            options = banDeleteOptions.drop(1),
            defaultIndex = banDeleteOptions.size - 2,
            confirmLabel = "Softban",
            onConfirm = { value ->
                dialog = null
                vm.softban(value.toInt(), toast)
            },
            onDismiss = { dialog = null }
        )
        ModDialog.TIMEOUT -> ChoiceDialog(
            title = "Mutear a $name",
            description = "No va a poder escribir, reaccionar ni hablar en voz durante este tiempo.",
            options = timeoutOptions,
            defaultIndex = 2,
            confirmLabel = "Mutear",
            onConfirm = { value ->
                dialog = null
                vm.timeout(value, toast)
            },
            onDismiss = { dialog = null }
        )
        ModDialog.KICK -> ConfirmDialog(
            title = "Expulsar a $name",
            text = "Se lo saca del servidor, pero puede volver con una invitación.",
            confirmLabel = "Expulsar",
            onConfirm = {
                dialog = null
                vm.kick(toast)
            },
            onDismiss = { dialog = null }
        )
        ModDialog.UNBAN -> ConfirmDialog(
            title = "Desbanear a $name",
            text = "Va a poder volver a entrar al servidor.",
            confirmLabel = "Desbanear",
            onConfirm = {
                dialog = null
                vm.unban(toast)
            },
            onDismiss = { dialog = null }
        )
        ModDialog.NICK -> NickDialog(
            current = m.member?.nick.orEmpty(),
            onSave = { nick ->
                dialog = null
                vm.setNickname(nick, toast)
            },
            onDismiss = { dialog = null }
        )
        ModDialog.ROLES -> {
            val roles = remember(state.guildRoles, guildId) {
                (guildId?.let { state.guildRoles[it] } ?: emptyMap()).values
                    .filter { it.id != guildId && !it.managed }
                    .sortedByDescending { it.position }
            }
            val owned = m.member?.roles ?: emptyList()
            RolesPickDialog(
                title = "Roles de $name",
                roles = roles,
                owned = owned,
                onToggle = { roleId, add ->
                    // Solo se avisan los errores: cada tilde correcta ya se ve en la lista.
                    vm.toggleRole(roleId, add) { msg -> if (msg.startsWith("✗")) toast(msg) }
                },
                onDismiss = { dialog = null }
            )
        }
        ModDialog.BANS -> BansDialog(vm, toast) { dialog = null }
        null -> Unit
    }
}

@Composable
private fun UserRow(user: DUser, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DcColors.Panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, color = DcColors.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(user.id, color = DcColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionItem(
    emoji: String,
    title: String,
    description: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(DcColors.Panel)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) DcColors.Danger else DcColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(description, color = DcColors.TextMuted, fontSize = 12.sp)
        }
    }
}

/** Diálogo con opciones para elegir una (con botones de radio) y confirmar. */
@Composable
private fun ChoiceDialog(
    title: String,
    description: String,
    options: List<Pair<String, Long>>,
    defaultIndex: Int,
    confirmLabel: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableIntStateOf(defaultIndex.coerceIn(0, options.lastIndex)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(description, color = DcColors.TextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = index },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == selected, onClick = { selected = index })
                        Text(option.first, color = DcColors.TextPrimary, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options[selected].second) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text(title) },
        text = { Text(text, color = DcColors.TextMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NickDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var nick by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text("Cambiar apodo") },
        text = {
            Column {
                FormField("Apodo", nick, { nick = it }, 32)
                Text(
                    "Dejalo vacío para quitar el apodo.",
                    color = DcColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(nick) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RolesPickDialog(
    title: String,
    roles: List<com.example.discordtoolkit.discord.RoleInfo>,
    owned: List<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (roles.isEmpty()) {
                    Text("No hay roles para asignar en este servidor.", color = DcColors.TextMuted)
                }
                roles.forEach { role ->
                    val checked = role.id in owned
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(role.id, !checked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(role.id, it) },
                            colors = CheckboxDefaults.colors(checkedColor = DcColors.Accent)
                        )
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (role.color != 0) Color(0xFF000000.toInt() or role.color) else DcColors.Offline
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(role.name, color = DcColors.TextPrimary, fontSize = 15.sp)
                    }
                }
                Text(
                    "El bot solo puede dar roles que estén por debajo de su rol más alto.",
                    color = DcColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}

@Composable
private fun BansDialog(vm: AppViewModel, toast: (String) -> Unit, onDismiss: () -> Unit) {
    val m = vm.mod
    LaunchedEffect(Unit) { if (m.bans == null) vm.loadBans() }
    val bans = m.bans

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text("Baneados") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    m.loadingBans || bans == null -> Text("Cargando…", color = DcColors.TextMuted)
                    bans.isEmpty() -> Text("No hay usuarios baneados.", color = DcColors.TextMuted)
                    else -> bans.forEach { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = entry.user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.user.name,
                                    color = DcColors.TextPrimary,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    entry.reason ?: "Sin motivo",
                                    color = DcColors.TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { vm.unbanEntry(entry, toast) },
                                enabled = !m.busy
                            ) { Text("Desbanear") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
