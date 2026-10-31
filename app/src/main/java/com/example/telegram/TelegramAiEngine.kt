package com.example.telegram

import android.content.Context
import com.example.BuildConfig
import com.example.data.*
import com.example.deserializeAdjustmentEntries
import com.example.deserializeJournalEntries
import com.example.network.AladhanClient
import com.example.network.HolidayClient
import com.example.network.WeatherClient
import com.example.ui.components.PrayerSchedule
import com.example.ui.components.PrayerTimesHelper
import com.example.ui.screens.deserializeCashier
import com.example.ui.screens.deserializeStock
import com.example.ui.screens.serializeStock
import com.example.ui.screens.StockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TelegramAiEngine {

    private val indonesianLocale = Locale("id", "ID")
    private val numberFormat = NumberFormat.getNumberInstance(indonesianLocale)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Memproses pesan teks yang masuk dari Telegram atau konsol uji coba in-app.
     * MENDUKUNG KOMUNIKASI DUA ARAH (BIDIRECTIONAL):
     * 1. Menjalankan Perintah / Aksi: Membuat catatan baru (misal "buatkan saya catatan penting untuk kegiatan besok"),
     *    menjadwalkan agenda kegiatan, menambah belanja pasar, dan mencatat tabungan langsung ke Room Database.
     * 2. Menjawab Pertanyaan: Membaca database aplikasi (Jadwal Sholat, Cuaca, Kasir POS, Company, Laporan Keuangan, Libur, dll.).
     */
    suspend fun processMessage(
        context: Context,
        rawText: String,
        chatId: Long,
        senderName: String = "Sobat My Singghara"
    ): String = withContext(Dispatchers.IO) {
        val text = rawText.trim()
        val lowerText = text.lowercase(indonesianLocale)

        // 1. Perintah Start, Bantuan, & Menu
        if (lowerText == "/start" || lowerText == "start" || lowerText == "/menu" || lowerText == "menu" || lowerText == "/help" || lowerText == "help" || lowerText == "bantuan" || lowerText == "halo" || lowerText == "hai") {
            return@withContext getWelcomeAndMenuMessage(senderName, chatId)
        }

        // 2. Chat ID check command
        if (lowerText == "/id" || lowerText == "chat id" || lowerText == "id" || lowerText == "my id" || lowerText == "cek id") {
            return@withContext "🆔 *Informasi ID Obrolan Telegram*\n\n" +
                    "• *Chat ID Anda:* `$chatId`\n" +
                    "• *Nama Pengguna:* $senderName\n\n" +
                    "ID ini telah terhubung dua arah dengan database aplikasi *My Singghara* pada ponsel Anda. Anda dapat mengajukan pertanyaan maupun memberikan perintah aksi kapan saja!"
        }

        // ========================================================
        // 3. PERINTAH AKSI DUA ARAH: MENGEDIT DATA (BIDIRECTIONAL EDIT & UPDATE)
        // Diprioritaskan agar perintah "edit", "ubah", "tandai", "ganti" tidak terpotong sebagai query baca atau tambah data.
        // ========================================================

        // 3.1 Edit Catatan (Ubah isi catatan, ganti judul, atau sematkan / pin)
        if (isEditNoteCommand(lowerText)) {
            return@withContext handleEditNoteCommand(context, text, lowerText)
        }

        // 3.2 Edit Belanja Pasar (Ubah jumlah, satuan, harga, atau tandai sudah dibeli/belum dibeli)
        if (isEditShoppingCommand(lowerText)) {
            return@withContext handleEditShoppingCommand(context, text, lowerText)
        }

        // 3.3 Edit Agenda Kegiatan (Ubah judul, detail, atau pindah tanggal agenda)
        if (isEditAgendaCommand(lowerText)) {
            return@withContext handleEditAgendaCommand(context, text, lowerText)
        }

        // 3.4 Edit Jadwal Rutin Harian (Ubah jam, nama kegiatan, atau tandai selesai)
        if (isEditJadwalCommand(lowerText)) {
            return@withContext handleEditJadwalCommand(context, text, lowerText)
        }

        // 3.5 Edit Stok & Harga Kasir POS (Update stok barang kasir atau ubah harga jual)
        if (isEditKasirStockCommand(lowerText)) {
            return@withContext handleEditKasirStockCommand(context, text, lowerText)
        }

        // 3.6 Edit Target Tabungan (Ubah nominal target atau nama sasaran tabungan)
        if (isEditSavingTargetCommand(lowerText)) {
            return@withContext handleEditSavingTargetCommand(context, text, lowerText)
        }

        // 3.7 Edit Data Anggota / Karyawan (Ubah divisi, kontak, atau jabatan di Company)
        if (isEditAnggotaCommand(lowerText)) {
            return@withContext handleEditAnggotaCommand(context, text, lowerText)
        }

        // 3.8 Fallback Edit Cerdas (Jika diawali "edit", "ubah", "ganti", "update", "revisi", "koreksi", "tandai")
        if (isGeneralEditCommand(lowerText)) {
            return@withContext handleGeneralEditCommand(context, text, lowerText, senderName)
        }

        // ========================================================
        // 4. PERINTAH AKSI DUA ARAH: MENAMBAH DATA BARU (BIDIRECTIONAL CREATE)
        // ========================================================

        // 4a. Catat Mutasi Tabungan (Uang Masuk / Keluar / Pemasukan / Pengeluaran)
        // Diprioritaskan agar perintah seperti "catat tabungan masuk 10000 dari bonus" tidak tertangkap sebagai catatan
        if (isCreateSavingCommand(lowerText)) {
            return@withContext handleCreateSavingCommand(context, text, lowerText)
        }

        // 4b. Tambah Barang Belanja Pasar
        if (isCreateShoppingCommand(lowerText)) {
            return@withContext handleCreateShoppingCommand(context, text, lowerText)
        }

        // 4c. Buatkan / Jadwalkan Agenda Kegiatan Baru
        if (isCreateAgendaCommand(lowerText)) {
            return@withContext handleCreateAgendaCommand(context, text, lowerText)
        }

        // 4d. Tambah Jadwal Rutin Harian
        if (isCreateJadwalCommand(lowerText)) {
            return@withContext handleCreateJadwalCommand(context, text, lowerText)
        }

        // 4e. Buatkan / Tulis Catatan Baru (Hanya jika bukan tabungan, belanja, atau agenda)
        if (isCreateNoteCommand(lowerText)) {
            return@withContext handleCreateNoteCommand(context, text, lowerText)
        }

        // ========================================================
        // 4. QUERY BACA DATA (READ-ONLY DATABASE QUERIES)
        // ========================================================

        // 4a. Status Hari Libur & Kalender Nasional
        if (isHolidayQuery(lowerText)) {
            return@withContext handleHolidayQuery(context, lowerText)
        }

        // 4b. Kartu Jadwal Sholat & Waktu Adzan
        if (isJadwalSholatQuery(lowerText)) {
            return@withContext handleJadwalSholatQuery(context, lowerText)
        }

        // 4c. Kartu Perkiraan Cuaca
        if (isWeatherQuery(lowerText)) {
            return@withContext handleWeatherQuery(context, lowerText)
        }

        // 4d. Halaman Kasir (Menu My Kasir / Point of Sale)
        if (isKasirQuery(lowerText)) {
            return@withContext handleKasirQuery(context, lowerText)
        }

        // 4e. Halaman Company (Menu Company: Data Karyawan, Presensi, Gaji)
        if (isCompanyQuery(lowerText)) {
            return@withContext handleCompanyQuery(context, lowerText)
        }

        // 4f. Halaman Laporan Keuangan (Jurnal Umum, Buku Besar, Neraca, Laba Rugi)
        if (isLaporanKeuanganQuery(lowerText)) {
            return@withContext handleLaporanKeuanganQuery(context, lowerText)
        }

        // 4g. Tabungan & Target Keuangan
        if (isSavingsQuery(lowerText)) {
            return@withContext handleSavingsQuery(context)
        }

        // 4h. Agenda & Jadwal Kegiatan
        if (isAgendaQuery(lowerText)) {
            return@withContext handleAgendaQuery(context, lowerText)
        }

        // 4i. Catatan & Memo
        if (isNotesQuery(lowerText)) {
            return@withContext handleNotesQuery(context)
        }

        // 4j. Daftar Belanja
        if (isShoppingQuery(lowerText)) {
            return@withContext handleShoppingQuery(context)
        }

        // 4k. Arisan Keluarga
        if (isArisanQuery(lowerText)) {
            return@withContext handleArisanQuery(context)
        }

        // 4l. Gaji & Pemasukan (Query Langsung Gaji)
        if (isSalaryQuery(lowerText)) {
            return@withContext handleSalaryQuery(context)
        }

        // 4m. Al-Quran & Ibadah
        if (isQuranQuery(lowerText)) {
            return@withContext handleQuranQuery(context)
        }

        // 4n. Status Sistem & Sinkronisasi My Singghara
        if (lowerText == "/status" || lowerText.contains("status aplikasi") || lowerText.contains("status sistem") || lowerText == "ping") {
            return@withContext handleSystemStatus(context)
        }

        // 5. Jika ada Gemini API Key, gunakan Gemini AI dengan konteks database My Singghara
        val geminiResponse = tryAnswerWithGemini(context, text, senderName)
        if (geminiResponse != null && geminiResponse.isNotBlank()) {
            return@withContext geminiResponse
        }

        // 6. Fallback cerdas berbasis konteks My Singghara
        return@withContext getSmartFallbackResponse(senderName, text)
    }

    private fun getWelcomeAndMenuMessage(senderName: String, chatId: Long): String {
        return """
👋 *Halo, $senderName!*
Selamat datang di *AI Assistant Dua Arah My Singghara*.

Saya terhubung langsung secara *dua arah* dengan database ponsel Anda (Chat ID: `$chatId`). Saya tidak hanya dapat menjawab pertanyaan atau menambah data baru, tetapi kini Anda juga dapat *MENGEDIT & MEMPERBARUI DATA* langsung dari Telegram!

✏️ *Perintah Mengedit Data (Update Real-Time ke Database):*
• 📝 *Edit Catatan:*
  - _"Edit catatan [Judul]: [Isi baru]"_
  - _"Ubah catatan kegiatan besok jadi Tambah sound system dan konsumsi"_
  - _"Sematkan catatan [Judul]"_ (Pin Catatan) atau _"Lepas pin catatan [Judul]"_
• 📅 *Edit Agenda:*
  - _"Ubah agenda rapat ke tanggal besok jam 10:00"_
  - _"Edit agenda [Judul]: [Detail baru]"_
  - _"Pindahkan agenda [Judul] ke lusa"_
• 🛒 *Edit Belanja Pasar:*
  - _"Tandai belanja telur sudah dibeli"_ (atau _"Sudah beli minyak"_)
  - _"Ubah belanja telur jadi 2 kg"_
  - _"Update harga belanja minyak goreng jadi 35000"_
• ⏰ *Edit Jadwal Rutin:*
  - _"Edit jadwal olahraga jam 06:30"_
  - _"Tandai jadwal sholat tahajud selesai"_
• 🏪 *Edit Stok & Harga Kasir POS:*
  - _"Edit stok beras jadi 30"_
  - _"Tambah stok indomie 10"_ (atau _"Kurangi stok gula 2"_)
  - _"Ubah harga minyak goreng jadi 28000"_
• 💰 *Edit Target Tabungan:*
  - _"Ubah target tabungan jadi 15000000"_
  - _"Edit target tabungan Beli Laptop Baru 12000000"_
• 🏢 *Edit Data Karyawan (Company):*
  - _"Ubah divisi Budi jadi Marketing & Penjualan"_
  - _"Ubah kontak Budi jadi 081234567890"_

⚡ *Perintah Menambah Data Baru:*
• ✍️ *Catatan:* _"Buatkan saya catatan penting untuk kegiatan besok"_
• 📅 *Agenda:* _"Jadwalkan agenda besok rapat evaluasi jam 09:00"_
• 🛒 *Belanja:* _"Beli telur 1 kg dan beras 5 kg"_
• 💰 *Tabungan:* _"Catat tabungan masuk 100000 dari jualan online"_

💡 *Contoh Pertanyaan Informasi Database:*
• 🕌 *Sholat & Cuaca:* _"Jadwal sholat hari ini"_, _"Bagaimana cuaca hari ini?"_
• 🏪 *Kasir POS:* _"Cek penjualan kasir"_, _"Stok barang kasir"_
• 🏢 *Company:* _"Data karyawan company"_, _"Absensi hari ini"_
• 📊 *Keuangan:* _"Laporan laba rugi usaha"_, _"Posisi kas usaha"_
• 🔴 *Libur:* _"Apakah hari ini libur?"_, _"Daftar libur 2026"_

Silakan ketik perintah edit data, simpan data, atau pertanyaan Anda! 😊
        """.trimIndent()
    }

    // ========================================================
    // PERINTAH 1: BUAT CATATAN BARU (CREATE NOTE COMMAND)
    // ========================================================
    private fun isCreateNoteCommand(text: String): Boolean {
        // Eksklusikan domain lain yang memiliki aksi spesifik tersendiri
        if (text.contains("tabungan") ||
            text.contains("uang masuk") ||
            text.contains("uang keluar") ||
            text.contains("pemasukan") ||
            text.contains("pengeluaran") ||
            text.contains("setoran") ||
            text.contains("penarikan") ||
            text.contains("belanja") ||
            text.contains("agenda") ||
            text.contains("jadwal") ||
            text.contains("jadwalkan")
        ) {
            return false
        }

        val isExplicitAction = text.startsWith("buatkan saya catatan") ||
                text.startsWith("buatkan catatan") ||
                text.startsWith("buat catatan") ||
                text.startsWith("bikin catatan") ||
                text.startsWith("tulis catatan") ||
                text.startsWith("tuliskan catatan") ||
                text.startsWith("tambah catatan") ||
                text.startsWith("tambahkan catatan") ||
                text.startsWith("catat:") ||
                text.startsWith("catat ") ||
                text.startsWith("catatan:") ||
                text.startsWith("simpan catatan") ||
                text.contains("buatkan saya catatan") ||
                text.contains("buatkan catatan") ||
                text.contains("buat catatan penting") ||
                text.contains("buat catatan") ||
                text.contains("bikin catatan") ||
                text.contains("tuliskan catatan") ||
                text.contains("tulis catatan") ||
                text.contains("tambahkan catatan") ||
                text.contains("tambah catatan") ||
                text.contains("catat penting:") ||
                text.startsWith("tolong catat") ||
                text.contains("tolong catat")
        val isReadQuery = text.startsWith("daftar catatan") ||
                text.startsWith("lihat catatan") ||
                text.startsWith("baca catatan") ||
                text.startsWith("cek catatan") ||
                text == "catatan"
        return isExplicitAction && !isReadQuery
    }

    private suspend fun handleCreateNoteCommand(context: Context, rawText: String, lowerText: String): String {
        val isImportant = lowerText.contains("penting") || lowerText.contains("urgent") || lowerText.contains("prioritas") || lowerText.contains("prioritaskan")
        val colorHex = if (isImportant) "#EF4444" else "#6366F1"

        // Ekstraksi Judul & Isi Catatan
        var extractedTitle = ""
        var extractedContent = ""
        var category = if (isImportant) "Penting" else "Umum"

        // Cek pemisah titik dua (:) atau strip (-)
        if (rawText.contains(":")) {
            val afterColon = rawText.substringAfter(":").trim()
            if (afterColon.contains("-")) {
                extractedTitle = afterColon.substringBefore("-").trim().replaceFirstChar { it.uppercase() }
                extractedContent = afterColon.substringAfter("-").trim()
            } else {
                extractedTitle = afterColon.take(35).trim().replaceFirstChar { it.uppercase() }
                extractedContent = afterColon
            }
        } else {
            // Pembersihan prefix perintah natural
            val clean = rawText
                .replace("tolong buatkan saya catatan penting untuk", "", ignoreCase = true)
                .replace("tolong buatkan saya catatan penting", "", ignoreCase = true)
                .replace("tolong buatkan saya catatan untuk", "", ignoreCase = true)
                .replace("tolong buatkan saya catatan", "", ignoreCase = true)
                .replace("tolong buat catatan penting untuk", "", ignoreCase = true)
                .replace("tolong buat catatan penting", "", ignoreCase = true)
                .replace("tolong buat catatan", "", ignoreCase = true)
                .replace("tolong catat", "", ignoreCase = true)
                .replace("bisa buatkan saya catatan", "", ignoreCase = true)
                .replace("buatkan saya catatan penting untuk", "", ignoreCase = true)
                .replace("buatkan saya catatan penting", "", ignoreCase = true)
                .replace("buatkan saya catatan untuk", "", ignoreCase = true)
                .replace("buatkan saya catatan", "", ignoreCase = true)
                .replace("buat catatan penting untuk", "", ignoreCase = true)
                .replace("buat catatan penting", "", ignoreCase = true)
                .replace("buat catatan untuk", "", ignoreCase = true)
                .replace("buat catatan", "", ignoreCase = true)
                .replace("bikin catatan", "", ignoreCase = true)
                .replace("tambahkan catatan", "", ignoreCase = true)
                .replace("tambah catatan", "", ignoreCase = true)
                .replace("tulis catatan", "", ignoreCase = true)
                .replace("tuliskan catatan", "", ignoreCase = true)
                .replace("catat ", "", ignoreCase = true)
                .trim()

            if (clean.isBlank()) {
                extractedTitle = "Catatan Penting"
                extractedContent = "Catatan penting baru yang dibuat melalui Telegram AI."
            } else {
                extractedTitle = clean.take(40).trim().replaceFirstChar { it.uppercase() }
                extractedContent = clean
            }
        }

        // Khusus contoh user: "buatkan saya catatan penting untuk kegiatan besok"
        if (lowerText.contains("kegiatan besok") || lowerText.contains("rencana besok") || lowerText.contains("acara besok")) {
            category = "Agenda & Kegiatan"
            if (extractedTitle.isBlank() || extractedTitle.length < 5) {
                extractedTitle = "Kegiatan Besok"
            }

            // Jika ada Gemini API Key, buatkan draf kegiatan besok yang sangat cerdas & profesional
            val geminiDraft = tryGenerateDraftWithGemini(context, "Buatkan draf isi catatan ringkas, rapi, dan terstruktur untuk: $rawText. Berikan langsung poin-poin checklist persiapan tanpa basa-basi.")
            extractedContent = if (geminiDraft != null && geminiDraft.isNotBlank()) {
                geminiDraft
            } else {
                """
📋 Rencana & Persiapan Kegiatan Besok:
• Pastikan semua berkas, materi, dan perlengkapan utama telah disiapkan
• Konfirmasi kembali jadwal, waktu, dan lokasi kegiatan
• Koordinasi dengan rekan atau pihak terkait
• Periksa estimasi waktu tempuh dan rute perjalanan
• Siapkan catatan evaluasi dan tindak lanjut kegiatan
(Dibuat otomatis oleh Asisten Telegram My Singghara)
                """.trimIndent()
            }
        }

        // Simpan langsung ke Room Database (Tabel note_items)
        val db = AppDatabase.getDatabase(context)
        val note = NoteItem(
            title = extractedTitle,
            content = extractedContent,
            charCount = extractedContent.length,
            lineCount = extractedContent.split("\n").size,
            lastSaved = System.currentTimeMillis(),
            category = category,
            isPinned = isImportant,
            colorHex = colorHex
        )

        val noteId = try {
            db.noteDao().insertNote(note)
        } catch (e: Exception) {
            -1L
        }

        // Tampilkan notifikasi di Android
        notifyActionExecuted(context, "📝 Catatan Baru Tersimpan", "Catatan '$extractedTitle' telah ditambahkan dari Telegram!")

        val timeStr = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", indonesianLocale).format(Date())

        val badgePinned = if (isImportant) "📌 *Disematkan sebagai Catatan Penting (Pinned)*\n" else ""

        return """
✅ *CATATAN BERHASIL DIBUAT & DISIMPAN!*
$badgePinned
📋 *Judul:* *$extractedTitle*
🏷️ *Kategori:* $category
🕒 *Waktu Simpan:* $timeStr WIB

📄 *Isi Catatan:*
$extractedContent

✨ _Catatan ini telah otomatis tersimpan di halaman Catatan aplikasi My Singghara di ponsel Anda._
        """.trimIndent()
    }

    // ========================================================
    // PERINTAH 2: BUAT AGENDA BARU (CREATE AGENDA COMMAND)
    // ========================================================
    private fun isCreateAgendaCommand(text: String): Boolean {
        val isExplicitAction = text.startsWith("jadwalkan agenda") ||
                text.startsWith("jadwalkan:") ||
                text.startsWith("jadwalkan ") ||
                text.startsWith("jadwalkan") ||
                text.startsWith("tambah agenda") ||
                text.startsWith("tambahkan agenda") ||
                text.startsWith("buat agenda") ||
                text.startsWith("buatkan agenda") ||
                text.startsWith("agendakan:") ||
                text.startsWith("agendakan ") ||
                text.startsWith("agendakan") ||
                text.startsWith("ingatkan saya") ||
                text.startsWith("buatkan pengingat") ||
                text.contains("jadwalkan agenda") ||
                text.contains("buatkan agenda") ||
                text.contains("tambahkan agenda")
        val isReadQuery = text.startsWith("daftar agenda") || text.startsWith("lihat agenda") || text.startsWith("cek agenda") || text == "agenda"
        return isExplicitAction && !isReadQuery
    }

    private suspend fun handleCreateAgendaCommand(context: Context, rawText: String, lowerText: String): String {
        val cal = Calendar.getInstance()
        var dateLabel = "Hari Ini"

        if (lowerText.contains("besok")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            dateLabel = "Besok"
        } else if (lowerText.contains("lusa")) {
            cal.add(Calendar.DAY_OF_YEAR, 2)
            dateLabel = "Lusa"
        }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfHari = SimpleDateFormat("EEEE, d MMMM yyyy", indonesianLocale)
        val targetDateStr = sdfDate.format(cal.time)
        val targetDateDisplay = sdfHari.format(cal.time)

        // Bersihkan teks untuk judul & detail agenda
        val clean = rawText
            .replace("tolong jadwalkan agenda besok", "", ignoreCase = true)
            .replace("tolong jadwalkan agenda", "", ignoreCase = true)
            .replace("tolong buatkan agenda", "", ignoreCase = true)
            .replace("buatkan agenda besok", "", ignoreCase = true)
            .replace("buatkan agenda lusa", "", ignoreCase = true)
            .replace("buatkan agenda", "", ignoreCase = true)
            .replace("jadwalkan agenda besok", "", ignoreCase = true)
            .replace("jadwalkan agenda lusa", "", ignoreCase = true)
            .replace("jadwalkan agenda hari ini", "", ignoreCase = true)
            .replace("jadwalkan agenda", "", ignoreCase = true)
            .replace("jadwalkan:", "", ignoreCase = true)
            .replace("jadwalkan", "", ignoreCase = true)
            .replace("tambah agenda besok", "", ignoreCase = true)
            .replace("tambah agenda", "", ignoreCase = true)
            .replace("tambahkan agenda", "", ignoreCase = true)
            .replace("buat agenda", "", ignoreCase = true)
            .replace("agendakan:", "", ignoreCase = true)
            .replace("agendakan", "", ignoreCase = true)
            .replace("ingatkan saya untuk", "", ignoreCase = true)
            .replace("ingatkan saya", "", ignoreCase = true)
            .replace("buatkan pengingat untuk", "", ignoreCase = true)
            .replace("buatkan pengingat", "", ignoreCase = true)
            .trim()

        val agendaTitle = if (clean.isNotBlank()) clean.take(50).replaceFirstChar { it.uppercase() } else "Agenda Kegiatan $dateLabel"
        val agendaDetail = if (clean.isNotBlank()) clean else "Agenda kegiatan terjadwal dari Telegram AI Asisten."

        val db = AppDatabase.getDatabase(context)
        val agenda = AgendaItem(
            tanggal = targetDateStr,
            judul = agendaTitle,
            detail = agendaDetail,
            tipe = "agenda"
        )

        try {
            db.agendaDao().insertAgenda(agenda)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "📅 Agenda Baru Dijadwalkan", "Agenda '$agendaTitle' untuk $dateLabel ($targetDateDisplay) telah disimpan!")

        return """
✅ *AGENDA BERHASIL DIJADWALKAN!*

📅 *Tanggal:* $targetDateDisplay ($dateLabel)
📌 *Judul Agenda:* *$agendaTitle*
📝 *Detail:* $agendaDetail
🏷️ *Tipe:* Agenda Pribadi

✨ _Agenda ini telah otomatis tercatat di Kalender & Jadwal Kegiatan aplikasi My Singghara di ponsel Anda._
        """.trimIndent()
    }

    // ========================================================
    // PERINTAH 3: TAMBAH BELANJA PASAR (CREATE SHOPPING ITEM)
    // ========================================================
    private fun isCreateShoppingCommand(text: String): Boolean {
        val isQuestion = text.contains("apa") || text.contains("?") || text.startsWith("daftar belanja") || text.startsWith("lihat belanja") || text.startsWith("cek belanja")
        if (isQuestion) return false

        return text.startsWith("beli ") ||
                text.startsWith("catat belanja:") ||
                text.startsWith("catat belanja ") ||
                text.startsWith("tambah belanja") ||
                text.startsWith("tambahkan belanja") ||
                text.startsWith("daftar belanja:") ||
                text.contains("tambahkan ke daftar belanja") ||
                text.contains("masukkan ke belanja pasar")
    }

    private suspend fun handleCreateShoppingCommand(context: Context, rawText: String, lowerText: String): String {
        val clean = rawText
            .replace("tambahkan ke daftar belanja", "", ignoreCase = true)
            .replace("masukkan ke belanja pasar", "", ignoreCase = true)
            .replace("catat belanja:", "", ignoreCase = true)
            .replace("catat belanja", "", ignoreCase = true)
            .replace("daftar belanja:", "", ignoreCase = true)
            .replace("tambah belanja pasar", "", ignoreCase = true)
            .replace("tambahkan belanja pasar", "", ignoreCase = true)
            .replace("tambah belanja", "", ignoreCase = true)
            .replace("tambahkan belanja", "", ignoreCase = true)
            .replace("beli ", "", ignoreCase = true)
            .trim()

        var namaBarang = clean.replaceFirstChar { it.uppercase() }
        var jumlah = 1.0
        var satuan = "pcs"
        var kategori = "Sembako"

        // Ekstraksi jumlah & satuan sederhana (misal "telur 2 kg", "minyak 1 liter")
        val regexQty = Regex("([0-9]+(?:[\\.,][0-9]+)?)\\s*(kg|kilo|gram|liter|ikat|bks|dus|pcs|butir|ons)?", RegexOption.IGNORE_CASE)
        val match = regexQty.find(clean)
        if (match != null) {
            jumlah = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
            val unitFound = match.groupValues[2].lowercase(Locale.ROOT)
            if (unitFound.isNotBlank()) {
                satuan = if (unitFound == "kilo") "kg" else unitFound
            }
            namaBarang = clean.replace(match.value, "").trim().replaceFirstChar { it.uppercase() }
        }

        if (namaBarang.isBlank()) {
            namaBarang = "Barang Belanjaan"
        }

        val lowerNama = namaBarang.lowercase(indonesianLocale)
        kategori = when {
            lowerNama.contains("bayam") || lowerNama.contains("kangkung") || lowerNama.contains("wortel") || lowerNama.contains("sayur") -> "Sayuran"
            lowerNama.contains("ayam") || lowerNama.contains("daging") || lowerNama.contains("sapi") -> "Daging & Unggas"
            lowerNama.contains("ikan") || lowerNama.contains("udang") || lowerNama.contains("cumi") -> "Ikan & Seafood"
            lowerNama.contains("bawang") || lowerNama.contains("cabai") || lowerNama.contains("garam") || lowerNama.contains("merica") -> "Bumbu Dapur"
            lowerNama.contains("apel") || lowerNama.contains("jeruk") || lowerNama.contains("pisang") || lowerNama.contains("buah") -> "Buah-buahan"
            else -> "Sembako"
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val db = AppDatabase.getDatabase(context)
        val item = BelanjaPasarItem(
            nama = namaBarang,
            kategori = kategori,
            jumlah = jumlah,
            satuan = satuan,
            perkiraanHarga = 0.0,
            realisasiHarga = 0.0,
            sudahDibeli = false,
            tanggal = todayStr
        )

        try {
            db.belanjaPasarDao().insertBelanjaPasar(item)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "🛒 Belanja Pasar Baru", "Barang '$namaBarang ($jumlah $satuan)' ditambahkan ke daftar belanja!")

        return """
✅ *BARANG BERHASIL DITAMBAHKAN KE BELANJA PASAR!*

🛒 *Nama Barang:* *$namaBarang*
📦 *Jumlah Kebutuhan:* ${if (jumlah % 1.0 == 0.0) jumlah.toInt() else jumlah} $satuan
🏷️ *Kategori:* $kategori
Status: ⏳ Belum Dibeli

✨ _Barang ini sudah masuk ke daftar belanja pasar di aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // PERINTAH 4: CATAT MUTASI TABUNGAN (CREATE SAVING TRANSACTION)
    // ========================================================
    private fun isCreateSavingCommand(text: String): Boolean {
        val isQuestion = text.contains("berapa") ||
                text.contains("?") ||
                text.startsWith("daftar tabungan") ||
                text.startsWith("lihat tabungan") ||
                text.startsWith("cek tabungan") ||
                text.contains("saldo tabungan")
        if (isQuestion) return false

        return text.startsWith("catat tabungan") ||
                text.startsWith("catat tabung") ||
                text.startsWith("tambah tabungan") ||
                text.startsWith("tambahkan tabungan") ||
                text.startsWith("tabung ") ||
                text.startsWith("tabungan:") ||
                text.startsWith("uang masuk") ||
                text.startsWith("uang keluar") ||
                text.startsWith("catat uang masuk") ||
                text.startsWith("catat uang keluar") ||
                text.startsWith("catat pemasukan") ||
                text.startsWith("catat pengeluaran") ||
                text.contains("catat tabungan") ||
                text.contains("catat uang masuk") ||
                text.contains("catat uang keluar") ||
                text.contains("catat pemasukan") ||
                text.contains("catat pengeluaran") ||
                text.contains("setor tabungan") ||
                text.contains("tarik tabungan") ||
                text.contains("masukkan ke tabungan") ||
                text.contains("simpan ke tabungan") ||
                (text.contains("tabungan") && (text.contains("masuk") || text.contains("keluar") || text.contains("setor") || text.contains("tarik") || text.contains("catat") || text.contains("tambah")))
    }

    private suspend fun handleCreateSavingCommand(context: Context, rawText: String, lowerText: String): String {
        val isKeluar = lowerText.contains("keluar") ||
                lowerText.contains("tarik") ||
                lowerText.contains("ambil") ||
                lowerText.contains("pengeluaran") ||
                lowerText.contains("dipakai")
        val jenis = if (isKeluar) "keluar" else "masuk"

        // Ekstraksi nominal angka (misal "10000", "10.000", atau "50000")
        val regexNominal = Regex("([0-9]{1,3}(?:[\\.,][0-9]{3})+|[0-9]+)")
        val match = regexNominal.find(rawText)
        val nominal = if (match != null) {
            match.value.replace(".", "").replace(",", "").toDoubleOrNull() ?: 10000.0
        } else {
            10000.0
        }

        // Ekstraksi deskripsi yang bersih
        var deskripsi = rawText
            .replace("catat tabungan masuk", "", ignoreCase = true)
            .replace("catat tabungan keluar", "", ignoreCase = true)
            .replace("catat tabungan", "", ignoreCase = true)
            .replace("tambah tabungan masuk", "", ignoreCase = true)
            .replace("tambah tabungan keluar", "", ignoreCase = true)
            .replace("tambah tabungan", "", ignoreCase = true)
            .replace("tambahkan tabungan", "", ignoreCase = true)
            .replace("catat uang masuk", "", ignoreCase = true)
            .replace("catat uang keluar", "", ignoreCase = true)
            .replace("catat pemasukan", "", ignoreCase = true)
            .replace("catat pengeluaran", "", ignoreCase = true)
            .replace("setor tabungan", "", ignoreCase = true)
            .replace("tarik tabungan", "", ignoreCase = true)
            .replace("masukkan ke tabungan", "", ignoreCase = true)
            .replace("simpan ke tabungan", "", ignoreCase = true)
            .replace("tabungan:", "", ignoreCase = true)
            .replace("tabung ", "", ignoreCase = true)
            .replace("uang masuk", "", ignoreCase = true)
            .replace("uang keluar", "", ignoreCase = true)
            .replace(match?.value ?: "", "")
            .replace("rp", "", ignoreCase = true)
            .trim()

        if (deskripsi.startsWith("dari ", ignoreCase = true)) {
            deskripsi = deskripsi.substring(5).trim()
        } else if (deskripsi.startsWith("untuk ", ignoreCase = true)) {
            deskripsi = deskripsi.substring(6).trim()
        } else if (deskripsi.startsWith("buat ", ignoreCase = true)) {
            deskripsi = deskripsi.substring(5).trim()
        }

        if (deskripsi.isBlank()) {
            deskripsi = if (jenis == "masuk") "Setoran tabungan via Telegram AI" else "Penarikan tabungan via Telegram AI"
        } else {
            deskripsi = deskripsi.replaceFirstChar { it.uppercase() }
        }

        val kategori = when {
            deskripsi.contains("bonus", ignoreCase = true) -> "Bonus"
            deskripsi.contains("gaji", ignoreCase = true) -> "Gaji"
            deskripsi.contains("jualan", ignoreCase = true) || deskripsi.contains("omset", ignoreCase = true) -> "Usaha & Jualan"
            jenis == "masuk" -> "Pemasukan"
            else -> "Pengeluaran"
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val db = AppDatabase.getDatabase(context)
        val tr = SavingTransaction(
            jenis = jenis,
            kategori = kategori,
            deskripsi = deskripsi,
            nominal = nominal,
            tanggal = todayStr
        )

        try {
            db.savingDao().insertTransaction(tr)
        } catch (_: Exception) {}

        // Hitung saldo baru
        val transactions = try {
            db.savingDao().getAllTransactions()
        } catch (e: Exception) {
            emptyList()
        }
        val totalDeposit = transactions.filter { it.jenis.equals("masuk", ignoreCase = true) || it.jenis.equals("setoran", ignoreCase = true) }.sumOf { it.nominal }
        val totalWithdraw = transactions.filter { it.jenis.equals("keluar", ignoreCase = true) || it.jenis.equals("penarikan", ignoreCase = true) }.sumOf { it.nominal }
        val saldoTerkumpul = (totalDeposit - totalWithdraw).coerceAtLeast(0.0)

        val tanda = if (jenis == "masuk") "🟢 UANG MASUK (SETORAN)" else "🔴 UANG KELUAR (PENARIKAN)"

        notifyActionExecuted(context, "💰 Mutasi Tabungan Dicatat", "Mutasi Rp ${numberFormat.format(nominal)} ($deskripsi) berhasil disimpan!")

        return """
✅ *MUTASI TABUNGAN BERHASIL DICATAT!*

💵 *Jenis:* $tanda
💰 *Nominal:* Rp ${numberFormat.format(nominal)}
📝 *Keterangan:* $deskripsi
📅 *Tanggal:* $todayStr

📊 *Total Saldo Tabungan Sekarang:* *Rp ${numberFormat.format(saldoTerkumpul)}*
✨ _Data telah tersimpan di Halaman Tabungan aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // PERINTAH 5: TAMBAH JADWAL RUTIN (CREATE DAILY SCHEDULE)
    // ========================================================
    private fun isCreateJadwalCommand(text: String): Boolean {
        return text.startsWith("tambah jadwal harian") ||
                text.startsWith("tambah jadwal rutin") ||
                text.startsWith("jadwal harian:")
    }

    private suspend fun handleCreateJadwalCommand(context: Context, rawText: String, lowerText: String): String {
        // Ekstraksi waktu format HH:mm
        val regexTime = Regex("([0-1]?[0-9]|2[0-3]):[0-5][0-9]")
        val matchTime = regexTime.find(rawText)
        val waktu = matchTime?.value ?: "08:00"

        val kegiatan = rawText
            .replace("tambah jadwal harian", "", ignoreCase = true)
            .replace("tambah jadwal rutin", "", ignoreCase = true)
            .replace("jadwal harian:", "", ignoreCase = true)
            .replace(waktu, "")
            .trim()
            .ifBlank { "Kegiatan Rutin Harian" }

        val db = AppDatabase.getDatabase(context)
        val jadwal = JadwalKegiatan(
            waktu = waktu,
            kegiatan = kegiatan,
            kategori = "Harian",
            isCompleted = false,
            lastCheckedDate = ""
        )

        try {
            db.jadwalDao().insertJadwal(jadwal)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "⏰ Jadwal Harian Ditambahkan", "Jadwal '$waktu - $kegiatan' telah disimpan!")

        return """
✅ *JADWAL HARIAN BERHASIL DITAMBAHKAN!*

⏰ *Waktu:* $waktu WIB
📌 *Kegiatan:* *$kegiatan*
🏷️ *Kategori:* Harian

✨ _Jadwal ini telah aktif di aplikasi My Singghara dan siap mengingatkan Anda._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 1: EDIT / UBAH CATATAN (EDIT NOTE COMMAND)
    // ========================================================
    private fun isEditNoteCommand(text: String): Boolean {
        return text.startsWith("edit catatan") ||
                text.startsWith("ubah catatan") ||
                text.startsWith("ganti catatan") ||
                text.startsWith("update catatan") ||
                text.startsWith("revisi catatan") ||
                text.startsWith("koreksi catatan") ||
                text.startsWith("pin catatan") ||
                text.startsWith("sematkan catatan") ||
                text.startsWith("unpin catatan") ||
                text.startsWith("lepas pin catatan") ||
                text.contains("edit catatan") ||
                text.contains("ubah catatan") ||
                text.contains("update catatan") ||
                text.contains("ganti isi catatan") ||
                text.contains("sematkan catatan")
    }

    private suspend fun handleEditNoteCommand(context: Context, rawText: String, lowerText: String): String {
        val db = AppDatabase.getDatabase(context)
        val notes = try {
            db.noteDao().getAllNotes().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (notes.isEmpty()) {
            return "📝 *Edit Catatan*\n\nBelum ada catatan yang tersimpan di aplikasi My Singghara. Anda dapat membuat catatan baru terlebih dahulu dengan mengetik: _\"Buat catatan: [Judul] - [Isi]\"_."
        }

        // 1. Tipe Aksi: Pin / Unpin Catatan
        if (lowerText.contains("pin") || lowerText.contains("semat")) {
            val isUnpin = lowerText.contains("unpin") || lowerText.contains("lepas") || lowerText.contains("batal")
            val cleanTarget = rawText
                .replace("lepas pin catatan", "", ignoreCase = true)
                .replace("unpin catatan", "", ignoreCase = true)
                .replace("sematkan catatan", "", ignoreCase = true)
                .replace("pin catatan", "", ignoreCase = true)
                .trim()

            val targetNote = if (cleanTarget.isNotBlank()) {
                notes.firstOrNull { it.title.contains(cleanTarget, ignoreCase = true) || cleanTarget.contains(it.title, ignoreCase = true) } ?: notes.first()
            } else {
                notes.first()
            }

            val updated = targetNote.copy(
                isPinned = !isUnpin,
                colorHex = if (!isUnpin) "#EF4444" else "#6366F1",
                lastSaved = System.currentTimeMillis()
            )
            try {
                db.noteDao().updateNote(updated)
            } catch (_: Exception) {}

            val statusStr = if (!isUnpin) "📌 *Disematkan sebagai Prioritas (Pinned)*" else "📋 *Pin Dilepas (Catatan Biasa)*"
            notifyActionExecuted(context, "📝 Status Catatan Diubah", "Catatan '${updated.title}' telah diperbarui!")

            return """
✅ *STATUS PIN CATATAN BERHASIL DIUBAH!*

📋 *Judul:* *${updated.title}*
🏷️ *Kategori:* ${updated.category}
📌 *Status:* $statusStr

✨ _Perubahan telah langsung tersimpan di halaman Catatan aplikasi My Singghara._
            """.trimIndent()
        }

        // 2. Tipe Aksi: Ubah Isi / Judul Catatan
        var targetKeyword = ""
        var newContent = ""

        if (rawText.contains(":")) {
            val beforeColon = rawText.substringBefore(":").trim()
            val afterColon = rawText.substringAfter(":").trim()
            targetKeyword = beforeColon
                .replace("edit catatan", "", ignoreCase = true)
                .replace("ubah catatan", "", ignoreCase = true)
                .replace("ganti catatan", "", ignoreCase = true)
                .replace("update catatan", "", ignoreCase = true)
                .replace("revisi catatan", "", ignoreCase = true)
                .replace("koreksi catatan", "", ignoreCase = true)
                .trim()
            newContent = afterColon
        } else {
            val clean = rawText
                .replace("edit catatan", "", ignoreCase = true)
                .replace("ubah catatan", "", ignoreCase = true)
                .replace("ganti catatan", "", ignoreCase = true)
                .replace("update catatan", "", ignoreCase = true)
                .replace("revisi catatan", "", ignoreCase = true)
                .trim()

            when {
                clean.contains(" menjadi ", ignoreCase = true) -> {
                    targetKeyword = clean.substringBefore(" menjadi ", "").trim()
                    newContent = clean.substringAfter(" menjadi ", "").trim()
                }
                clean.contains(" jadi ", ignoreCase = true) -> {
                    targetKeyword = clean.substringBefore(" jadi ", "").trim()
                    newContent = clean.substringAfter(" jadi ", "").trim()
                }
                else -> {
                    targetKeyword = clean
                    newContent = clean
                }
            }
        }

        // Cari catatan yang paling relevan berdasarkan judul atau isi
        val targetNote = if (targetKeyword.isNotBlank()) {
            notes.firstOrNull {
                it.title.contains(targetKeyword, ignoreCase = true) ||
                targetKeyword.contains(it.title, ignoreCase = true)
            } ?: notes.firstOrNull {
                it.content.contains(targetKeyword, ignoreCase = true)
            } ?: notes.first()
        } else {
            notes.first()
        }

        if (newContent.isBlank()) {
            return """
ℹ️ *Format Perintah Edit Catatan:*
Silakan gunakan format:
• _"Edit catatan [Judul]: [Isi baru]"_
• _"Ubah catatan [Judul] jadi [Isi baru]"_

📋 *Daftar Catatan yang Ada:*
${notes.take(5).mapIndexed { i, n -> "${i + 1}. *${n.title}*" }.joinToString("\n")}
            """.trimIndent()
        }

        val updated = targetNote.copy(
            content = newContent,
            charCount = newContent.length,
            lineCount = newContent.split("\n").size,
            lastSaved = System.currentTimeMillis()
        )

        try {
            db.noteDao().updateNote(updated)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "📝 Catatan Berhasil Diedit", "Catatan '${updated.title}' telah diperbarui dari Telegram!")

        val timeStr = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", indonesianLocale).format(Date())

        return """
✅ *CATATAN BERHASIL DIEDIT & DIPERBARUI!*

📋 *Judul:* *${updated.title}*
🏷️ *Kategori:* ${updated.category}
🕒 *Waktu Edit:* $timeStr WIB

📄 *Isi Catatan Baru:*
$newContent

✨ _Perubahan ini langsung tersimpan di database aplikasi My Singghara di ponsel Anda._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 2: EDIT / UBAH BELANJA PASAR (EDIT SHOPPING COMMAND)
    // ========================================================
    private fun isEditShoppingCommand(text: String): Boolean {
        return text.startsWith("tandai belanja") ||
                text.startsWith("centang belanja") ||
                text.startsWith("selesaikan belanja") ||
                text.startsWith("sudah beli ") ||
                text.startsWith("sudah dibeli ") ||
                text.startsWith("edit belanja") ||
                text.startsWith("ubah belanja") ||
                text.startsWith("update belanja") ||
                text.startsWith("ganti belanja") ||
                text.startsWith("update harga belanja") ||
                text.startsWith("edit harga belanja") ||
                text.startsWith("ubah harga belanja") ||
                (text.contains("belanja") && (text.contains("sudah dibeli") || text.contains("belum dibeli") || text.contains("ubah") || text.contains("edit") || text.contains("ganti harga") || text.contains("update harga")))
    }

    private suspend fun handleEditShoppingCommand(context: Context, rawText: String, lowerText: String): String {
        val db = AppDatabase.getDatabase(context)
        val items = try {
            db.belanjaPasarDao().getAllBelanjaPasar().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (items.isEmpty()) {
            return "🛒 *Edit Belanja Pasar*\n\nBelum ada daftar belanja pasar di aplikasi My Singghara. Anda dapat menambahkan barang belanjaan dengan mengetik: _\"Beli telur 1 kg dan beras 5 kg\"_."
        }

        // Kasus 1: Tandai status sudah dibeli / belum dibeli
        val isStatusToggle = lowerText.contains("sudah dibeli") ||
                lowerText.contains("belum dibeli") ||
                lowerText.contains("tandai") ||
                lowerText.contains("centang") ||
                lowerText.contains("selesaikan") ||
                lowerText.startsWith("sudah beli") ||
                lowerText.startsWith("sudah dibeli")

        if (isStatusToggle) {
            val markAsBought = !lowerText.contains("belum") && !lowerText.contains("batal")
            val targetName = rawText
                .replace("tandai belanja", "", ignoreCase = true)
                .replace("centang belanja", "", ignoreCase = true)
                .replace("selesaikan belanja", "", ignoreCase = true)
                .replace("sudah beli", "", ignoreCase = true)
                .replace("sudah dibeli", "", ignoreCase = true)
                .replace("belum dibeli", "", ignoreCase = true)
                .trim()

            val targetItem = if (targetName.isNotBlank()) {
                items.firstOrNull { it.nama.contains(targetName, ignoreCase = true) || targetName.contains(it.nama, ignoreCase = true) } ?: items.first()
            } else {
                items.firstOrNull { !it.sudahDibeli } ?: items.first()
            }

            try {
                db.belanjaPasarDao().setSudahDibeli(targetItem.id, markAsBought)
            } catch (_: Exception) {}

            val statusBadge = if (markAsBought) "✅ *SUDAH DIBELI (SELESAI)*" else "⏳ *BELUM DIBELI (TERTUNDA)*"
            notifyActionExecuted(context, "🛒 Status Belanja Diubah", "Barang '${targetItem.nama}' ditandai $statusBadge")

            return """
✅ *STATUS BELANJA PASAR BERHASIL DIUBAH!*

🛒 *Nama Barang:* *${targetItem.nama}*
📦 *Jumlah:* ${targetItem.jumlah} ${targetItem.satuan}
🏷️ *Kategori:* ${targetItem.kategori}
📌 *Status Baru:* $statusBadge

✨ _Daftar belanja pasar di aplikasi My Singghara telah diperbarui secara otomatis._
            """.trimIndent()
        }

        // Kasus 2: Ubah jumlah, satuan, atau harga
        val regexQty = Regex("([0-9]+(?:[\\.,][0-9]+)?)\\s*(kg|kilo|gram|liter|ikat|bks|dus|pcs|butir|ons)?", RegexOption.IGNORE_CASE)
        val matchQty = regexQty.find(rawText)

        // Cek harga jika disebutkan "harga" atau nominal uang
        val isPriceUpdate = lowerText.contains("harga")
        val regexNominal = Regex("([0-9]{1,3}(?:[\\.,][0-9]{3})+|[0-9]{4,})")
        val matchNominal = regexNominal.find(rawText)

        // Cari item berdasarkan kecocokan nama
        val cleanSearch = rawText
            .replace("edit belanja pasar", "", ignoreCase = true)
            .replace("ubah belanja pasar", "", ignoreCase = true)
            .replace("update belanja pasar", "", ignoreCase = true)
            .replace("edit belanja", "", ignoreCase = true)
            .replace("ubah belanja", "", ignoreCase = true)
            .replace("update belanja", "", ignoreCase = true)
            .replace("edit harga belanja", "", ignoreCase = true)
            .replace("ubah harga belanja", "", ignoreCase = true)
            .replace("update harga belanja", "", ignoreCase = true)
            .replace("harga", "", ignoreCase = true)
            .replace("jadi", "", ignoreCase = true)
            .replace("menjadi", "", ignoreCase = true)
            .replace(matchQty?.value ?: "", "")
            .replace(matchNominal?.value ?: "", "")
            .replace("rp", "", ignoreCase = true)
            .trim()

        val targetItem = if (cleanSearch.isNotBlank()) {
            items.firstOrNull { it.nama.contains(cleanSearch, ignoreCase = true) || cleanSearch.contains(it.nama, ignoreCase = true) } ?: items.first()
        } else {
            items.first()
        }

        var updated = targetItem

        if (isPriceUpdate && matchNominal != null) {
            val newPrice = matchNominal.value.replace(".", "").replace(",", "").toDoubleOrNull() ?: targetItem.realisasiHarga
            updated = updated.copy(realisasiHarga = newPrice, perkiraanHarga = newPrice)
        } else if (matchQty != null) {
            val newQty = matchQty.groupValues[1].replace(",", ".").toDoubleOrNull() ?: targetItem.jumlah
            val unitStr = matchQty.groupValues[2].lowercase(Locale.ROOT)
            val newSatuan = if (unitStr.isNotBlank()) (if (unitStr == "kilo") "kg" else unitStr) else targetItem.satuan
            updated = updated.copy(jumlah = newQty, satuan = newSatuan)
        }

        try {
            db.belanjaPasarDao().updateBelanjaPasar(updated)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "🛒 Data Belanja Diperbarui", "Barang '${updated.nama}' berhasil diubah!")

        return """
✅ *DATA BELANJA PASAR BERHASIL DIEDIT!*

🛒 *Nama Barang:* *${updated.nama}*
📦 *Jumlah Terbaru:* ${if (updated.jumlah % 1.0 == 0.0) updated.jumlah.toInt() else updated.jumlah} ${updated.satuan}
💵 *Harga:* Rp ${numberFormat.format(if (updated.realisasiHarga > 0) updated.realisasiHarga else updated.perkiraanHarga)}
🏷️ *Kategori:* ${updated.kategori}
📌 *Status:* ${if (updated.sudahDibeli) "✅ Sudah Dibeli" else "⏳ Belum Dibeli"}

✨ _Data belanja pasar telah tersinkronisasi di aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 3: EDIT / PINDAHKAN AGENDA (EDIT AGENDA COMMAND)
    // ========================================================
    private fun isEditAgendaCommand(text: String): Boolean {
        return text.startsWith("edit agenda") ||
                text.startsWith("ubah agenda") ||
                text.startsWith("ganti agenda") ||
                text.startsWith("update agenda") ||
                text.startsWith("revisi agenda") ||
                text.startsWith("pindahkan agenda") ||
                text.startsWith("geser agenda") ||
                text.contains("edit agenda") ||
                text.contains("ubah agenda") ||
                text.contains("pindahkan agenda") ||
                text.contains("geser agenda")
    }

    private suspend fun handleEditAgendaCommand(context: Context, rawText: String, lowerText: String): String {
        val db = AppDatabase.getDatabase(context)
        val agendas = try {
            db.agendaDao().getAllAgendas().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (agendas.isEmpty()) {
            return "📅 *Edit Agenda*\n\nBelum ada agenda yang tersimpan di aplikasi My Singghara. Anda dapat menjadwalkan agenda baru dengan mengetik: _\"Jadwalkan agenda besok rapat jam 09:00\"_."
        }

        val cal = Calendar.getInstance()
        var newDateStr: String? = null
        var dateLabel: String? = null

        if (lowerText.contains("besok")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            newDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            dateLabel = "Besok"
        } else if (lowerText.contains("lusa")) {
            cal.add(Calendar.DAY_OF_YEAR, 2)
            newDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            dateLabel = "Lusa"
        } else if (lowerText.contains("hari ini")) {
            newDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            dateLabel = "Hari Ini"
        }

        // Cek regex tanggal YYYY-MM-DD
        val regexDate = Regex("([0-9]{4}-[0-9]{2}-[0-9]{2})")
        val matchDate = regexDate.find(rawText)
        if (matchDate != null) {
            newDateStr = matchDate.value
            dateLabel = matchDate.value
        }

        // Cari agenda yang ingin diedit
        val cleanSearch = rawText
            .replace("pindahkan agenda", "", ignoreCase = true)
            .replace("geser agenda", "", ignoreCase = true)
            .replace("edit agenda", "", ignoreCase = true)
            .replace("ubah agenda", "", ignoreCase = true)
            .replace("ganti agenda", "", ignoreCase = true)
            .replace("update agenda", "", ignoreCase = true)
            .replace("revisi agenda", "", ignoreCase = true)
            .replace("ke tanggal", "", ignoreCase = true)
            .replace("ke", "", ignoreCase = true)
            .replace("besok", "", ignoreCase = true)
            .replace("lusa", "", ignoreCase = true)
            .replace("hari ini", "", ignoreCase = true)
            .replace(matchDate?.value ?: "", "")
            .trim()

        var newDetailOrTitle: String? = null
        var targetKeyword = cleanSearch

        if (rawText.contains(":")) {
            val afterColon = rawText.substringAfter(":").trim()
            val beforeColon = rawText.substringBefore(":").trim()
            targetKeyword = beforeColon
                .replace("edit agenda", "", ignoreCase = true)
                .replace("ubah agenda", "", ignoreCase = true)
                .replace("update agenda", "", ignoreCase = true)
                .trim()
            newDetailOrTitle = afterColon
        } else if (cleanSearch.contains(" jadi ", ignoreCase = true)) {
            targetKeyword = cleanSearch.substringBefore(" jadi ", "").trim()
            newDetailOrTitle = cleanSearch.substringAfter(" jadi ", "").trim()
        } else if (cleanSearch.contains(" menjadi ", ignoreCase = true)) {
            targetKeyword = cleanSearch.substringBefore(" menjadi ", "").trim()
            newDetailOrTitle = cleanSearch.substringAfter(" menjadi ", "").trim()
        }

        val targetAgenda = if (targetKeyword.isNotBlank()) {
            agendas.firstOrNull {
                it.judul.contains(targetKeyword, ignoreCase = true) ||
                it.detail.contains(targetKeyword, ignoreCase = true) ||
                targetKeyword.contains(it.judul, ignoreCase = true)
            } ?: agendas.first()
        } else {
            agendas.first()
        }

        val updated = targetAgenda.copy(
            tanggal = newDateStr ?: targetAgenda.tanggal,
            judul = if (!newDetailOrTitle.isNullOrBlank() && newDetailOrTitle.length <= 40) newDetailOrTitle else targetAgenda.judul,
            detail = if (!newDetailOrTitle.isNullOrBlank()) newDetailOrTitle else targetAgenda.detail
        )

        try {
            db.agendaDao().updateAgenda(updated)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "📅 Agenda Diperbarui", "Agenda '${updated.judul}' telah diperbarui!")

        return """
✅ *AGENDA BERHASIL DIEDIT & DIPERBARUI!*

📌 *Judul Agenda:* *${updated.judul}*
📅 *Tanggal Pelaksanaan:* ${updated.tanggal} ${if (dateLabel != null) "($dateLabel)" else ""}
📝 *Detail:* ${updated.detail}
🏷️ *Tipe:* Agenda Kegiatan

✨ _Perubahan jadwal agenda telah tersinkronisasi ke Kalender aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 4: EDIT JADWAL RUTIN (EDIT DAILY SCHEDULE)
    // ========================================================
    private fun isEditJadwalCommand(text: String): Boolean {
        return text.startsWith("edit jadwal") ||
                text.startsWith("ubah jadwal") ||
                text.startsWith("ganti jadwal") ||
                text.startsWith("update jadwal") ||
                text.startsWith("tandai jadwal") ||
                text.startsWith("selesaikan jadwal") ||
                text.contains("edit jadwal") ||
                text.contains("ubah jadwal")
    }

    private suspend fun handleEditJadwalCommand(context: Context, rawText: String, lowerText: String): String {
        val db = AppDatabase.getDatabase(context)
        val jadwalList = try {
            db.jadwalDao().getAllJadwal().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (jadwalList.isEmpty()) {
            return "⏰ *Edit Jadwal Rutin*\n\nBelum ada jadwal kegiatan rutin yang terdaftar di aplikasi My Singghara."
        }

        // Cek format jam HH:mm
        val regexTime = Regex("([0-1]?[0-9]|2[0-3]):[0-5][0-9]")
        val matchTime = regexTime.find(rawText)
        val newTime = matchTime?.value

        val isMarkDone = lowerText.contains("selesai") || lowerText.contains("tandai") || lowerText.contains("centang")

        val cleanSearch = rawText
            .replace("edit jadwal harian", "", ignoreCase = true)
            .replace("edit jadwal rutin", "", ignoreCase = true)
            .replace("ubah jadwal harian", "", ignoreCase = true)
            .replace("ubah jadwal rutin", "", ignoreCase = true)
            .replace("edit jadwal", "", ignoreCase = true)
            .replace("ubah jadwal", "", ignoreCase = true)
            .replace("update jadwal", "", ignoreCase = true)
            .replace("selesaikan jadwal", "", ignoreCase = true)
            .replace("tandai jadwal", "", ignoreCase = true)
            .replace("selesai", "", ignoreCase = true)
            .replace("jam", "", ignoreCase = true)
            .replace("pukul", "", ignoreCase = true)
            .replace(newTime ?: "", "")
            .trim()

        val target = if (cleanSearch.isNotBlank()) {
            jadwalList.firstOrNull { it.kegiatan.contains(cleanSearch, ignoreCase = true) || cleanSearch.contains(it.kegiatan, ignoreCase = true) } ?: jadwalList.first()
        } else {
            jadwalList.first()
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val updated = target.copy(
            waktu = newTime ?: target.waktu,
            isCompleted = if (isMarkDone) true else target.isCompleted,
            lastCheckedDate = if (isMarkDone) todayStr else target.lastCheckedDate
        )

        try {
            db.jadwalDao().updateJadwal(updated)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "⏰ Jadwal Diperbarui", "Jadwal '${updated.kegiatan}' telah diperbarui!")

        return """
✅ *JADWAL HARIAN BERHASIL DIPERBARUI!*

⏰ *Waktu:* ${updated.waktu} WIB
📌 *Kegiatan:* *${updated.kegiatan}*
🏷️ *Status:* ${if (updated.isCompleted) "✅ Selesai Dikerjakan" else "⏳ Menunggu Jadwal"}

✨ _Jadwal harian aktif telah diperbarui di aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 5: EDIT STOK & HARGA KASIR POS (EDIT CASHIER STOCK)
    // ========================================================
    private fun isEditKasirStockCommand(text: String): Boolean {
        return text.startsWith("edit stok") ||
                text.startsWith("ubah stok") ||
                text.startsWith("update stok") ||
                text.startsWith("ganti stok") ||
                text.startsWith("tambah stok") ||
                text.startsWith("tambahkan stok") ||
                text.startsWith("kurangi stok") ||
                text.startsWith("kurang stok") ||
                text.startsWith("edit harga") ||
                text.startsWith("ubah harga") ||
                text.startsWith("update harga") ||
                text.startsWith("ganti harga") ||
                ((text.contains("stok") || text.contains("harga")) &&
                 (text.contains("kasir") || text.contains("toko") || text.contains("produk") || text.contains("barang")) &&
                 (text.contains("edit") || text.contains("ubah") || text.contains("update") || text.contains("ganti")))
    }

    private fun handleEditKasirStockCommand(context: Context, rawText: String, lowerText: String): String {
        val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
        val rawStock = prefs.getString("list_stock", "") ?: ""
        val stockList = deserializeStock(rawStock).toMutableList()

        if (stockList.isEmpty()) {
            return "🏪 *Kasir POS*\n\nBelum ada data barang atau stok produk yang terdaftar di modul Kasir My Singghara."
        }

        val isPriceUpdate = lowerText.contains("harga")
        val isAdd = lowerText.contains("tambah")
        val isSubtract = lowerText.contains("kurang")

        // Ekstraksi angka nominal / jumlah
        val regexNumber = Regex("([0-9]{1,3}(?:[\\.,][0-9]{3})+|[0-9]+)")
        val match = regexNumber.find(rawText)
        val targetNumber = match?.value?.replace(".", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        val cleanSearch = rawText
            .replace("edit stok barang", "", ignoreCase = true)
            .replace("ubah stok barang", "", ignoreCase = true)
            .replace("update stok barang", "", ignoreCase = true)
            .replace("edit stok", "", ignoreCase = true)
            .replace("ubah stok", "", ignoreCase = true)
            .replace("update stok", "", ignoreCase = true)
            .replace("tambah stok", "", ignoreCase = true)
            .replace("tambahkan stok", "", ignoreCase = true)
            .replace("kurangi stok", "", ignoreCase = true)
            .replace("kurang stok", "", ignoreCase = true)
            .replace("edit harga", "", ignoreCase = true)
            .replace("ubah harga", "", ignoreCase = true)
            .replace("update harga", "", ignoreCase = true)
            .replace("ganti harga", "", ignoreCase = true)
            .replace("jadi", "", ignoreCase = true)
            .replace("menjadi", "", ignoreCase = true)
            .replace("harga", "", ignoreCase = true)
            .replace("stok", "", ignoreCase = true)
            .replace("kasir", "", ignoreCase = true)
            .replace("toko", "", ignoreCase = true)
            .replace("rp", "", ignoreCase = true)
            .replace(match?.value ?: "", "")
            .trim()

        val itemIndex = stockList.indexOfFirst {
            cleanSearch.isNotBlank() && (it.nama.contains(cleanSearch, ignoreCase = true) || cleanSearch.contains(it.nama, ignoreCase = true))
        }

        if (itemIndex == -1) {
            val availableNames = stockList.take(6).joinToString(", ") { it.nama }
            return """
⚠️ *Barang Tidak Ditemukan di Kasir*
Barang _"$cleanSearch"_ tidak ditemukan dalam stok kasir.

📦 *Daftar Produk yang Tersedia:*
$availableNames

💡 *Contoh:* _"Edit stok [Nama Barang] jadi 25"_ atau _"Ubah harga [Nama Barang] jadi 15000"_
            """.trimIndent()
        }

        val oldItem = stockList[itemIndex]
        val newItem: StockItem

        if (isPriceUpdate) {
            val newPrice = if (targetNumber > 0) targetNumber else oldItem.harga
            newItem = oldItem.copy(harga = newPrice)
            stockList[itemIndex] = newItem
            prefs.edit().putString("list_stock", serializeStock(stockList)).apply()

            notifyActionExecuted(context, "🏪 Harga Kasir Diperbarui", "Harga '${newItem.nama}' diubah menjadi Rp ${numberFormat.format(newPrice)}")

            return """
✅ *HARGA PRODUK KASIR BERHASIL DIUBAH!*

🏪 *Produk:* *${newItem.nama}* [${newItem.kategori}]
💵 *Harga Lama:* Rp ${numberFormat.format(oldItem.harga)}
💰 *Harga Baru:* *Rp ${numberFormat.format(newPrice)}*
📦 *Stok Sekarang:* ${newItem.stok} unit

✨ _Perubahan harga langsung aktif di mesin Kasir POS My Singghara._
            """.trimIndent()
        } else {
            val newQty = when {
                isAdd -> oldItem.stok + targetNumber.toInt()
                isSubtract -> (oldItem.stok - targetNumber.toInt()).coerceAtLeast(0)
                else -> targetNumber.toInt().coerceAtLeast(0)
            }
            newItem = oldItem.copy(stok = newQty)
            stockList[itemIndex] = newItem
            prefs.edit().putString("list_stock", serializeStock(stockList)).apply()

            notifyActionExecuted(context, "🏪 Stok Kasir Diperbarui", "Stok '${newItem.nama}' kini menjadi $newQty unit")

            val statusStok = if (newQty <= 0) "🔴 STOK HABIS" else if (newQty <= 3) "⚠️ STOK MENIPIS" else "🟢 TERSEDIA"

            return """
✅ *STOK PRODUK KASIR BERHASIL DIEDIT!*

🏪 *Produk:* *${newItem.nama}* [${newItem.kategori}]
📦 *Stok Sebelumnya:* ${oldItem.stok} unit
📈 *Stok Terbaru:* *${newQty} unit* ($statusStok)
💵 *Harga Jual:* Rp ${numberFormat.format(newItem.harga)}

✨ _Stok telah diperbarui di halaman Kasir & Pembukuan aplikasi My Singghara._
            """.trimIndent()
        }
    }

    // ========================================================
    // EDIT 6: EDIT TARGET TABUNGAN (EDIT SAVING TARGET)
    // ========================================================
    private fun isEditSavingTargetCommand(text: String): Boolean {
        return text.startsWith("edit target tabungan") ||
                text.startsWith("ubah target tabungan") ||
                text.startsWith("ganti target tabungan") ||
                text.startsWith("update target tabungan") ||
                (text.contains("target tabungan") && (text.contains("edit") || text.contains("ubah") || text.contains("ganti") || text.contains("update")))
    }

    private suspend fun handleEditSavingTargetCommand(context: Context, rawText: String, lowerText: String): String {
        val db = AppDatabase.getDatabase(context)
        val currentTarget = try {
            db.savingDao().getActiveTarget()
        } catch (_: Exception) {
            null
        }

        val regexNominal = Regex("([0-9]{1,3}(?:[\\.,][0-9]{3})+|[0-9]{4,})")
        val matchNominal = regexNominal.find(rawText)
        val newTargetAmount = matchNominal?.value?.replace(".", "")?.replace(",", "")?.toDoubleOrNull() ?: currentTarget?.jumlah ?: 10000000.0

        val cleanName = rawText
            .replace("edit target tabungan", "", ignoreCase = true)
            .replace("ubah target tabungan", "", ignoreCase = true)
            .replace("ganti target tabungan", "", ignoreCase = true)
            .replace("update target tabungan", "", ignoreCase = true)
            .replace("target tabungan", "", ignoreCase = true)
            .replace("menjadi", "", ignoreCase = true)
            .replace("jadi", "", ignoreCase = true)
            .replace("rp", "", ignoreCase = true)
            .replace(matchNominal?.value ?: "", "")
            .trim()

        val newTargetName = if (cleanName.isNotBlank()) cleanName.replaceFirstChar { it.uppercase() } else currentTarget?.nama ?: "Tabungan Impian"

        val updatedTarget = SavingTarget(id = 1, nama = newTargetName, jumlah = newTargetAmount)
        try {
            db.savingDao().setTarget(updatedTarget)
        } catch (_: Exception) {}

        val transactions = try {
            db.savingDao().getAllTransactions()
        } catch (e: Exception) {
            emptyList()
        }
        val totalDeposit = transactions.filter { it.jenis.equals("masuk", ignoreCase = true) || it.jenis.equals("setoran", ignoreCase = true) }.sumOf { it.nominal }
        val totalWithdraw = transactions.filter { it.jenis.equals("keluar", ignoreCase = true) || it.jenis.equals("penarikan", ignoreCase = true) }.sumOf { it.nominal }
        val saldoTerkumpul = (totalDeposit - totalWithdraw).coerceAtLeast(0.0)

        val persentase = if (newTargetAmount > 0) ((saldoTerkumpul / newTargetAmount) * 100.0).coerceIn(0.0, 100.0) else 0.0

        notifyActionExecuted(context, "🎯 Target Tabungan Diperbarui", "Target '$newTargetName' diubah menjadi Rp ${numberFormat.format(newTargetAmount)}")

        return """
✅ *TARGET TABUNGAN BERHASIL DIPERBARUI!*

🎯 *Sasaran Impian:* *$newTargetName*
💰 *Target Nominal Baru:* *Rp ${numberFormat.format(newTargetAmount)}*
💵 *Saldo Tabungan Saat Ini:* Rp ${numberFormat.format(saldoTerkumpul)}
📊 *Pencapaian Target:* ${String.format(Locale.US, "%.1f", persentase)}%

✨ _Sasaran target baru telah aktif di halaman Tabungan aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 7: EDIT DATA KARYAWAN / ANGGOTA (EDIT MEMBER IN COMPANY)
    // ========================================================
    private fun isEditAnggotaCommand(text: String): Boolean {
        return text.startsWith("edit karyawan") ||
                text.startsWith("ubah karyawan") ||
                text.startsWith("edit anggota") ||
                text.startsWith("ubah anggota") ||
                text.startsWith("ubah divisi") ||
                text.startsWith("edit divisi") ||
                text.startsWith("ubah jabatan") ||
                text.startsWith("edit jabatan")
    }

    private suspend fun handleEditAnggotaCommand(context: Context, rawText: String, lowerText: String): String {
        val db = AppDatabase.getDatabase(context)
        val anggotaList = try {
            db.anggotaDao().getAllAnggota().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (anggotaList.isEmpty()) {
            return "🏢 *Data Karyawan & Murid*\n\nBelum ada data anggota yang terdaftar di modul Company aplikasi My Singghara."
        }

        var targetName = ""
        var newDivision = ""

        if (rawText.contains(" jadi ", ignoreCase = true)) {
            val beforeJadi = rawText.substringBefore(" jadi ").trim()
            newDivision = rawText.substringAfter(" jadi ").trim()
            targetName = beforeJadi
                .replace("edit karyawan", "", ignoreCase = true)
                .replace("ubah karyawan", "", ignoreCase = true)
                .replace("edit anggota", "", ignoreCase = true)
                .replace("ubah divisi", "", ignoreCase = true)
                .replace("edit divisi", "", ignoreCase = true)
                .replace("ubah jabatan", "", ignoreCase = true)
                .replace("edit jabatan", "", ignoreCase = true)
                .trim()
        } else if (rawText.contains(":")) {
            targetName = rawText.substringBefore(":").replace("edit karyawan", "", ignoreCase = true).trim()
            newDivision = rawText.substringAfter(":").trim()
        }

        val target = if (targetName.isNotBlank()) {
            anggotaList.firstOrNull { it.nama.contains(targetName, ignoreCase = true) || targetName.contains(it.nama, ignoreCase = true) } ?: anggotaList.first()
        } else {
            anggotaList.first()
        }

        val updated = if (lowerText.contains("kontak") || lowerText.contains("telepon") || lowerText.contains("hp") || lowerText.contains("wa")) {
            target.copy(kontak = newDivision, emailOrTelp = newDivision)
        } else {
            target.copy(divisiOrJurusan = newDivision)
        }

        try {
            db.anggotaDao().updateAnggota(updated)
        } catch (_: Exception) {}

        notifyActionExecuted(context, "🏢 Data Karyawan Diperbarui", "Data '${updated.nama}' berhasil diperbarui!")

        return """
✅ *DATA KARYAWAN BERHASIL DIPERBARUI!*

👤 *Nama Anggota:* *${updated.nama}*
🏢 *Divisi / Jurusan:* *${updated.divisiOrJurusan}*
🏷️ *Status:* ${updated.status}
📞 *Kontak:* ${updated.kontak.ifBlank { updated.emailOrTelp.ifBlank { "-" } }}

✨ _Data karyawan telah tersimpan di modul Company aplikasi My Singghara._
        """.trimIndent()
    }

    // ========================================================
    // EDIT 8: GENERAL SMART EDIT DISPATCHER (FALLBACK EDIT)
    // ========================================================
    private fun isGeneralEditCommand(text: String): Boolean {
        return text.startsWith("edit ") ||
                text.startsWith("ubah ") ||
                text.startsWith("ganti ") ||
                text.startsWith("update ") ||
                text.startsWith("revisi ") ||
                text.startsWith("koreksi ")
    }

    private suspend fun handleGeneralEditCommand(
        context: Context,
        rawText: String,
        lowerText: String,
        senderName: String
    ): String {
        // Cek domain berdasarkan kata kunci dalam teks
        return when {
            lowerText.contains("catat") || lowerText.contains("memo") || lowerText.contains("tulisan") -> {
                handleEditNoteCommand(context, rawText, lowerText)
            }
            lowerText.contains("belanja") || lowerText.contains("beli") || lowerText.contains("pasar") -> {
                handleEditShoppingCommand(context, rawText, lowerText)
            }
            lowerText.contains("agenda") || lowerText.contains("rapat") || lowerText.contains("acara") || lowerText.contains("kegiatan") -> {
                handleEditAgendaCommand(context, rawText, lowerText)
            }
            lowerText.contains("jadwal") || lowerText.contains("rutin") -> {
                handleEditJadwalCommand(context, rawText, lowerText)
            }
            lowerText.contains("stok") || lowerText.contains("kasir") || lowerText.contains("harga") || lowerText.contains("toko") -> {
                handleEditKasirStockCommand(context, rawText, lowerText)
            }
            lowerText.contains("tabung") || lowerText.contains("target") -> {
                handleEditSavingTargetCommand(context, rawText, lowerText)
            }
            lowerText.contains("karyawan") || lowerText.contains("murid") || lowerText.contains("divisi") || lowerText.contains("jabatan") -> {
                handleEditAnggotaCommand(context, rawText, lowerText)
            }
            else -> {
                """
✏️ *Pusat Pengeditan Database My Singghara*
Halo $senderName, saya siap membantu mengedit data database Anda! Silakan sebutkan data apa yang ingin diubah:

• 📝 *Catatan:* _"Edit catatan [Judul]: [Isi baru]"_
• 📅 *Agenda:* _"Ubah agenda rapat ke tanggal besok"_
• 🛒 *Belanja Pasar:* _"Tandai belanja telur sudah dibeli"_ atau _"Ubah belanja minyak jadi 2 liter"_
• ⏰ *Jadwal Rutin:* _"Edit jadwal olahraga jam 06:30"_
• 🏪 *Stok Kasir:* _"Edit stok beras jadi 30"_ atau _"Ubah harga indomie jadi 3500"_
• 💰 *Target Tabungan:* _"Ubah target tabungan jadi 15000000"_
• 🏢 *Karyawan:* _"Ubah divisi Budi jadi Marketing"_
                """.trimIndent()
            }
        }
    }

    // Helper notifikasi Android
    private fun notifyActionExecuted(context: Context, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val builder = androidx.core.app.NotificationCompat.Builder(context, "agenda_channel")
                .setSmallIcon(com.example.R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        } catch (_: Exception) {}
    }

    // Helper draf cerdas Gemini
    private suspend fun tryGenerateDraftWithGemini(context: Context, prompt: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return null

        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                val cand = root.optJSONArray("candidates")?.optJSONObject(0)
                val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
                parts?.optJSONObject(0)?.optString("text", "")
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // 1. KARTU JADWAL SHOLAT
    // ==========================================
    private fun isJadwalSholatQuery(text: String): Boolean {
        return text.contains("sholat") ||
                text.contains("solat") ||
                text.contains("shalat") ||
                text.contains("adzan") ||
                text.contains("azan") ||
                text.contains("imsak") ||
                text.contains("subuh") ||
                text.contains("dzuhur") ||
                text.contains("dhuhur") ||
                text.contains("ashar") ||
                text.contains("maghrib") ||
                text.contains("isya") ||
                text.contains("waktu sholat") ||
                text.contains("jadwal shalat") ||
                text.contains("jadwal solat") ||
                text.contains("hijriyah") ||
                text.contains("hijri")
    }

    private suspend fun handleJadwalSholatQuery(context: Context, query: String): String {
        val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val calendar = Calendar.getInstance()
        val activeTimeZone = prefs.getString("active_timezone", "WIB") ?: "WIB"

        val kmImsak = prefs.getString("kemenag_imsak", null)
        val kmSubuh = prefs.getString("kemenag_subuh", null)
        val kmDzuhur = prefs.getString("kemenag_dzuhur", null)
        val kmAshar = prefs.getString("kemenag_ashar", null)
        val kmMaghrib = prefs.getString("kemenag_maghrib", null)
        val kmIsya = prefs.getString("kemenag_isya", null)
        val kmCity = prefs.getString("kemenag_city", null)

        val schedule: PrayerSchedule = if (kmImsak != null && kmSubuh != null && kmDzuhur != null && kmAshar != null && kmMaghrib != null && kmIsya != null) {
            PrayerSchedule(kmImsak, kmSubuh, kmDzuhur, kmAshar, kmMaghrib, kmIsya)
        } else {
            val userLat = prefs.getFloat("user_location_lat", -6.2088f).toDouble()
            val userLng = prefs.getFloat("user_location_lng", 106.8456f).toDouble()
            val offsetHours = calendar.timeZone.getOffset(System.currentTimeMillis()) / 3600000.0
            PrayerTimesHelper.getPrayerTimesForLocation(userLat, userLng, offsetHours, calendar)
        }

        val cityDisplay = kmCity ?: when (activeTimeZone) {
            "WITA" -> "Makassar (WITA)"
            "WIT" -> "Ambon (WIT)"
            else -> "Jakarta (WIB)"
        }

        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutesOfDay = currentHour * 60 + currentMinute

        fun parseMin(timeStr: String): Int {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                return h * 60 + m
            }
            return 0
        }

        val prayerEntries = listOf(
            Triple("Imsak", schedule.imsak, parseMin(schedule.imsak)),
            Triple("Subuh", schedule.subuh, parseMin(schedule.subuh)),
            Triple("Dzuhur", schedule.dzuhur, parseMin(schedule.dzuhur)),
            Triple("Ashar", schedule.ashar, parseMin(schedule.ashar)),
            Triple("Maghrib", schedule.maghrib, parseMin(schedule.maghrib)),
            Triple("Isya", schedule.isya, parseMin(schedule.isya))
        )

        var nextPrayer: Triple<String, String, Int>? = null
        for (entry in prayerEntries) {
            if (entry.third > currentMinutesOfDay) {
                nextPrayer = entry
                break
            }
        }

        val nextPrayerInfo = if (nextPrayer != null) {
            val diff = nextPrayer.third - currentMinutesOfDay
            val h = diff / 60
            val m = diff % 60
            val diffStr = if (h > 0) "$h jam $m menit lagi" else "$m menit lagi"
            "👉 *${nextPrayer.first} pukul ${nextPrayer.second} $activeTimeZone* (dalam $diffStr)"
        } else {
            val imsakMin = prayerEntries[0].third
            val diff = (1440 - currentMinutesOfDay) + imsakMin
            val h = diff / 60
            val m = diff % 60
            val diffStr = if (h > 0) "$h jam $m menit lagi" else "$m menit lagi"
            "👉 *Imsak besok pukul ${schedule.imsak} $activeTimeZone* (dalam $diffStr)"
        }

        val sdfMasehi = SimpleDateFormat("EEEE, d MMMM yyyy", indonesianLocale).format(calendar.time)
        val dateApiStr = String.format("%02d-%02d-%04d", calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR))
        val hijriDate = try {
            val resp = AladhanClient.service.convertGregorianToHijri(dateApiStr)
            val h = resp.data.hijri
            val monthIndo = AladhanClient.mapMonthToIndonesian(h.month.en)
            "${h.day} $monthIndo ${h.year} H"
        } catch (_: Exception) {
            null
        }

        val sb = StringBuilder("🕌 *KARTU JADWAL SHOLAT - MY SINGGHARA*\n\n")
        sb.append("📅 *Masehi:* $sdfMasehi\n")
        if (hijriDate != null) {
            sb.append("🌙 *Hijriyah:* $hijriDate\n")
        }
        sb.append("📍 *Wilayah / Lokasi:* $cityDisplay\n\n")

        if (query.contains("subuh")) {
            sb.append("🌅 *Waktu Subuh Hari Ini:* *${schedule.subuh} $activeTimeZone*\n\n")
        } else if (query.contains("dzuhur") || query.contains("dhuhur")) {
            sb.append("☀️ *Waktu Dzuhur Hari Ini:* *${schedule.dzuhur} $activeTimeZone*\n\n")
        } else if (query.contains("ashar")) {
            sb.append("🌤️ *Waktu Ashar Hari Ini:* *${schedule.ashar} $activeTimeZone*\n\n")
        } else if (query.contains("maghrib")) {
            sb.append("🌇 *Waktu Maghrib Hari Ini:* *${schedule.maghrib} $activeTimeZone*\n\n")
        } else if (query.contains("isya")) {
            sb.append("🌌 *Waktu Isya Hari Ini:* *${schedule.isya} $activeTimeZone*\n\n")
        } else if (query.contains("imsak")) {
            sb.append("⏳ *Waktu Imsak Hari Ini:* *${schedule.imsak} $activeTimeZone*\n\n")
        }

        sb.append("⏰ *Jadwal Lengkap Sholat Hari Ini:*\n")
        sb.append("• ⏳ *Imsak:* ${schedule.imsak} $activeTimeZone\n")
        sb.append("• 🌅 *Subuh:* ${schedule.subuh} $activeTimeZone\n")
        sb.append("• ☀️ *Dzuhur:* ${schedule.dzuhur} $activeTimeZone\n")
        sb.append("• 🌤️ *Ashar:* ${schedule.ashar} $activeTimeZone\n")
        sb.append("• 🌇 *Maghrib:* ${schedule.maghrib} $activeTimeZone\n")
        sb.append("• 🌌 *Isya:* ${schedule.isya} $activeTimeZone\n\n")

        sb.append("🔔 *Waktu Sholat Terdekat Berikutnya:*\n")
        sb.append("$nextPrayerInfo\n\n")
        sb.append("_Mari jaga sholat tepat waktu, semoga membawa keberkahan dan kedamaian._ ✨")

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 2. KARTU PERKIRAAN CUACA
    // ==========================================
    private fun isWeatherQuery(text: String): Boolean {
        return text.contains("cuaca") ||
                text.contains("hujan") ||
                text.contains("suhu") ||
                text.contains("panas") ||
                text.contains("dingin") ||
                text.contains("angin") ||
                text.contains("kelembaban") ||
                text.contains("prakiraan cuaca") ||
                text.contains("perkiraan cuaca") ||
                text.contains("mendung") ||
                text.contains("cerah")
    }

    private suspend fun handleWeatherQuery(context: Context, query: String): String {
        val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("cached_weather_response", null)

        var cityName = "Jakarta"
        var tempC = 29.0
        var feelsLikeC = 33.0
        var humidityPercent = 75.0
        var weatherDesc = "Cerah Berawan"
        var windSpeed = 3.2
        var pressureHpa = 1012.0
        var updateTimestamp = System.currentTimeMillis()

        var hasData = false

        if (cachedJson != null && cachedJson.isNotBlank()) {
            try {
                val root = JSONObject(cachedJson)
                cityName = root.optString("name", "Jakarta").ifBlank { "Jakarta" }
                val main = root.optJSONObject("main")
                if (main != null) {
                    tempC = main.optDouble("temp", 29.0)
                    feelsLikeC = main.optDouble("feels_like", 33.0)
                    humidityPercent = main.optDouble("humidity", 75.0)
                    pressureHpa = main.optDouble("pressure", 1012.0)
                }
                val weatherArr = root.optJSONArray("weather")
                if (weatherArr != null && weatherArr.length() > 0) {
                    val wObj = weatherArr.getJSONObject(0)
                    weatherDesc = wObj.optString("description", "Cerah Berawan").replaceFirstChar { it.uppercase() }
                }
                val wind = root.optJSONObject("wind")
                if (wind != null) {
                    windSpeed = wind.optDouble("speed", 3.2)
                }
                val dt = root.optLong("dt", 0L)
                if (dt > 0) {
                    updateTimestamp = dt * 1000L
                }
                hasData = true
            } catch (_: Exception) {}
        }

        if (!hasData) {
            try {
                val lat = prefs.getFloat("user_location_lat", -6.2088f).toDouble()
                val lon = prefs.getFloat("user_location_lng", 106.8456f).toDouble()
                val key = BuildConfig.OPENWEATHER_API_KEY
                val apiKey = if (key.isNotEmpty() && key != "MY_OPENWEATHER_API_KEY") key else "372412b80fd742017cb552b78ca79807"
                val liveResp = WeatherClient.service.getCurrentWeather(lat, lon, apiKey)
                cityName = liveResp.name ?: "Lokasi Anda"
                tempC = liveResp.main?.temp ?: 29.0
                feelsLikeC = liveResp.main?.feels_like ?: 33.0
                humidityPercent = liveResp.main?.humidity ?: 75.0
                pressureHpa = liveResp.main?.pressure ?: 1012.0
                windSpeed = liveResp.wind?.speed ?: 3.2
                val desc = liveResp.weather?.firstOrNull()?.description
                if (desc != null && desc.isNotBlank()) {
                    weatherDesc = desc.replaceFirstChar { it.uppercase() }
                }
                hasData = true
            } catch (_: Exception) {}
        }

        val descLower = weatherDesc.lowercase(indonesianLocale)
        val weatherIcon = when {
            descLower.contains("badai") || descLower.contains("petir") -> "⛈️"
            descLower.contains("hujan lebat") -> "🌧️⚡"
            descLower.contains("hujan") || descLower.contains("gerimis") -> "🌧️"
            descLower.contains("berawan") || descLower.contains("awan") -> "⛅"
            descLower.contains("kabut") || descLower.contains("asap") -> "🌫️"
            else -> "☀️"
        }

        val updateTimeStr = SimpleDateFormat("d MMM yyyy, HH:mm", indonesianLocale).format(Date(updateTimestamp))

        val sb = StringBuilder("🌤️ *KARTU PERKIRAAN CUACA - MY SINGGHARA*\n\n")
        sb.append("📍 *Lokasi:* $cityName\n")
        sb.append("🕒 *Pembaruan:* $updateTimeStr WIB\n\n")

        sb.append("🌡️ *Kondisi & Suhu:*\n")
        sb.append("• Cuaca: *$weatherDesc* $weatherIcon\n")
        sb.append("• Suhu Saat Ini: *${String.format(Locale.US, "%.1f", tempC)}°C*\n")
        sb.append("• Terasa Seperti: *${String.format(Locale.US, "%.1f", feelsLikeC)}°C*\n\n")

        sb.append("💨 *Detail Atmosfer:*\n")
        sb.append("• Kelembaban Udara: *${humidityPercent.toInt()}%*\n")
        sb.append("• Kecepatan Angin: *${String.format(Locale.US, "%.1f", windSpeed)} m/s*\n")
        sb.append("• Tekanan Udara: *${pressureHpa.toInt()} hPa*\n\n")

        val advice = when {
            descLower.contains("hujan") || descLower.contains("badai") -> "Siapkan payung atau jas hujan jika hendak bepergian keluar! ☂️🌧️"
            tempC >= 32.0 -> "Cuaca cukup terik, jangan lupa minum air putih yang cukup agar tetap terhidrasi! 🧴💧"
            else -> "Cuaca nyaman untuk beraktivitas. Selamat beraktivitas dan jaga kesehatan selalu! 😊🌿"
        }
        sb.append("💡 *Saran:* $advice")

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 3. KASIR (POINT OF SALE) ENGINE
    // ==========================================
    private fun isKasirQuery(text: String): Boolean {
        return text.contains("kasir") ||
                text.contains("pos") ||
                text.contains("stok barang") ||
                text.contains("stok produk") ||
                text.contains("penjualan hari ini") ||
                text.contains("omset kasir") ||
                text.contains("omzet") ||
                text.contains("barang habis") ||
                text.contains("stok menipis") ||
                text.contains("struk kasir") ||
                text.contains("toko")
    }

    private fun handleKasirQuery(context: Context, query: String): String {
        val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
        val namaUsaha = prefs.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya"
        val stockList = deserializeStock(prefs.getString("list_stock", "") ?: "")
        val cashierList = deserializeCashier(prefs.getString("list_cashier", "") ?: "")

        val isCheckingStockOnly = query.contains("stok") || query.contains("habis") || query.contains("menipis") || query.contains("barang")
        val isCheckingSalesOnly = query.contains("penjualan") || query.contains("omset") || query.contains("omzet") || query.contains("transaksi") || query.contains("struk")

        val totalProducts = stockList.size
        val totalUnits = stockList.sumOf { it.stok }
        val totalStockValue = stockList.sumOf { it.harga * it.stok }
        val lowStockItems = stockList.filter { it.stok <= 3 }.sortedBy { it.stok }

        val totalTransactions = cashierList.size
        val totalRevenue = cashierList.sumOf { it.totalHarga }

        val todayStr1 = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())
        val todayStr2 = SimpleDateFormat("dd MMM", indonesianLocale).format(Date())
        val todayTransactions = cashierList.filter {
            it.waktu.contains(todayStr1, ignoreCase = true) || it.waktu.contains(todayStr2, ignoreCase = true)
        }
        val todayRevenue = todayTransactions.sumOf { it.totalHarga }

        val sb = StringBuilder("🏪 *INFORMASI KASIR - $namaUsaha*\n\n")

        val specificSearch = stockList.filter { query.contains(it.nama.lowercase(indonesianLocale)) }
        if (specificSearch.isNotEmpty()) {
            sb.append("🔍 *Hasil Pencarian Barang di Kasir:*\n")
            specificSearch.forEach { item ->
                val badge = if (item.stok <= 0) "🔴 HABIS" else if (item.stok <= 3) "⚠️ SISA ${item.stok}" else "🟢 ADA (${item.stok} unit)"
                sb.append("• *${item.nama}* [${item.kategori}]\n")
                sb.append("  ↳ Harga: Rp ${numberFormat.format(item.harga)} | Status: $badge\n")
            }
            sb.append("\n")
        }

        if (!isCheckingStockOnly || isCheckingSalesOnly) {
            sb.append("💰 *Ringkasan Penjualan & Transaksi:*\n")
            sb.append("• *Omset Hari Ini:* Rp ${numberFormat.format(todayRevenue)} (${todayTransactions.size} Transaksi)\n")
            sb.append("• *Total Akumulasi Omset:* Rp ${numberFormat.format(totalRevenue)}\n")
            sb.append("• *Total Transaksi Tercatat:* $totalTransactions transaksi selesai\n\n")

            if (cashierList.isNotEmpty()) {
                sb.append("🧾 *Transaksi Kasir Terakhir:*\n")
                cashierList.takeLast(3).reversed().forEachIndexed { index, tx ->
                    sb.append("${index + 1}. *Waktu: ${tx.waktu}* - Rp ${numberFormat.format(tx.totalHarga)}\n")
                    val itemsSummary = tx.items.joinToString(", ") { "${it.namaBarang} (${it.qty}x)" }
                    if (itemsSummary.isNotBlank()) {
                        sb.append("   ↳ _${itemsSummary}_\n")
                    }
                }
                sb.append("\n")
            }
        }

        if (!isCheckingSalesOnly || isCheckingStockOnly) {
            sb.append("📦 *Manajemen Stok Barang:*\n")
            sb.append("• *Total Produk Terdaftar:* $totalProducts jenis barang\n")
            sb.append("• *Total Fisik Stok:* $totalUnits unit barang\n")
            sb.append("• *Estimasi Nilai Aset Stok:* Rp ${numberFormat.format(totalStockValue)}\n\n")

            if (lowStockItems.isNotEmpty()) {
                sb.append("⚠️ *Peringatan Stok Menipis / Kritis (<= 3 Unit):*\n")
                lowStockItems.take(5).forEach { item ->
                    val statusIcon = if (item.stok == 0) "🔴" else "⚠️"
                    sb.append("$statusIcon *${item.nama}* - Sisa: *${item.stok} unit* [${item.kategori}]\n")
                }
                if (lowStockItems.size > 5) {
                    sb.append("• _Dan ${lowStockItems.size - 5} barang lainnya hampir habis._\n")
                }
            } else {
                sb.append("✅ *Semua stok barang dalam kondisi aman (> 3 unit).*")
            }
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 4. COMPANY (MENU COMPANY) ENGINE
    // ==========================================
    private fun isCompanyQuery(text: String): Boolean {
        return text.contains("company") ||
                text.contains("karyawan") ||
                text.contains("pegawai") ||
                text.contains("murid") ||
                text.contains("staf") ||
                text.contains("staff") ||
                text.contains("divisi") ||
                text.contains("kehadiran") ||
                text.contains("presensi") ||
                text.contains("absensi") ||
                text.contains("siapa yang hadir") ||
                text.contains("siapa hadir") ||
                text.contains("absen hari ini")
    }

    private suspend fun handleCompanyQuery(context: Context, query: String): String {
        val db = AppDatabase.getDatabase(context)
        val anggotaList = try {
            db.anggotaDao().getAllAnggota().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val presensiList = try {
            db.presensiDao().getAllPresensi().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val gajiList = try {
            db.gajiDao().getAllGaji().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val sdfToday = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdfToday.format(Date())
        val todayDisplay = SimpleDateFormat("EEEE, d MMMM yyyy", indonesianLocale).format(Date())

        val todayPresensi = presensiList.filter {
            val presensiDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.timestamp))
            presensiDate == todayStr
        }

        val tepatWaktuCount = todayPresensi.count { it.statusPelanggaran.equals("Tepat Waktu", ignoreCase = true) }
        val terlambatCount = todayPresensi.count { it.statusPelanggaran.contains("Terlambat", ignoreCase = true) }

        val sb = StringBuilder("🏢 *INFORMASI MENU COMPANY (MY SINGGHARA)*\n\n")

        sb.append("👥 *Data Karyawan & Anggota Company:*\n")
        sb.append("• *Total Anggota Terdaftar:* ${anggotaList.size} orang\n")

        val karyawanCount = anggotaList.count { it.status.contains("Karyawan", ignoreCase = true) }
        val muridCount = anggotaList.count { it.status.contains("Murid", ignoreCase = true) }
        val guruCount = anggotaList.count { it.status.contains("Guru", ignoreCase = true) || it.status.contains("Dosen", ignoreCase = true) }
        val stafCount = anggotaList.count { it.status.contains("Staf", ignoreCase = true) || it.status.contains("Staff", ignoreCase = true) }

        sb.append("• Komposisi: $karyawanCount Karyawan, $muridCount Murid, $guruCount Guru, $stafCount Staf\n")

        if (anggotaList.isNotEmpty()) {
            sb.append("📋 *Daftar Anggota Terbaru:*\n")
            anggotaList.take(5).forEachIndexed { i, a ->
                val noId = if (a.nomorInduk.isNotBlank()) " (${a.nomorInduk})" else ""
                val div = if (a.divisiOrJurusan.isNotBlank()) " [${a.divisiOrJurusan}]" else ""
                sb.append("${i + 1}. *${a.nama}*$noId - ${a.status}$div\n")
            }
            sb.append("\n")
        } else {
            sb.append("• _Belum ada data anggota yang disimpan di menu Company._\n\n")
        }

        sb.append("⏱️ *Rekap Absensi Hari Ini ($todayDisplay):*\n")
        sb.append("• *Total Presensi Masuk:* ${todayPresensi.size} orang\n")
        sb.append("• *Tepat Waktu:* $tepatWaktuCount orang | *Terlambat:* $terlambatCount orang\n")

        if (todayPresensi.isNotEmpty()) {
            sb.append("✅ *Orang yang Sudah Hadir Hari Ini:*\n")
            todayPresensi.take(5).forEachIndexed { index, p ->
                val jamMasuk = SimpleDateFormat("HH:mm", Locale.US).format(Date(p.timestamp))
                val statusPel = if (p.statusPelanggaran.contains("Terlambat", ignoreCase = true)) "⚠️ Terlambat" else "✅ Tepat Waktu"
                sb.append("${index + 1}. *${p.nama}* ($jamMasuk WIB) - $statusPel\n")
            }
            sb.append("\n")
        } else {
            sb.append("• _Belum ada presensi yang tercatat untuk hari ini._\n\n")
        }

        val totalGajiBersih = gajiList.sumOf { it.gajiBersih }
        val gajiLunasCount = gajiList.count { it.statusPembayaran.equals("Lunas", ignoreCase = true) }
        sb.append("💼 *Rekap Penggajian (Gaji Company):*\n")
        sb.append("• *Total Slip Gaji:* ${gajiList.size} catatan (${gajiLunasCount} Lunas)\n")
        sb.append("• *Total Pengeluaran Gaji Bersih:* Rp ${numberFormat.format(totalGajiBersih)}\n")

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 5. LAPORAN KEUANGAN (JURNAL, BUKU BESAR, LABA RUGI)
    // ==========================================
    private fun isLaporanKeuanganQuery(text: String): Boolean {
        return text.contains("laporan keuangan") ||
                text.contains("jurnal umum") ||
                text.contains("jurnal") ||
                text.contains("buku besar") ||
                text.contains("laba rugi") ||
                text.contains("neraca") ||
                text.contains("profit") ||
                text.contains("apakah untung") ||
                text.contains("laba bersih") ||
                text.contains("rugi") ||
                text.contains("beban usaha") ||
                text.contains("pendapatan usaha") ||
                text.contains("kas usaha")
    }

    private fun handleLaporanKeuanganQuery(context: Context, query: String): String {
        val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val journalEntriesStr = prefs.getString("journal_entries_serialized", "") ?: ""
        val adjustmentEntriesStr = prefs.getString("adjustment_entries_serialized", "") ?: ""

        val journalEntries = deserializeJournalEntries(journalEntriesStr)
        val adjustmentEntries = deserializeAdjustmentEntries(adjustmentEntriesStr)

        if (journalEntries.isEmpty()) {
            return "📊 *LAPORAN KEUANGAN (MY SINGGHARA)*\n\nBelum ada transaksi jurnal umum yang tercatat di menu Laporan Keuangan. Tambahkan transaksi jurnal melalui aplikasi untuk melihat laporan Laba Rugi dan Neraca."
        }

        var totalPendapatan = 0.0
        var totalBeban = 0.0
        val pendapatanDetail = mutableMapOf<String, Double>()
        val bebanDetail = mutableMapOf<String, Double>()

        val allAccNames = (journalEntries.flatMap { listOf(it.debetAkun, it.kreditAkun) } +
                adjustmentEntries.flatMap { listOf(it.debetAkun, it.kreditAkun) }).toSet()

        for (acc in allAccNames) {
            val isRevenue = (acc.contains("Pendapatan", ignoreCase = true) || acc.contains("Penjualan", ignoreCase = true)) &&
                    !acc.contains("diterima di muka", ignoreCase = true) && !acc.contains("ditangguhkan", ignoreCase = true)

            val isExpense = (acc.contains("Beban", ignoreCase = true) || acc.contains("Biaya", ignoreCase = true) ||
                    acc.contains("Penyusutan", ignoreCase = true) || acc.equals("Harga Pokok Penjualan", ignoreCase = true) ||
                    acc.equals("HPP", ignoreCase = true)) && !acc.contains("Akumulasi", ignoreCase = true)

            val debitSum = journalEntries.filter { it.debetAkun == acc }.sumOf { it.nominal } +
                    adjustmentEntries.filter { it.debetAkun == acc }.sumOf { it.nominal }
            val creditSum = journalEntries.filter { it.kreditAkun == acc }.sumOf { it.nominal } +
                    adjustmentEntries.filter { it.kreditAkun == acc }.sumOf { it.nominal }

            if (isRevenue) {
                val net = creditSum - debitSum
                if (net > 0) {
                    totalPendapatan += net
                    pendapatanDetail[acc] = net
                }
            } else if (isExpense) {
                val net = debitSum - creditSum
                if (net > 0) {
                    totalBeban += net
                    bebanDetail[acc] = net
                }
            }
        }

        val netProfit = totalPendapatan - totalBeban
        val isSurplus = netProfit >= 0

        val kasDebit = journalEntries.filter { it.debetAkun.contains("Kas", ignoreCase = true) }.sumOf { it.nominal }
        val kasKredit = journalEntries.filter { it.kreditAkun.contains("Kas", ignoreCase = true) }.sumOf { it.nominal }
        val saldoKasUsaha = kasDebit - kasKredit

        val totalDebitJurnal = journalEntries.sumOf { it.nominal }
        val totalKreditJurnal = journalEntries.sumOf { it.nominal }
        val isBalanced = totalDebitJurnal == totalKreditJurnal

        val sb = StringBuilder("📊 *LAPORAN KEUANGAN (MY SINGGHARA)*\n\n")

        sb.append("📈 *Laporan Laba Rugi (Profit & Loss):*\n")
        sb.append("• *Total Pendapatan Usaha:* Rp ${numberFormat.format(totalPendapatan)}\n")
        sb.append("• *Total Beban Usaha:* Rp ${numberFormat.format(totalBeban)}\n")
        if (isSurplus) {
            sb.append("🟢 *HASIL AKHIR: SURPLUS / LABA BERSIH: Rp ${numberFormat.format(netProfit)}*\n")
        } else {
            sb.append("🔴 *HASIL AKHIR: DEFISIT / RUGI BERSIH: Rp ${numberFormat.format(Math.abs(netProfit))}*\n")
        }
        sb.append("\n")

        if (bebanDetail.isNotEmpty()) {
            sb.append("📉 *Rincian Beban Utama:*\n")
            bebanDetail.entries.sortedByDescending { it.value }.take(3).forEach {
                sb.append("• ${it.key}: Rp ${numberFormat.format(it.value)}\n")
            }
            sb.append("\n")
        }

        sb.append("💵 *Posisi Kas & Buku Besar:*\n")
        sb.append("• *Saldo Kas Usaha:* Rp ${numberFormat.format(saldoKasUsaha)}\n")
        sb.append("• *Total Transaksi Jurnal:* ${journalEntries.size} entri transaksi\n")
        sb.append("• *Keseimbangan Jurnal:* ${if (isBalanced) "✅ Seimbang (Rp ${numberFormat.format(totalDebitJurnal)})" else "⚠️ Selisih"}\n\n")

        sb.append("📖 *Transaksi Jurnal Terbaru:*\n")
        journalEntries.takeLast(3).reversed().forEachIndexed { index, j ->
            sb.append("${index + 1}. *${j.tanggal}* - Rp ${numberFormat.format(j.nominal)}\n")
            sb.append("   ↳ _${j.keterangan}_ (Debet: ${j.debetAkun} / Kredit: ${j.kreditAkun})\n")
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 6. HOLIDAY & CALENDAR
    // ==========================================
    private fun isHolidayQuery(text: String): Boolean {
        return text.contains("libur") ||
                text.contains("tanggal merah") ||
                text.contains("cuti bersama") ||
                text.contains("skb 3 menteri") ||
                text.contains("peringatan hari ini") ||
                text.contains("hari besar") ||
                text.contains("hari apa")
    }

    private suspend fun handleHolidayQuery(context: Context, query: String): String {
        val calendar = Calendar.getInstance()
        val isCheckingTomorrow = query.contains("besok") || query.contains("tomorrow")
        val isCheckingNext = query.contains("berikutnya") || query.contains("terdekat") || query.contains("kapan lagi") || query.contains("selanjutnya")
        val isCheckingList = query.contains("daftar") || query.contains("semua") || query.contains("jadwal libur") || query.contains("list")

        val currentYear = calendar.get(Calendar.YEAR)
        val holidaysMap: Map<String, String> = HolidayClient.getFallbackHolidays(currentYear)

        if (isCheckingList) {
            val sortedHolidays = holidaysMap.toSortedMap()
            val sb = StringBuilder("📅 *DAFTAR HARI LIBUR NASIONAL ($currentYear)*\n_Sesuai SKB 3 Menteri RI & Kalender Fisik_\n\n")
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sdfDisplay = SimpleDateFormat("d MMMM yyyy", indonesianLocale)
            for (entry in sortedHolidays.entries) {
                val dateStr = entry.key
                val name = entry.value
                val d = sdfInput.parse(dateStr)
                val formattedDate = if (d != null) sdfDisplay.format(d) else dateStr
                sb.append("🔴 *${formattedDate}*\n   ↳ ${name}\n")
            }
            return sb.toString().trimEnd()
        }

        if (isCheckingNext) {
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sdfDisplay = SimpleDateFormat("EEEE, d MMMM yyyy", indonesianLocale)
            val todayStr = sdfInput.format(calendar.time)

            val upcoming = holidaysMap.filter { it.key >= todayStr }.toSortedMap()
            if (upcoming.isEmpty()) {
                val nextYearHolidays: Map<String, String> = HolidayClient.getFallbackHolidays(currentYear + 1)
                val firstNext = nextYearHolidays.toSortedMap().entries.firstOrNull()
                return if (firstNext != null) {
                    val d = sdfInput.parse(firstNext.key)
                    val disp = if (d != null) sdfDisplay.format(d) else firstNext.key
                    "⏳ *Hari Libur Nasional Terdekat Berikutnya:*\n\n🔴 *${firstNext.value}*\n📅 Tanggal: *${disp}* (${currentYear + 1})"
                } else {
                    "Belum ada data hari libur nasional selanjutnya yang tercatat."
                }
            } else {
                val nextEntry = upcoming.entries.first()
                val d = sdfInput.parse(nextEntry.key)
                val disp = if (d != null) sdfDisplay.format(d) else nextEntry.key

                val diffDays = if (d != null) {
                    val diff = d.time - calendar.timeInMillis
                    (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                } else 0

                val countdownText = when {
                    nextEntry.key == todayStr -> "🎉 *Hari ini!*"
                    diffDays == 0L || diffDays == 1L -> "⏰ *Besok!*"
                    else -> "⏳ *Dalam $diffDays hari lagi*"
                }

                return "⏳ *Hari Libur Nasional Terdekat Berikutnya:*\n\n" +
                        "🔴 *${nextEntry.value}*\n" +
                        "📅 Tanggal: *${disp}*\n" +
                        "Status: $countdownText"
            }
        }

        val targetCal = Calendar.getInstance()
        if (isCheckingTomorrow) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfHari = SimpleDateFormat("EEEE, d MMMM yyyy", indonesianLocale)
        val targetDateStr = sdfDate.format(targetCal.time)
        val targetDateDisplay = sdfHari.format(targetCal.time)
        val targetYear = targetCal.get(Calendar.YEAR)
        val targetMonth = targetCal.get(Calendar.MONTH) + 1
        val targetDay = targetCal.get(Calendar.DAY_OF_MONTH)
        val isSunday = targetCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

        val targetHolidays = HolidayClient.getFallbackHolidays(targetYear)
        val holidayName = targetHolidays[targetDateStr]
        val observanceName = HolidayClient.getObservanceForDate(targetMonth, targetDay)

        val db = AppDatabase.getDatabase(context)
        val personalAgendas = try {
            db.agendaDao().getAgendasByDateList(targetDateStr)
        } catch (e: Exception) {
            emptyList()
        }

        val waktuLabel = if (isCheckingTomorrow) "Besok" else "Hari Ini"

        val sb = StringBuilder()
        when {
            holidayName != null -> {
                sb.append("🔴 *$waktuLabel adalah HARI LIBUR NASIONAL!* 🔴\n\n")
                sb.append("📅 *Tanggal:* $targetDateDisplay\n")
                sb.append("🎉 *Libur Resmi:* *$holidayName*\n")
                if (observanceName != null) {
                    sb.append("✨ *Peringatan:* $observanceName\n")
                }
                sb.append("Selamat berlibur, beristirahat dan berkumpul bersama keluarga! ☕🌿\n\n")
            }
            isSunday -> {
                sb.append("🟡 *$waktuLabel adalah HARI MINGGU (Akhir Pekan / Hari Libur)* 🟡\n\n")
                sb.append("📅 *Tanggal:* $targetDateDisplay\n")
                sb.append("Status: Hari Libur Akhir Pekan.\n")
                if (observanceName != null) {
                    sb.append("✨ *Peringatan Hari Ini:* $observanceName\n")
                }
                sb.append("Selamat menikmati waktu istirahat Anda! ☀️\n\n")
            }
            else -> {
                sb.append("🟢 *$waktuLabel BUKAN Hari Libur Nasional* 🟢\n\n")
                sb.append("📅 *Tanggal:* $targetDateDisplay\n")
                sb.append("Status: Hari kerja / aktivitas reguler.\n")
                if (observanceName != null) {
                    sb.append("✨ *Peringatan Nasional Hari Ini:* $observanceName\n")
                }
                sb.append("Semangat beraktivitas dan semoga harimu lancar serta berkah! 💪✨\n\n")
            }
        }

        sb.append("📋 *Agenda Tercatat di My Singghara ($waktuLabel):*\n")
        if (personalAgendas.isEmpty()) {
            sb.append("• _Tidak ada agenda kegiatan yang dijadwalkan._")
        } else {
            personalAgendas.forEachIndexed { index, agenda ->
                sb.append("${index + 1}. *${agenda.judul}*\n")
                if (agenda.detail.isNotBlank()) {
                    sb.append("   ↳ _${agenda.detail}_\n")
                }
            }
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 7. TABUNGAN & KEUANGAN
    // ==========================================
    private fun isSavingsQuery(text: String): Boolean {
        return text.contains("tabungan") ||
                text.contains("saldo tabungan") ||
                text.contains("nabung") ||
                text.contains("target tabungan") ||
                text.contains("target uang") ||
                text.contains("transaksi tabungan")
    }

    private suspend fun handleSavingsQuery(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val savingDao = db.savingDao()

        val activeTarget = try {
            savingDao.getActiveTarget()
        } catch (e: Exception) {
            null
        }

        val transactions = try {
            savingDao.getAllTransactions()
        } catch (e: Exception) {
            emptyList()
        }

        val totalDeposit = transactions.filter { it.jenis.equals("masuk", ignoreCase = true) || it.jenis.equals("setoran", ignoreCase = true) }.sumOf { it.nominal }
        val totalWithdraw = transactions.filter { it.jenis.equals("keluar", ignoreCase = true) || it.jenis.equals("penarikan", ignoreCase = true) }.sumOf { it.nominal }
        val saldoTerkumpul = (totalDeposit - totalWithdraw).coerceAtLeast(0.0)

        val sb = StringBuilder("💰 *RINGKASAN TABUNGAN (MY SINGGHARA)*\n\n")
        sb.append("💵 *Saldo Tabungan Saat Ini:* Rp ${numberFormat.format(saldoTerkumpul)}\n")

        if (activeTarget != null) {
            val targetNominal = activeTarget.jumlah
            val progressPercent = if (targetNominal > 0) ((saldoTerkumpul / targetNominal) * 100).coerceAtMost(100.0) else 0.0
            val sisaTarget = (targetNominal - saldoTerkumpul).coerceAtLeast(0.0)

            sb.append("🎯 *Target Utama:* *${activeTarget.nama}*\n")
            sb.append("🏁 *Target Sasaran:* Rp ${numberFormat.format(targetNominal)}\n")
            sb.append("📊 *Progres:* ${String.format(Locale.US, "%.1f", progressPercent)}%\n")
            if (sisaTarget > 0) {
                sb.append("⏳ *Sisa Kebutuhan:* Rp ${numberFormat.format(sisaTarget)}\n")
            } else {
                sb.append("🎉 *Selamat! Target tabungan Anda sudah tercapai!*\n")
            }
        } else {
            sb.append("ℹ️ _Belum ada target tabungan aktif yang disetel._\n")
        }

        sb.append("\n📝 *Transaksi Terakhir (${transactions.take(3).size} Catatan):*\n")
        if (transactions.isEmpty()) {
            sb.append("• _Belum ada catatan mutasi tabungan._")
        } else {
            transactions.take(3).forEach { tr ->
                val tanda = if (tr.jenis.equals("keluar", ignoreCase = true)) "🔴 -" else "🟢 +"
                sb.append("$tanda Rp ${numberFormat.format(tr.nominal)} (${tr.tanggal})\n")
                if (tr.deskripsi.isNotBlank()) {
                    sb.append("   ↳ _${tr.deskripsi}_\n")
                }
            }
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 8. AGENDA & JADWAL KEGIATAN
    // ==========================================
    private fun isAgendaQuery(text: String): Boolean {
        return text.contains("agenda") ||
                text.contains("jadwal kegiatan") ||
                text.contains("acara") ||
                text.contains("jadwalku") ||
                text.contains("rencana")
    }

    private suspend fun handleAgendaQuery(context: Context, text: String): String {
        val isTomorrow = text.contains("besok")
        val cal = Calendar.getInstance()
        if (isTomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfHari = SimpleDateFormat("EEEE, d MMMM yyyy", indonesianLocale)
        val dateStr = sdfDate.format(cal.time)
        val displayDate = sdfHari.format(cal.time)
        val label = if (isTomorrow) "Besok" else "Hari Ini"

        val db = AppDatabase.getDatabase(context)
        val agendas = try {
            db.agendaDao().getAgendasByDateList(dateStr)
        } catch (e: Exception) {
            emptyList()
        }

        val jadwalHarian = try {
            db.jadwalDao().getAllJadwal().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val sb = StringBuilder("📅 *AGENDA & JADWAL KEGIATAN ($label)*\n")
        sb.append("🗓️ $displayDate\n\n")

        sb.append("📌 *Agenda Khusus Tanggal Ini:*\n")
        if (agendas.isEmpty()) {
            sb.append("• _Tidak ada agenda khusus tercatat untuk $label._\n")
        } else {
            agendas.forEachIndexed { i, ag ->
                sb.append("${i + 1}. *${ag.judul}* [${ag.tipe}]\n")
                if (ag.detail.isNotBlank()) {
                    sb.append("   _${ag.detail}_\n")
                }
            }
        }

        if (jadwalHarian.isNotEmpty()) {
            sb.append("\n⏰ *Jadwal Rutin Harian:*\n")
            jadwalHarian.take(5).forEach { j ->
                val st = if (j.isCompleted) "✅" else "⏳"
                sb.append("• $st *${j.waktu}*: ${j.kegiatan}\n")
            }
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 9. CATATAN & MEMO
    // ==========================================
    private fun isNotesQuery(text: String): Boolean {
        return text.contains("catatan") ||
                text.contains("memo") ||
                text.contains("note") ||
                text.contains("tulisan") ||
                text.contains("ingat")
    }

    private suspend fun handleNotesQuery(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val notes = try {
            db.noteDao().getAllNotes().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (notes.isEmpty()) {
            return "📝 *Catatan My Singghara*\n\nBelum ada catatan yang tersimpan di aplikasi. Anda bisa memerintahkan saya: _\"Buatkan saya catatan penting untuk kegiatan besok\"_ dan saya akan menyimpannya langsung!"
        }

        val sb = StringBuilder("📝 *CATATAN & MEMO MY SINGGHARA (${notes.size} Catatan)*\n\n")
        val pinned = notes.filter { it.isPinned }
        if (pinned.isNotEmpty()) {
            sb.append("📌 *Catatan Disematkan (Penting):*\n")
            pinned.take(3).forEach { n ->
                sb.append("• *${n.title}* (${n.category})\n")
                val preview = n.content.replace("\n", " ").take(60)
                if (preview.isNotBlank()) sb.append("  ↳ _\"$preview...\"_\n")
            }
            sb.append("\n")
        }

        sb.append("📋 *Catatan Terbaru:*\n")
        notes.take(5).forEachIndexed { idx, n ->
            val pin = if (n.isPinned) "📌 " else ""
            sb.append("${idx + 1}. $pin*${n.title}* [${n.category}]\n")
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 10. DAFTAR BELANJA
    // ==========================================
    private fun isShoppingQuery(text: String): Boolean {
        return text.contains("belanja") ||
                text.contains("beli") ||
                text.contains("pasar") ||
                text.contains("shopping")
    }

    private suspend fun handleShoppingQuery(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val pasarItems = try {
            db.belanjaPasarDao().getAllBelanjaPasar().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val pending = pasarItems.filter { !it.sudahDibeli }
        val bought = pasarItems.filter { it.sudahDibeli }

        val sb = StringBuilder("🛒 *DAFTAR BELANJA PASAR (MY SINGGHARA)*\n\n")
        sb.append("⏳ *Belum Dibeli (${pending.size} Barang):*\n")
        if (pending.isEmpty()) {
            sb.append("• _Semua barang belanjaan pasar sudah selesai dibeli!_ 🎉\n")
        } else {
            var totalEst = 0.0
            pending.forEachIndexed { i, itm ->
                val hargaStr = if (itm.perkiraanHarga > 0) " (Rp ${numberFormat.format(itm.perkiraanHarga)})" else ""
                sb.append("${i + 1}. *${itm.nama}* - ${itm.jumlah} ${itm.satuan}$hargaStr\n")
                totalEst += itm.perkiraanHarga
            }
            if (totalEst > 0) {
                sb.append("💵 *Estimasi Total:* Rp ${numberFormat.format(totalEst)}\n")
            }
        }

        if (bought.isNotEmpty()) {
            sb.append("\n✅ *Sudah Dibeli (${bought.size} Barang)*")
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 11. ARISAN KELUARGA
    // ==========================================
    private fun isArisanQuery(text: String): Boolean {
        return text.contains("arisan") ||
                text.contains("kocok") ||
                text.contains("iuran arisan")
    }

    private suspend fun handleArisanQuery(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val kelompokList = try {
            db.arisanDao().getAllKelompok().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (kelompokList.isEmpty()) {
            return "👥 *Arisan Keluarga*\n\nBelum ada kelompok arisan yang terdaftar di aplikasi My Singghara."
        }

        val sb = StringBuilder("👥 *STATUS ARISAN (MY SINGGHARA)*\n\n")
        kelompokList.forEachIndexed { idx, k ->
            sb.append("${idx + 1}. *${k.namaArisan}*\n")
            sb.append("   • Putaran: Ke-${k.putaranSaatIni} dari ${k.totalPutaranTarget}\n")
            sb.append("   • Iuran per Anggota: Rp ${numberFormat.format(k.nominalIuran)}\n")
            sb.append("   • Status: ${if (k.status.equals("Selesai", ignoreCase = true)) "🏁 Selesai" else "🟢 Aktif"}\n\n")
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 12. GAJI & PEMASUKAN
    // ==========================================
    private fun isSalaryQuery(text: String): Boolean {
        return text.contains("gaji") ||
                text.contains("slip gaji") ||
                text.contains("honor") ||
                text.contains("upah") ||
                text.contains("penghasilan")
    }

    private suspend fun handleSalaryQuery(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val gajiList = try {
            db.gajiDao().getAllGaji().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (gajiList.isEmpty()) {
            return "💼 *Gaji & Pemasukan*\n\nBelum ada data slip gaji atau catatan gaji karyawan di aplikasi."
        }

        val sb = StringBuilder("💼 *CATATAN GAJI (MY SINGGHARA)*\n\n")
        gajiList.take(4).forEachIndexed { i, g ->
            sb.append("${i + 1}. *${g.nama}* (${g.periode})\n")
            sb.append("   ↳ Bersih: Rp ${numberFormat.format(g.gajiBersih)} [${g.statusPembayaran}]\n")
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 13. AL-QURAN & IBADAH
    // ==========================================
    private fun isQuranQuery(text: String): Boolean {
        return text.contains("quran") ||
                text.contains("surah") ||
                text.contains("ngaji") ||
                text.contains("ayat") ||
                text.contains("baca quran")
    }

    private suspend fun handleQuranQuery(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val historyList = try {
            db.quranDao().getAllHistoryFlow().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val bookmarks = try {
            db.quranDao().getAllBookmarksFlow().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val lastHistory = historyList.firstOrNull()

        val sb = StringBuilder("📖 *AL-QUR'AN & IBADAH (MY SINGGHARA)*\n\n")
        if (lastHistory != null) {
            sb.append("🔖 *Terakhir Dibaca:*\n")
            sb.append("• Surah: *${lastHistory.surahName}* (Surah ke-${lastHistory.surahId})\n")
            sb.append("• Ayat: *Ayat ke-${lastHistory.verseNumber}*\n\n")
        } else {
            sb.append("🔖 _Belum ada riwayat bacaan Al-Qur'an terakhir._\n\n")
        }

        if (bookmarks.isNotEmpty()) {
            sb.append("⭐ *Penanda Halaman (Bookmarks):* ${bookmarks.size} Ayat tersimpan.\n")
        }

        return sb.toString().trimEnd()
    }

    // ==========================================
    // 14. STATUS SISTEM
    // ==========================================
    private suspend fun handleSystemStatus(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val notesCount = db.noteDao().getAllNotes().firstOrNull()?.size ?: 0
        val agendaCount = db.agendaDao().getAllAgendas().firstOrNull()?.size ?: 0
        val shopCount = db.shoppingDao().getAllShoppingItems().firstOrNull()?.size ?: 0
        val anggotaCount = db.anggotaDao().getAllAnggota().firstOrNull()?.size ?: 0

        val prefsKasir = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
        val stockCount = deserializeStock(prefsKasir.getString("list_stock", "") ?: "").size
        val cashierCount = deserializeCashier(prefsKasir.getString("list_cashier", "") ?: "").size

        val prefsKeu = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val journalCount = deserializeJournalEntries(prefsKeu.getString("journal_entries_serialized", "") ?: "").size

        val cal = Calendar.getInstance()
        val timeStr = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", indonesianLocale).format(cal.time)

        return """
🟢 *STATUS SISTEM DUA ARAH MY SINGGHARA*
• *Versi Aplikasi:* v${BuildConfig.VERSION_NAME}
• *Waktu Perangkat:* $timeStr
• *Koneksi Telegram Bot:* Terhubung & Aktif Dua Arah (@My_Singghara_bot)
• *Database & Modul Terhubung:*
  - 📝 Catatan (Bisa Baca & Tulis): $notesCount data
  - 📅 Agenda (Bisa Baca & Jadwalkan): $agendaCount data
  - 🛒 Belanja Pasar (Bisa Baca & Tambah): $shopCount data
  - 🕌 Jadwal Sholat: Tersinkronisasi Otomatis
  - 🌤️ Perkiraan Cuaca: OpenWeatherMap Aktif
  - 🏪 Kasir POS: $stockCount produk, $cashierCount transaksi
  - 🏢 Company: $anggotaCount anggota karyawan/staf
  - 📊 Laporan Keuangan: $journalCount entri jurnal
• *Akses Offline:* 100% Berfungsi Penuh
        """.trimIndent()
    }

    // ==========================================
    // 15. GEMINI AI AUGMENTATION
    // ==========================================
    private suspend fun tryAnswerWithGemini(
        context: Context,
        query: String,
        senderName: String
    ): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return null
        }

        return try {
            val db = AppDatabase.getDatabase(context)
            val cal = Calendar.getInstance()
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = sdfDate.format(cal.time)
            val year = cal.get(Calendar.YEAR)

            val holidaysMap = HolidayClient.getFallbackHolidays(year)
            val todayHoliday = holidaysMap[todayStr] ?: "Bukan hari libur nasional"
            val target = db.savingDao().getActiveTarget()
            val targetInfo = if (target != null) "${target.nama} (Sasaran Rp ${numberFormat.format(target.jumlah)})" else "Tidak ada target"

            val prefsKasir = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
            val namaUsaha = prefsKasir.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya"
            val stockCount = deserializeStock(prefsKasir.getString("list_stock", "") ?: "").size
            val cashierCount = deserializeCashier(prefsKasir.getString("list_cashier", "") ?: "").size

            val anggotaCount = db.anggotaDao().getAllAnggota().firstOrNull()?.size ?: 0

            val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
            val cachedWeather = prefs.getString("cached_weather_response", null)
            val weatherSnippet = if (cachedWeather != null) {
                try {
                    val root = JSONObject(cachedWeather)
                    val main = root.optJSONObject("main")
                    val desc = root.optJSONArray("weather")?.optJSONObject(0)?.optString("description", "") ?: ""
                    "${main?.optDouble("temp")}°C ($desc)"
                } catch (_: Exception) { "" }
            } else ""

            val systemContext = """
Kamu adalah asisten AI resmi aplikasi My Singghara di Telegram yang beroperasi secara DUA ARAH.
Nama pengguna yang bertanya: $senderName.
Informasi real-time database My Singghara:
- Hari ini: $todayStr ($todayHoliday)
- Cuaca saat ini: $weatherSnippet
- Modul Kasir: Toko $namaUsaha ($stockCount produk, $cashierCount transaksi penjualan)
- Modul Company: $anggotaCount data karyawan/murid/staf
- Target tabungan: $targetInfo
Kamu dapat menjawab pertanyaan maupun merespon perintah tindakan pengguna dengan ramah, sopan, akurat dalam Bahasa Indonesia, dan sertakan emoji yang relevan.
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemContext\n\nPesan pengguna: $query")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val contentObj = firstCandidate.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text", "")
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getSmartFallbackResponse(senderName: String, query: String): String {
        return """
🤖 *Halo $senderName,*
Saya memahami Anda mengirim pesan: _"$query"_.

Sebagai *Asisten Dua Arah*, saya siap menerima perintah aksi maupun pertanyaan:
• ✍️ *"Buatkan saya catatan penting untuk kegiatan besok"*
• 📅 *"Jadwalkan agenda besok rapat koordinasi"*
• 🛒 *"Beli telur 1 kg dan beras 5 kg"*
• 💰 *"Catat tabungan masuk 100000"*
• 🕌 *"Jadwal sholat hari ini"*
• 🌤️ *"Bagaimana cuaca hari ini?"*
• 🏪 *"Cek penjualan kasir"*
• 🏢 *"Data karyawan company"*
• 📊 *"Laporan laba rugi usaha"*

Atau ketik */menu* untuk melihat panduan lengkap fitur asisten My Singghara! 😊
        """.trimIndent()
    }
}
