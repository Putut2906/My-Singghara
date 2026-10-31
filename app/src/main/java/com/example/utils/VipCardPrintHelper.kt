package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.MainViewModel
import java.io.File
import java.io.FileOutputStream

enum class VipPrintMode {
    MEMBER_SIDE, // Sisi Belakang / Data Anggota
    FRONT_SIDE,  // Sisi Depan / VIP Exclusive Access
    SHEET_BOTH   // Lembar Cetak Dua Sisi (Depan & Belakang Berdampingan)
}

object VipCardPrintHelper {

    private data class TierColors(
        val bgPrimary: Int,
        val bgSecondary: Int,
        val plateColor: Int,
        val accentGoldStart: Int,
        val accentGoldEnd: Int,
        val starColor: Int,
        val tierTitle: String,
        val durationLabel: String
    )

    private fun getTierColors(durationMonths: Int): TierColors {
        return when (durationMonths) {
            1 -> TierColors(
                bgPrimary = Color.rgb(18, 22, 28),
                bgSecondary = Color.rgb(38, 45, 58),
                plateColor = Color.rgb(10, 14, 20),
                accentGoldStart = Color.rgb(241, 245, 249),
                accentGoldEnd = Color.rgb(148, 163, 184),
                starColor = Color.rgb(226, 232, 240),
                tierTitle = "PLATINUM VIP",
                durationLabel = "1 Bulan"
            )
            3 -> TierColors(
                bgPrimary = Color.rgb(32, 19, 14),
                bgSecondary = Color.rgb(65, 38, 26),
                plateColor = Color.rgb(18, 10, 7),
                accentGoldStart = Color.rgb(253, 186, 116),
                accentGoldEnd = Color.rgb(194, 65, 12),
                starColor = Color.rgb(251, 146, 60),
                tierTitle = "BRONZE PRESTIGE",
                durationLabel = "3 Bulan"
            )
            5 -> TierColors(
                bgPrimary = Color.rgb(6, 28, 20),
                bgSecondary = Color.rgb(16, 64, 46),
                plateColor = Color.rgb(3, 16, 11),
                accentGoldStart = Color.rgb(110, 231, 183),
                accentGoldEnd = Color.rgb(5, 150, 105),
                starColor = Color.rgb(52, 211, 153),
                tierTitle = "EMERALD VIP",
                durationLabel = "5 Bulan"
            )
            7 -> TierColors(
                bgPrimary = Color.rgb(28, 10, 36),
                bgSecondary = Color.rgb(68, 24, 88),
                plateColor = Color.rgb(16, 5, 21),
                accentGoldStart = Color.rgb(244, 114, 182),
                accentGoldEnd = Color.rgb(147, 51, 234),
                starColor = Color.rgb(232, 121, 249),
                tierTitle = "ROYAL VIP",
                durationLabel = "7 Bulan"
            )
            else -> TierColors( // 12 Bulan / Gold
                bgPrimary = Color.rgb(18, 14, 8),
                bgSecondary = Color.rgb(44, 34, 16),
                plateColor = Color.rgb(10, 8, 4),
                accentGoldStart = Color.rgb(255, 236, 139),
                accentGoldEnd = Color.rgb(212, 160, 23),
                starColor = Color.rgb(255, 215, 0),
                tierTitle = "24K GOLD MASTER VIP",
                durationLabel = "12 Bulan"
            )
        }
    }

    private fun formatMemberId(rawUid: String): String {
        if (rawUid.isBlank()) return "0219-2129-1284"
        val clean = rawUid.filter { it.isLetterOrDigit() }.uppercase()
        val padded = if (clean.length < 12) clean.padEnd(12, 'X') else clean
        return "${padded.substring(0, 4)}-${padded.substring(4, 8)}-${padded.substring(8, 12)}"
    }

