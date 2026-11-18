package com.example.discordtoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.ui.theme.DcColors

@Composable
fun LoginScreen(error: String?, connecting: Boolean, onLogin: (String) -> Unit) {
    var token by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(DcColors.Rail)
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("DisCoff", color = DcColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Conectá tu bot con su token", color = DcColors.TextMuted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token del bot") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = DcColors.Danger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(token) },
            enabled = !connecting && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text("Conectar")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "El token se guarda cifrado en este dispositivo. Para leer mensajes, activá " +
                "MESSAGE CONTENT INTENT en discord.com/developers → tu app → Bot.",
            color = DcColors.TextMuted,
            fontSize = 13.sp
        )
    }
}
