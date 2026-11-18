package com.example.discordtoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.ui.theme.DcColors
import kotlinx.coroutines.delay

@Composable
fun AppRoot(state: UiState, vm: AppViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DcColors.Rail)
    ) {
        when {
            state.phase == Phase.INIT -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.phase == Phase.LOGIN -> LoginScreen(state.loginError, state.connecting, vm::login)
            state.openChannel != null -> ChatScreen(state, vm)
            state.rolesGuildId != null -> RolesScreen(state, vm)
            state.embedsGuildId != null -> EmbedScreen(state, vm)
            state.modGuildId != null -> ModerationScreen(state, vm)
            state.tool != null -> ToolHost(state, vm)
            else -> MainScreen(state, vm)
        }
        val notice = state.notice
        if (notice != null) {
            NoticeBar(
                text = notice,
                onDismiss = vm::dismissNotice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
            )
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit, modifier: Modifier) {
    LaunchedEffect(text) {
        delay(6000)
        onDismiss()
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DcColors.Danger)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}
