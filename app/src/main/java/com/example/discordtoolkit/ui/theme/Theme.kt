package com.example.discordtoolkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DcColors {
    val Rail = Color(0xFF050505)
    val Panel = Color(0xFF0B0B0C)
    val Surface = Color(0xFF151516)
    val SurfaceHigh = Color(0xFF212123)
    val Border = Color(0xFF2A2A2D)
    val TextPrimary = Color(0xFFF2F2F3)
    val TextMuted = Color(0xFF9A9AA0)
    val Accent = Color(0xFF5865F2)
    val AccentSoft = Color(0xFF23264F)
    val Online = Color(0xFF23A55A)
    val Offline = Color(0xFF80848E)
    val Danger = Color(0xFFED4245)
}

@Composable
fun DiscordToolkitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DcColors.Accent,
            onPrimary = Color.White,
            background = DcColors.Rail,
            surface = DcColors.Panel,
            onBackground = DcColors.TextPrimary,
            onSurface = DcColors.TextPrimary
        ),
        content = content
    )
}
