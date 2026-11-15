package com.aistudio.singghara.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** A @Composable that lives in commonMain and renders on BOTH Android and desktop. */
@Composable
fun SharedGreetingScreen(modifier: Modifier = Modifier) {
    MaterialTheme {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Greeting().greet())
        }
    }
}
