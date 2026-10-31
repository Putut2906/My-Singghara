package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.example.data.AnggotaItem
import com.example.ui.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Generator Bitmap Kartu VIP Membership Resolusi Tinggi untuk Cetak & Berbagi
 * Mendukung Cetak Langsung (via PrintHelper / Wi-Fi Printer / Simpan PDF)
 * dan Ekspor Gambar PNG (Tampak Depan, Tampak Belakang, atau Dual Side).
 */
object VipMembershipCardGenerator {

    const val CARD_WIDTH = 1050
    const val CARD_HEIGHT = 660

    data class QuickLoginCredentials(val email: String, val password: String)

    /**
     * Mengekstrak alamat email murni dari berbagai format teks atau QR code membership
     * Menghapus awalan seperti "MYSINGGHARA VIP:...", "MYSINGGHARA_VIP:...", JSON, dll.
     */
    fun extractCleanEmail(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val clean = input.trim()

        // 1. Format JSON
        if (clean.startsWith("{") && clean.endsWith("}")) {
            try {
                val json = org.json.JSONObject(clean)
                val rawEmail = json.optString("email", "").trim()
                if (rawEmail.isNotBlank()) {
                    val extracted = extractCleanEmail(rawEmail)
                    if (!extracted.isNullOrBlank()) return extracted
                }
            } catch (_: Exception) {}
        }

        // 2. Ekstrak dari pola key-value: EMAIL=..., email:..., mail=..., user=...
        val kvRegex = Regex("""(?:email|target|user|mail|akun)\s*[:=]\s*([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""", RegexOption.IGNORE_CASE)
        val kvMatch = kvRegex.find(clean)
        if (kvMatch != null && kvMatch.groupValues.size > 1) {
            return kvMatch.groupValues[1].trim()
        }

        // 3. Regex email dengan domain ber-TLD valid (.com, .id, .co.id, .net, dll.)
        val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        val matches = emailRegex.findAll(clean).map { it.value.trim() }.toList()
        if (matches.isNotEmpty()) {
            // Jika ada beberapa match (misal ada token mengandung '@' tanpa domain TLD valid),
            // prioritaskan email yang memiliki dot dan TLD 2-10 huruf di domainnya
            val valid = matches.lastOrNull { match ->
                val domain = match.substringAfter("@", "")
                domain.contains(".") && !domain.endsWith(".") && domain.substringAfterLast(".").length in 2..10
            } ?: matches.last()
            return valid
        }

        // 4. Fallback jika ada token dengan '@' di antara pembatas titik dua / koma / spasi
        if (clean.contains("@")) {
            val tokens = clean.split(":", ";", "=", ",", " ", "\n", "\r")
            val candidate = tokens.lastOrNull { it.contains("@") && it.contains(".") }
                ?: tokens.lastOrNull { it.contains("@") }
            if (!candidate.isNullOrBlank()) {
                val stripped = candidate.trim().filter { !it.isWhitespace() && it != '"' && it != '\'' }
                if (stripped.contains("@")) return stripped
            }
        }

        return null
    }

    fun buildQuickLoginPayload(email: String, password: String): String {
        val safeEmail = extractCleanEmail(email) ?: email.trim()
        val safePassword = password.trim()
        return try {
            val json = org.json.JSONObject()
            json.put("action", "quick_login")
            json.put("email", safeEmail)
            json.put("password", safePassword)
            json.toString()
        } catch (_: Exception) {
            "SINGGHARA_QUICK_LOGIN:$safeEmail:$safePassword"
        }
    }

