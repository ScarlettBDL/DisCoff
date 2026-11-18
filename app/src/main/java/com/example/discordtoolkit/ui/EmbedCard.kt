package com.example.discordtoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.discordtoolkit.discord.Embed
import com.example.discordtoolkit.discord.EmbedField
import com.example.discordtoolkit.ui.theme.DcColors

/** Agrupa los campos como Discord: los "en línea" van de a 3 por fila, los demás ocupan toda la fila. */
private fun groupFields(fields: List<EmbedField>): List<List<Int>> {
    val rows = ArrayList<MutableList<Int>>()
    fields.forEachIndexed { index, f ->
        val last = rows.lastOrNull()
        if (f.inline && last != null && last.all { fields[it].inline } && last.size < 3) {
            last.add(index)
        } else {
            rows.add(mutableListOf(index))
        }
    }
    return rows
}

/**
 * Embed completo: autor, título (con link), descripción, campos, miniatura, imagen, pie y fecha.
 * Lo usan el chat y la vista previa de la herramienta de embeds, así se ven igual.
 */
@Composable
fun EmbedCard(
    e: Embed,
    onImageClick: (String, String) -> Unit,
    onVideoClick: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val hasText = !e.title.isNullOrBlank() || !e.description.isNullOrBlank() ||
        !e.authorName.isNullOrBlank() || e.fields.isNotEmpty() ||
        !e.footerText.isNullOrBlank() || e.thumbnailUrl != null

    // Un embed que es solo una imagen o un gif se muestra sin marco, como en Discord.
    if (!hasText && e.imageUrl != null) {
        val playUrl = e.videoUrl
        SizedMedia(width = e.imageWidth, height = e.imageHeight, onClick = {
            if (playUrl != null) onVideoClick(playUrl, "video.mp4") else onImageClick(e.imageUrl, "imagen.png")
        }) { w, h ->
            AsyncImage(
                model = e.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(w, h)
            )
            if (playUrl != null) PlayBadge()
        }
        return
    }

    val barColor = if (e.color != null && e.color != 0) Color(0xFF000000.toInt() or e.color) else DcColors.Border
    val description: AnnotatedString? = remember(e.description) {
        if (e.description.isNullOrBlank()) null else renderMarkdown(e.description)
    }
    val fieldValues: List<AnnotatedString> = remember(e.fields) { e.fields.map { renderMarkdown(it.value) } }
    val fieldRows = remember(e.fields) { groupFields(e.fields) }

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
        // Autor
        if (!e.authorName.isNullOrBlank()) {
            val authorUrl = e.authorUrl
            Row(
                Modifier
                    .padding(bottom = 6.dp)
                    .clickable(enabled = authorUrl != null) { if (authorUrl != null) onOpenUrl(authorUrl) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (e.authorIconUrl != null) {
                    AsyncImage(
                        model = e.authorIconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    e.authorName,
                    color = DcColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Título, descripción, campos y miniatura (la miniatura va a la derecha)
        Row {
            Column(Modifier.weight(1f)) {
                if (!e.title.isNullOrBlank()) {
                    val titleUrl = e.linkUrl
                    Text(
                        e.title,
                        color = if (titleUrl != null) DcColors.Accent else DcColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clickable(enabled = titleUrl != null) { if (titleUrl != null) onOpenUrl(titleUrl) }
                    )
                }
                if (description != null) {
                    Text(description, color = DcColors.TextPrimary, fontSize = 14.sp)
                }
                fieldRows.forEach { rowIndexes ->
                    val inlineRow = e.fields[rowIndexes.first()].inline
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowIndexes.forEach { index ->
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.fields[index].name,
                                    color = DcColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(fieldValues[index], color = DcColors.TextPrimary, fontSize = 14.sp)
                            }
                        }
                        // Una fila "en línea" con menos de 3 campos deja el hueco, como Discord.
                        if (inlineRow) {
                            repeat(3 - rowIndexes.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (e.thumbnailUrl != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = e.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
        }

        // Imagen grande
        if (e.imageUrl != null) {
            val playUrl = e.videoUrl
            SizedMedia(width = e.imageWidth, height = e.imageHeight, onClick = {
                if (playUrl != null) onVideoClick(playUrl, "video.mp4") else onImageClick(e.imageUrl, "imagen.png")
            }) { w, h ->
                AsyncImage(
                    model = e.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(w, h)
                )
                if (playUrl != null) PlayBadge()
            }
        }

        // Pie y fecha
        val footer = e.footerText
        val time = e.timeLabel
        if (!footer.isNullOrBlank() || time != null) {
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (e.footerIconUrl != null && !footer.isNullOrBlank()) {
                    AsyncImage(
                        model = e.footerIconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                val line = listOfNotNull(footer?.takeIf { it.isNotBlank() }, time).joinToString(" • ")
                Text(line, color = DcColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}
