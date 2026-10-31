package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Environment
import com.example.ui.MainViewModel
import androidx.core.content.FileProvider
import com.example.data.AnggotaItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class IdCardColorIntPalette(
    val primaryDark: Int,
    val accent: Int,
    val accentDark: Int,
    val bottomBar: Int
)

object IdCardBitmapGenerator {

    fun getIdCardColorPalette(status: String): IdCardColorIntPalette {
        val s = status.lowercase()
        return when {
            s.contains("murid") || s.contains("siswa") || s.contains("santri") || s.contains("pelajar") -> {
                IdCardColorIntPalette(
                    primaryDark = Color.rgb(30, 27, 75),     // #1E1B4B Midnight Royal Indigo
                    accent = Color.rgb(56, 189, 248),        // #38BDF8 Sky Blue
                    accentDark = Color.rgb(2, 132, 199),     // #0284C7 Ocean Cyan
                    bottomBar = Color.rgb(37, 99, 235)       // #2563EB Cobalt Blue
                )
            }
            s.contains("guru") || s.contains("dosen") || s.contains("pendidik") || s.contains("pengajar") || s.contains("ustadz") -> {
                IdCardColorIntPalette(
                    primaryDark = Color.rgb(6, 78, 59),      // #064E3B Deep Emerald Pine
                    accent = Color.rgb(16, 185, 129),        // #10B981 Emerald Green
                    accentDark = Color.rgb(5, 150, 105),     // #059669 Forest Jade
                    bottomBar = Color.rgb(13, 148, 136)      // #0D9488 Deep Teal
                )
            }
            s.contains("staf") || s.contains("staff") || s.contains("admin") || s.contains("keuangan") || s.contains("tata usaha") -> {
                IdCardColorIntPalette(
                    primaryDark = Color.rgb(76, 5, 25),      // #4C0519 Burgundy Crimson
                    accent = Color.rgb(251, 113, 133),       // #FB7185 Rose Coral
                    accentDark = Color.rgb(225, 29, 72),     // #E11D48 Deep Crimson
                    bottomBar = Color.rgb(190, 18, 60)       // #BE123C Ruby Red
                )
            }
            s.contains("magang") || s.contains("pkl") || s.contains("intern") || s.contains("prakerin") -> {
                IdCardColorIntPalette(
                    primaryDark = Color.rgb(59, 7, 100),     // #3B0764 Deep Night Violet
                    accent = Color.rgb(192, 132, 252),       // #C084FC Lavender Purple
                    accentDark = Color.rgb(147, 51, 234),    // #9333EA Vivid Purple
                    bottomBar = Color.rgb(126, 34, 206)      // #7E22CE Royal Violet
                )
            }
            s.contains("tamu") || s.contains("mitra") || s.contains("guest") || s.contains("alumni") -> {
                IdCardColorIntPalette(
                    primaryDark = Color.rgb(30, 41, 59),     // #1E293B Deep Slate
                    accent = Color.rgb(148, 163, 184),       // #94A3B8 Silver Slate
                    accentDark = Color.rgb(100, 116, 139),   // #64748B Cool Slate
                    bottomBar = Color.rgb(71, 85, 105)       // #475569 Slate Charcoal
                )
            }
            else -> { // Default: Karyawan / Pegawai / Official
                IdCardColorIntPalette(
                    primaryDark = Color.rgb(27, 34, 46),     // #1B222E Dark Navy Charcoal
                    accent = Color.rgb(245, 158, 11),        // #F59E0B Vibrant Golden Amber
                    accentDark = Color.rgb(217, 119, 6),     // #D97706 Deep Amber
                    bottomBar = Color.rgb(245, 158, 11)      // #F59E0B Amber Gold
                )
            }
        }
    }

    /**
     * Renders a high-resolution vertical ID Card Bitmap (Front or Back side)
     */
    fun createVerticalIdCardBitmap(
        context: Context,
        anggota: AnggotaItem,
        qrBitmap: Bitmap?,
        isFrontSide: Boolean = true
    ): Bitmap {
        val width = 900
        val height = 1450
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dynamic Role-based Geometric Brand Color Palette
        val palette = getIdCardColorPalette(anggota.status)
        val darkNavy = palette.primaryDark
        val goldPrimary = palette.accent
        val goldDark = palette.accentDark
        val bottomBarColor = palette.bottomBar
        val bgWhite = Color.rgb(255, 255, 255)
        val textMuted = Color.rgb(100, 116, 139)     // Slate 500
        val textDark = Color.rgb(27, 34, 46)

        // 1. Base White Card with Smooth Rounded Corners & Drop Shadow
        val cardRect = RectF(20f, 20f, width - 20f, height - 20f)
        val cardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgWhite
            setShadowLayer(25f, 0f, 12f, Color.argb(60, 15, 23, 42))
        }
        canvas.drawRoundRect(cardRect, 40f, 40f, cardShadowPaint)