    fun parseQuickLoginPayload(raw: String): QuickLoginCredentials? {
        val clean = raw.trim()
        if (clean.isBlank()) return null

        // 1. Format JSON
        try {
            if (clean.startsWith("{") && clean.endsWith("}")) {
                val json = org.json.JSONObject(clean)
                val emailVal = extractCleanEmail(json.optString("email", ""))
                val password = json.optString("password", "")
                if (!emailVal.isNullOrBlank()) {
                    return QuickLoginCredentials(emailVal, password)
                }
            }
        } catch (_: Exception) {}

        // 2. Format Bersih Ekstraksi Email
        val cleanEmail = extractCleanEmail(clean)
        if (!cleanEmail.isNullOrBlank()) {
            var foundPassword = ""

            // Periksa jika ada kata sandi setelah email (misal: ...:email:password)
            val emailIdx = clean.indexOf(cleanEmail)
            if (emailIdx >= 0) {
                val afterEmail = clean.substring(emailIdx + cleanEmail.length).trim()
                if (afterEmail.startsWith(":") || afterEmail.startsWith(";") || afterEmail.startsWith("=")) {
                    val rawPass = afterEmail.substring(1).trim()
                    foundPassword = rawPass.split(";", "\n", "\r", " ")[0].trim()
                }
            }

            // Periksa pola PASS= atau PWD= atau PASSWORD:
            if (foundPassword.isBlank()) {
                val passRegex = Regex("""(?:pass|password|pwd|sandi)\s*[:=]\s*([^\s;]+)""", RegexOption.IGNORE_CASE)
                val passMatch = passRegex.find(clean)
                if (passMatch != null && passMatch.groupValues.size > 1) {
                    foundPassword = passMatch.groupValues[1].trim()
                }
            }

            return QuickLoginCredentials(cleanEmail, foundPassword)
        }

        return null
    }

    fun getVipStarCount(durationMonths: Int, isAdmin: Boolean = false): Int {
        if (isAdmin || durationMonths <= 0) return 6
        return when (durationMonths) {
            1 -> 1
            3 -> 2
            5 -> 3
            7 -> 4
            12 -> 5
            else -> when {
                durationMonths < 3 -> 1
                durationMonths < 5 -> 2
                durationMonths < 7 -> 3
                durationMonths < 12 -> 4
                durationMonths == 12 -> 5
                else -> 6
            }
        }
    }

