package com.example.discordtoolkit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.discordtoolkit.discord.Attachment
import com.example.discordtoolkit.discord.Embed
import com.example.discordtoolkit.discord.Message
import com.example.discordtoolkit.discord.Reaction
import com.example.discordtoolkit.discord.formatTime
import com.example.discordtoolkit.ui.theme.DcColors

private val quickEmojis = listOf("👍", "👎", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👀", "✅", "❌", "💀")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(state: UiState, vm: AppViewModel) {
    val channel = state.openChannel ?: return
    BackHandler { vm.closeChannel() }

    var draft by rememberSaveable(channel.id) { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.sendImage(uri, draft)
            draft = ""
        }
    }

    LaunchedEffect(state.editing?.id) {
        val editing = state.editing
        if (editing != null) draft = editing.rawContent
    }

    val prefix = if (channel.isDm) "@" else "#"

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
                    .clickable { vm.closeChannel() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = DcColors.TextPrimary, fontSize = 30.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                prefix + channel.title,
                color = DcColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DcColors.Border)
        )

        // Mensajes
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.loadingMessages -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.messages.isEmpty() -> Text(
                    "No hay mensajes todavía",
                    color = DcColors.TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.messages.asReversed(), key = { it.id }) { m ->
                        MessageItem(
                            m = m,
                            onLongPress = { actionsFor = m },
                            onReaction = { emoji -> vm.toggleReaction(m, emoji) },
                            onOpenUrl = { url -> uriHandler.openUri(url) }
                        )
                    }
                }
            }
        }

        // Banner de respuesta / edición
        val replying = state.replyingTo
        val editing = state.editing
        if (replying != null || editing != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(DcColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (replying != null) "Respondiendo a ${replying.author.name}" else "Editando mensaje",
                    color = DcColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    color = DcColors.TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable {
                            if (editing != null) draft = ""
                            vm.cancelCompose()
                        }
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // Composer
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = { picker.launch("image/*") }) {
                Icon(Icons.Filled.Add, contentDescription = "Adjuntar imagen", tint = DcColors.TextPrimary)
            }
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje en $prefix${channel.title}", color = DcColors.TextMuted) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DcColors.Surface,
                    unfocusedContainerColor = DcColors.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = DcColors.TextPrimary,
                    unfocusedTextColor = DcColors.TextPrimary
                )
            )
            val canSend = draft.isNotBlank() && !state.sending
            IconButton(
                onClick = {
                    vm.send(draft)
                    draft = ""
                },
                enabled = canSend
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (canSend) DcColors.Accent else DcColors.TextMuted
                )
            }
        }
    }

    // Acciones de un mensaje (mantener apretado)
    val target = actionsFor
    if (target != null) {
        val mine = target.author.id == state.me?.id
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = DcColors.Surface,
            confirmButton = {},
            text = {
                Column {
                    Text("Reaccionar", color = DcColors.TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    quickEmojis.chunked(6).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowItems.forEach { emoji ->
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            vm.toggleReaction(target, emoji)
                                            actionsFor = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ActionRow("Responder") {
                        vm.startReply(target)
                        actionsFor = null
                    }
                    if (mine) {
                        ActionRow("Editar") {
                            vm.startEdit(target)
                            actionsFor = null
                        }
                    }
                    ActionRow("Borrar") {
                        vm.deleteMessage(target)
                        actionsFor = null
                    }
                    if (!channel.isDm && !target.author.bot) {
                        ActionRow("Mensaje directo a ${target.author.name}") {
                            vm.startDm(target.author.id)
                            actionsFor = null
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = DcColors.TextPrimary,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    m: Message,
    onLongPress: () -> Unit,
    onReaction: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        AsyncImage(
            model = m.author.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    m.author.name,
                    color = DcColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (m.author.bot) {
                    Text("  BOT", color = DcColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("  " + formatTime(m.timestamp), color = DcColors.TextMuted, fontSize = 12.sp)
            }
            val reply = m.reply
            if (reply != null) {
                Text(
                    "↪ ${reply.authorName}: ${reply.content}",
                    color = DcColors.TextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (m.content.isNotBlank()) {
                Text(
                    if (m.edited) m.content + " (editado)" else m.content,
                    color = DcColors.TextPrimary,
                    fontSize = 16.sp
                )
            }
            m.embeds.forEach { EmbedBox(it) }
            m.attachments.forEach { AttachmentView(it, onOpenUrl) }
            if (m.reactions.isNotEmpty()) {
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    m.reactions.forEach { r -> ReactionChip(r) { onReaction(r.apiEmoji) } }
                }
            }
        }
    }
}

@Composable
private fun EmbedBox(e: Embed) {
    val color = if (e.color != null) Color(0xFF000000.toInt() or e.color) else DcColors.Border
    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DcColors.Surface)
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        if (!e.title.isNullOrBlank()) {
            Text(e.title, color = DcColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (!e.description.isNullOrBlank()) {
            Text(e.description, color = DcColors.TextPrimary, fontSize = 14.sp)
        }
        if (e.imageUrl != null) {
            AsyncImage(
                model = e.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun AttachmentView(a: Attachment, onOpenUrl: (String) -> Unit) {
    if (a.isImage) {
        val ratio = if (a.width != null && a.height != null && a.height > 0) {
            a.width.toFloat() / a.height.toFloat()
        } else {
            null
        }
        val base = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(max = 320.dp)
        AsyncImage(
            model = a.url,
            contentDescription = a.filename,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = (if (ratio != null) base.aspectRatio(ratio) else base)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenUrl(a.url) }
        )
    } else {
        Text(
            "📎 ${a.filename}",
            color = DcColors.Accent,
            fontSize = 15.sp,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { onOpenUrl(a.url) }
        )
    }
}

@Composable
private fun ReactionChip(r: Reaction, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (r.me) DcColors.AccentSoft else DcColors.Surface)
            .border(1.dp, if (r.me) DcColors.Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val customUrl = r.customUrl
        if (customUrl != null) {
            AsyncImage(model = customUrl, contentDescription = r.emojiName, modifier = Modifier.size(18.dp))
        } else {
            Text(r.emojiName, fontSize = 15.sp)
        }
        Spacer(Modifier.width(4.dp))
        Text("${r.count}", color = DcColors.TextPrimary, fontSize = 13.sp)
    }
}
