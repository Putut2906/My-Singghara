package com.example.ui.components

import java.util.Calendar
import kotlin.math.*

data class PrayerSchedule(
    val imsak: String,
    val subuh: String,
    val dzuhur: String,
    val ashar: String,
    val maghrib: String,
    val isya: String
)

object PrayerTimesHelper {

    fun getPrayerTimesForTimezone(timezone: String, calendar: Calendar): PrayerSchedule {
        // Jakarta for WIB, Makassar for WITA, Ambon for WIT
        val (lat, lng, offset) = when (timezone) {
            "WITA" -> Triple(-5.1477, 119.4327, 8.0)
            "WIT" -> Triple(-3.6954, 128.1814, 9.0)
            else -> Triple(-6.2088, 106.8456, 7.0) // WIB (Jakarta)
        }
        return getPrayerTimesForLocation(lat, lng, offset, calendar)
    }

    fun getPrayerTimesForLocation(latitude: Double, longitude: Double, timezoneOffset: Double, calendar: Calendar): PrayerSchedule {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        // Spencer's Fourier series approximation for Solar Declination and Equation of time
        val s = 2.0 * Math.PI * (dayOfYear - 1.0) / 365.25
        val declination = 0.006918 - 0.399912 * cos(s) + 0.070257 * sin(s) - 0.006758 * cos(2.0 * s) + 0.000907 * sin(2.0 * s) - 0.002697 * cos(3.0 * s) + 0.00148 * sin(3.0 * s)
        val eot = 229.18 * (0.000075 + 0.001868 * cos(s) - 0.032077 * sin(s) - 0.014615 * cos(2.0 * s) - 0.040849 * sin(2.0 * s) - 0.000602 * cos(3.0 * s) - 0.00085 * sin(3.0 * s))

        // Solar noon (transit) in hours
        val transit = 12.0 + timezoneOffset - (longitude / 15.0) - (eot / 60.0)

        // Helper to calculate hour angle of event with angular altitude 'a' below horizon in degrees
        fun hourAngle(angle: Double): Double {
            val aRad = Math.toRadians(angle)
            val latRad = Math.toRadians(latitude)
            val cosH = (sin(aRad) - sin(latRad) * sin(declination)) / (cos(latRad) * cos(declination))
            return if (cosH in -1.1..1.1) {
                val clampedCosH = cosH.coerceIn(-1.0, 1.0)
                Math.toDegrees(acos(clampedCosH)) / 15.0
            } else {
                6.0 // Fallback
            }
        }

        // Fajr (Subuh) angle: -20 degrees (Kemenag RI)
        val hFajr = hourAngle(-20.0)
        val subuhDecimal = transit - hFajr

        // Isha (Isya) angle: -18 degrees (Kemenag RI)
        val hIsha = hourAngle(-18.0)
        val isyaDecimal = transit + hIsha

        // Sunset (Maghrib) angle: -1.0 degrees (including atmospheric refraction)
        val hSunset = hourAngle(-1.0)
        val maghribDecimal = transit + hSunset

        // Zuhr (Dzuhur): transit time + buffer (2 minutes)
        val dzuhurDecimal = transit + (2.0 / 60.0)

        // Asr (Ashar): shadow ratio is 1 for Shafi'i
        val latDiffRad = Math.toRadians(abs(latitude - Math.toDegrees(declination)))
        val altitudeAsrRad = atan(1.0 / (1.0 + tan(latDiffRad)))
        val cosHAsr = (sin(altitudeAsrRad) - sin(Math.toRadians(latitude)) * sin(declination)) / (cos(Math.toRadians(latitude)) * cos(declination))
        val hAsr = if (cosHAsr in -1.1..1.1) {
            val clampedCosHAsr = cosHAsr.coerceIn(-1.0, 1.0)
            Math.toDegrees(acos(clampedCosHAsr)) / 15.0
        } else {
            3.0 // Fallback
        }
        val asharDecimal = transit + hAsr

        // Imsak is 10 minutes before Subuh
        val imsakDecimal = subuhDecimal - (10.0 / 60.0)

        return PrayerSchedule(
            imsak = formatTime(imsakDecimal),
            subuh = formatTime(subuhDecimal),
            dzuhur = formatTime(dzuhurDecimal),
            ashar = formatTime(asharDecimal),
            maghrib = formatTime(maghribDecimal),
            isya = formatTime(isyaDecimal)
        )
    }

    private fun formatTime(decimalHours: Double): String {
        val totalMinutes = (decimalHours * 60).roundToInt()
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        val resolvedHours = if (hours < 0) hours + 24 else hours
        val resolvedMinutes = if (minutes < 0) minutes + 60 else minutes
        return String.format("%02d:%02d", resolvedHours, resolvedMinutes)
    }
}
