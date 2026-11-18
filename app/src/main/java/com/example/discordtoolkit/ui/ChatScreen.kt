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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.discordtoolkit.discord.Attachment
import com.example.discordtoolkit.discord.Embed
import com.example.discordtoolkit.discord.Message
import com.example.discordtoolkit.discord.Reaction
import com.example.discordtoolkit.ui.theme.DcColors

private val quickEmojis = listOf("👍", "👎", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👀", "✅", "❌", "💀")

/** Mensajes seguidos del mismo autor dentro de este margen se muestran sin repetir avatar y nombre. */
private const val GROUP_WINDOW_MS = 7 * 60 * 1000L

@Composable
fun ChatScreen(state: UiState, vm: AppViewModel) {
    val channel = state.openChannel ?: return
    BackHandler { vm.closeChannel() }

    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<Pair<String, String>?>(null) }
    val uriHandler = LocalUriHandler.current
    val prefix = if (channel.isDm) "@" else "#"
    val reversed = remember(state.messages) { state.messages.asReversed() }

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
                    itemsIndexed(reversed, key = { _, msg -> msg.id }) { index, m ->
                        val older = reversed.getOrNull(index + 1)
                        val grouped = older != null &&
                            older.author.id == m.author.id &&
                            m.reply == null &&
                            (m.epochMs - older.epochMs) in 0L until GROUP_WINDOW_MS
                        MessageItem(
                            m = m,
                            showHeader = !grouped,
                            onLongPress = { actionsFor = m },
                            onReaction = { emoji -> vm.toggleReaction(m, emoji) },
                            onOpenUrl = { url -> uriHandler.openUri(url) },
                            onImageClick = { url, name -> viewer = Pair(url, name) }
                        )
                    }
                }
            }
        }

        Composer(channel, state.replyingTo, state.editing, state.sending, vm)
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

    // Visor de imágenes
    val viewing = viewer
    if (viewing != null) {
        ImageViewer(url = viewing.first, filename = viewing.second, onDismiss = { viewer = null })
    }
}

/** El texto escrito vive acá adentro para que escribir no vuelva a dibujar toda la lista de mensajes. */
@Composable
private fun Composer(
    channel: ChannelRef,
    replyingTo: Message?,
    editing: Message?,
    uploading: Boolean,
    vm: AppViewModel
) {
    var draft by rememberSaveable(channel.id) { mutableStateOf("") }
    val prefix = if (channel.isDm) "@" else "#"
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.sendImage(uri, draft)
            draft = ""
        }
    }

    LaunchedEffect(editing?.id) {
        if (editing != null) draft = editing.rawContent
    }

    Column {
        if (replyingTo != null || editing != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(DcColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (replyingTo != null) "Respondiendo a ${replyingTo.author.name}" else "Editando mensaje",
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
        if (uploading) {
            Text(
                "Subiendo archivo…",
                color = DcColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
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
            val canSend = draft.isNotBlank()
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
    showHeader: Boolean,
    onLongPress: () -> Unit,
    onReaction: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onImageClick: (String, String) -> Unit
) {
    val body: AnnotatedString? = remember(m.content, m.edited) {
        if (m.content.isBlank()) {
            null
        } else {
            val base = renderMarkdown(m.content)
            if (m.edited) {
                buildAnnotatedString {
                    append(base)
                    withStyle(SpanStyle(color = DcColors.TextMuted, fontSize = 11.sp)) { append(" (editado)") }
                }
            } else {
                base
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (m.pending) 0.55f else 1f)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(start = 12.dp, end = 12.dp, top = if (showHeader) 8.dp else 1.dp, bottom = 1.dp)
    ) {
        // El avatar solo se dibuja en el primer mensaje de una racha del mismo autor.
        if (showHeader) {
            AsyncImage(
                model = m.author.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val reply = m.reply
            if (showHeader) {
                if (reply != null) {
                    Text(
                        "↪ ${reply.authorName}: ${reply.content}",
                        color = DcColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    Text("  " + m.timeLabel, color = DcColors.TextMuted, fontSize = 12.sp)
                }
            }
            if (body != null) {
                Text(body, color = DcColors.TextPrimary, fontSize = 16.sp)
            }
            m.embeds.forEach { EmbedBox(it, onImageClick) }
            m.attachments.forEach { AttachmentView(it, onOpenUrl, onImageClick) }
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
private fun EmbedBox(e: Embed, onImageClick: (String, String) -> Unit) {
    val barColor = if (e.color != null) Color(0xFF000000.toInt() or e.color) else DcColors.Border
    val description: AnnotatedString? = remember(e.description) {
        if (e.description.isNullOrBlank()) null else renderMarkdown(e.description)
    }
    // Solo la barra de la izquierda lleva el color del embed, como en Discord.
    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DcColors.Surface)
            .drawBehind { drawRect(color = barColor, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
    ) {
        if (!e.title.isNullOrBlank()) {
            Text(e.title, color = DcColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (description != null) {
            Text(description, color = DcColors.TextPrimary, fontSize = 14.sp)
        }
        if (e.imageUrl != null) {
            SizedImage(
                url = e.imageUrl,
                width = e.imageWidth,
                height = e.imageHeight,
                description = null,
                onClick = { onImageClick(e.imageUrl, "imagen.png") }
            )
        }
    }
}

@Composable
private fun AttachmentView(a: Attachment, onOpenUrl: (String) -> Unit, onImageClick: (String, String) -> Unit) {
    if (a.isImage) {
        SizedImage(
            url = a.url,
            width = a.width,
            height = a.height,
            description = a.filename,
            onClick = { onImageClick(a.url, a.filename) }
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

/**
 * Imagen con tamaño calculado de antemano a partir de sus dimensiones reales. Así la lista no
 * "salta" al cargar y las imágenes nunca se superponen con el texto ni entre ellas.
 */
@Composable
private fun SizedImage(url: String, width: Int?, height: Int?, description: String?, onClick: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        val availableWidth = minOf(maxWidth.value, 300f)
        val w = width ?: 0
        val h = height ?: 0
        val displayWidth: Dp
        val displayHeight: Dp
        if (w > 0 && h > 0) {
            val scale = minOf(availableWidth / w, 340f / h, 1f)
            displayWidth = (w * scale).dp
            displayHeight = (h * scale).dp
        } else {
            displayWidth = availableWidth.dp
            displayHeight = (availableWidth * 0.65f).dp
        }
        AsyncImage(
            model = url,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(displayWidth, displayHeight)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
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
