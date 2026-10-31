package com.example

import com.example.util.AzanDownloader
import com.example.util.SinggharaFolderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AzanDownloaderTest {

    @Test
    fun testDefaultAzanFilesCatalog() {
        val files = AzanDownloader.DEFAULT_AZAN_FILES
        assertEquals(4, files.size)

        val fileNames = files.map { it.name }
        assertTrue(fileNames.contains("adzan_turkey-1.mp3"))
        assertTrue(fileNames.contains("adzan_turkey-2.mp3"))
        assertTrue(fileNames.contains("fajr_128_44.mp3"))
        assertTrue(fileNames.contains("Madina-Adzan-Fajr.mp3"))

        for (item in files) {
            assertTrue(item.id.isNotBlank())
            assertTrue(item.name.endsWith(".mp3"))
        }
    }

    @Test
    fun testObsoleteBundledFilesList() {
        val obsolete = SinggharaFolderManager.OBSOLETE_BUNDLED_MP3_FILES
        assertTrue(obsolete.contains("adzan_makkah.mp3"))
        assertTrue(obsolete.contains("adzan_merdu.mp3"))
        assertTrue(obsolete.contains("azan_madina.mp3"))
        assertTrue(obsolete.contains("azan_subuh.mp3"))
    }

    @Test
    fun testBundledAssetsExist() {
        val assetsDir = java.io.File("src/main/assets/mp3")
        assertTrue("Assets directory should exist", assetsDir.exists())
        val files = assetsDir.listFiles() ?: emptyArray()
        assertTrue("Assets should contain mp3 files", files.isNotEmpty())
        val names = files.map { it.name }
        assertTrue(names.contains("adzan_turkey-1.mp3"))
        assertTrue(names.contains("adzan_turkey-2.mp3"))
        assertTrue(names.contains("fajr_128_44.mp3"))
        assertTrue(names.contains("Madina-Adzan-Fajr.mp3"))
        for (f in files) {
            assertTrue("File ${f.name} should have valid size", f.length() > 500_000L)
        }
    }
}
