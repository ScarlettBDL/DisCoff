package com.example.discordtoolkit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.discordtoolkit.discord.DChannel
import com.example.discordtoolkit.discord.DGuild
import com.example.discordtoolkit.discord.DUser
import com.example.discordtoolkit.discord.DmEntry
import com.example.discordtoolkit.ui.theme.DcColors

private class ToolInfo(val id: String, val title: String, val description: String)

private val tools = listOf(
    ToolInfo("roles", "Roles masivos", "Creá varios roles de una sola vez"),
    ToolInfo("embeds", "Embeds", "Armá y mandá embeds sin webhooks"),
    ToolInfo("mod", "Moderación", "Ban, softban, mute y más"),
    ToolInfo("msgs", "Mensajes", "Leé canales y enviá mensajes")
)

private val railPalette = listOf(0xFF3B3F8F, 0xFF1F6F54, 0xFF8F3B3B, 0xFF6B4A1E, 0xFF2E5A7A, 0xFF5A2E7A)

private fun railColor(id: String): Color =
    Color(railPalette[(id.hashCode() and 0x7fffffff) % railPalette.size])

@Composable
fun MainScreen(state: UiState, vm: AppViewModel) {
    // Qué panel se ve (DMs / herramientas) vive en el ViewModel para que sobreviva al abrir un chat o una herramienta.
    val guild = state.guilds.firstOrNull { it.id == state.selectedGuildId }

    BackHandler(enabled = state.showTools) { vm.showTools(false) }

    Row(
        Modifier
            .fillMaxSize()
            .background(DcColors.Rail)
            .systemBarsPadding()
    ) {
        ServerRail(
            guilds = state.guilds,
            dmsSelected = state.showDms,
            selectedGuildId = if (!state.showDms) state.selectedGuildId else null,
            onHome = { vm.showDms(true) },
            onSelectGuild = { id -> vm.selectGuild(id) }
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 28.dp))
                .background(DcColors.Panel)
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    state.showDms -> DmPanel(state.dms, vm)
                    state.showTools -> ToolsPanel(
                        onBack = { vm.showTools(false) },
                        onOpenTool = { id ->
                            if (guild != null) {
                                when (id) {
                                    "roles" -> vm.openRoles(guild.id)
                                    "embeds" -> vm.openEmbeds(guild.id)
                                }
                            }
                        }
                    )
                    guild == null -> Text(
                        "Cargando servidores… Si no aparece ninguno, el bot todavía no está en ningún server.",
                        color = DcColors.TextMuted,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> ChannelPanel(
                        guild = guild,
                        channels = state.channels[guild.id],
                        vm = vm,
                        onOpenTools = { vm.showTools(true) }
                    )
                }
            }
            UserBar(state.me, state.gatewayStatus, onLogout = vm::logout)
        }
    }
}

// ---------- Rail de servers ----------

