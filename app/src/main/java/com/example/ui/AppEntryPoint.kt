package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.components.SpringToastPopup
import com.example.ui.screens.FirstInstallSetupScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

/**
 * Common main entry point for My Singghara.
 * Separates core Compose UI logic from platform-specific code (e.g., Android Activities, OS permissions),
 * allowing UI code to be shared seamlessly between Android, WebAssembly (Wasm), and Desktop targets.
 */
@Composable
fun AppEntryPoint(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onMainAppContent: @Composable () -> Unit
) {
    MyApplicationTheme {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!viewModel.isFirstInstallSetupCompleted) {
                FirstInstallSetupScreen(
                    viewModel = viewModel,
                    onComplete = {
                        viewModel.isAppUnlocked = true
                    }
                )
            } else {
                onMainAppContent()
            }

            if (viewModel.isAppUnlocked && viewModel.toastPopupMessage != null) {
                SpringToastPopup(
                    message = viewModel.toastPopupMessage,
                    onDismiss = { viewModel.toastPopupMessage = null },
                    type = viewModel.toastPopupType
                )
            }
        }
    }
}
