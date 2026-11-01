package com.example.j2me

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a J2ME .jar: copies it into filesDir, unpacks its resources, converts the
 * classes to Dalvik with the bundled dx tool and stores everything in the layout the
 * embedded engine reads.
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
        onProgress(3)
        // Everything, including the temporary copy, stays inside filesDir.
        val jarFile = try {
            J2meFiles.copyJarToInternal(context, uri, displayName)
        } catch (e: Exception) {
            throw if (e is InstallException) e else InstallException(e.message ?: "Gagal memasang game.", e)
        }

        try {
            installFromJarFile(context, jarFile, displayName, onProgress)
        } catch (e: Exception) {
            throw if (e is InstallException) e else InstallException(e.message ?: "Gagal memasang game.", e)
        } finally {
            jarFile.delete()
        }
    }

    suspend fun installFromJarFile(
        context: Context,
        jarFile: File,
        displayName: String? = null,
        onProgress: (Int) -> Unit = {}
    ): J2meStore.Game = withContext(Dispatchers.IO) {
        if (!jarFile.isFile || jarFile.length() == 0L) {
            throw InstallException("Berkas .jar tidak ditemukan atau kosong.")
        }

        onProgress(10)
        val manifest = readManifestFromJar(jarFile)
        if (manifest["MIDlet-1"].isNullOrBlank()) {
            throw InstallException(
                "Berkas ini bukan game J2ME: MIDlet-1 tidak ada di META-INF/MANIFEST.MF."
            )
        }
        checkBytecode(jarFile)

        onProgress(25)
        val dir = J2meStore.createGameDir(context, displayName ?: jarFile.name)
        var success = false
        try {
            val storedJar = File(dir, J2meStore.JAR_FILE_NAME)
            jarFile.copyTo(storedJar, overwrite = true)

            ZipFile(storedJar).use { zip ->
                extractResources(zip, File(dir, J2meStore.RES_DIR_NAME))
                extractIcon(zip, manifest, File(dir, J2meStore.ICON_FILE_NAME))
            }

            onProgress(45)
            convertToDex(storedJar, File(dir, J2meStore.DEX_FILE_NAME))
            onProgress(90)

            writeManifest(manifest, File(dir, J2meStore.CONF_FILE_NAME))
            onProgress(100)
            success = true
            J2meStore.toGame(dir)
        } finally {
            if (!success) dir.deleteRecursively()
        }
    }

    private fun readManifestFromJar(jar: File): Map<String, String> {
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

    /**
     * Saves the MIDlet's own icon as icon.png. Falls back to the first PNG at the root of
     * the archive when the manifest does not point at one.
     */
    private fun extractIcon(zip: ZipFile, manifest: Map<String, String>, outFile: File) {
        val declared = declaredIconPath(manifest)
        val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()

        var entry = declared?.let { path ->
            entries.firstOrNull { it.name.equals(path, ignoreCase = true) }
        }
        if (entry == null) {
            entry = entries.firstOrNull {
                it.name.endsWith(".png", ignoreCase = true) &&
                    !it.name.startsWith("META-INF", ignoreCase = true) &&
                    !it.name.contains('/')
            }
        }
        if (entry == null) return

        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        try {
            FileOutputStream(outFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun declaredIconPath(manifest: Map<String, String>): String? {
        val midlet = manifest["MIDlet-1"]
        val fromMidlet = midlet?.split(',')
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val raw = fromMidlet ?: manifest["MIDlet-Icon"]?.trim()?.takeIf { it.isNotEmpty() }
        return raw?.trimStart('/')
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
        // Android 14+ refuses to load a dex file that is still writable.
        dexFile.setReadOnly()
    }

    private fun writeManifest(props: Map<String, String>, file: File) {
        file.bufferedWriter().use { writer ->
            for ((key, value) in props) {
                writer.appendLine("$key: $value")
            }
        }
    }
}
