package com.example.discordtoolkit.ui

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.discordtoolkit.ui.theme.DcColors

private enum class Section { GUILD, DMS }

@Composable
fun MainScreen() {
    var section by remember { mutableStateOf(Section.GUILD) }
    var selectedGuildId by remember { mutableStateOf(FakeData.guilds.first().id) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var showTools by remember { mutableStateOf(false) }
    val guild = FakeData.guilds.first { it.id == selectedGuildId }

    Row(
        Modifier
            .fillMaxSize()
            .background(DcColors.Rail)
            .systemBarsPadding()
    ) {
        ServerRail(
            guilds = FakeData.guilds,
            dmsSelected = section == Section.DMS,
            selectedGuildId = if (section == Section.GUILD) selectedGuildId else null,
            onHome = {
                section = Section.DMS
                showTools = false
            },
            onSelectGuild = { id ->
                selectedGuildId = id
                selectedChannelId = null
                section = Section.GUILD
                showTools = false
            }
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
                    section == Section.DMS -> DmPanel()
                    showTools -> ToolsPanel(onBack = { showTools = false })
                    else -> ChannelPanel(
                        guild = guild,
                        selectedChannelId = selectedChannelId,
                        onSelectChannel = { selectedChannelId = it },
                        onOpenTools = { showTools = true }
                    )
                }
            }
            UserBar(FakeData.bot)
        }
    }
}

// ---------- Rail de servers ----------

@Composable
private fun ServerRail(
    guilds: List<Guild>,
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
                background = Color(g.color),
                onClick = { onSelectGuild(g.id) }
            ) {
                Text(g.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

    data class Header(val category: Category, val collapsed: Boolean) : ListEntry {
        override val key: String get() = "h-${category.id}"
    }

    data class Item(val channel: Channel) : ListEntry {
        override val key: String get() = channel.id
    }
}

@Composable
private fun ChannelPanel(
    guild: Guild,
    selectedChannelId: String?,
    onSelectChannel: (String) -> Unit,
    onOpenTools: () -> Unit
) {
    val categories = remember(guild.id) { FakeData.categoriesFor(guild.id) }
    val collapsed = remember(guild.id) { mutableStateListOf<String>() }
    val entries = categories.flatMap { cat ->
        val isCollapsed = cat.id in collapsed
        val header: ListEntry = ListEntry.Header(cat, isCollapsed)
        if (isCollapsed) {
            listOf(header)
        } else {
            listOf(header) + cat.channels.map { ListEntry.Item(it) }
        }
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
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is ListEntry.Header -> CategoryHeader(entry.category.name, entry.collapsed) {
                    if (entry.collapsed) {
                        collapsed.remove(entry.category.id)
                    } else {
                        collapsed.add(entry.category.id)
                    }
                }
                is ListEntry.Item -> ChannelRow(
                    channel = entry.channel,
                    selected = entry.channel.id == selectedChannelId,
                    onClick = { onSelectChannel(entry.channel.id) }
                )
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
private fun ChannelRow(channel: Channel, selected: Boolean, onClick: () -> Unit) {
    val isText = channel.type == ChannelType.TEXT
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DcColors.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (isText) "#" else "🔊",
            color = DcColors.TextMuted,
            fontSize = if (isText) 22.sp else 16.sp,
            modifier = Modifier.width(32.dp)
        )
        Text(
            channel.name,
            color = if (selected) DcColors.TextPrimary else DcColors.TextMuted,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- Herramientas ----------

@Composable
private fun ToolsPanel(onBack: () -> Unit) {
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
        items(FakeData.tools, key = { it.id }) { tool ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DcColors.Surface)
                    .padding(16.dp)
            ) {
                Text(
                    tool.title,
                    color = DcColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(tool.description, color = DcColors.TextMuted, fontSize = 14.sp)
            }
        }
    }
}

// ---------- Mensajes directos ----------

@Composable
private fun DmPanel() {
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
        items(FakeData.directMessages, key = { it.id }) { dm ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(dm.name.first().toString(), 44.dp, DcColors.SurfaceHigh)
                Spacer(Modifier.width(12.dp))
                Text(dm.name, color = DcColors.TextPrimary, fontSize = 17.sp)
            }
        }
    }
}

// ---------- Barra de usuario (el bot) ----------

@Composable
private fun UserBar(bot: BotUser) {
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
            Avatar(bot.name.first().toString(), 44.dp, DcColors.Accent)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(DcColors.Online)
                    .border(2.dp, DcColors.Surface, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bot.name,
                color = DcColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(bot.status, color = DcColors.TextMuted, fontSize = 14.sp)
        }
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = DcColors.TextMuted
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
