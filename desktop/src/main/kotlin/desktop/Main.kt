package desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.example.web.WebUiPage
import desktop.db.DesktopDatabase
import desktop.db.RoomStore
import desktop.server.Api
import desktop.server.ClasspathAssets
import desktop.server.DesktopServer
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.RandomAccessFile
import javax.imageio.ImageIO

private const val DEFAULT_PORT = 18765

fun main() {
    System.setProperty("apple.awt.application.name", "My Singghara")
    val settings = Settings(AppPaths.settingsFile)

    // Satu instans saja: dua proses membuka berkas SQLite yang sama akan saling mengunci.
    val lockChannel = RandomAccessFile(File(AppPaths.dataDir, "app.lock"), "rw").channel
    val lock = try { lockChannel.tryLock() } catch (_: Exception) { null }
    val runningUrlFile = File(AppPaths.dataDir, "running.url")
    if (lock == null) {
        val url = runCatching { runningUrlFile.readText().trim() }.getOrDefault("")
        if (url.isNotEmpty()) BrowserShell.open(url, settings)
        return
    }

    val db = DesktopDatabase.open(AppPaths.databaseFile)
    lateinit var server: DesktopServer
    server = DesktopServer(
        api = Api(RoomStore(db), settings) { server.port },
        pageHtml = WebUiPage::html,
        assets = ClasspathAssets,
        preferredPort = settings["port", DEFAULT_PORT.toString()].toIntOrNull() ?: DEFAULT_PORT,
    )
    server.start()
    settings["port"] = server.port.toString()
    runningUrlFile.writeText(server.url)

    BrowserShell.open(server.url, settings)

    try {
        application {
            var windowVisible by remember { mutableStateOf(true) }
            val icon = remember { loadIcon() }
            val openApp = { BrowserShell.open(server.url, settings) }

            if (isTraySupported) {
                Tray(
                    icon = icon ?: BitmapPainter(java.awt.image.BufferedImage(16, 16, 2).toComposeImageBitmap()),
                    tooltip = "My Singghara",
                    onAction = { windowVisible = true },
                ) {
                    Item("Buka My Singghara", onClick = { openApp() })
                    Item("Tampilkan jendela kontrol", onClick = { windowVisible = true })
                    Item("Keluar", onClick = ::exitApplication)
                }
            }

            Window(
                onCloseRequest = { if (isTraySupported) windowVisible = false else exitApplication() },
                visible = windowVisible,
                title = "My Singghara",
                icon = icon,
                state = rememberWindowState(width = 460.dp, height = 380.dp),
            ) {
                ControlPanel(
                    url = server.url,
                    dataDir = AppPaths.dataDir,
                    onOpen = { openApp() },
                    onQuit = ::exitApplication,
                    hasTray = isTraySupported,
                )
            }
        }
    } finally {
        runCatching { server.stop() }
        runCatching { db.close() }
        runCatching { runningUrlFile.delete() }
        runCatching { lock.release() }
    }
}

private fun loadIcon(): BitmapPainter? =
    ClasspathAssets.read("web/icon-192.png")?.let { bytes ->
        runCatching { BitmapPainter(ImageIO.read(bytes.inputStream()).toComposeImageBitmap()) }.getOrNull()
    }

@Composable
private fun ControlPanel(url: String, dataDir: File, onOpen: () -> Unit, onQuit: () -> Unit, hasTray: Boolean) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text("My Singghara untuk PC", style = MaterialTheme.typography.headlineSmall)
                Text("Server lokal berjalan di $url", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Data tersimpan di komputer ini:\n${dataDir.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (hasTray) {
                    Text(
                        "Menutup jendela ini menyembunyikannya ke ikon baki sistem; aplikasi tetap berjalan.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Buka My Singghara") }
                OutlinedButton(
                    onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salin alamat") }
                OutlinedButton(
                    onClick = { runCatching { Desktop.getDesktop().open(dataDir) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Buka folder data") }
                OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) { Text("Keluar") }
            }
        }
    }
}