    /**
     * Menghasilkan Bitmap resolusi tinggi (1200 x 756 px, rasio standar CR80 ID Card)
     */
    fun drawSingleCard(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        adminPhone: String,
        isFront: Boolean,
        userEmail: String = ""
    ): Bitmap {
        val width = 1200
        val height = 756
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = getTierColors(durationMonths)

        // 1. Base Radial Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.4f, width * 0.8f,
                colors.bgSecondary, colors.bgPrimary, Shader.TileMode.CLAMP
            )
        }
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(cardRect, 36f, 36f, bgPaint)

        // 2. Texture Mesh Dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(16, 255, 255, 255)
        }
        val spacing = 28f
        var y = 0f
        var row = 0
        while (y < height) {
            val xOffset = if (row % 2 == 0) 0f else spacing / 2f
            var x = xOffset
            while (x < width) {
                canvas.drawCircle(x, y, 2.5f, dotPaint)
                x += spacing
            }
            y += spacing * 0.75f
            row++
        }

        // 3. Angular Beveled Plates (Top Left & Bottom Right)
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.plateColor
            style = Paint.Style.FILL
        }

        // Top-Left Plate
        val topPlate = android.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(width * 0.44f, 0f)
            lineTo(width * 0.34f, height * 0.22f)
            lineTo(width * 0.12f, height * 0.22f)
            lineTo(0f, height * 0.38f)
            close()
        }
        canvas.drawPath(topPlate, platePaint)

        // Bottom Plate
        val bottomPlate = android.graphics.Path().apply {
            moveTo(0f, height * 0.74f)
            lineTo(width * 0.38f, height * 0.74f)
            lineTo(width * 0.58f, height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        canvas.drawPath(bottomPlate, platePaint)

        // Outer Metallic Rounded Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(2f, 2f, width - 2f, height - 2f), 36f, 36f, borderPaint)

        if (isFront) {
            // ================= FRONT SIDE (VIP EXCLUSIVE ACCESS) =================
            // Chamfered Center Badge
            val badgeW = width * 0.70f
            val badgeH = 180f
            val badgeX = (width - badgeW) / 2f
            val badgeY = (height - badgeH) / 2f
            val cut = 32f

            val chamferPath = android.graphics.Path().apply {
                moveTo(badgeX + cut, badgeY)
                lineTo(badgeX + badgeW, badgeY)
                lineTo(badgeX + badgeW, badgeY + badgeH - cut)
                lineTo(badgeX + badgeW - cut, badgeY + badgeH)
                lineTo(badgeX, badgeY + badgeH)
                lineTo(badgeX, badgeY + cut)
                close()
            }

            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawPath(chamferPath, badgeBgPaint)

            val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                shader = LinearGradient(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH,
                    colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(chamferPath, badgeStrokePaint)

            // VIP Title di Kiri Badge
            val vipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 86f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                shader = LinearGradient(badgeX, badgeY, badgeX + 220f, badgeY + 120f,
                    colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawText("VIP", badgeX + 44f, badgeY + 115f, vipPaint)

            // Subtitle "LOGIN CEPAT" (Menggantikan title "EXCLUSIVE ACCESS")
            val quickLoginSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.15f
                color = colors.accentGoldStart
            }
            canvas.drawText("LOGIN CEPAT", badgeX + 46f, badgeY + 155f, quickLoginSubPaint)

            // QR Code Login Cepat di Kanan Badge
            val effectivePassword = com.example.firebase.FirebaseAuthManager.getSavedPasswordForEmail(context, userEmail).ifBlank {
                val googleId = userEmail.hashCode().toString()
                "GAuth#${googleId.take(10)}_${userEmail.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"
            }
            val quickLoginPayload = VipMembershipCardGenerator.buildQuickLoginPayload(
                email = userEmail.ifBlank { "member@singghara.com" },
                password = effectivePassword
            )
            val qrCodeBitmap = QrCodeHelper.generateQrBitmap(quickLoginPayload, 260)
            val qrSize = 145f
            val qrLeft = badgeX + badgeW - qrSize - 36f
            val qrTop = badgeY + (badgeH - qrSize) / 2f

            val qrBgRect = RectF(qrLeft - 6f, qrTop - 6f, qrLeft + qrSize + 6f, qrTop + qrSize + 6f)
            val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(qrBgRect, 14f, 14f, qrBgPaint)

            if (qrCodeBitmap != null) {
                val destRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
                canvas.drawBitmap(qrCodeBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }

            // Top Right Duration Badge
            val durationText = colors.durationLabel
            val durPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = colors.accentGoldStart
            }
            canvas.drawText(durationText, width - 160f, 60f, durPaint)

            // Bottom Brand
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.argb(160, 255, 255, 255)
                letterSpacing = 0.2f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("MY SINGGHARA OFFICIAL MEMBERSHIP", width * 0.5f, height - 44f, brandPaint)

        } else {
            // ================= MEMBER SIDE (DATA ANGGOTA & LOGO APLIKASI) =================
            // 1. 5 Golden Stars at Top Left
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.starColor
                textSize = 34f
            }
            var starX = 54f
            val starY = 82f
            for (i in 0 until 5) {
                drawStar(canvas, starX, starY, 18f, colors.starColor)
                starX += 42f
            }

            // 2. Logo / Icon Aplikasi My Singghara di bawah icon bintang
            val appIconSize = 88f
            val iconX = 54f
            val iconY = 114f

            try {
                // Decode icon aplikasi My Singghara
                val rawIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
                    ?: BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
                if (rawIcon != null) {
                    val scaledIcon = Bitmap.createScaledBitmap(rawIcon, appIconSize.toInt(), appIconSize.toInt(), true)
                    val iconRect = RectF(iconX, iconY, iconX + appIconSize, iconY + appIconSize)

                    // Background & Border untuk Icon Aplikasi
                    val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(180, 0, 0, 0)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(iconRect, 18f, 18f, iconBgPaint)

                    // Draw rounded icon
                    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val roundPath = android.graphics.Path().apply {
                        addRoundRect(iconRect, 18f, 18f, android.graphics.Path.Direction.CW)
                    }
                    canvas.save()
                    canvas.clipPath(roundPath)
                    canvas.drawBitmap(scaledIcon, iconX, iconY, iconPaint)
                    canvas.restore()

                    val iconBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = colors.accentGoldEnd
                    }
                    canvas.drawRoundRect(iconRect, 18f, 18f, iconBorderPaint)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Label "MY SINGGHARA OFFICIAL MEMBER" di samping logo
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.1f
                shader = LinearGradient(iconX + appIconSize + 20f, iconY, iconX + appIconSize + 300f, iconY + 50f,
                    colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawText("MY SINGGHARA", iconX + appIconSize + 22f, iconY + 40f, titlePaint)

            val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.12f
                color = Color.argb(190, 255, 255, 255)
            }
            canvas.drawText("OFFICIAL MEMBER", iconX + appIconSize + 22f, iconY + 74f, subTitlePaint)

            // 3. ID Box di Sebelah Kanan Atas
            val idBoxW = 430f
            val idBoxH = 142f
            val idBoxX = width - idBoxW - 54f
            val idBoxY = 46f
            val idCut = 24f

            val idBoxPath = android.graphics.Path().apply {
                moveTo(idBoxX, idBoxY)
                lineTo(idBoxX + idBoxW - idCut, idBoxY)
                lineTo(idBoxX + idBoxW, idBoxY + idCut)
                lineTo(idBoxX + idBoxW, idBoxY + idBoxH)
                lineTo(idBoxX + idCut, idBoxY + idBoxH)
                lineTo(idBoxX, idBoxY + idBoxH - idCut)
                close()
            }

            val idBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawPath(idBoxPath, idBgPaint)

            val idStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
                shader = LinearGradient(idBoxX, idBoxY, idBoxX + idBoxW, idBoxY + idBoxH,
                    colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(idBoxPath, idStrokePaint)

            // Formatted ID
            val formattedId = formatMemberId(memberId)
            val idNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 36f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                shader = LinearGradient(idBoxX, idBoxY, idBoxX + idBoxW, idBoxY + 60f,
                    colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawText(formattedId, idBoxX + idBoxW / 2f, idBoxY + 66f, idNumPaint)

            // Admin Phone Number di bawah ID
            val effectiveAdminPhone = if (adminPhone.isNotBlank()) adminPhone else "088806643415"
            val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 25f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                color = colors.accentGoldStart
            }
            canvas.drawText(effectiveAdminPhone, idBoxX + idBoxW / 2f, idBoxY + 110f, phonePaint)

            // 4. Nama Member di Bawah
            val memberNameStr = userName.ifEmpty { "MEMBER NAME SURNAME" }.uppercase()
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
                shader = LinearGradient(54f, height - 120f, width * 0.7f, height - 80f,
                    colors.accentGoldStart, colors.accentGoldEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawText(memberNameStr, 54f, height - 90f, namePaint)

            // 5. Tier Title & Durasi di Bawah
            val tierInfo = "${colors.tierTitle} • ${colors.durationLabel}"
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = colors.accentGoldEnd
                letterSpacing = 0.06f
            }
            canvas.drawText(tierInfo, 54f, height - 42f, infoPaint)

            // Verified Badge di kanan bawah
            val verifyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.argb(180, 255, 255, 255)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("OFFICIAL PASS", width - 54f, height - 42f, verifyPaint)
        }

        return bitmap
    }

    /**
     * Menggambar bintang geometris pada canvas
     */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val path = android.graphics.Path()
        val innerRadius = radius * 0.42f
        val points = 5
        var angle = -Math.PI / 2.0

        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) radius else innerRadius
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            angle += Math.PI / points
        }
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Menghasilkan lembar cetak standar (Depan & Belakang berdampingan dengan garis potong)
     * Format siap cetak kertas A4 / Kertas Kartu ID Card standar.
     */
    fun drawPrintSheet(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        adminPhone: String
    ): Bitmap {
        val cardW = 1200
        val cardH = 756
        val margin = 80
        val gap = 60

        // Lembar cetak vertikal: Depan di atas, Belakang di bawah
        val sheetW = cardW + (margin * 2)
        val sheetH = (cardH * 2) + (margin * 2) + gap + 160 // Extra untuk header & panduan potong

        val sheetBitmap = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheetBitmap)

        // Latar Putih Bersih Siap Cetak
        canvas.drawColor(Color.WHITE)

        // Header Cetak
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(20, 20, 20)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LEMBAR CETAK KARTU MEMBERSHIP VIP - MY SINGGHARA", sheetW * 0.5f, 65f, headerPaint)

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            color = Color.rgb(100, 100, 100)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Ukuran Standar ID Card CR80 (85.6 mm × 53.98 mm) • Potong mengikuti garis panduan", sheetW * 0.5f, 105f, subHeaderPaint)

        // 1. Gambar Sisi Depan
        val frontBitmap = drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = true)
        val frontY = margin.toFloat() + 60f
        canvas.drawBitmap(frontBitmap, margin.toFloat(), frontY, null)

        // Label Sisi Depan
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(80, 80, 80)
        }
        canvas.drawText("▲ TAMPAK DEPAN (FRONT SIDE)", margin.toFloat(), frontY - 14f, labelPaint)

        // 2. Garis Pemisah / Potong (Dashed Line)
        val cutLineY = frontY + cardH + (gap / 2f)
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 180, 180)
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }
        canvas.drawLine(margin.toFloat() - 40f, cutLineY, sheetW - margin.toFloat() + 40f, cutLineY, dashPaint)

        val scissorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            color = Color.rgb(140, 140, 140)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("✂ Garis Potong / Lipat Kartu", sheetW * 0.5f, cutLineY - 8f, scissorPaint)

        // 3. Gambar Sisi Belakang (Data Member)
        val backBitmap = drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = false)
        val backY = frontY + cardH + gap
        canvas.drawBitmap(backBitmap, margin.toFloat(), backY, null)

        canvas.drawText("▼ TAMPAK BELAKANG / KARTU MEMBER (MEMBER SIDE)", margin.toFloat(), backY - 14f, labelPaint)

        // Footer Catatan
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 19f
            color = Color.rgb(120, 120, 120)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Cetak dengan skala 100% (Actual Size / Fit to Printable Area) pada kertas foto glossy / PVC card.", sheetW * 0.5f, sheetH - 40f, footerPaint)

        return sheetBitmap
    }

    /**
     * Membuka Android Print Manager bawaan untuk mencetak kartu secara langsung atau Simpan sebagai PDF
     */
    fun printVipCard(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        adminPhone: String,
        mode: VipPrintMode
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                MainViewModel.instance?.showToastPopup("Layanan cetak tidak tersedia pada perangkat ini.")
                return
            }

            val bitmap = when (mode) {
                VipPrintMode.MEMBER_SIDE -> drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = false)
                VipPrintMode.FRONT_SIDE -> drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = true)
                VipPrintMode.SHEET_BOTH -> drawPrintSheet(context, userName, memberId, durationMonths, adminPhone)
            }

            val cleanName = userName.replace("\\s+".toRegex(), "_").ifEmpty { "Member" }
            val jobTitle = "VIP_Card_${cleanName}_${mode.name}"

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobTitle.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("Tujuan cetak tidak valid.")
                        return
                    }

                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)

                    try {
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            pdfDocument.writeTo(out)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfDocument.close()
                    }
                }
            }

            printManager.print(
                jobTitle,
                printAdapter,
                PrintAttributes.Builder()
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal mencetak: ${e.message}")
        }
    }

    /**
     * Menyimpan gambar kartu ke cache & direktori Download, lalu membagikannya melalui Android Share Sheet
     */
    fun shareVipCard(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        adminPhone: String,
        mode: VipPrintMode
    ): Uri? {
        return try {
            val bitmap = when (mode) {
                VipPrintMode.MEMBER_SIDE -> drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = false)
                VipPrintMode.FRONT_SIDE -> drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = true)
                VipPrintMode.SHEET_BOTH -> drawPrintSheet(context, userName, memberId, durationMonths, adminPhone)
            }

            val cleanName = userName.replace("\\s+".toRegex(), "_").ifEmpty { "Member" }
            val fileName = "VIP_Card_${cleanName}_${mode.name}_${System.currentTimeMillis()}.png"

            // 1. Simpan ke Cache untuk FileProvider
            val cacheDir = File(context.cacheDir, "vip_cards")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            // 2. Simpan juga salinan ke folder Download umum agar pengguna bisa langsung mengakses filenya
            try {
                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dlDir != null && dlDir.exists()) {
                    val publicFile = File(dlDir, fileName)
                    FileOutputStream(publicFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } catch (_: Exception) {}

            // 3. Buka Android Share Sheet
            val modeTitle = when (mode) {
                VipPrintMode.MEMBER_SIDE -> "Kartu Member VIP"
                VipPrintMode.FRONT_SIDE -> "Tampak Depan Kartu VIP"
                VipPrintMode.SHEET_BOTH -> "Lembar Cetak Kartu VIP (Depan & Belakang)"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Kartu Membership VIP My Singghara")
                putExtra(Intent.EXTRA_TEXT, "Kartu Membership Resmi: $userName\nMember ID: ${formatMemberId(memberId)}\nPaket: $modeTitle\nBisa langsung dicetak atau disimpan.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan / Cetak $modeTitle"))
            MainViewModel.instance?.showToastPopup("Kartu siap dibagikan / dicetak!")
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal membagikan kartu: ${e.message}")
            null
        }
    }

    /**
     * Menyimpan gambar kartu langsung ke folder Download perangkat
     */
    fun saveCardToDownloads(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        adminPhone: String,
        mode: VipPrintMode
    ): Boolean {
        return try {
            val bitmap = when (mode) {
                VipPrintMode.MEMBER_SIDE -> drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = false)
                VipPrintMode.FRONT_SIDE -> drawSingleCard(context, userName, memberId, durationMonths, adminPhone, isFront = true)
                VipPrintMode.SHEET_BOTH -> drawPrintSheet(context, userName, memberId, durationMonths, adminPhone)
            }

            val cleanName = userName.replace("\\s+".toRegex(), "_").ifEmpty { "Member" }
            val fileName = "VIP_Card_${cleanName}_${mode.name}_${System.currentTimeMillis()}.png"
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = if (dlDir != null && dlDir.exists()) dlDir else context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

            if (targetDir != null) {
                val publicFile = File(targetDir, fileName)
                FileOutputStream(publicFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                MainViewModel.instance?.showToastPopup("Kartu berhasil disimpan di folder Download: $fileName")
                true
            } else {
                MainViewModel.instance?.showToastPopup("Gagal menemukan folder penyimpanan.")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal menyimpan kartu: ${e.message}")
            false
        }
    }
}
