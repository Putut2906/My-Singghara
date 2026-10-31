package com.example.data.arisan

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class AnggotaArisan(
    val id: String = UUID.randomUUID().toString(),
    val nama: String,
    val hubunganKeluarga: String = "Kerabat",
    val nomorWa: String = "",
    val jumlahSlot: Int = 1,
    val sudahMenang: Boolean = false,
    val periodeMenang: Int? = null,
    val tanggalMenang: String? = null,
    val catatan: String = ""
)

@JsonClass(generateAdapter = true)
data class PembayaranIuran(
    val id: String = UUID.randomUUID().toString(),
    val periode: Int,
    val anggotaId: String,
    val namaAnggota: String,
    val jumlahSlot: Int = 1,
    val iuranPokok: Double,
    val iuranKas: Double,
    val totalTagihan: Double,
    val isLunas: Boolean = false,
    val metodePembayaran: String = "Tunai", // "Tunai" atau "Transfer Bank"
    val tanggalBayar: String = "",
    val catatan: String = ""
)

@JsonClass(generateAdapter = true)
data class KasSosialArisan(
    val id: String = UUID.randomUUID().toString(),
    val tanggal: String,
    val jenis: String, // "Masuk" atau "Keluar"
    val kategori: String, // "Iuran Kas Anggota", "Konsumsi Pertemuan", "Snack & Minuman", "Santunan / Dana Darurat", "Sewa Perlengkapan", "Lain-lain"
    val deskripsi: String,
    val nominal: Double
)

@JsonClass(generateAdapter = true)
data class RiwayatPemenangArisan(
    val id: String = UUID.randomUUID().toString(),
    val periode: Int,
    val tanggalPengundian: String,
    val anggotaId: String,
    val namaPemenang: String,
    val hubunganKeluarga: String,
    val nomorWa: String,
    val slotKe: Int = 1,
    val nominalTarikan: Double,
    val nomorKuitansi: String,
    val catatan: String = ""
)

@JsonClass(generateAdapter = true)
data class ArisanConfig(
    val namaArisan: String = "Arisan Keluarga Besar Singghara",
    val periodeAktif: Int = 1,
    val iuranPokokPerSlot: Double = 150000.0,
    val iuranKasPerSlot: Double = 25000.0,
    val jadwalPertemuanBerikutnya: String = "Minggu, 25 Oktober 2026",
    val waktuPertemuanBerikutnya: String = "10:00 WIB",
    val lokasiPertemuanBerikutnya: String = "Kediaman Bpk. Singgih Pamungkas (Jl. Singghara No. 12)",
    val namaKetuaArisan: String = "Bpk. Singgih Pamungkas",
    val namaBendahara: String = "Ibu Siti Singghara",
    val infoRekening: String = "Tunai / Transfer Rekening Bendahara",
    val catatanPertemuan: String = "Harap hadir tepat waktu dan membawa wadah makan keluarga."
)

@JsonClass(generateAdapter = true)
data class ArisanDataState(
    val config: ArisanConfig = ArisanConfig(),
    val anggotaList: List<AnggotaArisan> = emptyList(),
    val pembayaranList: List<PembayaranIuran> = emptyList(),
    val kasSosialList: List<KasSosialArisan> = emptyList(),
    val riwayatPemenangList: List<RiwayatPemenangArisan> = emptyList(),
    val siklusKe: Int = 1
)

object TerbilangHelper {
    private val SATUAN = arrayOf(
        "", "Satu", "Dua", "Tiga", "Empat", "Lima",
        "Enam", "Tujuh", "Delapan", "Sembilan", "Sepuluh", "Sebelas"
    )

    fun konversi(nilai: Long): String {
        return when {
            nilai < 0 -> "Minus " + konversi(-nilai)
            nilai == 0L -> "Nol Rupiah"
            nilai < 12 -> SATUAN[nilai.toInt()] + " "
            nilai < 20 -> konversi(nilai - 10) + "Belas "
            nilai < 100 -> konversi(nilai / 10) + "Puluh " + konversi(nilai % 10)
            nilai < 200 -> "Seratus " + konversi(nilai - 100)
            nilai < 1000 -> konversi(nilai / 100) + "Ratus " + konversi(nilai % 100)
            nilai < 2000 -> "Seribu " + konversi(nilai - 1000)
            nilai < 1000000 -> konversi(nilai / 1000) + "Ribu " + konversi(nilai % 1000)
            nilai < 1000000000 -> konversi(nilai / 1000000) + "Juta " + konversi(nilai % 1000000)
            nilai < 1000000000000L -> konversi(nilai / 1000000000) + "Miliar " + konversi(nilai % 1000000000)
            else -> konversi(nilai / 1000000000000L) + "Triliun " + konversi(nilai % 1000000000000L)
        }.replace("\\s+".toRegex(), " ").trim() + " Rupiah"
    }
}
