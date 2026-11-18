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
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            DiscordToolkitTheme {
                val vm: AppViewModel = viewModel()
                val state by vm.state.collectAsState()
                AppRoot(state, vm)
            }
        }
    }
}
