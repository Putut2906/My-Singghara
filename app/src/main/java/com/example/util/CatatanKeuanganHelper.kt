package com.example.util

import com.example.data.CatatanKeuanganItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CatatanKeuanganHelper {

    const val KATEGORI_MAKAN = "Makan & Minum"
    const val KATEGORI_TRANSPORTASI = "Transportasi"
    const val KATEGORI_TAGIHAN = "Tagihan"
    const val KATEGORI_BELANJA = "Belanja & Hiburan"
    const val KATEGORI_LAINNYA = "Lainnya"
    const val KATEGORI_PEMASUKAN = "Pemasukan"

    val KATEGORI_PENGELUARAN = listOf(
        KATEGORI_MAKAN,
        KATEGORI_TRANSPORTASI,
        KATEGORI_TAGIHAN,
        KATEGORI_BELANJA,
        KATEGORI_LAINNYA
    )

    val SUMBER_DANA_UMUM = listOf(
        "BCA", "Mandiri", "BRI", "BNI", "Bank Jago",
        "GoPay", "DANA", "OVO", "ShopeePay", "Tunai"
    )

    // Kata kunci diurutkan dari yang terpanjang agar GOFOOD/SHOPEEFOOD tidak
    // tertangkap lebih dulu oleh GRAB/SHOPEE.
    private val KATA_KUNCI: List<Pair<String, String>> = run {
        val daftar = mutableListOf<Pair<String, String>>()
        fun tambah(kategori: String, vararg kata: String) {
            kata.forEach { daftar.add(it.uppercase(Locale.US) to kategori) }
        }
        tambah(
            KATEGORI_MAKAN,
            "MAKAN", "MAKANAN", "MINUM", "MINUMAN", "KOPI", "CAFE", "KAFE", "RESTO", "RESTORAN",
            "WARUNG", "BAKSO", "NASI", "MIE", "AYAM", "SATE", "BURGER", "PIZZA", "SUSHI",
            "JAJAN", "KUE", "ROTI", "BAKERY", "KFC", "MCD", "MCDONALD", "GOFOOD", "GRABFOOD",
            "SHOPEEFOOD", "KANTIN", "CATERING", "ES", "TEH", "SUSU", "SARAPAN", "MAKANSIANG"
        )
        tambah(
            KATEGORI_TRANSPORTASI,
            "BENSIN", "PERTAMINA", "PERTALITE", "PERTAMAX", "SOLAR", "OJEK", "GOJEK", "GOJCAR",
            "GOJBIKE", "GRAB", "GRABCAR", "GRABBIKE", "MAXIM", "TOL", "PARKIR", "KRL", "MRT", "LRT",
            "BUS", "ANGKOT", "TAKSI", "TAXI", "TIKET", "TRAVEL", "BANDARA", "KERETA", "OLI",
            "SERVIS", "BENGKEL", "GANJIL"
        )
        tambah(
            KATEGORI_TAGIHAN,
            "LISTRIK", "PLN", "AIR", "PDAM", "PAM", "INTERNET", "WIFI", "INDIHOME", "PULSA",
            "PAKET", "TELKOMSEL", "TELKOM", "XL", "BPJS", "KOS", "SEWA", "KONTRAKAN", "CICILAN",
            "ANGSURAN", "KREDIT", "TAGIHAN", "IURAN", "ASURANSI", "PAJAK", "SUMBANGAN", "DONASI"
        )
        tambah(
            KATEGORI_BELANJA,
            "BELANJA", "INDOMARET", "ALFAMART", "SUPERMARKET", "MINIMARKET", "TOKOPEDIA", "TOKO",
            "SHOPEE", "LAZADA", "BIBIT", "BLIBLI", "MALL", "FASHION", "PAKAIAN", "SEPATU",
            "BIOSKOP", "NONTON", "CINEMA", "NETFLIX", "SPOTIFY", "YOUTUBE", "DISNEY", "GAME",
            "GAMES", "STEAM", "KONSER", "WISATA", "HOTEL", "LIBURAN"
        )
        daftar.sortedByDescending { it.first.length }
    }

    /** Menebak kategori pengeluaran dari keterangan; null bila tidak ada yang cocok. */
    fun deteksiKategori(keterangan: String): String? {
        val bersih = keterangan.uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), " ").trim()
        if (bersih.isEmpty()) return null
        val token = bersih.split(' ')
        for ((kata, kategori) in KATA_KUNCI) {
            for (t in token) {
                if (t == kata) return kategori
                // Kata panjang boleh berimbuhan: "BENSINNYA", "PARKIRAN".
                if (kata.length >= 5 && t.startsWith(kata) && t.length - kata.length <= 5) return kategori
            }
        }
        return null
    }

    /** Tanggal Senin pada pekan yang memuat tanggal tersebut (YYYY-MM-DD). */
    fun awalPekan(tanggal: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = try {
            sdf.parse(tanggal)
        } catch (e: Exception) {
            null
        } ?: return tanggal
        val cal = Calendar.getInstance().apply { time = parsed }
        val hari = cal.get(Calendar.DAY_OF_WEEK)
        val selisih = if (hari == Calendar.SUNDAY) -6 else Calendar.MONDAY - hari
        cal.add(Calendar.DAY_OF_MONTH, selisih)
        return sdf.format(cal.time)
    }

    fun akhirPekan(awal: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = try {
            sdf.parse(awal)
        } catch (e: Exception) {
            null
        } ?: return awal
        val cal = Calendar.getInstance().apply { time = parsed }
        cal.add(Calendar.DAY_OF_MONTH, 6)
        return sdf.format(cal.time)
    }

    fun formatTanggalPanjang(tanggal: String): String {
        if (tanggal.isBlank()) return ""
        return try {
            val masuk = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(tanggal)
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(masuk!!)
        } catch (e: Exception) {
            tanggal
        }
    }

    fun formatRupiah(nilai: Double): String =
        "Rp " + String.format(Locale("id", "ID"), "%,.0f", nilai)

    fun saldoTercatat(
        transaksi: List<CatatanKeuanganItem>,
        sumberDana: String,
        saldoAwal: Double
    ): Double = saldoAwal + transaksi
        .filter { it.sumberDana.equals(sumberDana, ignoreCase = true) }
        .sumOf { if (it.jenis == "masuk") it.jumlah else -it.jumlah }
}
