package desktop.server

import com.example.data.AgendaItem
import com.example.data.JadwalKegiatan
import com.example.data.NoteItem
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.data.ShoppingItem
import com.example.ui.components.PrayerTimesHelper
import desktop.Settings
import java.time.LocalDate
import java.util.Calendar
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

class Reply(val status: Int, val body: JsonElement) {
    companion object {
        fun ok(body: JsonElement) = Reply(200, body)
    }
}

/**
 * Kontrak /api/... versi desktop. Bentuk JSON-nya disalin dari LocalHttpServer.kt (Android), sehingga
 * halaman web yang sama (WebUiPage + *WebScript) berjalan tanpa diubah.
 *
 * Rute yang belum dipindahkan menjawab 501 dengan JSON, bukan 404/HTML, supaya JS halaman bisa
 * menampilkan pesan yang jelas alih-alih macet. Daftarnya ada di desktop/README.md.
 */
class Api(
    private val store: Store,
    private val settings: Settings,
    private val port: () -> Int,
) {

    suspend fun handle(path: String, method: String, query: Map<String, String>, body: String): Reply {
        val json = parseObject(body)
        return when {
            path == "/api/status" -> Reply.ok(status())
            path.startsWith("/api/auth") -> Reply.ok(auth(method))
            path.startsWith("/api/dashboard") -> dashboard(method, json)
            path.startsWith("/api/notifications") -> Reply.ok(notifications(method))
            path.startsWith("/api/profile") -> profile(method, json)
            path.startsWith("/api/notes") -> notes(method, query, json)
            path.startsWith("/api/shopping") -> shopping(method, query, json)
            path.startsWith("/api/agenda") -> agenda(method, query, json)
            path.startsWith("/api/jadwal") -> jadwal(method, query, json)
            path.startsWith("/api/savings") -> savings(method, query, json)
            else -> notAvailable(path)
        }
    }

    // --- Dasar: status & sesi ------------------------------------------------------------------

    private fun status(): JsonObject = obj {
        put("status", "online")
        put("app", "Singghara Suite")
        put("ip", "127.0.0.1")
        put("port", port())
        put("platform", "desktop")
    }

    private fun profileName(): String = settings["profile_name"].ifBlank { "Singghara User" }
    private fun avatarIndex(): Int = settings["profile_avatar_index", "0"].toIntOrNull() ?: 0

    /**
     * Di Android, /api/auth meminta PC dipasangkan lewat PIN/QR dari HP. Di desktop, server hanya
     * mendengarkan 127.0.0.1 (lihat DesktopServer) dan datanya milik pengguna PC ini, jadi sesi
     * selalu dianggap terverifikasi.
     */
    private fun auth(method: String): JsonObject = obj {
        if (method == "POST") put("success", true)
        put("authenticated", true)
        put("rejected", false)
        put("logged_out", false)
        put("token", "desktop-local")
        put("pin", "")
        put("deviceName", "PC (lokal)")
        put("ip", "127.0.0.1")
        put("port", port())
        put("url", "http://127.0.0.1:${port()}")
        put("qrPayload", "")
        put("profileName", profileName())
        put("profileAvatarIndex", avatarIndex())
        put("hasCustomImage", false)
        put("profileImageUrl", "")
        put("currentUid", "desktop-local")
        put("isAdmin", false)
        put("adminUid", "")
        if (method == "POST") put("message", "Mode desktop: tidak perlu pemasangan dari HP")
    }

    private suspend fun dashboard(method: String, json: JsonObject): Reply {
        if (method == "POST") {
            if (json.has("activeTimeZone")) settings["active_timezone"] = json.str("activeTimeZone", "WIB")
            if (json.has("clockFontSizeSp")) {
                settings["clock_font_size_sp"] = json.dbl("clockFontSizeSp", 52.0).coerceIn(24.0, 96.0).toString()
            }
            return Reply.ok(success())
        }

        val notes = store.notes()
        val shopping = store.shopping()
        val agenda = store.agendas()
        val jadwal = store.jadwal()
        val savings = store.savingTransactions()
        val tz = settings["active_timezone", "WIB"]
        val balance = savings.fold(0.0) { acc, s -> if (s.jenis == "masuk") acc + s.nominal else acc - s.nominal }

        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, when (tz) { "WITA" -> 1; "WIT" -> 2; else -> 0 })
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val indoDays = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
        val indoMonths = arrayOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )
        val jawa = JavaneseCalendarHelper.calculateJawa(year, month, day)
        val hijri = HijriCalendarHelper.getHijriDate(year, month, day, settings["hijri_offset", "0"].toIntOrNull() ?: 0)
        val prayer = PrayerTimesHelper.getPrayerTimesForTimezone(tz, Calendar.getInstance())
        val fontSize = settings["clock_font_size_sp", "52"].toDoubleOrNull() ?: 52.0

        return Reply.ok(obj {
            put("notesCount", notes.size)
            put("shoppingCount", shopping.size)
            put("agendaCount", agenda.size)
            put("jadwalCount", jadwal.size)
            put("savingsCount", savings.size)
            // Kasir/stok (pembukuan usaha) tersimpan di SharedPreferences Android; belum dipindah.
            put("productsCount", 0)
            put("salesCount", 0)
            put("totalRevenue", 0)
            put("savingsBalance", balance)
            put("unpurchasedShopping", shopping.count { it.qty > 0 })
            put("appName", "Singghara Suite")
            put("profileName", profileName())
            put("profileAvatarIndex", avatarIndex())
            put("hasCustomImage", false)
            put("profileImageUrl", "")
            put("remotePhotoUrl", "")
            put("activeTimeZone", tz)
            put("deviceIp", "127.0.0.1")
            put("status", "Aktif (mode desktop)")
            put("recentNotes", notes.take(5).toJsonArray {
                put("id", it.id)
                put("title", it.title)
                put("category", it.category)
                put("content", if (it.content.length > 60) it.content.take(60) + "..." else it.content)
            })
            put("recentAgenda", agenda.take(5).toJsonArray {
                put("id", it.id)
                put("judul", it.judul)
                put("tanggal", it.tanggal)
                put("tipe", it.tipe)
            })
            put("allAgendas", agenda.toJsonArray {
                put("id", it.id)
                put("judul", it.judul)
                put("tanggal", it.tanggal)
                put("detail", it.detail)
                put("tipe", it.tipe)
            })
            put("currentDay", indoDays[cal.get(Calendar.DAY_OF_WEEK) - 1])
            put("currentDate", "$day ${indoMonths[month - 1]} $year")
            put("jawaDina", jawa.dina)
            put("jawaPasaran", jawa.pasaran)
            put("jawaWuku", jawa.wuku)
            put("hijriDate", hijri)
            put("clockFontSizeSp", fontSize)
            put("clockFontName", "Technology Bold (Bawaan My Singghara)")
            put("hasCustomClockFont", false)
            put("recentCashier", JsonArray(emptyList()))
            put("prayerSchedule", obj {
                put("imsak", prayer.imsak)
                put("subuh", prayer.subuh)
                put("dzuhur", prayer.dzuhur)
                put("ashar", prayer.ashar)
                put("maghrib", prayer.maghrib)
                put("isya", prayer.isya)
            })
            put("prayerScheduleSource", "Kalkulasi Astronomis")
            put("prayerLocationText", "Lokasi Default ($tz)")
            put("prayerSoundSettings", obj {
                listOf("Imsak", "Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya").forEach { put(it, if (it == "Imsak") 1 else 2) }
            })
        })
    }

    private suspend fun notifications(method: String): JsonObject {
        if (method != "GET") return success()
        val today = LocalDate.now()
        val key = today.toString()
        val jawa = JavaneseCalendarHelper.calculateJawa(today.year, today.monthValue, today.dayOfMonth)
        val hijri = HijriCalendarHelper.getHijriDate(
            today.year, today.monthValue, today.dayOfMonth, settings["hijri_offset", "0"].toIntOrNull() ?: 0
        )
        val activeAgendas = store.agendas().filter { it.tanggal == key }
        return obj {
            put("unreadFcmCount", 0)
            put("totalNotificationsCount", activeAgendas.size)
            put("todayYear", today.year)
            put("todayDateKey", key)
            put("todayHolidayName", JsonNull)
            put("todayObservanceName", JsonNull)
            put("hasHolidayOrObservanceToday", false)
            put("hijriDate", hijri)
            put("jawaDate", "${jawa.dina} ${jawa.pasaran}")
            put("ongoingJadwal", JsonNull)
            put("activeAgendas", activeAgendas.toJsonArray {
                put("id", it.id)
                put("judul", it.judul)
                put("detail", it.detail)
                put("tanggal", it.tanggal)
                put("tipe", it.tipe)
            })
            put("allHolidays", JsonArray(emptyList()))
            put("fcmNotifications", JsonArray(emptyList()))
        }
    }

    private fun profile(method: String, json: JsonObject): Reply {
        if (method == "POST") {
            return when (json.str("action", "save_profile")) {
                "save_profile", "update_profile" -> {
                    val name = json.str("name").trim()
                    if (name.isNotEmpty()) settings["profile_name"] = name
                    settings["profile_avatar_index"] = json.int("avatarIndex", 0).toString()
                    Reply.ok(success("message" to "Profil berhasil disimpan!"))
                }
                else -> notAvailable("/api/profile (${json.str("action")})")
            }
        }
        return Reply.ok(obj {
            put("profileName", profileName())
            put("profileAvatarIndex", avatarIndex())
            put("hasCustomImage", false)
            put("profileImageUrl", "")
        })
    }

    // --- Catatan ---------------------------------------------------------------------------------

    private suspend fun notes(method: String, q: Map<String, String>, json: JsonObject): Reply = when (method) {
        "GET" -> Reply.ok(store.notes().toJsonArray {
            put("id", it.id)
            put("title", it.title)
            put("content", it.content)
            put("category", it.category)
            put("isPinned", it.isPinned)
            put("colorHex", it.colorHex)
            put("lastSaved", it.lastSaved)
        })
        "POST" -> {
            val id = store.upsertNote(noteFrom(json, 0, "Catatan Baru"))
            Reply.ok(success("id" to id))
        }
        "PUT" -> {
            val id = json.int("id", -1)
            if (id < 0) badId() else {
                store.upsertNote(noteFrom(json, id, "Catatan"))
                Reply.ok(success())
            }
        }
        "DELETE" -> deleteById(q, json) { store.deleteNote(it) }
        else -> methodNotAllowed()
    }

    private fun noteFrom(json: JsonObject, id: Int, defaultTitle: String): NoteItem {
        val content = json.str("content")
        return NoteItem(
            id = id,
            title = json.str("title", defaultTitle),
            content = content,
            charCount = content.length,
            lineCount = content.split("\n").size,
            lastSaved = System.currentTimeMillis(),
            category = json.str("category", "Umum"),
            isPinned = json.bool("isPinned"),
            colorHex = json.str("colorHex", "#6366F1")
        )
    }

    // --- Belanja ---------------------------------------------------------------------------------

    private suspend fun shopping(method: String, q: Map<String, String>, json: JsonObject): Reply = when (method) {
        "GET" -> Reply.ok(store.shopping().toJsonArray {
            put("id", it.id)
            put("nama", it.nama)
            put("jenis", it.jenis)
            put("hargaSatuan", it.hargaSatuan)
            put("qty", it.qty)
            put("pajak", it.pajak)
            put("total", it.total)
            put("tanggal", it.tanggal)
        })
        "POST" -> {
            store.upsertShopping(shoppingFrom(json, 0))
            Reply.ok(success())
        }
        "PUT" -> {
            val id = json.int("id", -1)
            if (id < 0) badId() else {
                store.upsertShopping(shoppingFrom(json, id))
                Reply.ok(success())
            }
        }
        "DELETE" -> deleteById(q, json) { store.deleteShopping(it) }
        else -> methodNotAllowed()
    }

    private fun shoppingFrom(json: JsonObject, id: Int): ShoppingItem {
        val harga = json.dbl("hargaSatuan")
        val qty = json.int("qty", 1)
        val pajak = json.dbl("pajak")
        return ShoppingItem(
            id = id,
            nama = json.str("nama", "Barang"),
            jenis = json.str("jenis", "Lainnya"),
            hargaSatuan = harga,
            qty = qty,
            pajak = pajak,
            total = (harga * qty) * (1.0 + pajak / 100.0),
            tanggal = json.str("tanggal", LocalDate.now().toString())
        )
    }

    // --- Agenda & jadwal -------------------------------------------------------------------------

    private suspend fun agenda(method: String, q: Map<String, String>, json: JsonObject): Reply = when (method) {
        "GET" -> Reply.ok(store.agendas().toJsonArray {
            put("id", it.id)
            put("tanggal", it.tanggal)
            put("judul", it.judul)
            put("detail", it.detail)
            put("tipe", it.tipe)
        })
        "POST" -> {
            store.upsertAgenda(
                AgendaItem(
                    tanggal = json.str("tanggal", LocalDate.now().toString()),
                    judul = json.str("judul", "Agenda Baru"),
                    detail = json.str("detail"),
                    tipe = json.str("tipe", "agenda")
                )
            )
            Reply.ok(success())
        }
        "PUT" -> {
            val id = json.int("id", -1)
            if (id < 0) badId() else {
                store.upsertAgenda(
                    AgendaItem(
                        id = id,
                        tanggal = json.str("tanggal"),
                        judul = json.str("judul"),
                        detail = json.str("detail"),
                        tipe = json.str("tipe", "agenda")
                    )
                )
                Reply.ok(success())
            }
        }
        "DELETE" -> deleteById(q, json) { store.deleteAgenda(it) }
        else -> methodNotAllowed()
    }

    private suspend fun jadwal(method: String, q: Map<String, String>, json: JsonObject): Reply = when (method) {
        "GET" -> Reply.ok(store.jadwal().toJsonArray {
            put("id", it.id)
            put("waktu", it.waktu)
            put("kegiatan", it.kegiatan)
            put("kategori", it.kategori)
            put("isCompleted", it.isCompleted)
        })
        "POST" -> {
            store.upsertJadwal(
                JadwalKegiatan(
                    waktu = json.str("waktu", "08:00"),
                    kegiatan = json.str("kegiatan", "Kegiatan"),
                    kategori = json.str("kategori", "Umum"),
                    isCompleted = false
                )
            )
            Reply.ok(success())
        }
        "PUT" -> {
            val id = json.int("id", -1)
            if (id < 0) badId() else {
                store.upsertJadwal(
                    JadwalKegiatan(
                        id = id,
                        waktu = json.str("waktu", "08:00"),
                        kegiatan = json.str("kegiatan"),
                        kategori = json.str("kategori", "Umum"),
                        isCompleted = json.bool("isCompleted")
                    )
                )
                Reply.ok(success())
            }
        }
        "DELETE" -> deleteById(q, json) { store.deleteJadwal(it) }
        else -> methodNotAllowed()
    }

    // --- Tabungan --------------------------------------------------------------------------------

    private suspend fun savings(method: String, q: Map<String, String>, json: JsonObject): Reply = when (method) {
        "GET" -> {
            val target = store.savingTarget()
            Reply.ok(obj {
                put("success", true)
                put("target", obj {
                    put("id", target?.id ?: 1)
                    put("nama", target?.nama ?: "Tabungan Utama")
                    put("jumlah", target?.jumlah ?: 0.0)
                })
                put("transactions", store.savingTransactions().toJsonArray {
                    put("id", it.id)
                    put("jenis", it.jenis)
                    put("kategori", it.kategori)
                    put("deskripsi", it.deskripsi)
                    put("nominal", it.nominal)
                    put("tanggal", it.tanggal)
                })
            })
        }
        "POST", "PUT" -> {
            val isTarget = json.str("action") == "set_target" || json.has("targetNama") ||
                (method == "POST" && json.has("targetJumlah"))
            if (isTarget) {
                val nama = json.str("targetNama", "Tabungan Utama").trim().ifEmpty { "Tabungan Utama" }
                val jumlah = json.dbl("targetJumlah", json.dbl("jumlah"))
                store.setSavingTarget(SavingTarget(id = store.savingTarget()?.id ?: 1, nama = nama, jumlah = jumlah))
                Reply.ok(success())
            } else {
                val id = if (method == "PUT") json.int("id", -1) else 0
                if (id < 0) badId() else {
                    store.upsertSaving(
                        SavingTransaction(
                            id = id,
                            jenis = json.str("jenis", "masuk"),
                            kategori = json.str("kategori", "Umum"),
                            deskripsi = json.str("deskripsi"),
                            nominal = json.dbl("nominal"),
                            tanggal = json.str("tanggal", LocalDate.now().toString())
                        )
                    )
                    Reply.ok(success())
                }
            }
        }
        "DELETE" -> deleteById(q, json) { store.deleteSaving(it) }
        else -> methodNotAllowed()
    }

    // --- Util ------------------------------------------------------------------------------------

    private suspend fun deleteById(q: Map<String, String>, json: JsonObject, delete: suspend (Int) -> Boolean): Reply {
        val id = (q["id"] ?: json.str("id")).toIntOrNull()
        return if (id != null && delete(id)) Reply.ok(success()) else badId()
    }

    private fun badId() = Reply(400, failure("ID tidak ditemukan"))
    private fun methodNotAllowed() = Reply(405, failure("Metode tidak didukung"))

    private fun notAvailable(path: String) = Reply(
        501,
        obj {
            put("success", false)
            put("error", "not_available_on_desktop")
            put("message", "Fitur ini belum tersedia di versi desktop: $path")
        }
    )
}
