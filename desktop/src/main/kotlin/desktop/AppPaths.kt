package desktop

import java.io.File
import java.util.Properties

/** Lokasi data per sistem operasi, di luar folder instalasi supaya aman saat aplikasi diperbarui. */
object AppPaths {
    val dataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
            os.contains("mac") -> File(home, "Library/Application Support")
            else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(home, ".local/share")
        }
        File(base, "MySingghara").also { it.mkdirs() }
    }

    val databaseFile: File get() = File(dataDir, "mysingghara.db")
    val settingsFile: File get() = File(dataDir, "settings.properties")

    /** Profil browser khusus jendela aplikasi, terpisah dari profil browser harian. */
    val browserProfileDir: File get() = File(dataDir, "browser-profile")
}

/** Pengaturan sederhana (port, nama profil, zona waktu) di berkas .properties. */
class Settings(private val file: File) {
    private val props = Properties()

    init {
        if (file.exists()) file.reader(Charsets.UTF_8).use { props.load(it) }
    }

    operator fun get(key: String, default: String = ""): String = props.getProperty(key, default)

    @Synchronized
    operator fun set(key: String, value: String) {
        props.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.writer(Charsets.UTF_8).use { props.store(it, "My Singghara Desktop") }
    }
}
