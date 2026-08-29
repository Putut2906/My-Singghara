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
     * atau API sedang dalam pemeliharaan (maintenance mode).
     */
    fun getFallbackHolidays(year: Int): Map<String, String> {
        return mapOf(
            "$year-01-01" to "Tahun Baru Masehi",
            "$year-01-29" to "Tahun Baru Imlek 2576 Kongzili",
            "$year-01-27" to "Isra Mikraj Nabi Muhammad SAW",
            "$year-03-29" to "Hari Suci Nyepi (Tahun Baru Saka 1947)",
            "$year-03-31" to "Hari Raya Idul Fitri 1446 Hijriah",
            "$year-04-01" to "Hari Raya Idul Fitri (Hari Kedua)",
            "$year-04-18" to "Wafat Yesus Kristus (Jumat Agung)",
            "$year-04-20" to "Hari Paskah",
            "$year-05-01" to "Hari Buruh Internasional",
            "$year-05-12" to "Hari Raya Waisak 2569 BE",
            "$year-05-29" to "Kenaikan Yesus Kristus",
            "$year-06-01" to "Hari Lahir Pancasila",
            "$year-06-06" to "Hari Raya Idul Adha 1446 Hijriah",
            "$year-06-27" to "Tahun Baru Islam 1447 Hijriah",
            "$year-08-17" to "Hari Kemerdekaan Republik Indonesia",
            "$year-09-05" to "Maulid Nabi Muhammad SAW",
            "$year-12-25" to "Hari Raya Natal",
            "$year-12-26" to "Cuti Bersama Hari Raya Natal"
        )
    }
}
