package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Membaca payload QR Login Cepat dari file kartu membership: gambar (PNG/JPG),
 * PDF hasil cetak, atau berkas teks/JSON berisi payload.
 */
object MembershipCardQuickLoginReader {

    private val barcodeOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93
        )
        .build()

    suspend fun readQuickLoginPayload(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            when {
                mime.startsWith("text/") || mime == "application/json" -> readTextPayload(context, uri)
                mime == "application/pdf" -> scanPdf(context, uri) ?: readTextPayload(context, uri)
                else -> scanImage(context, uri) ?: readTextPayload(context, uri)
            }
        }

    private suspend fun scanImage(context: Context, uri: Uri): String? = try {
        scan(InputImage.fromFilePath(context, uri))
    } catch (_: Exception) {
        null
    }

    private suspend fun scanPdf(context: Context, uri: Uri): String? {
        var descriptor: ParcelFileDescriptor? = null
        return try {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val scale = minOf(2f, 2200f / page.width.coerceAtLeast(1))
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    scan(InputImage.fromBitmap(bitmap, 0))
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { descriptor?.close() }
        }
    }

    private suspend fun scan(image: InputImage): String? {
        val scanner = BarcodeScanning.getClient(barcodeOptions)
        return try {
            scanner.process(image).await()
                .asSequence()
                .mapNotNull { it.rawValue?.trim() }
                .firstOrNull { it.isNotEmpty() }
        } finally {
            scanner.close()
        }
    }

    private fun readTextPayload(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(16 * 1024)
            val read = stream.read(buffer)
            val text = if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8).trim()
            // Payload Login Cepat selalu memuat email; ini sekaligus menyaring berkas biner lain.
            text.takeIf { it.isNotBlank() && it.contains("@") }
        }
    } catch (_: Exception) {
        null
    }
}