    fun formatMemberId(rawUid: String): String {
        if (rawUid.isBlank()) return "0219-2129-1284"
        val clean = rawUid.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.length < 12) {
            val padded = clean.padEnd(12, 'X')
            return "${padded.substring(0, 4)}-${padded.substring(4, 8)}-${padded.substring(8, 12)}"
        }
        return "${clean.substring(0, 4)}-${clean.substring(4, 8)}-${clean.substring(8, 12)}"
    }

    /**
     * Membuat Bitmap Kartu VIP Membership dari objek AnggotaItem
     */
    fun createVipMembershipCardBitmap(
        context: Context,
        anggota: AnggotaItem,
        qrBitmap: Bitmap?,
        isFrontSide: Boolean,
        adminPhoneNumber: String = "088806643415"
    ): Bitmap {
        val memberId = if (anggota.nomorInduk.isNotBlank()) anggota.nomorInduk else anggota.id.toString()
        val email = anggota.email.ifBlank { anggota.emailOrTelp }
        val durationMonths = when {
            anggota.status.contains("12", ignoreCase = true) || anggota.status.contains("Tahun", ignoreCase = true) -> 12
            anggota.status.contains("7", ignoreCase = true) -> 7
            anggota.status.contains("5", ignoreCase = true) -> 5
            anggota.status.contains("3", ignoreCase = true) -> 3
            else -> 1
        }
        return createVipCardFromDetails(
            context = context,
            userName = anggota.nama,
            memberId = memberId,
            durationMonths = durationMonths,
            isAdmin = false,
            isFrontSide = isFrontSide,
            adminPhoneNumber = adminPhoneNumber,
            qrBitmap = qrBitmap,
            userEmail = email
        )
    }

    /**
     * Membuat Bitmap Lembar Cetak Dua Sisi (Dual-Side) dari AnggotaItem
     */
    fun createCombinedDualSideVipCardBitmap(
        context: Context,
        anggota: AnggotaItem,
        qrBitmap: Bitmap?,
        adminPhoneNumber: String = "088806643415"
    ): Bitmap {
        val front = createVipMembershipCardBitmap(context, anggota, qrBitmap, isFrontSide = true, adminPhoneNumber = adminPhoneNumber)
        val back = createVipMembershipCardBitmap(context, anggota, qrBitmap, isFrontSide = false, adminPhoneNumber = adminPhoneNumber)
        return combineFrontAndBackBitmaps(front, back, anggota.nama)
    }

    /**
     * Membuat Bitmap Lembar Cetak Dua Sisi (Dual-Side) langsung dari detail pengguna
     */
    fun createCombinedDualSideFromUser(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        isAdmin: Boolean = false,
        adminPhoneNumber: String = "088806643415",
        userEmail: String = "",
        userPassword: String = ""
    ): Bitmap {
        val front = createVipCardFromDetails(
            context = context,
            userName = userName,
            memberId = memberId,
            durationMonths = durationMonths,
            isAdmin = isAdmin,
            isFrontSide = true,
            adminPhoneNumber = adminPhoneNumber,
            userEmail = userEmail,
            userPassword = userPassword
        )
        val back = createVipCardFromDetails(
            context = context,
            userName = userName,
            memberId = memberId,
            durationMonths = durationMonths,
            isAdmin = isAdmin,
            isFrontSide = false,
            adminPhoneNumber = adminPhoneNumber,
            userEmail = userEmail,
            userPassword = userPassword
        )
        return combineFrontAndBackBitmaps(front, back, userName)
    }

    /**
     * Membuat Bitmap Kartu VIP Membership berdasarkan detail lengkap
     */
    fun createVipCardFromDetails(
        context: Context,
        userName: String,
        memberId: String,
        durationMonths: Int,
        isAdmin: Boolean = false,
        isFrontSide: Boolean = false,
        adminPhoneNumber: String = "088806643415",
        qrBitmap: Bitmap? = null,
        userEmail: String = "",
        userPassword: String = ""
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val (primaryColor, gradientColors, tierTitle, durationLabel) = getTierTheme(durationMonths, isAdmin)

        // 1. Background Radial & Mesh Dots
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                CARD_WIDTH * 0.5f,
                CARD_HEIGHT * 0.4f,
                CARD_WIDTH * 0.8f,
                gradientColors,
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val cardRect = RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat())
        val cornerRadius = 36f
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        // Dot Mesh Texture
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(14, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val spacing = 28f
        var y = 0f
        var row = 0
        while (y < CARD_HEIGHT) {
            val xOffset = if (row % 2 == 0) 0f else spacing / 2f
            var x = xOffset
            while (x < CARD_WIDTH) {
                canvas.drawCircle(x, y, 2.5f, dotPaint)
                x += spacing
            }
            y += spacing * 0.75f
            row++
        }

        // 2. Beveled Dark Plates (Top-left & Bottom-right)
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 16, 18, 22)
            style = Paint.Style.FILL
        }
        val topPlatePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(CARD_WIDTH * 0.44f, 0f)
            lineTo(CARD_WIDTH * 0.34f, CARD_HEIGHT * 0.22f)
            lineTo(CARD_WIDTH * 0.12f, CARD_HEIGHT * 0.22f)
            lineTo(0f, CARD_HEIGHT * 0.38f)
            close()
        }
        canvas.drawPath(topPlatePath, platePaint)

        val bottomPlatePath = Path().apply {
            moveTo(0f, CARD_HEIGHT * 0.74f)
            lineTo(CARD_WIDTH * 0.38f, CARD_HEIGHT * 0.74f)
            lineTo(CARD_WIDTH * 0.58f, CARD_HEIGHT.toFloat())
            lineTo(0f, CARD_HEIGHT.toFloat())
            close()
        }
        canvas.drawPath(bottomPlatePath, platePaint)

        val cornerPlatePath = Path().apply {
            moveTo(CARD_WIDTH * 0.72f, CARD_HEIGHT.toFloat())
            lineTo(CARD_WIDTH * 0.58f, CARD_HEIGHT * 0.52f)
            lineTo(CARD_WIDTH * 0.82f, CARD_HEIGHT * 0.52f)
            lineTo(CARD_WIDTH.toFloat(), CARD_HEIGHT * 0.74f)
            lineTo(CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat())
            close()
        }
        canvas.drawPath(cornerPlatePath, platePaint)

        // Metallic Outer Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            shader = LinearGradient(
                0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
                intArrayOf(primaryColor, Color.rgb(255, 255, 255), primaryColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val borderRect = RectF(2f, 2f, CARD_WIDTH - 2f, CARD_HEIGHT - 2f)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        if (isFrontSide) {
            // ================= TAMPILAN DEPAN: "VIP" & QR CODE LOGIN CEPAT =================
            // Badge Tengah Persegi Panjang Berujung Sudut Terpotong (Chamfered)
            val badgeW = CARD_WIDTH * 0.76f
            val badgeH = 200f
            val badgeLeft = (CARD_WIDTH - badgeW) / 2f
            val badgeTop = (CARD_HEIGHT - badgeH) / 2f
            val cut = 32f

            val chamferedPath = Path().apply {
                moveTo(badgeLeft + cut, badgeTop)
                lineTo(badgeLeft + badgeW, badgeTop)
                lineTo(badgeLeft + badgeW, badgeTop + badgeH - cut)
                lineTo(badgeLeft + badgeW - cut, badgeTop + badgeH)
                lineTo(badgeLeft, badgeTop + badgeH)
                lineTo(badgeLeft, badgeTop + cut)
                close()
            }
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawPath(chamferedPath, badgeBgPaint)

            val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                shader = LinearGradient(
                    badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH,
                    intArrayOf(primaryColor, Color.WHITE, primaryColor),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(chamferedPath, badgeStrokePaint)

            // Teks "VIP" di Kiri Badge
            val vipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 86f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                shader = LinearGradient(
                    badgeLeft, badgeTop, badgeLeft + 200f, badgeTop + badgeH,
                    intArrayOf(Color.WHITE, primaryColor), null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawText("VIP", badgeLeft + 54f, badgeTop + 115f, vipPaint)

            // Subtitle "LOGIN CEPAT" (Menggantikan title "EXCLUSIVE ACCESS")
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.15f
                color = primaryColor
            }
            canvas.drawText("LOGIN CEPAT", badgeLeft + 56f, badgeTop + 155f, subPaint)

            // QR Code Login Cepat di Kanan Badge
            val effectivePassword = (if (userPassword.isNotBlank()) userPassword 
                else com.example.firebase.FirebaseAuthManager.getSavedPasswordForEmail(context, userEmail)).ifBlank {
                val googleId = userEmail.hashCode().toString()
                "GAuth#${googleId.take(10)}_${userEmail.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"
            }
            val quickLoginPayload = buildQuickLoginPayload(userEmail, effectivePassword)
            val quickLoginQr = qrBitmap ?: QrCodeHelper.generateQrBitmap(quickLoginPayload, 300)

            val qrSize = 155f
            val qrLeft = badgeLeft + badgeW - qrSize - 36f
            val qrTop = badgeTop + (badgeH - qrSize) / 2f

            // Frame Background Putih untuk QR Code
            val qrBgRect = RectF(qrLeft - 6f, qrTop - 6f, qrLeft + qrSize + 6f, qrTop + qrSize + 6f)
            val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(qrBgRect, 14f, 14f, qrBgPaint)

            if (quickLoginQr != null) {
                val destRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
                canvas.drawBitmap(quickLoginQr, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }

            // Teks panduan scan di samping kiri QR
            val qrLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.WHITE
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("SCAN QR", qrLeft - 16f, qrTop + 72f, qrLabelPaint)
            val qrSubLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = primaryColor
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("UNTUK MASUK", qrLeft - 16f, qrTop + 98f, qrSubLabelPaint)

            // Label Durasi di Kanan Atas
            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = primaryColor
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(durationLabel, CARD_WIDTH - 48f, 54f, tagPaint)

            // Footer Brand
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = Color.argb(120, 255, 255, 255)
                letterSpacing = 0.15f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("MY SINGGHARA OFFICIAL MEMBERSHIP", CARD_WIDTH / 2f, CARD_HEIGHT - 36f, footerPaint)

        } else {
            // ================= TAMPILAN BELAKANG / MEMBER (5 BINTANG, ID, NAMA) =================
            // 1. Gambar Bintang Emas di Kiri Atas Sesuai Masa Berlaku (1: 1★, 3: 2★, 5: 3★, 7: 4★, 12: 5★, Tanpa Batas: 6★)
            val starCount = getVipStarCount(durationMonths, isAdmin)
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                style = Paint.Style.FILL
            }
            val starStartX = 54f
            val starY = 64f
            val starSize = if (starCount >= 6) 20f else 22f
            val starSpacing = if (starCount >= 6) 33f else 38f
            for (i in 0 until starCount) {
                drawStar(canvas, starStartX + (i * starSpacing), starY, starSize, starPaint)
            }

            // 1.1 Logo Desain My Singghara di Bawah Icon Bintang
            val logoBitmap = try {
                BitmapFactory.decodeResource(context.resources, com.example.R.drawable.ic_app_logo)
                    ?: BitmapFactory.decodeResource(context.resources, com.example.R.drawable.img_app_icon_1786282576281)
            } catch (e: Exception) {
                null
            }
            if (logoBitmap != null) {
                val logoSize = 130f
                val logoLeft = 54f
                val logoTop = starY + 28f
                val logoRect = RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)

                // Background container
                val logoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(140, 0, 0, 0)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(logoRect, 20f, 20f, logoBgPaint)

                // Metallic Border
                val logoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = primaryColor
                }
                canvas.drawRoundRect(logoRect, 20f, 20f, logoBorderPaint)

                // Draw Logo Bitmap
                val padding = 8f
                val innerRect = RectF(logoLeft + padding, logoTop + padding, logoLeft + logoSize - padding, logoTop + logoSize - padding)
                val srcRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)
                canvas.drawBitmap(logoBitmap, srcRect, innerRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }

            // 2. Kotak ID Member di Kanan Atas
            val idBoxW = 420f
            val idBoxH = 120f
            val idBoxLeft = CARD_WIDTH - idBoxW - 44f
            val idBoxTop = 38f
            val idCut = 24f

            val idBoxPath = Path().apply {
                moveTo(idBoxLeft, idBoxTop)
                lineTo(idBoxLeft + idBoxW - idCut, idBoxTop)
                lineTo(idBoxLeft + idBoxW, idBoxTop + idCut)
                lineTo(idBoxLeft + idBoxW, idBoxTop + idBoxH)
                lineTo(idBoxLeft + idCut, idBoxTop + idBoxH)
                lineTo(idBoxLeft, idBoxTop + idBoxH - idCut)
                close()
            }
            val idBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawPath(idBoxPath, idBoxBg)

            val idBoxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                shader = LinearGradient(
                    idBoxLeft, idBoxTop, idBoxLeft + idBoxW, idBoxTop + idBoxH,
                    intArrayOf(primaryColor, Color.WHITE, primaryColor),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(idBoxPath, idBoxStroke)

            // Nomor ID Member Terformat (Tanpa Title "MEMBER ID")
            val formattedId = formatMemberId(memberId)
            val idTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                shader = LinearGradient(
                    idBoxLeft, idBoxTop, idBoxLeft + idBoxW, idBoxTop + idBoxH,
                    intArrayOf(Color.WHITE, primaryColor), null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawText(formattedId, idBoxLeft + (idBoxW / 2f), idBoxTop + 62f, idTextPaint)

            // Nomor Admin Default (Menggantikan Akses Resmi Terverifikasi)
            val adminNumText = if (adminPhoneNumber.isNotBlank()) adminPhoneNumber.trim() else "088806643415"
            val adminPhonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 21f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                color = primaryColor
            }
            canvas.drawText(adminNumText, idBoxLeft + (idBoxW / 2f), idBoxTop + 100f, adminPhonePaint)

            // QR Code (Opsional di Sisi Kanan Tengah)
            if (qrBitmap != null) {
                val qrSize = 140
                val qrDest = Rect(CARD_WIDTH - qrSize - 48, CARD_HEIGHT - qrSize - 120, CARD_WIDTH - 48, CARD_HEIGHT - 120)
                val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                val qrBgRect = RectF(qrDest.left - 6f, qrDest.top - 6f, qrDest.right + 6f, qrDest.bottom + 6f)
                canvas.drawRoundRect(qrBgRect, 10f, 10f, qrBgPaint)
                canvas.drawBitmap(qrBitmap, null, qrDest, null)
            }

            // 3. Nama Member Besar di Bawah
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.05f
                shader = LinearGradient(
                    48f, CARD_HEIGHT - 120f, CARD_WIDTH * 0.7f, CARD_HEIGHT - 60f,
                    intArrayOf(Color.WHITE, primaryColor), null, Shader.TileMode.CLAMP
                )
            }
            val displayName = if (userName.isNotBlank()) userName.uppercase() else "MEMBER NAME SURNAME"
            canvas.drawText(displayName, 54f, CARD_HEIGHT - 110f, namePaint)

            // Tier & Status Masa Berlaku
            val tierInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = primaryColor
            }
            canvas.drawText("$tierTitle • $durationLabel", 54f, CARD_HEIGHT - 62f, tierInfoPaint)

            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = Color.argb(160, 255, 255, 255)
            }
            canvas.drawText("STATUS: AKTIF & RESMI", 54f, CARD_HEIGHT - 32f, statusPaint)
        }

        return bitmap
    }

    /**
     * Menggabungkan Tampak Depan dan Tampak Belakang ke dalam 1 lembar A4 siap cetak dengan garis potong
     */
    fun combineFrontAndBackBitmaps(front: Bitmap, back: Bitmap, memberName: String): Bitmap {
        val sheetWidth = 1200
        val sheetHeight = 1680
        val combined = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)

        // Background Putih Kertas Cetak
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(15, 23, 42)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LEMBAR CETAK KARTU VIP MEMBERSHIP", sheetWidth / 2f, 65f, headerPaint)

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.rgb(100, 116, 139)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Nama: ${memberName.uppercase()} • Resolusi Cetak Standar Kartu ID CR80", sheetWidth / 2f, 100f, subHeaderPaint)

        // Garis batas pemisah
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.rgb(226, 232, 240)
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

        val cardW = 1050
        val cardH = 660
        val startX = (sheetWidth - cardW) / 2f

        // Sisi Depan
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(71, 85, 105)
        }
        canvas.drawText("▼ TAMPAK DEPAN (FRONT SIDE)", startX, 138f, labelPaint)
        canvas.drawBitmap(front, startX, 150f, null)

        // Garis Lipat / Potong di tengah
        val middleY = 850f
        canvas.drawLine(40f, middleY, sheetWidth - 40f, middleY, linePaint)
        val cutTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("- - - - - - - - - - - GARIS POTONG / LIPAT KARTU - - - - - - - - - - -", sheetWidth / 2f, middleY - 8f, cutTextPaint)

        // Sisi Belakang
        canvas.drawText("▼ TAMPAK BELAKANG (MEMBER SIDE)", startX, middleY + 44f, labelPaint)
        canvas.drawBitmap(back, startX, middleY + 56f, null)

        // Instruksi Cetak di bagian bawah
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Petunjuk: Cetak pada kertas tebal / foto paper (230-310 gsm), lalu potong dan laminasi / masukkan ke sleeve ID.", sheetWidth / 2f, sheetHeight - 35f, footerPaint)

        return combined
    }

    /**
     * Membuka Fitur Cetak Sistem Android (Print Spooler)
     * Memungkinkan cetak langsung ke printer Wi-Fi / Bluetooth atau Simpan sebagai PDF.
     */
    fun printVipCard(context: Context, bitmap: Bitmap, jobName: String = "VIP_Membership_Card") {
        try {
            val printHelper = PrintHelper(context).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
                colorMode = PrintHelper.COLOR_MODE_COLOR
            }
            printHelper.printBitmap(jobName, bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal membuka dialog cetak: ${e.message}")
        }
    }

    /**
     * Menyimpan dan Membagikan File Gambar Kartu VIP (PNG) ke Aplikasi Lain (WhatsApp, Telegram, dsb.)
     */
    fun saveAndShareVipCard(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val cleanName = fileName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val cacheFile = File(context.cacheDir, "${cleanName}_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Kartu VIP Membership Singghara")
                putExtra(Intent.EXTRA_TEXT, "Berikut adalah Kartu VIP Membership resmi My Singghara.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan / Cetak Kartu VIP").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal membagikan kartu: ${e.message}")
            null
        }
    }

    /**
     * Menyimpan Gambar ke Galeri Foto / Folder Download
     */
    fun saveCardToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val cleanName = fileName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val outputName = "${cleanName}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Singghara")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    MainViewModel.instance?.showToastPopup("Kartu berhasil disimpan ke Galeri Foto!")
                    true
                } else false
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val singgharaDir = File(picturesDir, "Singghara").apply { mkdirs() }
                val targetFile = File(singgharaDir, outputName)
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                MainViewModel.instance?.showToastPopup("Kartu berhasil disimpan ke Galeri!")
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MainViewModel.instance?.showToastPopup("Gagal menyimpan ke galeri: ${e.message}")
            false
        }
    }

    private data class TierInfo(
        val primaryColor: Int,
        val gradientColors: IntArray,
        val tierTitle: String,
        val durationLabel: String
    )

    private fun getTierTheme(months: Int, isAdmin: Boolean): TierInfo {
        if (isAdmin) {
            return TierInfo(
                primaryColor = Color.rgb(245, 158, 11),
                gradientColors = intArrayOf(Color.rgb(18, 16, 26), Color.rgb(26, 21, 38), Color.rgb(8, 7, 13)),
                tierTitle = "ADMINISTRATOR RESMI",
                durationLabel = "AKSES PERMANEN"
            )
        }
        return when (months) {
            1 -> TierInfo(
                primaryColor = Color.rgb(56, 189, 248),
                gradientColors = intArrayOf(Color.rgb(15, 23, 42), Color.rgb(22, 36, 61), Color.rgb(8, 13, 25)),
                tierTitle = "VIP BRONZE",
                durationLabel = "1 BULAN (30 HARI)"
            )
            3 -> TierInfo(
                primaryColor = Color.rgb(226, 232, 240),
                gradientColors = intArrayOf(Color.rgb(24, 27, 34), Color.rgb(36, 42, 53), Color.rgb(13, 15, 20)),
                tierTitle = "VIP SILVER",
                durationLabel = "3 BULAN (90 HARI)"
            )
            5 -> TierInfo(
                primaryColor = Color.rgb(234, 179, 8),
                gradientColors = intArrayOf(Color.rgb(28, 22, 12), Color.rgb(44, 34, 16), Color.rgb(14, 11, 6)),
                tierTitle = "VIP GOLD",
                durationLabel = "5 BULAN (150 HARI)"
            )
            7 -> TierInfo(
                primaryColor = Color.rgb(168, 85, 247),
                gradientColors = intArrayOf(Color.rgb(26, 16, 38), Color.rgb(39, 22, 58), Color.rgb(13, 8, 20)),
                tierTitle = "VIP PLATINUM",
                durationLabel = "7 BULAN (210 HARI)"
            )
            12 -> TierInfo(
                primaryColor = Color.rgb(245, 158, 11),
                gradientColors = intArrayOf(Color.rgb(30, 20, 10), Color.rgb(48, 30, 14), Color.rgb(15, 10, 5)),
                tierTitle = "VIP TITANIUM",
                durationLabel = "12 BULAN (1 TAHUN)"
            )
            else -> TierInfo(
                primaryColor = Color.rgb(234, 179, 8),
                gradientColors = intArrayOf(Color.rgb(24, 27, 34), Color.rgb(36, 42, 53), Color.rgb(13, 15, 20)),
                tierTitle = "VIP EXCLUSIVE",
                durationLabel = "OFFICIAL MEMBER"
            )
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        val innerRadius = radius * 0.42f
        var angle = -Math.PI / 2.0
        val step = Math.PI / 5.0

        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else innerRadius
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            angle += step
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
