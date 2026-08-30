package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.AgendaItem
import com.example.network.HolidayClient
import com.example.ui.screens.HijriCalendarHelper
import com.example.ui.screens.JavaneseCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

object CalendarExportHelper {

    val MONTH_NAMES = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private val DAYS_HEADER = listOf("MINGGU", "SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU")
    private val DAYS_HEADER_SHORT = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    /**
     * Export kalender baik 12 Bulan (1 Tahun Penuh) maupun 1 Bulan pilihan
     * dengan opsi format PDF (multi-page/annual), PNG, dan JPG beresolusi tinggi,
     * serta spesifikasi kertas Art Paper / Art Carton.
     */
    suspend fun exportCalendar(
        context: Context,
        year: Int,
        month: Int, // 1-12 (digunakan untuk scope 1_MONTH atau konteks)
        hijriOffset: Int = -1,
        holidays: Map<String, String>,
        agendas: List<AgendaItem>,
        format: String, // "PNG", "JPG", "PDF"
        paperType: String, // "Art Paper (120–150 gsm)", "Art Carton (190–260 gsm)", or "Kartu ATM / IC Card (Ukuran Dompet)"
        scope: String = "12_MONTHS" // "12_MONTHS" or "1_MONTH"
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Gabungkan holiday fallback agar seluruh 12 bulan tercover lengkap
            val completeHolidays = HolidayClient.getFallbackHolidays(year).toMutableMap().apply {
                putAll(holidays)
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanPaper = paperType.replace(" ", "_").replace("(", "").replace(")", "").replace("–", "-").replace("/", "-")
            val isAtmCard = paperType.contains("ATM", ignoreCase = true) ||
                    paperType.contains("IC Card", ignoreCase = true) ||
                    paperType.contains("Dompet", ignoreCase = true)

            if (isAtmCard) {
                // EXPORT FORMAT KARTU ATM / IC CARD UKURAN DOMPET (CR-80: 85.6 x 54 mm)
                if (scope == "12_MONTHS") {
                    val fileNameBase = "Kalender_ATM_Card_12_Bulan_${year}"

                    if (format.equals("PDF", ignoreCase = true)) {
                        // PDF 2 Halaman CR80 (Hal 1: Muka Depan Semester I, Hal 2: Muka Belakang Semester II)
                        val pdfDocument = PdfDocument()
                        val cardWidthPx = 1012
                        val cardHeightPx = 638

                        // Hal 1: Sisi Depan (Jan - Jun)
                        val bmpA = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
                        val canvasA = Canvas(bmpA)
                        draw12MonthAtmCardSideA(canvasA, cardWidthPx, cardHeightPx, year, hijriOffset, completeHolidays)

                        val pageInfo1 = PdfDocument.PageInfo.Builder(243, 153, 1).create() // 85.6mm x 54mm in PDF points
                        val page1 = pdfDocument.startPage(pageInfo1)
                        page1.canvas.drawColor(Color.WHITE)
                        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        page1.canvas.drawBitmap(bmpA, null, RectF(0f, 0f, 243f, 153f), filterPaint)
                        pdfDocument.finishPage(page1)
                        bmpA.recycle()

                        // Hal 2: Sisi Belakang (Jul - Des)
                        val bmpB = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
                        val canvasB = Canvas(bmpB)
                        draw12MonthAtmCardSideB(canvasB, cardWidthPx, cardHeightPx, year, hijriOffset, completeHolidays)

                        val pageInfo2 = PdfDocument.PageInfo.Builder(243, 153, 2).create()
                        val page2 = pdfDocument.startPage(pageInfo2)
                        page2.canvas.drawColor(Color.WHITE)
                        page2.canvas.drawBitmap(bmpB, null, RectF(0f, 0f, 243f, 153f), filterPaint)
                        pdfDocument.finishPage(page2)
                        bmpB.recycle()

                        val pdfFile = File(exportDir, "$fileNameBase.pdf")
                        FileOutputStream(pdfFile).use { out ->
                            pdfDocument.writeTo(out)
                        }
                        pdfDocument.close()

                        saveToPublicDownloads(context, pdfFile, "application/pdf")
                        Result.success(pdfFile)
                    } else {
                        // PNG / JPG: Lembar Cetak 2 Sisi Lengkap (Depan + Belakang + Garis Potong Dompet)
                        val isPng = format.equals("PNG", ignoreCase = true)
                        val ext = if (isPng) "png" else "jpg"
                        val mimeType = if (isPng) "image/png" else "image/jpeg"
                        val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        val quality = if (isPng) 100 else 95

                        val sheetW = 1012
                        val sheetH = 1340
                        val bitmap = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)

                        draw12MonthAtmCardPrintSheet(
                            canvas = canvas,
                            width = sheetW,
                            height = sheetH,
                            year = year,
                            hijriOffset = hijriOffset,
                            holidays = completeHolidays
                        )

                        val imageFile = File(exportDir, "$fileNameBase.$ext")
                        FileOutputStream(imageFile).use { out ->
                            bitmap.compress(compressFormat, quality, out)
                        }
                        bitmap.recycle()

                        saveToPublicPictures(context, imageFile, mimeType)
                        Result.success(imageFile)
                    }
                } else {
                    // EXPORT 1 BULAN KARTU ATM / IC CARD
                    val monthName = MONTH_NAMES.getOrElse(month - 1) { "Bulan" }
                    val fileNameBase = "Kalender_ATM_Card_${monthName}_${year}"
                    val cardWidthPx = 1012
                    val cardHeightPx = 638

                    val bitmap = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    drawAtmCardMonthSheet(
                        canvas = canvas,
                        width = cardWidthPx,
                        height = cardHeightPx,
                        year = year,
                        month = month,
                        monthName = monthName,
                        hijriOffset = hijriOffset,
                        holidays = completeHolidays,
                        agendas = agendas
                    )

                    if (format.equals("PDF", ignoreCase = true)) {
                        val pdfDocument = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(243, 153, 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        page.canvas.drawColor(Color.WHITE)
                        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, 243f, 153f), filterPaint)
                        pdfDocument.finishPage(page)

                        val pdfFile = File(exportDir, "$fileNameBase.pdf")
                        FileOutputStream(pdfFile).use { out ->
                            pdfDocument.writeTo(out)
                        }
                        pdfDocument.close()
                        bitmap.recycle()

                        saveToPublicDownloads(context, pdfFile, "application/pdf")
                        Result.success(pdfFile)
                    } else {
                        val isPng = format.equals("PNG", ignoreCase = true)
                        val ext = if (isPng) "png" else "jpg"
                        val mimeType = if (isPng) "image/png" else "image/jpeg"
                        val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        val quality = if (isPng) 100 else 95

                        val imageFile = File(exportDir, "$fileNameBase.$ext")
                        FileOutputStream(imageFile).use { out ->
                            bitmap.compress(compressFormat, quality, out)
                        }
                        bitmap.recycle()

                        saveToPublicPictures(context, imageFile, mimeType)
                        Result.success(imageFile)
                    }
                }
            } else if (scope == "12_MONTHS") {
                // EXPORT 12 BULAN (1 TAHUN PENUH) STANDAR KERTAS
                val fileNameBase = "Kalender_12_Bulan_${year}_${cleanPaper}"

                if (format.equals("PDF", ignoreCase = true)) {
                    // PDF 12 HALAMAN (Setiap bulan 1 halaman A4 beresolusi tinggi)
                    val pdfDocument = PdfDocument()

                    for (m in 1..12) {
                        val mName = MONTH_NAMES[m - 1]
                        val bitmapWidth = 1240
                        val bitmapHeight = 1754
                        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)

                        drawPrintableCalendarSheet(
                            canvas = canvas,
                            width = bitmapWidth,
                            height = bitmapHeight,
                            year = year,
                            month = m,
                            monthName = mName,
                            hijriOffset = hijriOffset,
                            holidays = completeHolidays,
                            agendas = agendas,
                            paperType = paperType,
                            pageInfoText = "Halaman $m dari 12"
                        )

                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, m).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val pdfCanvas = page.canvas

                        val margin = 16f
                        val availableWidth = 595f - (margin * 2f)
                        val availableHeight = 842f - (margin * 2f)
                        val scale = minOf(availableWidth / bitmapWidth, availableHeight / bitmapHeight)
                        val drawW = bitmapWidth * scale
                        val drawH = bitmapHeight * scale
                        val left = (595f - drawW) / 2f
                        val top = (842f - drawH) / 2f

                        pdfCanvas.drawColor(Color.WHITE)
                        val destRect = RectF(left, top, left + drawW, top + drawH)
                        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        pdfCanvas.drawBitmap(bitmap, null, destRect, filterPaint)
                        pdfDocument.finishPage(page)

                        bitmap.recycle()
                    }

