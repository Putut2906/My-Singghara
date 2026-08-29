package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*

/**
 * A highly reusable Jetpack Compose wrapper for managing runtime storage permissions
 * using the Accompanist Permissions library. Handles both legacy storage requirements
 * (on Android 10 and below) andscoped / broad storage permissions (on Android 11+).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoragePermissionBox(
    onPermissionGranted: () -> Unit,
    content: @Composable (requestPermission: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }

    // Check MANAGE_EXTERNAL_STORAGE on Android 11+
    fun isAllFilesGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+ Flow: Needs MANAGE_EXTERNAL_STORAGE
        var hasAllFilesAccess by remember { mutableStateOf(isAllFilesGranted()) }

        // Periodically refresh state when app is resumed or when checked
        LaunchedEffect(Unit) {
            hasAllFilesAccess = isAllFilesGranted()
        }

        if (hasAllFilesAccess) {
            content {
                onPermissionGranted()
            }
        } else {
            if (showRationaleDialog) {
                AlertDialog(
                    onDismissRequest = { showRationaleDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Akses Folder File Diperlukan", fontSize = 16.sp)
                        }
                    },
                    text = {
                        Text(
                            "Aplikasi memerlukan izin 'Akses Semua File' (MANAGE_EXTERNAL_STORAGE) untuk " +
                            "menyimpan cadangan terenkripsi Anda di folder Download eksternal publik " +
                            "serta memulihkannya kembali secara langsung.",
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRationaleDialog = false
                                com.example.ui.MainViewModel.instance?.preventAutoLock = true
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        // Fail-safe manual settings redirection
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        ) {
                            Text("Buka Pengaturan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRationaleDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            content {
                if (isAllFilesGranted()) {
                    hasAllFilesAccess = true
                    onPermissionGranted()
                } else {
                    showRationaleDialog = true
                }
            }
        }
    } else {
        // Android 10 (API 29) & Legacy Storage Flow: Use Accompanist for READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
        val permissionState = rememberMultiplePermissionsState(
            permissions = listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )

        if (showRationaleDialog) {
            AlertDialog(
                onDismissRequest = { showRationaleDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Izin Penyimpanan Diperlukan", fontSize = 16.sp)
                    }
                },
                text = {
                    Text(
                        "Izin baca/tulis penyimpanan eksternal diperlukan oleh " +
                        "My Singghara agar fitur ekspor dan impor file backup database JSON " +
                        "dapat diakses pada perangkat Anda.",
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRationaleDialog = false
                            permissionState.launchMultiplePermissionRequest()
                        }
                    ) {
                        Text("Izinkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRationaleDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (permissionState.allPermissionsGranted) {
            content {
                onPermissionGranted()
            }
        } else {
            content {
                if (permissionState.allPermissionsGranted) {
                    onPermissionGranted()
                } else if (permissionState.shouldShowRationale) {
                    showRationaleDialog = true
                } else {
                    permissionState.launchMultiplePermissionRequest()
                }
            }
        }
    }
}
