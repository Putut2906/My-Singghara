package com.example.j2me

import android.content.Context
import java.io.File

/**
 * Storage for J2ME games imported into the app. Each game lives in its own folder
 * under filesDir/j2me and keeps the layout the embedded engine expects:
 * converted.dex, converted.dex.conf and res/.
 */
object J2meStore {

    const val DEX_FILE_NAME = "converted.dex"
    const val CONF_FILE_NAME = "converted.dex.conf"
    const val RES_DIR_NAME = "res"
    const val JAR_FILE_NAME = "game.jar"
    const val ICON_FILE_NAME = "icon.png"

    data class Game(
        val id: String,
        val dir: File,
        val title: String,
        val vendor: String,
        val version: String,
        val sizeBytes: Long,
        val iconFile: File?
    )

    fun rootDir(context: Context): File = File(context.filesDir, "j2me").apply { mkdirs() }

    fun listGames(context: Context): List<Game> =
        rootDir(context).listFiles()
            ?.filter { it.isDirectory && File(it, DEX_FILE_NAME).isFile }
            ?.map { toGame(it) }
            ?.sortedBy { it.title.lowercase() }
            ?: emptyList()

    fun toGame(dir: File): Game {
        val props = readManifest(File(dir, CONF_FILE_NAME))
        return Game(
            id = dir.name,
            dir = dir,
            title = props["MIDlet-Name"] ?: dir.name,
            vendor = props["MIDlet-Vendor"].orEmpty(),
            version = props["MIDlet-Version"].orEmpty(),
            sizeBytes = directorySize(dir),
            iconFile = File(dir, ICON_FILE_NAME).takeIf { it.isFile && it.length() > 0L }
                ?: iconFromResources(dir, props)
        )
    }

    /** Older installs did not copy icon.png, but the icon still sits in res/. */
    private fun iconFromResources(dir: File, props: Map<String, String>): File? {
        val raw = props["MIDlet-1"]?.split(',')?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: props["MIDlet-Icon"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val path = raw.trimStart('/')
        val file = File(File(dir, RES_DIR_NAME), path)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun createGameDir(context: Context, jarName: String): File {
        val slug = jarName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "game" }
        return File(rootDir(context), "${slug}_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    fun delete(game: Game) {
        game.dir.deleteRecursively()
    }

    /** Reads key/value manifest lines, joining values wrapped by the 72-byte format. */
    fun readManifest(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val result = LinkedHashMap<String, String>()
        var currentKey: String? = null
        file.forEachLine { raw ->
            val key = currentKey
            when {
                raw.startsWith(" ") && key != null ->
                    result[key] = result[key].orEmpty() + raw.substring(1)
                raw.isBlank() -> currentKey = null
                raw.indexOf(':') > 0 -> {
                    val index = raw.indexOf(':')
                    val newKey = raw.substring(0, index).trim()
                    result[newKey] = raw.substring(index + 1).trim()
                    currentKey = newKey
                }
            }
        }
        return result
    }

    private fun directorySize(dir: File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}