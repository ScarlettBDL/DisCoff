package com.example.discordtoolkit

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.discordtoolkit.ui.AppRoot
import com.example.discordtoolkit.ui.AppViewModel
import com.example.discordtoolkit.ui.theme.DiscordToolkitTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Si el usuario activó los avisos, se vuelve a levantar el servicio al abrir la app.
        val storage = com.example.discordtoolkit.data.Storage(this)
        if (storage.token() != null && storage.blob("notif_enabled") == "1") {
            try {
                NotifyService.start(this)
            } catch (e: Exception) {
                // Android puede negar el inicio del servicio; no es grave
            }
        }
        setContent {
            DiscordToolkitTheme {
                val vm: AppViewModel = viewModel()
                val state by vm.state.collectAsState()
                AppRoot(state, vm)
            }
        }
    }
}
