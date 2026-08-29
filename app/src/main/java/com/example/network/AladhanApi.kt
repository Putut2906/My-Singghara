package com.example.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class AladhanResponse(
    val code: Int,
    val status: String,
    val data: AladhanData
)

data class AladhanData(
    val hijri: AladhanHijri
)

data class AladhanHijri(
    val date: String,
    val day: String,
    val month: AladhanMonth,
    val year: String
)

data class AladhanMonth(
    val number: Int,
    val en: String,
    val ar: String
)

interface AladhanService {
    @GET("gregorianToHijri/{date}")
    suspend fun convertGregorianToHijri(
        @Path("date") date: String // Format: dd-mm-yyyy (e.g. 13-06-2026)
    ): AladhanResponse
}

object AladhanClient {
    private const val BASE_URL = "https://api.aladhan.com/v1/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: AladhanService = retrofit.create(AladhanService::class.java)

    // Maps English month names from Aladhan to common Indonesian spelling
    fun mapMonthToIndonesian(enMonth: String): String {
        return when (enMonth.lowercase().replace("ū", "u").replace("ḥ", "h").replace("ā", "a").replace("['`]", "")) {
            "muharram" -> "Muharram"
            "safar" -> "Safar"
            "rabiu al-awwal", "rabiul awwal", "rabi al-awwal" -> "Rabi'ul Awwal"
            "rabiu al-akhir", "rabiul akhir", "rabi al-akhir", "rabiul thani" -> "Rabi'ul Akhir"
            "jumada al-ula", "jumadil awwal" -> "Jumadil Awwal"
            "jumada al-akhira", "jumadil akhir", "jumada al-thani" -> "Jumadil Akhir"
            "rajab" -> "Rajab"
            "shaban", "syaban" -> "Sya'ban"
            "ramadan", "ramadhan" -> "Ramadhan"
            "shawwal", "syawwal" -> "Syawwal"
            "dhu al-qadah", "dzulqa'dah", "dhu l-qadah" -> "Dzulqa'dah"
            "dhu al-hijjah", "dzulhijjah", "dhu l-hijjah" -> "Dzulhijjah"
            else -> enMonth
        }
    }
}
