package com.example.ht

/**
 * Model data untuk Handy Talky (Walkie Talkie) Jaringan Lokal Singghara.
 */

data class HtChannel(
    val id: Int,
    val code: String,
    val name: String,
    val frequencyMHz: Double,
    val band: HtBand,
    val description: String,
    val isEmergency: Boolean = false
)

enum class HtBand(val label: String, val shortName: String, val minFreq: Double, val maxFreq: Double) {
    VHF("VHF (Area Terbuka / Lapangan)", "VHF", 136.0, 174.0),
    UHF("UHF (Gedung & Area Padat)", "UHF", 400.0, 470.0),
    CUSTOM("Frekuensi Khusus (Manual)", "CUSTOM", 100.0, 500.0)
}

data class HtDevice(
    val id: String,
    val callsign: String,
    val ip: String,
    val frequencyMHz: Double,
    val channelName: String,
    val lastSeen: Long,
    val isTransmitting: Boolean = false
)

enum class HtLogType {
    TX, RX, BEACON, SYSTEM
}

data class HtLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val callsign: String,
    val frequencyMHz: Double,
    val type: HtLogType,
    val message: String
)

object HtChannelPresets {
    val CHANNELS: List<HtChannel> = listOf(
        // VHF Channels (136.000 - 174.000 MHz) - Sangat cocok untuk area terbuka & outdoor
        HtChannel(1, "CH-01", "Panggilan Umum", 144.100, HtBand.VHF, "Kanal panggilan umum VHF lokal"),
        HtChannel(2, "CH-02", "Koordinasi Singghara", 144.200, HtBand.VHF, "Kanal utama koordinasi harian"),
        HtChannel(3, "CH-03", "Operasional & Logistik", 144.300, HtBand.VHF, "Koordinasi kerja & pengantaran"),
        HtChannel(4, "CH-04", "Darurat & Emergency", 145.000, HtBand.VHF, "Prioritas pertolongan & situasi darurat", isEmergency = true),
        HtChannel(5, "CH-05", "VHF Simplex 1", 146.520, HtBand.VHF, "Kanal bebas hambatan jarak menengah"),
        HtChannel(6, "CH-06", "VHF Keamanan & Patroli", 155.500, HtBand.VHF, "Kanal pos satpam & patroli lingkungan"),

        // UHF Channels (400.000 - 470.000 MHz) - Sangat baik menembus tembok, beton, & gedung bertingkat
        HtChannel(7, "CH-07", "Gedung & Kantor Utama", 433.075, HtBand.UHF, "PMR UHF indoor gedung perkantoran"),
        HtChannel(8, "CH-08", "Produksi & Gudang", 433.200, HtBand.UHF, "Area gudang logistik tertutup"),
        HtChannel(9, "CH-09", "UHF Simplex Kantor", 434.500, HtBand.UHF, "Kanal kerja tim dalam ruangan"),
        HtChannel(10, "CH-10", "PMR Standar 1", 446.006, HtBand.UHF, "Walkie talkie standar band eropa"),
        HtChannel(11, "CH-11", "Tim Lapangan & Toko", 462.562, HtBand.UHF, "FRS channel 1 area komersil"),
        HtChannel(12, "CH-12", "Lantai 2 & Rooftop", 467.562, HtBand.UHF, "FRS channel 2 area vertikal gedung")
    )

    fun getChannelByFreq(freq: Double): HtChannel? {
        return CHANNELS.find { kotlin.math.abs(it.frequencyMHz - freq) < 0.001 }
    }
}

/**
 * Konfigurasi Radio over IP (RoIP) untuk menghubungkan aplikasi dengan Radio HT Fisik (Baofeng, Motorola, dll)
 */
data class HtRoipConfig(
    val enabled: Boolean = false,
    val gatewayIp: String = "",
    val gatewayPort: Int = 9876,
    val radioModel: String = "Baofeng UV-5R / Kenwood K-Plug",
    val description: String = "Gateway Radio VHF/UHF over IP"
)
