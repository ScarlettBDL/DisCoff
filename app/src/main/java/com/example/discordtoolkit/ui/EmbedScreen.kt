package com.example.discordtoolkit.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.ui.theme.DcColors

@Composable
fun EmbedScreen(state: UiState, vm: AppViewModel) {
    val d = vm.embedDraft
    val guildId = state.embedsGuildId
    val guild = state.guilds.firstOrNull { it.id == guildId }
    val allChannels = state.channels[guildId] ?: emptyList()
    val textChannels = remember(allChannels) { allChannels.filter { it.isText }.sortedBy { it.position } }
    val categories = remember(allChannels) { allChannels.filter { it.isCategory }.associateBy { it.id } }
    val context = LocalContext.current
    var pickChannel by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler { vm.closeEmbeds() }

    // Si los canales llegaron después de abrir la herramienta, se elige uno por defecto.
    LaunchedEffect(textChannels) {
        if (d.channelId == null) d.channelId = textChannels.firstOrNull()?.id
    }

    val total = d.totalChars()
    val preview = d.toEmbed()
    val channelName = textChannels.firstOrNull { it.id == d.channelId }?.name

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
                    .clickable { vm.closeEmbeds() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = DcColors.TextPrimary, fontSize = 30.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Embeds", color = DcColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

        // Formulario
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Canal de destino
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DcColors.Surface)
                    .clickable { pickChannel = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enviar a", color = DcColors.TextMuted, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (channelName != null) "#$channelName" else "Elegir canal",
                    color = DcColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("▾", color = DcColors.TextMuted, fontSize = 16.sp)
            }

            Section("Mensaje (texto fuera del embed)") {
                FormField(
                    "Mensaje", d.content, { d.content = it },
                    EmbedDraft.MAX_CONTENT, singleLine = false, minLines = 2
                )
            }

            Section("Autor") {
                FormField("Nombre del autor", d.authorName, { d.authorName = it }, EmbedDraft.MAX_AUTHOR)
                FormField("URL del autor", d.authorUrl, { d.authorUrl = it }, EmbedDraft.MAX_URL, uri = true)
                FormField("URL del ícono del autor", d.authorIconUrl, { d.authorIconUrl = it }, EmbedDraft.MAX_URL, uri = true)
            }

            Section("Título y descripción") {
                FormField("Título", d.title, { d.title = it }, EmbedDraft.MAX_TITLE)
                FormField("URL del título", d.titleUrl, { d.titleUrl = it }, EmbedDraft.MAX_URL, uri = true)
                FormField(
                    "Descripción", d.description, { d.description = it },
                    EmbedDraft.MAX_DESCRIPTION, singleLine = false, minLines = 4
                )
                Text(
                    "Se pueden usar **negrita**, *cursiva*, __subrayado__, ~~tachado~~, `código`, > citas y links.",
                    color = DcColors.TextMuted,
                    fontSize = 12.sp
                )
            }

            Section("Color de la barra") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (d.color == 0) DcColors.SurfaceHigh else Color(0xFF000000.toInt() or d.color))
                            .border(1.dp, DcColors.Border, RoundedCornerShape(10.dp))
                            .clickable { pickColor = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (d.color == 0) Text("🎨", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (d.color == 0) "Sin color" else "#%06X".format(d.color),
                        color = DcColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (d.color != 0) {
                        TextButton(onClick = { d.color = 0 }) { Text("Quitar") }
                    }
                }
            }

            Section("Imágenes") {
                FormField("URL de la miniatura (chica, a la derecha)", d.thumbnailUrl, { d.thumbnailUrl = it }, EmbedDraft.MAX_URL, uri = true)
                FormField("URL de la imagen (grande, abajo)", d.imageUrl, { d.imageUrl = it }, EmbedDraft.MAX_URL, uri = true)
            }

            Section("Campos (${d.fields.size}/${EmbedDraft.MAX_FIELDS})") {
                d.fields.forEachIndexed { index, field ->
                    key(field.key) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DcColors.Panel)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Campo ${index + 1}",
                                color = DcColors.TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { d.fields.remove(field) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar campo", tint = DcColors.TextMuted)
                            }
                        }
                        FormField("Nombre", field.name, { field.name = it }, EmbedDraft.MAX_FIELD_NAME)
                        FormField(
                            "Valor", field.value, { field.value = it },
                            EmbedDraft.MAX_FIELD_VALUE, singleLine = false, minLines = 2
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = field.inline, onCheckedChange = { field.inline = it })
                            Spacer(Modifier.width(10.dp))
                            Text("En línea (hasta 3 por fila)", color = DcColors.TextPrimary, fontSize = 14.sp)
                        }
                    }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DcColors.Border, RoundedCornerShape(12.dp))
                        .clickable(enabled = d.fields.size < EmbedDraft.MAX_FIELDS) { d.addField() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = DcColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar campo", color = DcColors.TextPrimary, fontWeight = FontWeight.Medium)
                }
            }

            Section("Pie") {
                FormField("Texto del pie", d.footerText, { d.footerText = it }, EmbedDraft.MAX_FOOTER)
                FormField("URL del ícono del pie", d.footerIconUrl, { d.footerIconUrl = it }, EmbedDraft.MAX_URL, uri = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = d.timestamp, onCheckedChange = { d.timestamp = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Mostrar fecha y hora del envío", color = DcColors.TextPrimary, fontSize = 14.sp)
                }
            }

            Section("Vista previa") {
                if (d.content.isNotBlank()) {
                    Text(renderMarkdown(d.content), color = DcColors.TextPrimary, fontSize = 16.sp)
                }
                if (d.isEmbedEmpty()) {
                    Text(
                        "Completá algún campo y acá vas a ver cómo queda.",
                        color = DcColors.TextMuted,
                        fontSize = 14.sp
                    )
                } else {
                    EmbedCard(
                        e = preview,
                        onImageClick = { _, _ -> },
                        onVideoClick = { _, _ -> },
                        onOpenUrl = { }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Barra de abajo
        Column(
            Modifier
                .fillMaxWidth()
                .background(DcColors.Surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$total / ${EmbedDraft.MAX_TOTAL} caracteres",
                color = if (total > EmbedDraft.MAX_TOTAL) DcColors.Danger else DcColors.TextMuted,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DcColors.SurfaceHigh,
                        contentColor = DcColors.TextPrimary
                    )
                ) { Text("Limpiar") }
                Button(
                    onClick = {
                        vm.sendEmbed { error ->
                            Toast.makeText(context, error ?: "Embed enviado ✓", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !state.sendingEmbed && d.channelId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.sendingEmbed) "Enviando…" else "Enviar embed")
                }
            }
        }
    }

    if (pickChannel) {
        AlertDialog(
            onDismissRequest = { pickChannel = false },
            containerColor = DcColors.Surface,
            title = { Text("Elegí el canal") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (textChannels.isEmpty()) {
                        Text("Este server no tiene canales de texto visibles para el bot.", color = DcColors.TextMuted)
                    }
                    textChannels.forEach { ch ->
                        val categoryName = ch.parentId?.let { categories[it]?.name }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (ch.id == d.channelId) DcColors.SurfaceHigh else Color.Transparent)
                                .clickable {
                                    d.channelId = ch.id
                                    pickChannel = false
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Text("#${ch.name}", color = DcColors.TextPrimary, fontSize = 16.sp)
                            if (categoryName != null) {
                                Text(categoryName, color = DcColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickChannel = false }) { Text("Cancelar") } }
        )
    }

    if (pickColor) {
        ColorPickerDialog(
            initial = d.color,
            onDismiss = { pickColor = false },
            onPick = {
                d.color = it
                pickColor = false
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = DcColors.Surface,
            title = { Text("Limpiar todo") },
            text = { Text("Se borran todos los campos del embed. El canal elegido se mantiene.") },
            confirmButton = {
                TextButton(onClick = {
                    d.clear()
                    confirmClear = false
                }) { Text("Limpiar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DcColors.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = DcColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    maxLen: Int,
    singleLine: Boolean = true,
    minLines: Int = 1,
    uri: Boolean = false
) {
    TextField(
        value = value,
        onValueChange = { onChange(it.take(maxLen)) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 12,
        keyboardOptions = KeyboardOptions(keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DcColors.SurfaceHigh,
            unfocusedContainerColor = DcColors.SurfaceHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = DcColors.TextPrimary,
            unfocusedTextColor = DcColors.TextPrimary,
            focusedLabelColor = DcColors.TextMuted,
            unfocusedLabelColor = DcColors.TextMuted
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