        // Clip canvas to the card boundary so geometric polygon banners stay cleanly inside
        val clipPath = Path().apply {
            addRoundRect(cardRect, 40f, 40f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // Card Border Stroke
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val instTitle = if (anggota.instansi.isNotBlank() && !anggota.instansi.equals("My Singghara Academy", ignoreCase = true)) anggota.instansi.uppercase() else "MY SINGGHARA"
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldPrimary }
        val darkNavyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkNavy }
        val goldDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldDark }

        val appLogoBitmap: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, com.example.R.drawable.img_app_icon_1786282576281)
                ?: BitmapFactory.decodeResource(context.resources, com.example.R.drawable.ic_app_logo)
        } catch (e: Exception) {
            null
        }

        if (isFrontSide) {
            // ==========================================
            // === FRONT SIDE (REF DESIGN MATCH) ===
            // ==========================================

            // Top Left Gold Angled Stripe
            val topLeftGoldPath = Path().apply {
                moveTo(20f, 60f)
                lineTo(240f, 60f)
                lineTo(200f, 130f)
                lineTo(20f, 130f)
                close()
            }
            canvas.drawPath(topLeftGoldPath, goldPaint)

            // Top Right Dark Navy Geometric Wedge
            val topRightDarkPath = Path().apply {
                moveTo(width - 20f, 20f)
                lineTo(width - 320f, 20f)
                lineTo(width - 240f, 130f)
                lineTo(width - 20f, 130f)
                close()
            }
            canvas.drawPath(topRightDarkPath, darkNavyPaint)

            // Top Right Thin Gold Accent Line
            val topRightGoldPath = Path().apply {
                moveTo(width - 335f, 20f)
                lineTo(width - 320f, 20f)
                lineTo(width - 240f, 130f)
                lineTo(width - 255f, 130f)
                close()
            }
            canvas.drawPath(topRightGoldPath, goldPaint)

            // Top Logo & Institution Name Header (Logo centered above title with neat spacing)
            val iconSize = 52f
            val iconY = 85f
            if (appLogoBitmap != null) {
                val circularLogo = getCircularBitmap(appLogoBitmap, iconSize.toInt())
                val iconStartX = (width - iconSize) / 2f
                canvas.drawBitmap(circularLogo, iconStartX, iconY, null)

                // Subtle circular border around logo
                val logoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = goldPrimary
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawCircle(width / 2f, iconY + iconSize / 2f, iconSize / 2f, logoBorderPaint)
            }

            val logoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkNavy
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }
            val headerY = if (appLogoBitmap != null) iconY + iconSize + 36f else 170f
            canvas.drawText(instTitle, width / 2f, headerY, logoTextPaint)

            // --- PHOTO & BACKGROUND GEOMETRIC ANGLED BANDS ---
            val photoCenterX = width / 2f
            val photoCenterY = 470f
            val photoRadius = 135f

            // Left Behind-Photo Gold & Dark Geometric Wing
            val leftBandDark = Path().apply {
                moveTo(20f, 490f)
                lineTo(220f, 490f)
                lineTo(180f, 540f)
                lineTo(20f, 540f)
                close()
            }
            canvas.drawPath(leftBandDark, darkNavyPaint)

            val leftBandGold = Path().apply {
                moveTo(20f, 540f)
                lineTo(240f, 540f)
                lineTo(200f, 600f)
                lineTo(20f, 600f)
                close()
            }
            canvas.drawPath(leftBandGold, goldPaint)

            // Right Behind-Photo Dark & Gold Geometric Wing
            val rightBandDark = Path().apply {
                moveTo(width - 20f, 480f)
                lineTo(width - 240f, 480f)
                lineTo(width - 200f, 530f)
                lineTo(width - 20f, 530f)
                close()
            }
            canvas.drawPath(rightBandDark, darkNavyPaint)

            val rightBandGold = Path().apply {
                moveTo(width - 20f, 530f)
                lineTo(width - 220f, 530f)
                lineTo(width - 180f, 580f)
                lineTo(width - 20f, 580f)
                close()
            }
            canvas.drawPath(rightBandGold, goldPaint)

            // Large Circular Gold Ring Border (Solid Bold Ring from reference)
            val photoGoldRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = goldPrimary
                style = Paint.Style.STROKE
                strokeWidth = 14f
            }
            canvas.drawCircle(photoCenterX, photoCenterY, photoRadius + 4f, photoGoldRingPaint)

            // Photo bitmap / monogram
            var loadedPhotoBitmap: Bitmap? = null
            if (anggota.fotoUri.isNotBlank()) {
                try {
                    if (anggota.fotoUri.startsWith("data:image")) {
                        val base64Data = anggota.fotoUri.substringAfter("base64,")
                        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        loadedPhotoBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    } else if (anggota.fotoUri.startsWith("/")) {
                        loadedPhotoBitmap = BitmapFactory.decodeFile(anggota.fotoUri)
                    } else {
                        val uri = Uri.parse(anggota.fotoUri)
                        val inputStream = context.contentResolver.openInputStream(uri)
                        loadedPhotoBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (loadedPhotoBitmap != null) {
                val circularPhoto = getCircularBitmap(loadedPhotoBitmap, (photoRadius * 2).toInt())
                canvas.drawBitmap(circularPhoto, photoCenterX - photoRadius, photoCenterY - photoRadius, Paint(Paint.ANTI_ALIAS_FLAG))
            } else {
                val photoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(241, 245, 249)
                }
                canvas.drawCircle(photoCenterX, photoCenterY, photoRadius, photoBgPaint)
                val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = darkNavy
                    textSize = 110f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val initials = if (anggota.nama.isNotBlank()) anggota.nama.first().uppercase() else "A"
                canvas.drawText(initials, photoCenterX, photoCenterY + 40f, initialPaint)
            }

            // --- MEMBER NAME & ROLE / POSITION ---
            val fullName = (if (anggota.nama.isNotBlank()) anggota.nama else "NAMA LENGKAP").trim().uppercase()
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textDark
                textSize = when {
                    fullName.length <= 18 -> 44f
                    fullName.length <= 32 -> 38f
                    fullName.length <= 48 -> 32f
                    else -> 28f
                }
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.04f
            }

            val nameLines = wrapTextToLines(fullName, namePaint, width - 100f)
            val lineCount = nameLines.size.coerceAtLeast(1)
            val lineSpacing = (namePaint.textSize * 1.15f)

            val startNameY = when (lineCount) {
                1 -> 688f
                2 -> 666f
                3 -> 652f
                else -> 640f
            }

            var currentNameY = startNameY
            for (line in nameLines) {
                canvas.drawText(line, width / 2f, currentNameY, namePaint)
                currentNameY += lineSpacing
            }

            // Job Position / Status (Gold uppercase bold)
            val positionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = goldDark
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.10f
            }
            val roleName = (if (anggota.status.isNotBlank()) anggota.status else "MEMBER").uppercase()
            val positionY = (currentNameY + 12f).coerceAtMost(756f)
            canvas.drawText(roleName, width / 2f, positionY, positionPaint)

            // Subtle divider line
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 2.5f
            }
            val dividerY = (positionY + 28f).coerceAtMost(778f)
            canvas.drawLine(width / 2f - 160f, dividerY, width / 2f + 160f, dividerY, dividerPaint)

            // --- CREDENTIAL DETAILS (ID, EMAIL, PHONE, JOIN DATE ONLY, QR) ---
            val detailLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textMuted
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.05f
            }

            val detailValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textDark
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            // ID Number
            canvas.drawText("ID : ${anggota.nomorInduk}", width / 2f, 815f, detailValuePaint)

            // Email
            val emailText = if (anggota.email.isNotBlank()) anggota.email else "${anggota.nomorInduk.lowercase()}@singghara.ac.id"
            canvas.drawText("EMAIL : $emailText", width / 2f, 855f, detailLabelPaint)

            // Phone / Contact
            val phoneText = if (anggota.kontak.isNotBlank()) anggota.kontak else "-"
            canvas.drawText("PHONE : $phoneText", width / 2f, 895f, detailLabelPaint)

            // Join Date Only (below Phone, no title)
            val joinDate = try {
                java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault()).format(java.util.Date(anggota.tanggalDibuat))
            } catch (e: Exception) {
                "12/10/24"
            }
            val dateOnlyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textMuted
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(joinDate, width / 2f, 932f, dateOnlyPaint)

            // QR Code in Clean Centered Frame
            val qrBoxSize = 215f
            val qrX = width / 2f - (qrBoxSize / 2f)
            val qrY = 960f
            val qrRect = RectF(qrX, qrY, qrX + qrBoxSize, qrY + qrBoxSize)

            val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgWhite
                setShadowLayer(8f, 0f, 4f, Color.argb(25, 0, 0, 0))
            }
            canvas.drawRoundRect(qrRect, 16f, 16f, qrBgPaint)

            val qrBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(qrRect, 16f, 16f, qrBorderPaint)

            if (qrBitmap != null) {
                val scaledQr = Bitmap.createScaledBitmap(qrBitmap, (qrBoxSize - 20f).toInt(), (qrBoxSize - 20f).toInt(), true)
                canvas.drawBitmap(scaledQr, qrX + 10f, qrY + 10f, Paint(Paint.ANTI_ALIAS_FLAG))
            }

            // --- BOTTOM GEOMETRIC FOOTER & DEPARTMENT BAR ---
            // Bottom Navy Geometric Right Accent
            val bottomNavyPath = Path().apply {
                moveTo(width - 20f, height - 200f)
                lineTo(width - 240f, height - 120f)
                lineTo(width - 20f, height - 120f)
                close()
            }
            canvas.drawPath(bottomNavyPath, darkNavyPaint)

            val bottomNavyLeftPath = Path().apply {
                moveTo(20f, height - 170f)
                lineTo(140f, height - 120f)
                lineTo(20f, height - 120f)
                close()
            }
            canvas.drawPath(bottomNavyLeftPath, darkNavyPaint)

            // Solid Bottom Department Bar
            val deptRect = RectF(20f, height - 120f, width - 20f, height - 20f)
            val bottomBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bottomBarColor }
            canvas.drawRect(deptRect, bottomBarPaint)

            val deptTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.16f
            }
            val deptName = if (anggota.divisiOrJurusan.isNotBlank()) anggota.divisiOrJurusan.uppercase() else "DEPARTMENT"
            canvas.drawText(deptName, width / 2f, height - 58f, deptTextPaint)

        } else {
            // ==========================================
            // === BACK SIDE (REF DESIGN MATCH) ===
            // ==========================================

            // Top Left Dark Navy Wedge
            val topDarkWedge = Path().apply {
                moveTo(20f, 20f)
                lineTo(260f, 20f)
                lineTo(200f, 100f)
                lineTo(20f, 100f)
                close()
            }
            canvas.drawPath(topDarkWedge, darkNavyPaint)

            // Top Right Dark Wedge
            val topDarkWedgeR = Path().apply {
                moveTo(width - 20f, 20f)
                lineTo(width - 200f, 20f)
                lineTo(width - 20f, 90f)
                close()
            }
            canvas.drawPath(topDarkWedgeR, darkNavyPaint)

            // Top Header: Institution Brand Logo & Title (Centered at Top Area)
            val backIconSize = 46f
            val backIconY = 50f
            if (appLogoBitmap != null) {
                val circularLogo = getCircularBitmap(appLogoBitmap, backIconSize.toInt())
                val backIconX = (width - backIconSize) / 2f
                canvas.drawBitmap(circularLogo, backIconX, backIconY, null)

                val backLogoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = goldPrimary
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawCircle(width / 2f, backIconY + backIconSize / 2f, backIconSize / 2f, backLogoBorderPaint)
            }

            val backLogoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkNavy
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }
            val backLogoY = if (appLogoBitmap != null) backIconY + backIconSize + 32f else 115f
            canvas.drawText(instTitle, width / 2f, backLogoY, backLogoTextPaint)

            // Gold Angled Header Bar: "TERM & CONDITION"
            val termBarPath = Path().apply {
                moveTo(20f, 160f)
                lineTo(width - 160f, 160f)
                lineTo(width - 240f, 240f)
                lineTo(20f, 240f)
                close()
            }
            canvas.drawPath(termBarPath, goldPaint)

            val termTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.12f
            }
            canvas.drawText("TERM & CONDITION", 60f, 212f, termTitlePaint)

            // Terms & Conditions Body with Gold Bullet Dots
            val termsList = listOf(
                "Kartu ini adalah identitas resmi pemegang yang terdaftar pada sistem ${if (anggota.instansi.isNotBlank()) anggota.instansi else "My Singghara"}.",
                "Wajib dibawa dan dipindai saat presensi digital harian serta kegiatan operasional resmi.",
                "Dilarang keras memindahtangankan, menggandakan, atau memalsukan kartu identitas ini.",
                "Segala bentuk penyalahgunaan kartu akan dikenakan sanksi sesuai ketentuan yang berlaku.",
                "Jika menemukan kartu ini dalam keadaan hilang, mohon segera mengembalikan ke bagian administrasi."
            )

            var currentY = 290f
            val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldPrimary }
            val termsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            for (term in termsList) {
                // Gold bullet dot
                canvas.drawCircle(70f, currentY - 8f, 6.5f, bulletPaint)
                
                // Wrap text manually if long
                val words = term.split(" ")
                var line = ""
                for (word in words) {
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    if (termsTextPaint.measureText(testLine) > 710f) {
                        canvas.drawText(line, 95f, currentY, termsTextPaint)
                        line = word
                        currentY += 30f
                    } else {
                        line = testLine
                    }
                }
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 95f, currentY, termsTextPaint)
                }
                currentY += 38f
            }

            // --- SIGNATURES ROW: CEO (LEFT) & USER (RIGHT) ---
            val sigTopY = 700f
            val sigBottomY = 810f

            // 1. LEFT: CEO Signature & Label
            val sigFile = File(context.filesDir, "company_id_card_signature.png")
            if (sigFile.exists() && sigFile.length() > 0) {
                try {
                    val sigBmp = BitmapFactory.decodeFile(sigFile.absolutePath)
                    if (sigBmp != null) {
                        val sigRect = RectF(70f, sigTopY, 320f, sigBottomY)
                        canvas.drawBitmap(sigBmp, null, sigRect, null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(148, 163, 184)
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("my singghara", 195f, sigTopY + 60f, placeholderPaint)
            }

            val ceoPrefs = context.getSharedPreferences("singghara_idcard_prefs", Context.MODE_PRIVATE)
            val ceoLabel = ceoPrefs.getString("key_ceo_signature_label", "my singghara") ?: "my singghara"

            val ceoLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = goldDark
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.05f
            }
            canvas.drawText(ceoLabel, 195f, 845f, ceoLabelPaint)

            // 2. RIGHT: User Signature & Name & Expired Date Only
            var loadedUserSig: Bitmap? = null
            if (anggota.tandaTanganUri.isNotBlank()) {
                try {
                    if (anggota.tandaTanganUri.startsWith("data:image")) {
                        val base64Data = anggota.tandaTanganUri.substringAfter("base64,")
                        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        loadedUserSig = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    } else if (anggota.tandaTanganUri.startsWith("/")) {
                        loadedUserSig = BitmapFactory.decodeFile(anggota.tandaTanganUri)
                    } else {
                        val uri = Uri.parse(anggota.tandaTanganUri)
                        val inputStream = context.contentResolver.openInputStream(uri)
                        loadedUserSig = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val userSigCenterX = width - 195f
            if (loadedUserSig != null) {
                val userSigRect = RectF(width - 320f, sigTopY, width - 70f, sigBottomY)
                canvas.drawBitmap(loadedUserSig, null, userSigRect, null)
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(148, 163, 184)
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("( TTD Pengguna )", userSigCenterX, sigTopY + 60f, placeholderPaint)
            }

            val backFullName = (if (anggota.nama.isNotBlank()) anggota.nama else "NAMA LENGKAP").trim().uppercase()
            val userNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkNavy
                textSize = when {
                    backFullName.length <= 18 -> 21f
                    backFullName.length <= 32 -> 18f
                    else -> 16f
                }
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.04f
            }

            val backNameLines = wrapTextToLines(backFullName, userNamePaint, 240f)
            val backLineCount = backNameLines.size.coerceAtLeast(1)
            val backLineSpacing = (userNamePaint.textSize * 1.15f)

            val startBackY = when (backLineCount) {
                1 -> 845f
                2 -> 834f
                3 -> 822f
                else -> 812f
            }

            var currentBackY = startBackY
            for (line in backNameLines) {
                canvas.drawText(line, userSigCenterX, currentBackY, userNamePaint)
                currentBackY += backLineSpacing
            }

            val expDate = if (anggota.berlakuHingga.isNotBlank()) anggota.berlakuHingga else "01/11/27"
            val expDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            val expDateY = (currentBackY + 12f).coerceAtMost(890f)
            canvas.drawText(expDate, userSigCenterX, expDateY, expDatePaint)

            // --- BOTTOM GEOMETRIC BANDS (REF DESIGN) ---
            // Bottom Left Dark Navy Polygons
            val botLeftNavy = Path().apply {
                moveTo(20f, height - 260f)
                lineTo(140f, height - 260f)
                lineTo(220f, height - 140f)
                lineTo(width - 20f, height - 140f)
                lineTo(width - 20f, height - 20f)
                lineTo(20f, height - 20f)
                close()
            }
            canvas.drawPath(botLeftNavy, darkNavyPaint)

            // Bottom Right Gold Angled Stripe
            val botRightGold = Path().apply {
                moveTo(width - 20f, height - 240f)
                lineTo(width - 180f, height - 240f)
                lineTo(width - 240f, height - 170f)
                lineTo(width - 20f, height - 170f)
                close()
            }
            canvas.drawPath(botRightGold, goldPaint)
        }

        canvas.restore()
        canvas.drawRoundRect(cardRect, 40f, 40f, cardBorderPaint)

        return bitmap
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, targetW: Int, targetH: Int, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rectF = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, targetSize, targetSize)
        val rectF = RectF(rect)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawOval(rectF, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        canvas.drawBitmap(scaled, rect, rect, paint)
        return output
    }

    /**
     * Creates a single bitmap containing both Front and Back sides side-by-side on clean background
     */
    fun createCombinedDualSideBitmap(
        context: Context,
        anggota: AnggotaItem,
        qrBitmap: Bitmap?
    ): Bitmap {
        val front = createVerticalIdCardBitmap(context, anggota, qrBitmap, isFrontSide = true)
        val back = createVerticalIdCardBitmap(context, anggota, qrBitmap, isFrontSide = false)

        val padding = 40
        val totalWidth = front.width * 2 + padding * 3
        val totalHeight = front.height + padding * 2

        val combined = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)

        canvas.drawBitmap(front, padding.toFloat(), padding.toFloat(), null)
        canvas.drawBitmap(back, (front.width + padding * 2).toFloat(), padding.toFloat(), null)

        return combined
    }

    /**
     * Saves ID Card bitmap to device Download folder and shares via Intent
     */
    fun saveAndShareIdCard(context: Context, bitmap: Bitmap, anggotaName: String): Uri? {
        return try {
            val fileName = "ID_Card_${anggotaName.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.png"
            val cacheDir = File(context.cacheDir, "id_cards")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            // Also save a copy in public download directory
            try {
                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dlDir != null && dlDir.exists()) {
                    val publicFile = File(dlDir, fileName)
                    FileOutputStream(publicFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "ID Card Resmi: $anggotaName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan ID Card"))
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal membagikan ID card: ${e.message}")
            null
        }
    }

    /**
     * Memecah teks menjadi beberapa baris berdasarkan lebar maksimum canvas.
     * Tidak memotong nama dengan "..." melainkan menampilkan secara utuh di baris berikutnya.
     */
    fun wrapTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val words = clean.split("\\s+".toRegex())
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                // Jika satu kata melebihi maxWidth, pecah karakter per karakter
                if (paint.measureText(word) > maxWidth) {
                    var remaining = word
                    while (paint.measureText(remaining) > maxWidth && remaining.length > 1) {
                        var cutIndex = remaining.length - 1
                        while (cutIndex > 1 && paint.measureText(remaining.substring(0, cutIndex)) > maxWidth) {
                            cutIndex--
                        }
                        lines.add(remaining.substring(0, cutIndex))
                        remaining = remaining.substring(cutIndex)
                    }
                    currentLine = StringBuilder(remaining)
                } else {
                    currentLine = StringBuilder(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}
