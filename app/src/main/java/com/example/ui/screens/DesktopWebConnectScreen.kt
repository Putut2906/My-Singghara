package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.MainViewModel
import com.example.web.LocalHttpServer

private val DESKTOP_WEB_URL = "http://127.0.0.1:${LocalHttpServer.DEFAULT_PORT}/"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/**
 * Mode desktop aktif saat aplikasi berjalan di Samsung DeX (UiMode DESK) atau
 * ketika SemDesktopModeManager melaporkan desktop mode lewat refleksi.
 */
fun isDesktopModeActive(context: Context): Boolean {
    val uiModeType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (uiModeType == Configuration.UI_MODE_TYPE_DESK) return true
    return try {
        val manager = context.getSystemService("desktopmode")
        manager?.javaClass?.getMethod("isDesktopMode")?.invoke(manager) as? Boolean ?: false
    } catch (_: Throwable) {
        false
    }
}

@Composable
fun DesktopWebConnectScreen(
    viewModel: MainViewModel,
    onUseMobileUi: () -> Unit
) {
    var state by remember { mutableStateOf<DesktopServerState>(DesktopServerState.Starting) }
    var startAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(startAttempt) {
        if (viewModel.webServer?.isServerRunning() == true) {
            state = DesktopServerState.Ready(DESKTOP_WEB_URL)
        } else {
            state = DesktopServerState.Starting
            viewModel.startWebServer(
                onStarted = { state = DesktopServerState.Ready(DESKTOP_WEB_URL) },
                onError = { message -> state = DesktopServerState.Failed(message) }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (val current = state) {
            is DesktopServerState.Starting -> DesktopLoading()
            is DesktopServerState.Failed -> DesktopErrorScreen(
                message = current.message,
                onRetry = { startAttempt++ },
                onUseMobileUi = onUseMobileUi
            )
            is DesktopServerState.Ready -> DesktopWebView(
                url = current.url,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private sealed interface DesktopServerState {
    data object Starting : DesktopServerState
    data class Ready(val url: String) : DesktopServerState
    data class Failed(val message: String) : DesktopServerState
}

@Composable
private fun DesktopLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Menyiapkan antarmuka desktop...",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Memuat Web Connect (Akses PC) 100% offline dari perangkat ini",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DesktopErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onUseMobileUi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Antarmuka desktop gagal dimuat",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry) { Text("Coba Lagi") }
            OutlinedButton(onClick = onUseMobileUi) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tampilan Aplikasi")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DesktopWebView(url: String, modifier: Modifier = Modifier) {
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val callback = fileCallback
        fileCallback = null
        callback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.textZoom = 100
                settings.userAgentString = DESKTOP_USER_AGENT

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val target = request.url
                        val host = target.host ?: return false
                        if (host == "127.0.0.1" || host == "localhost") return false
                        return try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, target))
                            true
                        } catch (_: Exception) {
                            true
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult
                    ): Boolean {
                        AlertDialog.Builder(view.context)
                            .setMessage(message)
                            .setPositiveButton("OK") { _, _ -> result.confirm() }
                            .setOnCancelListener { result.cancel() }
                            .show()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult
                    ): Boolean {
                        AlertDialog.Builder(view.context)
                            .setMessage(message)
                            .setPositiveButton("OK") { _, _ -> result.confirm() }
                            .setNegativeButton("Batal") { _, _ -> result.cancel() }
                            .setOnCancelListener { result.cancel() }
                            .show()
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView,
                        url: String,
                        message: String,
                        defaultValue: String?,
                        result: JsPromptResult
                    ): Boolean {
                        val density = view.resources.displayMetrics.density
                        val input = EditText(view.context).apply {
                            setText(defaultValue ?: "")
                            setSingleLine()
                        }
                        val container = LinearLayout(view.context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
                            addView(
                                input,
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            )
                        }
                        AlertDialog.Builder(view.context)
                            .setMessage(message)
                            .setView(container)
                            .setPositiveButton("OK") { _, _ -> result.confirm(input.text.toString()) }
                            .setNegativeButton("Batal") { _, _ -> result.cancel() }
                            .setOnCancelListener { result.cancel() }
                            .show()
                        return true
                    }

                    override fun onShowFileChooser(
                        webView: WebView,
                        callback: ValueCallback<Array<Uri>>,
                        fileChooserParams: WebChromeClient.FileChooserParams
                    ): Boolean {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = callback
                        return try {
                            filePicker.launch(arrayOf("*/*"))
                            true
                        } catch (_: Exception) {
                            fileCallback = null
                            false
                        }
                    }
                }

                loadUrl(url)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        }
    )
}
