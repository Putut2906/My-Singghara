package com.example.network

import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val name: String?,
    val main: WeatherMain?,
    val weather: List<WeatherDescription>?,
    val wind: WeatherWind?,
    val dt: Long?
)

@JsonClass(generateAdapter = true)
data class WeatherMain(
    val temp: Double?,
    val feels_like: Double?,
    val humidity: Double?,
    val pressure: Double?
)

@JsonClass(generateAdapter = true)
data class WeatherDescription(
    val id: Int?,
    val main: String?,
    val description: String?,
    val icon: String?
)

@JsonClass(generateAdapter = true)
data class WeatherWind(
    val speed: Double?
)

interface WeatherService {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "id"
    ): WeatherResponse
}

object WeatherClient {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: WeatherService = retrofit.create(WeatherService::class.java)
}
