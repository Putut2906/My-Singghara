package com.example.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// Model response dari API Hari Libur Nasional (Nager.Date)
data class NagerHolidayResponse(
    val date: String?, // YYYY-MM-DD
    val localName: String?,
    val name: String?,
    val countryCode: String?
)

interface HolidayService {
    @GET("api/v3/PublicHolidays/{year}/{countryCode}")
    suspend fun getHolidays(
        @Path("year") year: Int,
        @Path("countryCode") countryCode: String
    ): List<NagerHolidayResponse>
}

object HolidayClient {
    private const val BASE_URL = "https://date.nager.at/" // URL API Libur Internasional Real-time

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: HolidayService = retrofit.create(HolidayService::class.java)

    /**
     * Memperoleh daftar hari libur nasional bawaan (offline/fallback) jika koneksi internet terputus
     * atau API sedang dalam pemeliharaan (maintenance mode), akurat sesuai Kalender Nasional RI & SKB 3 Menteri.
     */
    fun getFallbackHolidays(year: Int): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 1. Hari Libur Nasional Tetap Setiap Tahun
        result["$year-01-01"] = "Tahun Baru Masehi"
        result["$year-05-01"] = "Hari Buruh Internasional"
        result["$year-06-01"] = "Hari Lahir Pancasila"
        result["$year-08-17"] = "Hari Kemerdekaan Republik Indonesia"
        result["$year-12-25"] = "Hari Raya Natal"
        result["$year-12-26"] = "Cuti Bersama Hari Raya Natal"

