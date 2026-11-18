package com.example.discordtoolkit.ui

import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Reproductor de video a pantalla completa con los controles del sistema (play, pausa, barra de tiempo). */
@Composable
fun VideoPlayerDialog(url: String, filename: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val frame = FrameLayout(ctx)
                    val video = VideoView(ctx)
                    frame.addView(
                        video,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                    )
                    val controller = MediaController(ctx)
                    controller.setAnchorView(frame)
                    video.setMediaController(controller)
                    video.setOnPreparedListener {
                        loading = false
                        video.start()
                    }
                    video.setOnErrorListener { _, _, _ ->
                        loading = false
                        failed = true
                        true
                    }
                    video.setVideoURI(Uri.parse(url))
                    frame
                },
                onRelease = { frame ->
                    (frame.getChildAt(0) as? VideoView)?.stopPlayback()
                }
            )
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            if (failed) {
                Text(
                    "No se pudo reproducir este video acá.\nProbá descargarlo.",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .systemBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable { downloadFile(context, url, filename) },
                contentAlignment = Alignment.Center
            ) {
                Text("⬇", color = Color.White, fontSize = 18.sp)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
