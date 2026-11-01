package com.example.j2me

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a J2ME .jar: unpacks its resources, converts the classes to Dalvik with the
 * bundled dx tool and stores everything in the layout the embedded engine reads.
 */
object J2meInstaller {

    class InstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

    // dx 1.10 (app/libs/dx.jar) understands class files up to Java 7 (major 51).
    private const val MAX_CLASS_MAJOR_VERSION = 51

    suspend fun install(
        context: Context,
        uri: Uri,
        displayName: String?,
        onProgress: (Int) -> Unit
    ): J2meStore.Game = withContext(Dispatchers.IO) {
        onProgress(5)
        val tempDir = File(context.cacheDir, "j2me-install").apply {
            deleteRecursively()
            mkdirs()
        }
        val jarFile = File(tempDir, "game.jar")
        var gameDir: File? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(jarFile).use { output -> input.copyTo(output) }
            } ?: throw InstallException("Berkas .jar tidak bisa dibuka.")

            if (jarFile.length() == 0L) {
                throw InstallException("Berkas .jar kosong.")
            }

            onProgress(15)
            val manifest = readManifest(jarFile)
            if (manifest["MIDlet-1"].isNullOrBlank()) {
                throw InstallException(
                    "Berkas ini bukan game J2ME: MIDlet-1 tidak ada di META-INF/MANIFEST.MF."
                )
            }
            checkBytecode(jarFile)

            onProgress(30)
            val dir = J2meStore.createGameDir(
                context,
                displayName ?: uri.lastPathSegment.orEmpty()
            )
            gameDir = dir
            ZipFile(jarFile).use { zip ->
                extractResources(zip, File(dir, J2meStore.RES_DIR_NAME))
            }

            onProgress(45)
            val dexFile = File(dir, J2meStore.DEX_FILE_NAME)
            convertToDex(jarFile, dexFile)
            onProgress(85)

            writeManifest(manifest, File(dir, J2meStore.CONF_FILE_NAME))
            onProgress(100)
            J2meStore.toGame(dir)
        } catch (e: Exception) {
            gameDir?.deleteRecursively()
            throw if (e is InstallException) {
                e
            } else {
                InstallException(e.message ?: "Gagal memasang game.", e)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun readManifest(jar: File): Map<String, String> {
        ZipFile(jar).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { it.name.equals("META-INF/MANIFEST.MF", ignoreCase = true) }
                ?: throw InstallException("META-INF/MANIFEST.MF tidak ditemukan di dalam .jar.")
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            return parseManifest(text)
        }
    }

    private fun parseManifest(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var currentKey: String? = null
        for (raw in text.split("\r\n", "\n")) {
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

    private fun checkBytecode(jar: File) {
        var classCount = 0
        ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.name.endsWith(".class", ignoreCase = true)) continue
                classCount++
                zip.getInputStream(entry).use { input ->
                    val header = ByteArray(8)
                    var read = 0
                    while (read < header.size) {
                        val count = input.read(header, read, header.size - read)
                        if (count <= 0) break
                        read += count
                    }
                    if (read >= 8 &&
                        header[0] == 0xCA.toByte() && header[1] == 0xFE.toByte() &&
                        header[2] == 0xBA.toByte() && header[3] == 0xBE.toByte()
                    ) {
                        val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
                        if (major > MAX_CLASS_MAJOR_VERSION) {
                            throw InstallException(
                                "Game ini memakai bytecode Java ${major - 44} yang belum didukung emulator. " +
                                    "Gunakan versi .jar yang lebih lama."
                            )
                        }
                    }
                }
            }
        }
        if (classCount == 0) {
            throw InstallException("Tidak ada file kelas di dalam .jar; game tidak bisa dijalankan.")
        }
    }

    private fun extractResources(zip: ZipFile, resDir: File) {
        for (entry in zip.entries()) {
            if (entry.isDirectory) continue
            val name = entry.name
            if (name.endsWith(".class", ignoreCase = true)) continue
            if (name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) continue
            val safeName = name.replace('\\', '/')
            if (safeName.startsWith("/") || safeName.split('/').contains("..")) continue
            val outFile = File(resDir, safeName)
            outFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun convertToDex(jar: File, dexFile: File) {
        dexFile.parentFile?.mkdirs()
        if (dexFile.exists()) dexFile.delete()
        try {
            // Main.main() is used instead of System.exit-carrying helpers; dx only exits on
            // usage/internal errors, which the checks above are meant to prevent.
            val dxMain = Class.forName("com.android.dx.command.Main")
            val main = dxMain.getMethod("main", Array<String>::class.java)
            main.invoke(null, arrayOf("--dex", "--output=${dexFile.absolutePath}", jar.absolutePath))
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException
            throw InstallException("Konversi game gagal: ${cause?.message ?: cause}", cause)
        } catch (e: Exception) {
            throw InstallException("Konversi game gagal: ${e.message}", e)
        }
        if (!dexFile.isFile || dexFile.length() == 0L) {
            throw InstallException("Konversi game tidak menghasilkan berkas dex.")
        }
    }

    private fun writeManifest(props: Map<String, String>, file: File) {
        file.bufferedWriter().use { writer ->
            for ((key, value) in props) {
                writer.appendLine("$key: $value")
            }
        }
    }
}