@Composable
private fun ServerRail(
    guilds: List<DGuild>,
    dmsSelected: Boolean,
    selectedGuildId: String?,
    onHome: () -> Unit,
    onSelectGuild: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            RailButton(selected = dmsSelected, background = DcColors.Surface, onClick = onHome) {
                Text("💬", fontSize = 20.sp)
            }
        }
        item {
            Box(
                Modifier
                    .width(32.dp)
                    .height(1.dp)
                    .background(DcColors.Border)
            )
        }
        items(guilds, key = { it.id }) { g ->
            RailButton(
                selected = g.id == selectedGuildId,
                background = railColor(g.id),
                onClick = { onSelectGuild(g.id) }
            ) {
                val iconUrl = g.iconUrl
                if (iconUrl != null) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = g.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(g.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    selected: Boolean,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(Color.White)
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .clip(RoundedCornerShape(if (selected) 14.dp else 18.dp))
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

// ---------- Panel de canales ----------

private sealed interface ListEntry {
    val key: String

    data class Header(val category: DChannel, val collapsed: Boolean) : ListEntry {
        override val key: String get() = "h-${category.id}"
    }

    data class Item(val channel: DChannel) : ListEntry {
        override val key: String get() = channel.id
    }
}

private fun buildEntries(channels: List<DChannel>, collapsed: List<String>): List<ListEntry> {
    val sorter = compareBy<DChannel>({ it.isVoice }, { it.position })
    val categories = channels.filter { it.isCategory }.sortedBy { it.position }
    val categoryIds = categories.map { it.id }.toSet()
    val shown = channels.filter { it.isText || it.isVoice }
    val result = mutableListOf<ListEntry>()

    shown
        .filter { it.parentId == null || it.parentId !in categoryIds }
        .sortedWith(sorter)
        .forEach { result.add(ListEntry.Item(it)) }

    categories.forEach { cat ->
        val isCollapsed = cat.id in collapsed
        result.add(ListEntry.Header(cat, isCollapsed))
        if (!isCollapsed) {
            shown
                .filter { it.parentId == cat.id }
                .sortedWith(sorter)
                .forEach { result.add(ListEntry.Item(it)) }
        }
    }
    return result
}

@Composable
private fun ChannelPanel(
    guild: DGuild,
    channels: List<DChannel>?,
    vm: AppViewModel,
    onOpenTools: () -> Unit
) {
    val collapsed = remember(guild.id) { mutableStateListOf<String>() }
    val collapsedNow = collapsed.toList()
    val entries = remember(channels, collapsedNow) {
        if (channels == null) emptyList() else buildEntries(channels, collapsedNow)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    guild.name,
                    color = DcColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("  ›", color = DcColors.TextMuted, fontSize = 22.sp)
            }
        }
        item { SearchRow() }
        item { ToolsEntry(onOpenTools) }
        if (channels == null) {
            item {
                Text(
                    "Cargando canales…",
                    color = DcColors.TextMuted,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is ListEntry.Header -> CategoryHeader(entry.category.name, entry.collapsed) {
                    if (entry.collapsed) {
                        collapsed.remove(entry.category.id)
                    } else {
                        collapsed.add(entry.category.id)
                    }
                }
                is ListEntry.Item -> ChannelRow(entry.channel) {
                    if (entry.channel.isText) {
                        vm.openChannel(ChannelRef(entry.channel.id, entry.channel.name, false, guild.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, DcColors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = DcColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Buscar", color = DcColors.TextPrimary, fontWeight = FontWeight.Medium)
        }
        SquareIconButton(Icons.Filled.Person)
        SquareIconButton(Icons.Filled.DateRange)
    }
}

@Composable
private fun SquareIconButton(icon: ImageVector) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DcColors.Border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DcColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToolsEntry(onClick: () -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DcColors.Border)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                tint = DcColors.TextMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("Herramientas del bot", color = DcColors.TextMuted, fontSize = 18.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DcColors.Border)
        )
    }
}

@Composable
private fun CategoryHeader(name: String, collapsed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = DcColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = DcColors.TextMuted,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (collapsed) -90f else 0f)
        )
    }
}

@Composable
private fun ChannelRow(channel: DChannel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (channel.isText) "#" else "🔊",
            color = DcColors.TextMuted,
            fontSize = if (channel.isText) 22.sp else 16.sp,
            modifier = Modifier.width(32.dp)
        )
        Text(
            channel.name,
            color = DcColors.TextMuted,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- Herramientas ----------

@Composable
private fun ToolsPanel(onBack: () -> Unit, onOpenTool: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = DcColors.TextPrimary, fontSize = 28.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Herramientas",
                    color = DcColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        items(tools, key = { it.id }) { tool ->
            val ready = tool.id == "roles" || tool.id == "embeds"
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DcColors.Surface)
                    .clickable(enabled = ready) { onOpenTool(tool.id) }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tool.title,
                        color = if (ready) DcColors.TextPrimary else DcColors.TextMuted,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!ready) {
                        Text("Próximamente", color = DcColors.TextMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(tool.description, color = DcColors.TextMuted, fontSize = 14.sp)
            }
        }
    }
}

// ---------- Mensajes directos ----------

@Composable
private fun DmPanel(dms: List<DmEntry>, vm: AppViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                "Mensajes directos",
                color = DcColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DcColors.Surface)
                    .clickable { showDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+  Nuevo mensaje por ID de usuario", color = DcColors.TextPrimary, fontSize = 16.sp)
            }
        }
        if (dms.isEmpty()) {
            item {
                Text(
                    "Todavía no hay conversaciones. Las que te escriban van a aparecer acá, " +
                        "o abrí una con el ID de alguien que comparta un server con el bot " +
                        "(también podés mantener apretado un mensaje y elegir Mensaje directo).",
                    color = DcColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        items(dms, key = { it.channelId }) { dm ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.openChannel(ChannelRef(dm.channelId, dm.name, true)) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = dm.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(dm.name, color = DcColors.TextPrimary, fontSize = 17.sp)
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = DcColors.Surface,
            title = { Text("Nuevo mensaje directo") },
            text = {
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("ID del usuario") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    vm.startDm(userId)
                    userId = ""
                }) { Text("Abrir") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ---------- Barra de usuario (el bot) ----------

@Composable
private fun UserBar(me: DUser?, status: String, onLogout: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DcColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            if (me != null) {
                AsyncImage(
                    model = me.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
            } else {
                Avatar("?", 44.dp, DcColors.Accent)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (status == "Conectado") DcColors.Online else DcColors.Offline)
                    .border(2.dp, DcColors.Surface, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                me?.name ?: "…",
                color = DcColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(status, color = DcColors.TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { confirm = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Cerrar sesión",
                tint = DcColors.TextMuted
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = DcColors.Surface,
            title = { Text("Cerrar sesión") },
            text = { Text("Se borra el token guardado en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onLogout()
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun Avatar(letter: String, size: Dp, background: Color) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