                    val pdfFile = File(exportDir, "$fileNameBase.pdf")
                    FileOutputStream(pdfFile).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()

                    saveToPublicDownloads(context, pdfFile, "application/pdf")
                    Result.success(pdfFile)
                } else {
                    // PNG atau JPG 12 BULAN: Poster Kalender Dinding 1 Tahun Lengkap (2480 x 3508 px - High Res)
                    val isPng = format.equals("PNG", ignoreCase = true)
                    val ext = if (isPng) "png" else "jpg"
                    val mimeType = if (isPng) "image/png" else "image/jpeg"
                    val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val quality = if (isPng) 100 else 95

                    val posterWidth = 2480
                    val posterHeight = 3508
                    val bitmap = Bitmap.createBitmap(posterWidth, posterHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    draw12MonthPosterSheet(
                        canvas = canvas,
                        width = posterWidth,
                        height = posterHeight,
                        year = year,
                        hijriOffset = hijriOffset,
                        holidays = completeHolidays,
                        paperType = paperType
                    )

                    val imageFile = File(exportDir, "$fileNameBase.$ext")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(compressFormat, quality, out)
                    }
                    bitmap.recycle()

                    saveToPublicPictures(context, imageFile, mimeType)
                    Result.success(imageFile)
                }
            } else {
                // EXPORT 1 BULAN TUNGGAL
                val monthName = MONTH_NAMES.getOrElse(month - 1) { "Bulan" }
                val fileNameBase = "Kalender_${monthName}_${year}_${cleanPaper}"

                val bitmapWidth = 1240
                val bitmapHeight = 1754
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                drawPrintableCalendarSheet(
                    canvas = canvas,
                    width = bitmapWidth,
                    height = bitmapHeight,
                    year = year,
                    month = month,
                    monthName = monthName,
                    hijriOffset = hijriOffset,
                    holidays = completeHolidays,
                    agendas = agendas,
                    paperType = paperType,
                    pageInfoText = null
                )

                if (format.equals("PDF", ignoreCase = true)) {
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val pdfCanvas = page.canvas

                    val margin = 16f
                    val availableWidth = 595f - (margin * 2f)
                    val availableHeight = 842f - (margin * 2f)
                    val scale = minOf(availableWidth / bitmapWidth, availableHeight / bitmapHeight)
                    val drawW = bitmapWidth * scale
                    val drawH = bitmapHeight * scale
                    val left = (595f - drawW) / 2f
                    val top = (842f - drawH) / 2f

                    pdfCanvas.drawColor(Color.WHITE)
                    val destRect = RectF(left, top, left + drawW, top + drawH)
                    val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    pdfCanvas.drawBitmap(bitmap, null, destRect, filterPaint)
                    pdfDocument.finishPage(page)

                    val pdfFile = File(exportDir, "$fileNameBase.pdf")
                    FileOutputStream(pdfFile).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()
                    bitmap.recycle()

                    saveToPublicDownloads(context, pdfFile, "application/pdf")
                    Result.success(pdfFile)
                } else {
                    val isPng = format.equals("PNG", ignoreCase = true)
                    val ext = if (isPng) "png" else "jpg"
                    val mimeType = if (isPng) "image/png" else "image/jpeg"
                    val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val quality = if (isPng) 100 else 95

                    val imageFile = File(exportDir, "$fileNameBase.$ext")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(compressFormat, quality, out)
                    }
                    bitmap.recycle()

                    saveToPublicPictures(context, imageFile, mimeType)
                    Result.success(imageFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Render 1 lembar halaman kalender bulanan lengkap (A4 aspect)
     */
    private fun drawPrintableCalendarSheet(
        canvas: Canvas,
        width: Int,
        height: Int,
        year: Int,
        month: Int,
        monthName: String,
        hijriOffset: Int,
        holidays: Map<String, String>,
        agendas: List<AgendaItem>,
        paperType: String,
        pageInfoText: String? = null
    ) {
        // Background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer border
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 16f, 16f, paint)

        // Header Background Banner
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F172A") // Deep slate
        canvas.drawRoundRect(RectF(36f, 36f, width - 36f, 160f), 12f, 12f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 34f
        canvas.drawText("KALENDER $monthName $year".uppercase(Locale("id", "ID")), 60f, 95f, paint)

        // Header Subtitle & Paper Specification
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 17f
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("My Singghara • Kalender Nasional, Jawa & Hijriah", 60f, 132f, paint)

        // Paper badge on top right
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.parseColor("#38BDF8")
        val badgeText = "Kertas: $paperType"
        val badgeWidth = paint.measureText(badgeText)
        canvas.drawText(badgeText, width - 60f - badgeWidth, 115f, paint)

        // 2. Day Header Bar (7 columns)
        val gridLeft = 36f
        val gridRight = width - 36f
        val gridWidth = gridRight - gridLeft
        val colWidth = gridWidth / 7f
        val headerTop = 180f
        val headerHeight = 44f

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(RectF(gridLeft, headerTop, gridRight, headerTop + headerHeight), 8f, 8f, paint)

        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        for (i in 0..6) {
            val dName = DAYS_HEADER[i]
            paint.color = if (i == 0) Color.parseColor("#DC2626") else Color.parseColor("#334155")
            val textW = paint.measureText(dName)
            val cx = gridLeft + (i * colWidth) + (colWidth - textW) / 2f
            canvas.drawText(dName, cx, headerTop + 28f, paint)
        }

        // 3. Compute Calendar Cells
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val totalSlots = firstDayDayOfWeek + totalDaysInMonth
        val numWeeks = if (totalSlots > 35) 6 else 5
        val gridTop = headerTop + headerHeight + 8f
        val gridBottom = 960f
        val availableGridHeight = gridBottom - gridTop
        val rowHeight = availableGridHeight / numWeeks.toFloat()

        var currentDay = 1
        for (week in 0 until numWeeks) {
            val rowY = gridTop + (week * rowHeight)
            for (dayOfWeek in 0..6) {
                val colX = gridLeft + (dayOfWeek * colWidth)
                val slotIndex = week * 7 + dayOfWeek

                if (slotIndex >= firstDayDayOfWeek && currentDay <= totalDaysInMonth) {
                    val dateKey = String.format("%04d-%02d-%02d", year, month, currentDay)
                    val holidayName = holidays[dateKey]
                    val observanceName = HolidayClient.getObservanceForDate(month, currentDay)
                    val jawa = JavaneseCalendarHelper.calculateJawa(year, month, currentDay)
                    val hijri = HijriCalendarHelper.getHijriDate(year, month, currentDay, hijriOffset)
                    val dayAgendas = agendas.filter { it.tanggal == dateKey }
                    val isSunday = (dayOfWeek == 0)

                    // Cell Background & Border
                    val cellRect = RectF(colX + 3f, rowY + 3f, colX + colWidth - 3f, rowY + rowHeight - 3f)
                    paint.style = Paint.Style.FILL
                    paint.color = when {
                        holidayName != null -> Color.parseColor("#FEF2F2") // soft red
                        observanceName != null -> Color.parseColor("#F0FDF4") // soft green
                        else -> Color.parseColor("#FAFAFA")
                    }
                    canvas.drawRoundRect(cellRect, 8f, 8f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f
                    paint.color = when {
                        holidayName != null -> Color.parseColor("#FCA5A5")
                        observanceName != null -> Color.parseColor("#86EFAC")
                        else -> Color.parseColor("#E2E8F0")
                    }
                    canvas.drawRoundRect(cellRect, 8f, 8f, paint)

                    // Day Number (Large Bold)
                    paint.style = Paint.Style.FILL
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 28f
                    paint.color = when {
                        holidayName != null || isSunday -> Color.parseColor("#DC2626") // Red
                        observanceName != null -> Color.parseColor("#16A34A") // Green
                        else -> Color.parseColor("#0F172A")
                    }
                    canvas.drawText(currentDay.toString(), colX + 12f, rowY + 34f, paint)

                    // Javanese Pasaran (e.g. Legi/Pahing)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 13f
                    paint.color = Color.parseColor("#64748B")
                    val pasaranText = "${jawa.dina} ${jawa.pasaran}"
                    canvas.drawText(pasaranText, colX + 12f, rowY + 54f, paint)

                    // Hijri Date
                    paint.textSize = 11f
                    paint.color = Color.parseColor("#94A3B8")
                    val hijriShort = hijri.split(" ").take(2).joinToString(" ")
                    canvas.drawText(hijriShort, colX + 12f, rowY + 70f, paint)

                    // Indicators
                    if (holidayName != null) {
                        paint.color = Color.parseColor("#DC2626")
                        canvas.drawCircle(colX + colWidth - 14f, rowY + 16f, 4f, paint)
                    } else if (observanceName != null) {
                        paint.color = Color.parseColor("#16A34A")
                        canvas.drawCircle(colX + colWidth - 14f, rowY + 16f, 4f, paint)
                    }
                    if (dayAgendas.isNotEmpty()) {
                        paint.color = Color.parseColor("#EAB308")
                        canvas.drawCircle(colX + colWidth - 24f, rowY + 16f, 4f, paint)
                    }

                    currentDay++
                }
            }
        }

        // 4. Bottom Detail Sections (Holidays, Observances & Agendas)
        var cursorY = 980f

        // Filter Holidays for this month
        val monthHolidays = holidays.filter { (key, _) ->
            val parts = key.split("-")
            parts.size == 3 && parts[0].toIntOrNull() == year && parts[1].toIntOrNull() == month
        }.toList()

        // Filter Observances for this month
        val monthObservances = (1..totalDaysInMonth).mapNotNull { d ->
            val obs = HolidayClient.getObservanceForDate(month, d)
            if (obs != null) Pair(d, obs) else null
        }

        // Month Agendas
        val monthAgendas = agendas.filter {
            val parts = it.tanggal.split("-")
            parts.size == 3 && parts[0].toIntOrNull() == year && parts[1].toIntOrNull() == month
        }

        // Section A: Hari Libur Nasional & Cuti Bersama (Red)
        if (monthHolidays.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FEF2F2")
            val boxH = 40f + (monthHolidays.size * 26f)
            canvas.drawRoundRect(RectF(gridLeft, cursorY, gridRight, cursorY + boxH), 10f, 10f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#FCA5A5")
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(RectF(gridLeft, cursorY, gridRight, cursorY + boxH), 10f, 10f, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 15f
            paint.color = Color.parseColor("#DC2626")
            canvas.drawText("HARI LIBUR NASIONAL & CUTI BERSAMA (SKB 3 MENTERI)", gridLeft + 16f, cursorY + 26f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 14f
            var itemY = cursorY + 54f
            monthHolidays.forEach { (dateKey, name) ->
                val dayNum = dateKey.split("-").lastOrNull() ?: ""
                paint.color = Color.parseColor("#B91C1C")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("• $dayNum $monthName :", gridLeft + 20f, itemY, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = Color.parseColor("#450A0A")
                canvas.drawText(name, gridLeft + 160f, itemY, paint)
                itemY += 26f
            }
            cursorY += boxH + 16f
        }

        // Section B: Hari Peringatan & Hari Besar (Green)
        if (monthObservances.isNotEmpty() && cursorY < 1480f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F0FDF4")
            val maxItems = minOf(monthObservances.size, 8)
            val boxH = 40f + (maxItems * 24f)
            canvas.drawRoundRect(RectF(gridLeft, cursorY, gridRight, cursorY + boxH), 10f, 10f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#86EFAC")
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(RectF(gridLeft, cursorY, gridRight, cursorY + boxH), 10f, 10f, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 15f
            paint.color = Color.parseColor("#16A34A")
            canvas.drawText("HARI PERINGATAN & HARI BESAR NASIONAL", gridLeft + 16f, cursorY + 26f, paint)

            var itemY = cursorY + 52f
            monthObservances.take(maxItems).forEach { (d, name) ->
                paint.color = Color.parseColor("#15803D")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("• $d $monthName :", gridLeft + 20f, itemY, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = Color.parseColor("#14532D")
                canvas.drawText(name, gridLeft + 160f, itemY, paint)
                itemY += 24f
            }
            cursorY += boxH + 16f
        }

        // Section C: Agendas (if any and space allows)
        if (monthAgendas.isNotEmpty() && cursorY < 1580f) {
            val maxAgendas = minOf(monthAgendas.size, 4)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F8FAFC")
            val boxH = 36f + (maxAgendas * 24f)
            canvas.drawRoundRect(RectF(gridLeft, cursorY, gridRight, cursorY + boxH), 10f, 10f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#CBD5E1")
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(RectF(gridLeft, cursorY, gridRight, cursorY + boxH), 10f, 10f, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14f
            paint.color = Color.parseColor("#334155")
            canvas.drawText("AGENDA BULAN INI", gridLeft + 16f, cursorY + 24f, paint)

            var itemY = cursorY + 48f
            monthAgendas.take(maxAgendas).forEach { ag ->
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 13f
                paint.color = Color.parseColor("#0F172A")
                canvas.drawText("• ${ag.tanggal} : ${ag.judul}", gridLeft + 20f, itemY, paint)
                itemY += 24f
            }
            cursorY += boxH + 16f
        }

        // Footer Brand & Print Notice
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = if (pageInfoText != null) {
            "Dokumen Kalender Resmi • $pageInfoText • My Singghara • Kertas: $paperType"
        } else {
            "Dokumen Kalender Resmi • Dibuat Otomatis oleh My Singghara • Format Kertas: $paperType"
        }
        val fW = paint.measureText(footerText)
        canvas.drawText(footerText, (width - fW) / 2f, height - 42f, paint)
    }

    /**
     * Render Poster Kalender Dinding 12 Bulan Lengkap (1 Lembar Annual Poster - 2480 x 3508 px)
     */
    private fun draw12MonthPosterSheet(
        canvas: Canvas,
        width: Int,
        height: Int,
        year: Int,
        hijriOffset: Int,
        holidays: Map<String, String>,
        paperType: String
    ) {
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer Frame Border
        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 24f, 24f, paint)

        // 1. Header Banner
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRoundRect(RectF(60f, 60f, width - 60f, 250f), 20f, 20f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 62f
        canvas.drawText("KALENDER TAHUN $year", 100f, 150f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 28f
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("My Singghara • Lengkap Penanggalan Nasional, Pasaran Jawa & Hijriah", 100f, 205f, paint)

        // Badge Spesifikasi Kertas
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.parseColor("#38BDF8")
        val badgeText = "Kertas Cetak: $paperType"
        val badgeW = paint.measureText(badgeText)
        canvas.drawText(badgeText, width - 100f - badgeW, 165f, paint)

        // 2. 12-Month Grid (3 Columns x 4 Rows)
        val gridLeft = 60f
        val gridRight = width - 60f
        val gridTop = 280f
        val gridBottom = 2600f
        val totalGridWidth = gridRight - gridLeft
        val totalGridHeight = gridBottom - gridTop

        val colSpacing = 28f
        val rowSpacing = 28f
        val panelWidth = (totalGridWidth - (colSpacing * 2f)) / 3f
        val panelHeight = (totalGridHeight - (rowSpacing * 3f)) / 4f

        for (m in 1..12) {
            val colIdx = (m - 1) % 3
            val rowIdx = (m - 1) / 3
            val panelLeft = gridLeft + (colIdx * (panelWidth + colSpacing))
            val panelTop = gridTop + (rowIdx * (panelHeight + rowSpacing))
            val panelRight = panelLeft + panelWidth
            val panelBottom = panelTop + panelHeight

            drawMonthMiniPanel(
                canvas = canvas,
                left = panelLeft,
                top = panelTop,
                right = panelRight,
                bottom = panelBottom,
                year = year,
                month = m,
                monthName = MONTH_NAMES[m - 1],
                hijriOffset = hijriOffset,
                holidays = holidays
            )
        }

        // 3. Bottom Section: Daftar Hari Libur Nasional 12 Bulan (SKB 3 Menteri)
        val bottomSectionTop = 2630f
        val bottomSectionBottom = height - 100f

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FEF2F2")
        canvas.drawRoundRect(RectF(gridLeft, bottomSectionTop, gridRight, bottomSectionBottom), 16f, 16f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#FCA5A5")
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(gridLeft, bottomSectionTop, gridRight, bottomSectionBottom), 16f, 16f, paint)

        // Section Title
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        paint.color = Color.parseColor("#DC2626")
        canvas.drawText("DAFTAR HARI LIBUR NASIONAL $year (SKB 3 MENTERI & KALENDER RESMI)", gridLeft + 30f, bottomSectionTop + 45f, paint)

        // Sorted List of Holidays
        val allYearHolidays = holidays.filter { (k, _) ->
            k.startsWith("$year-")
        }.toList().sortedBy { it.first }

        val halfSize = (allYearHolidays.size + 1) / 2
        val col1Holidays = allYearHolidays.take(halfSize)
        val col2Holidays = allYearHolidays.drop(halfSize)

        paint.textSize = 20f
        var yPos1 = bottomSectionTop + 85f
        col1Holidays.forEach { (dateKey, name) ->
            val parts = dateKey.split("-")
            val mIdx = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
            val dStr = parts.getOrNull(2) ?: ""
            val mStr = MONTH_NAMES.getOrElse(mIdx) { "" }

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#B91C1C")
            canvas.drawText("• $dStr $mStr :", gridLeft + 30f, yPos1, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#450A0A")
            canvas.drawText(name, gridLeft + 250f, yPos1, paint)
            yPos1 += 34f
        }

        var yPos2 = bottomSectionTop + 85f
        val col2Left = gridLeft + (totalGridWidth / 2f) + 20f
        col2Holidays.forEach { (dateKey, name) ->
            val parts = dateKey.split("-")
            val mIdx = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
            val dStr = parts.getOrNull(2) ?: ""
            val mStr = MONTH_NAMES.getOrElse(mIdx) { "" }

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#B91C1C")
            canvas.drawText("• $dStr $mStr :", col2Left, yPos2, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#450A0A")
            canvas.drawText(name, col2Left + 220f, yPos2, paint)
            yPos2 += 34f
        }

        // Footer Notice
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = "Dokumen Poster Kalender Resmi 12 Bulan • My Singghara • Rekomendasi Cetak: $paperType"
        val fw = paint.measureText(footerText)
        canvas.drawText(footerText, (width - fw) / 2f, height - 50f, paint)
    }

    /**
     * Render satu kotak bulan pada poster tahunan 12 bulan
     */
    private fun drawMonthMiniPanel(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        year: Int,
        month: Int,
        monthName: String,
        hijriOffset: Int,
        holidays: Map<String, String>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background Panel
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFFFFF")
        canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, paint)

        // Panel Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, paint)

        // Month Header Strip
        val headerH = 46f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(RectF(left, top, right, top + headerH), 12f, 12f, paint)
        canvas.drawRect(RectF(left, top + headerH - 8f, right, top + headerH), paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText("${month}. ${monthName.uppercase(Locale("id", "ID"))}", left + 16f, top + 32f, paint)

        // Day of Week Header Row
        val dayBarTop = top + headerH
        val dayBarH = 30f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(RectF(left, dayBarTop, right, dayBarTop + dayBarH), paint)

        val colW = (right - left) / 7f
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        for (i in 0..6) {
            val dName = DAYS_HEADER_SHORT[i]
            paint.color = if (i == 0) Color.parseColor("#DC2626") else Color.parseColor("#475569")
            val tw = paint.measureText(dName)
            val cx = left + (i * colW) + (colW - tw) / 2f
            canvas.drawText(dName, cx, dayBarTop + 20f, paint)
        }

        // Calendar Calculation
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val totalSlots = firstDayDayOfWeek + totalDaysInMonth
        val numWeeks = if (totalSlots > 35) 6 else 5
        val gridY = dayBarTop + dayBarH + 4f
        val availableH = bottom - gridY - 6f
        val rowH = availableH / numWeeks.toFloat()

        var currentDay = 1
        for (week in 0 until numWeeks) {
            val rowTop = gridY + (week * rowH)
            for (dIdx in 0..6) {
                val colX = left + (dIdx * colW)
                val slotIdx = week * 7 + dIdx

                if (slotIdx >= firstDayDayOfWeek && currentDay <= totalDaysInMonth) {
                    val dateKey = String.format("%04d-%02d-%02d", year, month, currentDay)
                    val holidayName = holidays[dateKey]
                    val observanceName = HolidayClient.getObservanceForDate(month, currentDay)
                    val jawa = JavaneseCalendarHelper.calculateJawa(year, month, currentDay)
                    val isSunday = (dIdx == 0)

                    // Cell Day Number
                    paint.style = Paint.Style.FILL
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 20f
                    paint.color = when {
                        holidayName != null || isSunday -> Color.parseColor("#DC2626")
                        observanceName != null -> Color.parseColor("#16A34A")
                        else -> Color.parseColor("#0F172A")
                    }
                    val numStr = currentDay.toString()
                    val nW = paint.measureText(numStr)
                    val nX = colX + (colW - nW) / 2f
                    canvas.drawText(numStr, nX, rowTop + 24f, paint)

                    // Pasaran Initial / Short
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 11f
                    paint.color = when {
                        holidayName != null || isSunday -> Color.parseColor("#EF4444")
                        observanceName != null -> Color.parseColor("#22C55E")
                        else -> Color.parseColor("#64748B")
                    }
                    val pasaranShort = jawa.pasaran.take(3)
                    val pW = paint.measureText(pasaranShort)
                    val pX = colX + (colW - pW) / 2f
                    canvas.drawText(pasaranShort, pX, rowTop + 40f, paint)

                    // Dot Indicator for Holiday / Observance
                    if (holidayName != null) {
                        paint.color = Color.parseColor("#DC2626")
                        canvas.drawCircle(colX + colW - 8f, rowTop + 10f, 3f, paint)
                    } else if (observanceName != null) {
                        paint.color = Color.parseColor("#16A34A")
                        canvas.drawCircle(colX + colW - 8f, rowTop + 10f, 3f, paint)
                    }

                    currentDay++
                }
            }
        }
    }

    /**
     * Render Kalender 1 Bulan format Kartu ATM / IC Card Ukuran Dompet (1012 x 638 px)
     */
    fun drawAtmCardMonthSheet(
        canvas: Canvas,
        width: Int,
        height: Int,
        year: Int,
        month: Int,
        monthName: String,
        hijriOffset: Int,
        holidays: Map<String, String>,
        agendas: List<AgendaItem>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        val cardRect = RectF(16f, 16f, width - 16f, height - 16f)
        val cornerRadius = 32f

        // Card Base Background (Navy Slate Metal)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        // Card Border Accent (Sky Cyan)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        // 1. Smart Gold IC Chip
        drawGoldSmartChip(canvas, 36f, 32f, 96f, 68f)

        // 2. Contactless Icon
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#38BDF8")
        val cxWave = 154f
        val cyWave = 66f
        canvas.drawArc(RectF(cxWave - 14f, cyWave - 14f, cxWave + 14f, cyWave + 14f), -45f, 90f, false, paint)
        canvas.drawArc(RectF(cxWave - 22f, cyWave - 22f, cxWave + 22f, cyWave + 22f), -45f, 90f, false, paint)
        canvas.drawArc(RectF(cxWave - 30f, cyWave - 30f, cxWave + 30f, cyWave + 30f), -45f, 90f, false, paint)

        // 3. Brand & Subtitle
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText("MY SINGGHARA", 200f, 54f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("PASARAN JAWA & HIJRIAH • WALLET POCKET CARD", 200f, 74f, paint)

        // 4. Month & Year Title (Top Right)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        paint.color = Color.parseColor("#FBBF24")
        val titleText = "${monthName.uppercase(Locale("id", "ID"))} $year"
        val tw = paint.measureText(titleText)
        canvas.drawText(titleText, width - 36f - tw, 62f, paint)

        // 5. Day Header
        val gridLeft = 36f
        val gridRight = width - 36f
        val gridW = gridRight - gridLeft
        val colW = gridW / 7f
        val dayBarTop = 112f
        val dayBarH = 28f

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(RectF(gridLeft, dayBarTop, gridRight, dayBarTop + dayBarH), 8f, 8f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        for (i in 0..6) {
            val dName = DAYS_HEADER_SHORT[i]
            paint.color = if (i == 0) Color.parseColor("#EF4444") else Color.parseColor("#E2E8F0")
            val dw = paint.measureText(dName)
            val dx = gridLeft + (i * colW) + (colW - dw) / 2f
            canvas.drawText(dName, dx, dayBarTop + 19f, paint)
        }

        // 6. Calendar Days Grid
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDay = cal.get(Calendar.DAY_OF_WEEK) - 1
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val numWeeks = if (firstDay + totalDays > 35) 6 else 5

        val gridTop = dayBarTop + dayBarH + 6f
        val gridBottom = height - 76f
        val rowH = (gridBottom - gridTop) / numWeeks.toFloat()

        var currentDay = 1
        for (week in 0 until numWeeks) {
            val rowY = gridTop + (week * rowH)
            for (dIdx in 0..6) {
                val colX = gridLeft + (dIdx * colW)
                val slotIdx = week * 7 + dIdx

                if (slotIdx >= firstDay && currentDay <= totalDays) {
                    val dateKey = String.format("%04d-%02d-%02d", year, month, currentDay)
                    val holidayName = holidays[dateKey]
                    val observanceName = HolidayClient.getObservanceForDate(month, currentDay)
                    val jawa = JavaneseCalendarHelper.calculateJawa(year, month, currentDay)
                    val isSunday = (dIdx == 0)

                    val cellRect = RectF(colX + 2f, rowY + 2f, colX + colW - 2f, rowY + rowH - 2f)

                    paint.style = Paint.Style.FILL
                    paint.color = when {
                        holidayName != null -> Color.parseColor("#450A0A")
                        observanceName != null -> Color.parseColor("#052E16")
                        isSunday -> Color.parseColor("#2D1515")
                        else -> Color.parseColor("#1E293B")
                    }
                    canvas.drawRoundRect(cellRect, 6f, 6f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    paint.color = when {
                        holidayName != null -> Color.parseColor("#EF4444")
                        observanceName != null -> Color.parseColor("#22C55E")
                        else -> Color.parseColor("#334155")
                    }
                    canvas.drawRoundRect(cellRect, 6f, 6f, paint)

                    // Day Number
                    paint.style = Paint.Style.FILL
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 20f
                    paint.color = when {
                        holidayName != null || isSunday -> Color.parseColor("#F87171")
                        observanceName != null -> Color.parseColor("#4ADE80")
                        else -> Color.WHITE
                    }
                    canvas.drawText(currentDay.toString(), colX + 8f, rowY + 23f, paint)

                    // Pasaran
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 10f
                    paint.color = Color.parseColor("#94A3B8")
                    val pasaranShort = jawa.pasaran.take(3)
                    canvas.drawText(pasaranShort, colX + 8f, rowY + 40f, paint)

                    // Indicators
                    if (holidayName != null) {
                        paint.color = Color.parseColor("#EF4444")
                        canvas.drawCircle(colX + colW - 8f, rowY + 10f, 3.5f, paint)
                    } else if (observanceName != null) {
                        paint.color = Color.parseColor("#22C55E")
                        canvas.drawCircle(colX + colW - 8f, rowY + 10f, 3.5f, paint)
                    }

                    currentDay++
                }
            }
        }

        // 7. Footer
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 12f
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("••••  8560  5400  $year", 36f, height - 34f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 11f
        paint.color = Color.parseColor("#38BDF8")
        val walletText = "CR-80 IC CARD • 85.6 x 54 mm"
        val ww = paint.measureText(walletText)
        canvas.drawText(walletText, width - 36f - ww, height - 34f, paint)
    }

    /**
     * Render Sisi Depan Kartu ATM (Semester I: Jan - Jun)
     */
    fun draw12MonthAtmCardSideA(
        canvas: Canvas,
        width: Int,
        height: Int,
        year: Int,
        hijriOffset: Int,
        holidays: Map<String, String>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        val cardRect = RectF(16f, 16f, width - 16f, height - 16f)
        val cornerRadius = 32f

        // Card Base
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0B192C")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        // Gold IC Chip
        drawGoldSmartChip(canvas, 36f, 28f, 80f, 56f)

        // Contactless Icon
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.parseColor("#38BDF8")
        val cxWave = 136f
        val cyWave = 56f
        canvas.drawArc(RectF(cxWave - 10f, cyWave - 10f, cxWave + 10f, cyWave + 10f), -45f, 90f, false, paint)
        canvas.drawArc(RectF(cxWave - 16f, cyWave - 16f, cxWave + 16f, cyWave + 16f), -45f, 90f, false, paint)

        // Title Header
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 19f
        canvas.drawText("MY SINGGHARA", 170f, 48f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawText("SEMESTER I (JAN – JUN) • KALENDER DOMPET", 170f, 68f, paint)

        // Year Gold Top Right
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        paint.color = Color.parseColor("#FBBF24")
        val yrText = "$year"
        val yw = paint.measureText(yrText)
        canvas.drawText(yrText, width - 36f - yw, 58f, paint)

        // 6 Month Mini Grid: 3 Columns x 2 Rows (Months 1 to 6)
        val gridLeft = 32f
        val gridRight = width - 32f
        val gridTop = 96f
        val gridBottom = height - 48f

        val totalW = gridRight - gridLeft
        val totalH = gridBottom - gridTop
        val colGap = 12f
        val rowGap = 10f
        val colW = (totalW - (colGap * 2f)) / 3f
        val rowH = (totalH - rowGap) / 2f

        for (m in 1..6) {
            val cIdx = (m - 1) % 3
            val rIdx = (m - 1) / 3
            val pLeft = gridLeft + (cIdx * (colW + colGap))
            val pTop = gridTop + (rIdx * (rowH + rowGap))

            drawAtmMonthMiniGrid(
                canvas = canvas,
                left = pLeft,
                top = pTop,
                right = pLeft + colW,
                bottom = pTop + rowH,
                year = year,
                month = m,
                monthName = MONTH_NAMES[m - 1],
                hijriOffset = hijriOffset,
                holidays = holidays
            )
        }

        // Footer
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("MUKA DEPAN • STANDARD ATM/IC CARD (85.6 x 54 mm)", 36f, height - 24f, paint)
    }

    /**
     * Render Sisi Belakang Kartu ATM (Semester II: Jul - Des)
     */
    fun draw12MonthAtmCardSideB(
        canvas: Canvas,
        width: Int,
        height: Int,
        year: Int,
        hijriOffset: Int,
        holidays: Map<String, String>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        val cardRect = RectF(16f, 16f, width - 16f, height - 16f)
        val cornerRadius = 32f

        // Card Base (Dark Slate)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = Color.parseColor("#F59E0B")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        // Magnetic Stripe along the top
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#020617")
        canvas.drawRect(RectF(16f, 32f, width - 16f, 80f), paint)

        // Glossy line on magnetic stripe
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRect(RectF(16f, 54f, width - 16f, 58f), paint)

        // Signature Strip & Title
        val sigTop = 90f
        val sigHeight = 32f
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(RectF(36f, sigTop, width - 36f, sigTop + sigHeight), 6f, 6f, paint)

        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText("MY SINGGHARA • SEMESTER II (JUL – DES) • $year", 50f, sigTop + 21f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        paint.color = Color.parseColor("#D97706")
        val holoText = "AUTHENTIC WALLET CARD"
        val hw = paint.measureText(holoText)
        canvas.drawText(holoText, width - 50f - hw, sigTop + 21f, paint)

        // 6 Month Mini Grid: 3 Columns x 2 Rows (Months 7 to 12)
        val gridLeft = 32f
        val gridRight = width - 32f
        val gridTop = 130f
        val gridBottom = height - 48f

        val totalW = gridRight - gridLeft
        val totalH = gridBottom - gridTop
        val colGap = 12f
        val rowGap = 10f
        val colW = (totalW - (colGap * 2f)) / 3f
        val rowH = (totalH - rowGap) / 2f

        for (m in 7..12) {
            val cIdx = (m - 7) % 3
            val rIdx = (m - 7) / 3
            val pLeft = gridLeft + (cIdx * (colW + colGap))
            val pTop = gridTop + (rIdx * (rowH + rowGap))

            drawAtmMonthMiniGrid(
                canvas = canvas,
                left = pLeft,
                top = pTop,
                right = pLeft + colW,
                bottom = pTop + rowH,
                year = year,
                month = m,
                monthName = MONTH_NAMES[m - 1],
                hijriOffset = hijriOffset,
                holidays = holidays
            )
        }

        // Footer
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("MUKA BELAKANG • LENGKAP LIBUR NASIONAL & PASARAN JAWA", 36f, height - 24f, paint)
    }

    /**
     * Render Lembar Cetak Kartu ATM 2 Sisi Lengkap dengan Garis Lipat/Potong (1012 x 1340 px)
     */
    fun draw12MonthAtmCardPrintSheet(
        canvas: Canvas,
        width: Int,
        height: Int,
        year: Int,
        hijriOffset: Int,
        holidays: Map<String, String>
    ) {
        canvas.drawColor(Color.WHITE)

        val cardH = 638
        val bmpA = Bitmap.createBitmap(width, cardH, Bitmap.Config.ARGB_8888)
        val canvasA = Canvas(bmpA)
        draw12MonthAtmCardSideA(canvasA, width, cardH, year, hijriOffset, holidays)

        val bmpB = Bitmap.createBitmap(width, cardH, Bitmap.Config.ARGB_8888)
        val canvasB = Canvas(bmpB)
        draw12MonthAtmCardSideB(canvasB, width, cardH, year, hijriOffset, holidays)

        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Draw Sisi Depan
        canvas.drawBitmap(bmpA, 0f, 10f, filterPaint)

        // Draw Divider with Cut/Fold Guideline
        val dividerY = 660f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#94A3B8")
        paint.strokeWidth = 2f
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
        canvas.drawLine(24f, dividerY, width - 24f, dividerY, paint)

        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        paint.color = Color.parseColor("#475569")
        val cutNotice = "✂  Garis Lipat / Gunting untuk Kartu Dompet (85.6 x 54 mm)  ✂"
        val nw = paint.measureText(cutNotice)
        canvas.drawText(cutNotice, (width - nw) / 2f, dividerY - 8f, paint)

        // Draw Sisi Belakang
        canvas.drawBitmap(bmpB, 0f, dividerY + 20f, filterPaint)

        bmpA.recycle()
        bmpB.recycle()
    }

    /**
     * Render satu kotak mini bulan pada Kartu ATM (High-density wallet layout)
     */
    private fun drawAtmMonthMiniGrid(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        year: Int,
        month: Int,
        monthName: String,
        hijriOffset: Int,
        holidays: Map<String, String>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Mini Card Background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(RectF(left, top, right, bottom), 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(RectF(left, top, right, bottom), 8f, 8f, paint)

        // Month Title Header
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0284C7")
        canvas.drawRoundRect(RectF(left, top, right, top + 22f), 8f, 8f, paint)
        canvas.drawRect(RectF(left, top + 14f, right, top + 22f), paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("${month}. ${monthName.uppercase(Locale("id", "ID"))}", left + 8f, top + 16f, paint)

        // Day of Week Header Row
        val dayBarTop = top + 24f
        val colW = (right - left) / 7f
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val shortDays = listOf("M", "S", "S", "R", "K", "J", "S")
        for (i in 0..6) {
            val dName = shortDays[i]
            paint.color = if (i == 0) Color.parseColor("#F87171") else Color.parseColor("#94A3B8")
            val tw = paint.measureText(dName)
            val cx = left + (i * colW) + (colW - tw) / 2f
            canvas.drawText(dName, cx, dayBarTop + 10f, paint)
        }

        // Compute Grid
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDay = cal.get(Calendar.DAY_OF_WEEK) - 1
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val numWeeks = if (firstDay + totalDays > 35) 6 else 5

        val gridY = dayBarTop + 14f
        val availableH = bottom - gridY - 2f
        val rowH = availableH / numWeeks.toFloat()

        var currentDay = 1
        for (week in 0 until numWeeks) {
            val rowTop = gridY + (week * rowH)
            for (dIdx in 0..6) {
                val colX = left + (dIdx * colW)
                val slotIdx = week * 7 + dIdx

                if (slotIdx >= firstDay && currentDay <= totalDays) {
                    val dateKey = String.format("%04d-%02d-%02d", year, month, currentDay)
                    val holidayName = holidays[dateKey]
                    val isSunday = (dIdx == 0)

                    // Day Number
                    paint.style = Paint.Style.FILL
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 10f
                    paint.color = when {
                        holidayName != null || isSunday -> Color.parseColor("#F87171")
                        else -> Color.WHITE
                    }
                    val numStr = currentDay.toString()
                    val nw = paint.measureText(numStr)
                    val nx = colX + (colW - nw) / 2f
                    canvas.drawText(numStr, nx, rowTop + 11f, paint)

                    currentDay++
                }
            }
        }
    }

    /**
     * Draw Realistic Gold Smart IC Chip (EMV Contact Chip)
     */
    private fun drawGoldSmartChip(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val chipRect = RectF(left, top, left + width, top + height)

        // Gold Base
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F59E0B")
        canvas.drawRoundRect(chipRect, 8f, 8f, paint)

        // Gold Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.parseColor("#D97706")
        canvas.drawRoundRect(chipRect, 8f, 8f, paint)

        // Contact Pad Segments
        paint.color = Color.parseColor("#B45309")
        paint.strokeWidth = 1.2f

        val midX = left + (width / 2f)
        val midY = top + (height / 2f)
        // Center rectangle
        val padW = width * 0.38f
        val padH = height * 0.45f
        canvas.drawRoundRect(RectF(midX - padW / 2f, midY - padH / 2f, midX + padW / 2f, midY + padH / 2f), 4f, 4f, paint)

        // Segment lines
        canvas.drawLine(left, midY, midX - padW / 2f, midY, paint)
        canvas.drawLine(midX + padW / 2f, midY, left + width, midY, paint)
        canvas.drawLine(midX, top, midX, midY - padH / 2f, paint)
        canvas.drawLine(midX, midY + padH / 2f, midX, top + height, paint)
    }

    private fun saveToPublicDownloads(context: Context, srcFile: File, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, srcFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MySingghara")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveToPublicPictures(context: Context, srcFile: File, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, srcFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MySingghara")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareCalendarFile(context: Context, file: File, mimeType: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

