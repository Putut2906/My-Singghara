package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class ParsedReceiptData(
    val jenis: String, // "masuk" atau "keluar"
    val nominal: Double,
    val kategori: String,
    val deskripsi: String,
    val tanggal: String, // YYYY-MM-DD
    val rawText: String,
    val platformName: String
)

object ReceiptScannerHelper {

    suspend fun parseReceiptImage(context: Context, imageUri: Uri): ParsedReceiptData {
        val bitmap = loadBitmapFromUri(context, imageUri)
        return if (bitmap != null) {
            parseReceiptBitmap(bitmap)
        } else {
            fallbackData("Gambar tidak dapat dimuat", todayDate())
        }
    }

    suspend fun parseReceiptBitmap(bitmap: Bitmap): ParsedReceiptData {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = recognizer.process(image).await()
            val text = visionText.text
            extractReceiptData(text)
        } catch (e: Exception) {
            fallbackData("Pindai Bukti Transaksi", todayDate())
        }
    }

    fun parseReceiptText(text: String): ParsedReceiptData {
        return extractReceiptData(text)
    }

    private fun extractReceiptData(rawText: String): ParsedReceiptData {
        val upperText = rawText.uppercase(Locale.getDefault())

        // 1. Deteksi Platform E-Wallet / Bank
        val platform = when {
            upperText.contains("GOPAY") || upperText.contains("GOJEK") -> "GoPay"
            upperText.contains("DANA") -> "DANA"
            upperText.contains("OVO") -> "OVO"
            upperText.contains("SHOPEEPAY") || upperText.contains("SHOPEE") -> "ShopeePay"
            upperText.contains("BCA") -> "BCA"
            upperText.contains("MANDIRI") || upperText.contains("LIVIN") -> "Mandiri"
            upperText.contains("BRI") || upperText.contains("BRIMO") -> "BRI"
            upperText.contains("BNI") -> "BNI"
            upperText.contains("QRIS") -> "QRIS"
            else -> "E-Wallet/Bank"
        }

        // 2. Deteksi Jenis (Masuk vs Keluar)
        val isMasuk = upperText.contains("TRANSFER MASUK") ||
                upperText.contains("UANG DITERIMA") ||
                upperText.contains("SALDO DITERIMA") ||
                upperText.contains("DITERIMA DARI") ||
                upperText.contains("TOP UP BERHASIL") ||
                upperText.contains("TOPUP BERHASIL") ||
                upperText.contains("ISI SALDO") ||
                upperText.contains("KREDIT") ||
                (upperText.contains("DARI:") && !upperText.contains("KEPADA:"))

        val jenis = if (isMasuk) "masuk" else "keluar"

        // 3. Ekstraksi Nominal
        val nominal = extractAmount(rawText)

        // 4. Deteksi Tanggal (format YYYY-MM-DD)
        val tanggal = extractDate(rawText) ?: todayDate()

        // 5. Ekstraksi Penerima / Pengirim / Merchant untuk Deskripsi
        val recipientOrMerchant = extractRecipientOrMerchant(rawText, platform)

        // 6. Penentuan Kategori & Deskripsi
        val deskripsi = if (recipientOrMerchant.isNotBlank()) {
            if (jenis == "masuk") "Terima dari $recipientOrMerchant via $platform"
            else "Bayar $recipientOrMerchant via $platform"
        } else {
            if (jenis == "masuk") "Pemasukan via $platform"
            else "Pengeluaran via $platform"
        }

        val kategori = when {
            jenis == "masuk" -> {
                when {
                    upperText.contains("GAJI") || upperText.contains("SALARY") -> "Gaji"
                    upperText.contains("BONUS") || upperText.contains("THR") -> "Bonus"
                    upperText.contains("HADIAH") || upperText.contains("GIFT") -> "Hadiah"
                    upperText.contains("INVESTASI") || upperText.contains("PROFIT") -> "Investasi"
                    else -> "Lainnya"
                }
            }
            else -> {
                when {
                    upperText.contains("PLN") || upperText.contains("LISTRIK") ||
                            upperText.contains("PULSA") || upperText.contains("PAKET DATA") ||
                            upperText.contains("TAGIHAN") || upperText.contains("BPJS") ||
                            upperText.contains("INTERNET") || upperText.contains("WIFI") -> "Tagihan"

                    upperText.contains("MAKAN") || upperText.contains("FOOD") ||
                            upperText.contains("RESTO") || upperText.contains("CAFE") ||
                            upperText.contains("WARUNG") || upperText.contains("MINUM") ||
                            upperText.contains("INDOMARET") || upperText.contains("ALFAMART") ||
                            upperText.contains("SUPERMARKET") || upperText.contains("PASAR") -> "Kebutuhan Pokok"

                    upperText.contains("GOJEK") || upperText.contains("GRAB") ||
                            upperText.contains("MAXIM") || upperText.contains("BENSIN") ||
                            upperText.contains("PERTAMINA") || upperText.contains("PARKIR") ||
                            upperText.contains("TOL") || upperText.contains("KRL") ||
                            upperText.contains("KERETA") || upperText.contains("TIKET") -> "Transportasi"

                    upperText.contains("SEKOLAH") || upperText.contains("KURSUS") ||
                            upperText.contains("KULIAH") || upperText.contains("SPP") ||
                            upperText.contains("BUKU") -> "Pendidikan"

                    upperText.contains("BIOSKOP") || upperText.contains("NONTON") ||
                            upperText.contains("STREAMING") || upperText.contains("GAME") ||
                            upperText.contains("FASHION") || upperText.contains("SHOPPING") -> "Gaya Hidup"

                    else -> "Lainnya"
                }
            }
        }

        return ParsedReceiptData(
            jenis = jenis,
            nominal = nominal,
            kategori = kategori,
            deskripsi = deskripsi,
            tanggal = tanggal,
            rawText = rawText,
            platformName = platform
        )
    }

    private fun extractAmount(text: String): Double {
        val lines = text.lines()

        // Prioritaskan baris yang mengandung 'Total', 'Nominal', 'Jumlah', 'Rp'
        val amountRegex = Pattern.compile("(?i)(?:rp\\.?|idr)?\\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?)")

        var foundAmounts = mutableListOf<Double>()

        for (line in lines) {
            val trimmed = line.trim()
            val matcher = amountRegex.matcher(trimmed)
            while (matcher.find()) {
                val numStr = matcher.group(1) ?: continue
                val cleanNum = cleanNumberString(numStr)
                if (cleanNum > 100.0) { // filter out small artifacts like dates or line numbers
                    // If the line has 'Total', 'Jumlah', or 'Nominal', prioritize it
                    if (trimmed.contains("total", ignoreCase = true) ||
                        trimmed.contains("jumlah", ignoreCase = true) ||
                        trimmed.contains("nominal", ignoreCase = true) ||
                        trimmed.contains("rp", ignoreCase = true)
                    ) {
                        return cleanNum
                    }
                    foundAmounts.add(cleanNum)
                }
            }
        }

        // Jika tidak ada kata kunci khusus, ambil nilai yang paling masuk akal (terbesar jika wajar)
        return foundAmounts.maxOrNull() ?: 0.0
    }

    private fun cleanNumberString(raw: String): Double {
        var str = raw.replace(" ", "")
        // Check format: 50.000 or 50,000 or 50.000,00 or 50,000.00
        val lastDot = str.lastIndexOf('.')
        val lastComma = str.lastIndexOf(',')

        if (lastDot != -1 && lastComma != -1) {
            if (lastDot > lastComma) {
                // e.g. 50,000.00 -> remove commas, dot is decimal
                str = str.replace(",", "")
            } else {
                // e.g. 50.000,00 -> remove dots, comma is decimal
                str = str.replace(".", "").replace(',', '.')
            }
        } else if (lastDot != -1) {
            // e.g. 50.000 (Indonesian thousands) vs 50.00 (decimal)
            val parts = str.split('.')
            if (parts.last().length == 3) {
                // thousands separator
                str = str.replace(".", "")
            } else if (parts.size > 2) {
                // multiple dots e.g. 1.000.000
                str = str.replace(".", "")
            }
        } else if (lastComma != -1) {
            val parts = str.split(',')
            if (parts.last().length == 3 || parts.size > 2) {
                // thousands separator
                str = str.replace(",", "")
            } else {
                str = str.replace(',', '.')
            }
        }

        return str.toDoubleOrNull() ?: 0.0
    }

    private fun extractDate(text: String): String? {
        val sdfOut = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Pola DD/MM/YYYY atau DD-MM-YYYY
        val regexDate1 = Pattern.compile("(\\b\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})\\b")
        val m1 = regexDate1.matcher(text)
        if (m1.find()) {
            try {
                val d = m1.group(1)!!.toInt()
                val m = m1.group(2)!!.toInt()
                var y = m1.group(3)!!.toInt()
                if (y < 100) y += 2000
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
            } catch (_: Exception) {}
        }

        // Pola nama bulan: e.g. 19 Okt 2026 atau 19 Oktober 2026
        val months = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5,
            "jun" to 6, "jul" to 7, "agu" to 8, "aug" to 8, "sep" to 9, "okt" to 10,
            "oct" to 10, "nov" to 11, "des" to 12, "dec" to 12
        )
        for ((mName, mNum) in months) {
            val regexMonth = Pattern.compile("(?i)(\\d{1,2})\\s+$mName[a-z]*\\s+(\\d{4})")
            val m2 = regexMonth.matcher(text)
            if (m2.find()) {
                try {
                    val d = m2.group(1)!!.toInt()
                    val y = m2.group(2)!!.toInt()
                    return String.format(Locale.US, "%04d-%02d-%02d", y, mNum, d)
                } catch (_: Exception) {}
            }
        }

        return null
    }

    private fun extractRecipientOrMerchant(text: String, platform: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val keywords = listOf("PENERIMA", "KEPADA", "TUJUAN", "MERCHANT", "DITERIMA DARI", "DARI", "KE:")
        for (i in lines.indices) {
            val lineUpper = lines[i].uppercase(Locale.getDefault())
            for (kw in keywords) {
                if (lineUpper.startsWith(kw) || lineUpper.contains("$kw:")) {
                    var candidate = lines[i].substringAfter(":", "").trim()
                    if (candidate.isBlank() && i + 1 < lines.size) {
                        candidate = lines[i + 1].trim()
                    }
                    if (candidate.isNotBlank() && candidate.length < 40 && !candidate.contains("Rp", ignoreCase = true)) {
                        return candidate
                    }
                }
            }
        }

        return ""
    }

    private fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun fallbackData(deskripsi: String, tanggal: String): ParsedReceiptData {
        return ParsedReceiptData(
            jenis = "keluar",
            nominal = 0.0,
            kategori = "Lainnya",
            deskripsi = deskripsi,
            tanggal = tanggal,
            rawText = "",
            platformName = "E-Wallet/Bank"
        )
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