        // 2. Daftar Hari Libur Nasional Dinamis Berdasarkan Tahun (Akurat SKB 3 Menteri & Kalender Fisik)
        val dynamicYearHolidays = mapOf(
            // 2024
            "2024-02-08" to "Isra Mi'raj Nabi Muhammad SAW",
            "2024-02-10" to "Tahun Baru Imlek 2575 Kongzili",
            "2024-03-11" to "Hari Suci Nyepi (Tahun Baru Saka 1946)",
            "2024-03-29" to "Wafat Yesus Kristus (Jumat Agung)",
            "2024-03-31" to "Hari Paskah",
            "2024-04-10" to "Hari Raya Idul Fitri 1445 H",
            "2024-04-11" to "Hari Raya Idul Fitri 1445 H (Hari Kedua)",
            "2024-05-09" to "Kenaikan Yesus Kristus",
            "2024-05-23" to "Hari Raya Waisak 2568 BE",
            "2024-06-17" to "Hari Raya Idul Adha 1445 H",
            "2024-07-07" to "Tahun Baru Islam 1446 H",
            "2024-09-16" to "Maulid Nabi Muhammad SAW",

            // 2025
            "2025-01-27" to "Isra Mikraj Nabi Muhammad SAW",
            "2025-01-29" to "Tahun Baru Imlek 2576 Kongzili",
            "2025-03-29" to "Hari Suci Nyepi (Tahun Baru Saka 1947)",
            "2025-03-31" to "Hari Raya Idul Fitri 1446 H",
            "2025-04-01" to "Hari Raya Idul Fitri 1446 H (Hari Kedua)",
            "2025-04-18" to "Wafat Yesus Kristus (Jumat Agung)",
            "2025-04-20" to "Kebangkitan Yesus Kristus (Hari Paskah)",
            "2025-05-12" to "Hari Raya Waisak 2569 BE",
            "2025-05-29" to "Kenaikan Yesus Kristus",
            "2025-06-06" to "Hari Raya Idul Adha 1446 H",
            "2025-06-27" to "Tahun Baru Islam 1447 H",
            "2025-09-05" to "Maulid Nabi Muhammad SAW",

            // 2026 (Akurat Kalender Fisik & Web Connect)
            "2026-01-16" to "Isra Mi'raj Nabi Muhammad SAW",
            "2026-02-17" to "Tahun Baru Imlek 2577 Kongzili",
            "2026-03-19" to "Hari Suci Nyepi (Tahun Baru Saka 1948)",
            "2026-03-20" to "Hari Raya Idul Fitri 1447 H",
            "2026-03-21" to "Hari Raya Idul Fitri 1447 H (Hari Kedua)",
            "2026-04-03" to "Wafat Yesus Kristus (Jumat Agung)",
            "2026-04-05" to "Kebangkitan Yesus Kristus (Paskah)",
            "2026-05-14" to "Kenaikan Yesus Kristus",
            "2026-05-27" to "Hari Raya Idul Adha 1447 H",
            "2026-05-31" to "Hari Raya Waisak 2570 BE",
            "2026-06-16" to "Tahun Baru Islam 1448 H",
            "2026-08-25" to "Maulid Nabi Muhammad SAW 1448 H",

            // 2027
            "2027-01-06" to "Isra Mi'raj Nabi Muhammad SAW",
            "2027-02-06" to "Tahun Baru Imlek 2578 Kongzili",
            "2027-03-09" to "Hari Suci Nyepi (Tahun Baru Saka 1949)",
            "2027-03-10" to "Hari Raya Idul Fitri 1448 H",
            "2027-03-11" to "Hari Raya Idul Fitri 1448 H (Hari Kedua)",
            "2027-03-26" to "Wafat Yesus Kristus (Jumat Agung)",
            "2027-03-28" to "Kebangkitan Yesus Kristus (Paskah)",
            "2027-05-06" to "Kenaikan Yesus Kristus",
            "2027-05-16" to "Hari Raya Idul Adha 1448 H",
            "2027-05-20" to "Hari Raya Waisak 2571 BE",
            "2027-06-06" to "Tahun Baru Islam 1449 H",
            "2027-08-14" to "Maulid Nabi Muhammad SAW 1449 H",

            // 2028
            "2028-01-26" to "Tahun Baru Imlek 2579 Kongzili",
            "2028-02-27" to "Hari Raya Idul Fitri 1449 H",
            "2028-02-28" to "Hari Raya Idul Fitri 1449 H (Hari Kedua)",
            "2028-03-27" to "Hari Suci Nyepi (Tahun Baru Saka 1950)",
            "2028-04-14" to "Wafat Yesus Kristus (Jumat Agung)",
            "2028-04-16" to "Kebangkitan Yesus Kristus (Paskah)",
            "2028-05-05" to "Hari Raya Idul Adha 1449 H",
            "2028-05-09" to "Hari Raya Waisak 2572 BE",
            "2028-05-25" to "Kenaikan Yesus Kristus",
            "2028-05-26" to "Tahun Baru Islam 1450 H",
            "2028-08-03" to "Maulid Nabi Muhammad SAW 1450 H"
        )

        dynamicYearHolidays.forEach { (date, name) ->
            if (date.startsWith("$year-")) {
                result[date] = name
            }
        }

