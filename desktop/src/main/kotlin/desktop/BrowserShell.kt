package desktop

import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Membuka UI web sebagai jendela aplikasi (tanpa bilah alamat/tab) memakai Chromium yang sudah ada di
 * komputer: Edge (selalu ada di Windows 10/11), Chrome, Brave, atau Chromium. Memakai profil sendiri
 * sehingga tidak mengganggu browser harian dan localStorage-nya tetap tersimpan.
 *
 * Jika tidak ada browser Chromium, dibuka di browser bawaan sistem.
 *
 * Alternatif jendela tertanam penuh (Chromium di dalam jendela Compose) dijelaskan di desktop/README.md.
 */
object BrowserShell {

    fun open(url: String, settings: Settings): Boolean {
        val exe = findChromium(settings["browser_path"])
        if (exe != null) {
            try {
                AppPaths.browserProfileDir.mkdirs()
                ProcessBuilder(
                    exe,
                    "--app=$url",
                    "--user-data-dir=${AppPaths.browserProfileDir.absolutePath}",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-features=Translate",
                    "--window-size=1366,820"
                )
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                return true
            } catch (_: Exception) {
                // jatuh ke browser bawaan di bawah
            }
        }
        return openDefaultBrowser(url)
    }

    private fun openDefaultBrowser(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else if (osName().contains("linux")) {
                ProcessBuilder("xdg-open", url).start()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun osName(): String = System.getProperty("os.name").lowercase()

    private fun findChromium(override: String): String? {
        if (override.isNotBlank() && File(override).canExecute()) return override
        val os = osName()
        val candidates: List<String> = when {
            os.contains("win") -> {
                val roots = listOfNotNull(
                    System.getenv("ProgramFiles(x86)"),
                    System.getenv("ProgramFiles"),
                    System.getenv("LOCALAPPDATA"),
                )
                val rel = listOf(
                    "Microsoft\\Edge\\Application\\msedge.exe",
                    "Google\\Chrome\\Application\\chrome.exe",
                    "BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                )
                rel.flatMap { r -> roots.map { root -> "$root\\$r" } }
            }
            os.contains("mac") -> listOf(
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
            )
            else -> {
                val names = listOf(
                    "google-chrome", "google-chrome-stable", "chromium", "chromium-browser",
                    "microsoft-edge", "microsoft-edge-stable", "brave-browser",
                )
                val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }
                names.flatMap { n -> dirs.map { d -> "$d/$n" } }
            }
        }
        return candidates.firstOrNull { File(it).canExecute() }
    }
}
