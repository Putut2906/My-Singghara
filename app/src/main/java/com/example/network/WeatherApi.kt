package com.example.network

import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo (https://open-meteo.com) is used instead of OpenWeatherMap because it is
 * free for non-commercial use and requires no API key, account, or subscription.
 */
data class OpenMeteoResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val current: OpenMeteoCurrent? = null
)

data class OpenMeteoCurrent(
    val time: String? = null,
    val temperature_2m: Double? = null,
    val relative_humidity_2m: Double? = null,
    val apparent_temperature: Double? = null,
    val weather_code: Int? = null,
    val wind_speed_10m: Double? = null,
    val surface_pressure: Double? = null
)

// Normalised shape consumed by the home card, Telegram engine and the local cache.
data class WeatherResponse(
    val name: String? = null,
    val main: WeatherMain? = null,
    val weather: List<WeatherDescription>? = null,
    val wind: WeatherWind? = null,
    val dt: Long? = null
)

data class WeatherMain(
    val temp: Double? = null,
    val feels_like: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null
)

data class WeatherDescription(
    val id: Int? = null,
    val main: String? = null,
    val description: String? = null,
    val icon: String? = null
)

data class WeatherWind(
    val speed: Double? = null
)

interface WeatherService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,surface_pressure",
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse
}

object WeatherClient {
    private const val BASE_URL = "https://api.open-meteo.com/"

    private val moshi: Moshi = networkMoshi

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: WeatherService = retrofit.create(WeatherService::class.java)

    suspend fun getWeather(
        lat: Double,
        lon: Double,
        locationName: String? = null
    ): WeatherResponse {
        val raw = service.getCurrentWeather(lat = lat, lon = lon)
        return raw.toWeatherResponse(locationName)
    }

    fun toJson(response: WeatherResponse): String? = runCatching {
        moshi.adapter(WeatherResponse::class.java).toJson(response)
    }.getOrNull()

    fun fromJson(json: String?): WeatherResponse? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            moshi.adapter(WeatherResponse::class.java).fromJson(json)
        }.getOrNull()
    }
}

private fun OpenMeteoResponse.toWeatherResponse(locationName: String?): WeatherResponse {
    val now = current
    val mapped = mapWeatherCode(now?.weather_code)
    return WeatherResponse(
        name = locationName?.takeIf { it.isNotBlank() },
        main = WeatherMain(
            temp = now?.temperature_2m,
            feels_like = now?.apparent_temperature,
            humidity = now?.relative_humidity_2m,
            pressure = now?.surface_pressure
        ),
        weather = listOf(
            WeatherDescription(
                id = mapped.id,
                main = mapped.group,
                description = mapped.label,
                icon = null
            )
        ),
        wind = WeatherWind(speed = now?.wind_speed_10m),
        dt = System.currentTimeMillis() / 1000L
    )
}

private data class WeatherCondition(val id: Int, val group: String, val label: String)

private fun mapWeatherCode(code: Int?): WeatherCondition = when (code) {
    0 -> WeatherCondition(800, "Clear", "Cerah")
    1 -> WeatherCondition(801, "Clouds", "Cerah Berawan")
    2 -> WeatherCondition(802, "Clouds", "Berawan Sebagian")
    3 -> WeatherCondition(804, "Clouds", "Berawan")
    45, 48 -> WeatherCondition(741, "Fog", "Berkabut")
    51 -> WeatherCondition(300, "Drizzle", "Gerimis Ringan")
    53 -> WeatherCondition(301, "Drizzle", "Gerimis Sedang")
    55 -> WeatherCondition(302, "Drizzle", "Gerimis Lebat")
    56, 57 -> WeatherCondition(511, "Freezing Rain", "Gerimis Membeku")
    61 -> WeatherCondition(500, "Rain", "Hujan Ringan")
    63 -> WeatherCondition(501, "Rain", "Hujan Sedang")
    65 -> WeatherCondition(502, "Rain", "Hujan Lebat")
    66, 67 -> WeatherCondition(511, "Freezing Rain", "Hujan Membeku")
    71 -> WeatherCondition(600, "Snow", "Salju Ringan")
    73 -> WeatherCondition(601, "Snow", "Salju Sedang")
    75 -> WeatherCondition(602, "Snow", "Salju Lebat")
    77 -> WeatherCondition(600, "Snow", "Butiran Salju")
    80 -> WeatherCondition(520, "Rain", "Hujan Lokal Ringan")
    81 -> WeatherCondition(521, "Rain", "Hujan Lokal Sedang")
    82 -> WeatherCondition(522, "Rain", "Hujan Lokal Lebat")
    85 -> WeatherCondition(600, "Snow", "Hujan Salju Ringan")
    86 -> WeatherCondition(602, "Snow", "Hujan Salju Lebat")
    95 -> WeatherCondition(200, "Thunderstorm", "Badai Petir")
    96 -> WeatherCondition(211, "Thunderstorm", "Badai Petir dengan Hujan Es")
    99 -> WeatherCondition(232, "Thunderstorm", "Badai Petir dengan Hujan Es Lebat")
    else -> WeatherCondition(800, "Clear", "Cerah")
}
