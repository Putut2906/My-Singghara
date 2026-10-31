package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MasaBerlakuHelper {

    /**
     * Mengecek apakah masa berlaku ID card telah habis.
     * Mendukung:
     * - "Berlaku Selama Aktif", "Seumur Hidup", "Permanen", "Selamanya" -> tidak pernah kedaluwarsa.
     * - Durasi relatif: "1 Tahun", "2 Tahun", "3 Tahun", "4 Tahun", "5 Tahun", "6 Bulan", dll.
     * - Format tanggal pasti: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd, dd/MM/yy, d MMMM yyyy, dll.
     */
    fun isExpired(
        berlakuHingga: String?,
        tanggalDibuat: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (berlakuHingga.isNullOrBlank()) return false
        val text = berlakuHingga.trim()

        // 1. Keyword tidak pernah kedaluwarsa
        if (text.contains("Seumur Hidup", ignoreCase = true) ||
            text.contains("Permanen", ignoreCase = true) ||
            text.contains("Selama Aktif", ignoreCase = true) ||
            text.contains("Selamanya", ignoreCase = true)
        ) {
            return false
        }

        // 2. Durasi relatif dalam Tahun
        val yearMatch = Regex("(\\d+)\\s*Tahun", RegexOption.IGNORE_CASE).find(text)
        if (yearMatch != null) {
            val years = yearMatch.groupValues[1].toIntOrNull() ?: 1
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (tanggalDibuat > 0) tanggalDibuat else currentTimeMillis
                add(Calendar.YEAR, years)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return currentTimeMillis > cal.timeInMillis
        }

        // 3. Durasi relatif dalam Bulan
        val monthMatch = Regex("(\\d+)\\s*Bulan", RegexOption.IGNORE_CASE).find(text)
        if (monthMatch != null) {
            val months = monthMatch.groupValues[1].toIntOrNull() ?: 1
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (tanggalDibuat > 0) tanggalDibuat else currentTimeMillis
                add(Calendar.MONTH, months)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return currentTimeMillis > cal.timeInMillis
        }

        // 4. Pola Tanggal Tertentu
        val datePatterns = listOf(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd",
            "dd/MM/yy",
            "dd-MM-yy",
            "d MMMM yyyy",
            "d MMM yyyy",
            "yyyy/MM/dd"
        )
        for (pattern in datePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale("id", "ID"))
                sdf.isLenient = false
                val date = sdf.parse(text)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        if (get(Calendar.YEAR) < 100) {
                            set(Calendar.YEAR, 2000 + get(Calendar.YEAR))
                        }
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    return currentTimeMillis > cal.timeInMillis
                }
            } catch (_: Exception) {
            }

            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(text)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        if (get(Calendar.YEAR) < 100) {
                            set(Calendar.YEAR, 2000 + get(Calendar.YEAR))
                        }
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    return currentTimeMillis > cal.timeInMillis
                }
            } catch (_: Exception) {
            }
        }

        return false
    }
}