        return result
    }

    /**
     * Daftar Hari Peringatan & Hari Besar Nasional / Internasional (Observances)
     * Ditampilkan dengan teks hijau di kalender Masehi.
     */
    private val observancesMap = mapOf(
        "01-03" to "Hari Amal Bhakti Kementerian Agama",
        "01-05" to "Hari Korps Wanita Angkatan Laut (KOWAL)",
        "01-10" to "Hari Gerakan Satu Juta Pohon",
        "01-15" to "Hari Dharma Samudera",
        "01-25" to "Hari Gizi dan Makanan Nasional",
        "02-09" to "Hari Pers Nasional (HPN)",
        "02-14" to "Hari Peringatan Pemberontakan PETA",
        "02-21" to "Hari Peduli Sampah Nasional",
        "02-28" to "Hari Gizi Nasional",
        "03-01" to "Hari Penegakan Kedaulatan Negara (Serangan Umum 1 Maret)",
        "03-08" to "Hari Perempuan Internasional",
        "03-09" to "Hari Musik Nasional",
        "03-22" to "Hari Air Sedunia",
        "03-24" to "Hari Peringatan Bandung Lautan Api",
        "03-30" to "Hari Film Nasional",
        "04-07" to "Hari Kesehatan Internasional",
        "04-21" to "Hari Kartini",
        "04-22" to "Hari Bumi Sedunia",
        "04-28" to "Hari Puisi Nasional",
        "05-02" to "Hari Pendidikan Nasional (Hardiknas)",
        "05-17" to "Hari Buku Nasional",
        "05-20" to "Hari Kebangkitan Nasional (Harkitnas)",
        "05-29" to "Hari Lanjut Usia Nasional",
        "06-05" to "Hari Lingkungan Hidup Sedunia",
        "06-21" to "Hari Krida Pertanian",
        "06-24" to "Hari Bidan Nasional",
        "06-29" to "Hari Keluarga Nasional (Harganas)",
        "07-01" to "Hari Bhayangkara (Polri)",
        "07-05" to "Hari Bank Indonesia",
        "07-12" to "Hari Koperasi Indonesia",
        "07-22" to "Hari Kejaksaan Republik Indonesia",
        "07-23" to "Hari Anak Nasional",
        "08-10" to "Hari Veteran Nasional & Hari Kebangkitan Teknologi Nasional",
        "08-14" to "Hari Pramuka Nasional",
        "08-18" to "Hari Konstitusi Republik Indonesia",
        "08-19" to "Hari Departemen Luar Negeri RI",
        "09-01" to "Hari Polisi Wanita (Polwan)",
        "09-04" to "Hari Pelanggan Nasional",
        "09-09" to "Hari Olahraga Nasional (Haornas)",
        "09-11" to "Hari Radio Republik Indonesia (RRI)",
        "09-17" to "Hari Palang Merah Indonesia (PMI)",
        "09-24" to "Hari Tani Nasional",
        "09-27" to "Hari Pos dan Telekomunikasi (Postel)",
        "09-28" to "Hari Kereta Api Nasional",
        "09-30" to "Hari Peringatan Pemberontakan G30S/PKI",
        "10-01" to "Hari Kesaktian Pancasila",
        "10-02" to "Hari Batik Nasional",
        "10-05" to "Hari Tentara Nasional Indonesia (TNI)",
        "10-16" to "Hari Pangan Sedunia",
        "10-22" to "Hari Santri Nasional",
        "10-24" to "Hari Dokter Nasional (IDI)",
        "10-27" to "Hari Listrik Nasional",
        "10-28" to "Hari Sumpah Pemuda",
        "10-30" to "Hari Keuangan Nasional",
        "11-10" to "Hari Pahlawan Nasional",
        "11-12" to "Hari Kesehatan Nasional & Hari Ayah Nasional",
        "11-14" to "Hari Korps Marinir & Hari Diabetes Sedunia",
        "11-25" to "Hari Guru Nasional & HUT PGRI",
        "11-28" to "Hari Menanam Pohon Indonesia",
        "11-29" to "Hari KORPRI",
        "12-01" to "Hari AIDS Sedunia",
        "12-03" to "Hari Penyandang Disabilitas Internasional",
        "12-04" to "Hari Artileri Nasional",
        "12-09" to "Hari Anti Korupsi Sedunia",
        "12-10" to "Hari Hak Asasi Manusia (HAM)",
        "12-13" to "Hari Nusantara",
        "12-15" to "Hari Juang Kartika TNI AD",
        "12-19" to "Hari Bela Negara",
        "12-22" to "Hari Ibu"
    )

    fun getObservanceForDate(month: Int, day: Int): String? {
        val key = String.format("%02d-%02d", month, day)
        return observancesMap[key]
    }

    fun getAllObservances(): Map<String, String> = observancesMap
}
