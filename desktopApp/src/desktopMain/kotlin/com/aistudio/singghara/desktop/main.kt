package com.aistudio.singghara.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aistudio.singghara.shared.SharedGreetingScreen

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1100.dp, 750.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "My Singghara (Desktop)",
        state = windowState,
        // Fase 4 will add minimumSize, icon, tray, keyboard shortcuts.
    ) {
        SharedGreetingScreen(modifier = Modifier)
    }
}
