package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nama: String,
    val jenis: String,
    val hargaSatuan: Double,
    val qty: Int,
    val pajak: Double, // dalam persen, misalnya 10.0 untuk 10%
    val total: Double,
    val tanggal: String // YYYY-MM-DD
)

@Entity(tableName = "note_items")
data class NoteItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val charCount: Int,
    val lineCount: Int,
    val lastSaved: Long = System.currentTimeMillis(),
    val fileUri: String? = null,
    val category: String = "Umum",
    val isPinned: Boolean = false,
    val colorHex: String = "#6366F1"
)

@Entity(tableName = "saving_targets")
data class SavingTarget(
    @PrimaryKey val id: Int = 1, // Kita batasi 1 target tabungan aktif, sesuai spesifikasi
    val nama: String,
    val jumlah: Double
)

@Entity(tableName = "saving_transactions")
data class SavingTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val jenis: String, // "masuk" atau "keluar"
    val kategori: String,
    val deskripsi: String,
    val nominal: Double,
    val tanggal: String // YYYY-MM-DD
)

@Entity(tableName = "agenda_items")
data class AgendaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tanggal: String, // YYYY-MM-DD
    val judul: String,
    val detail: String,
    val tipe: String // "agenda", "libur", "peringatan"
)

@Entity(tableName = "mind_maps")
data class MindMap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalText: String,
    val jsonStructure: String, // JSON string representing the hierarchical nodes
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "jadwal_kegiatan")
data class JadwalKegiatan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val waktu: String, // HH:mm format, e.g., "08:30"
    val kegiatan: String,
    val kategori: String,
    val isCompleted: Boolean = false,
    val lastCheckedDate: String = ""
)

@Entity(tableName = "anggota_items")
data class AnggotaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nomorInduk: String, // NIM / NIP / NISN
    val nama: String,
    val status: String, // "Karyawan", "Murid", "Guru / Dosen", "Staf", "Magang"
    val divisiOrJurusan: String = "", // Divisi / Jurusan / Kelas
    val emailOrTelp: String = "",
    val fotoUri: String = "", // URI foto atau path
    val qrCodeData: String = "", // Data QR code untuk absensi
    val instansi: String = "My Singghara",
    val tanggalDibuat: Long = System.currentTimeMillis(),
    val berlakuHingga: String = "Berlaku Selama Aktif",
    val kontak: String = "",
    val email: String = "",
    val catatanQr: String = "",
    val jenisKelamin: String = "Laki-laki",
    val alamat: String = "",
    val catatan: String = "",
    val tandaTanganUri: String = ""
)

@Entity(tableName = "riwayat_presensi_anggota")
data class RiwayatPresensi(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val anggotaId: Long = 0L,
    val nomorInduk: String,
    val nama: String,
    val status: String,
    val divisi: String = "",
    val statusPresensi: String = "Hadir",
    val timestamp: Long = System.currentTimeMillis(), // Waktu Masuk / Absensi
    val waktuPulang: Long? = null, // Waktu Pulang (nullable timestamp)
    val batasWaktuMasuk: String = "08:00", // Format HH:mm
    val batasWaktuPulang: String = "17:00", // Format HH:mm
    val statusPelanggaran: String = "Tepat Waktu", // "Tepat Waktu", "Terlambat Masuk", "Pulang Lebih Awal", "Terlambat & Pulang Cepat"
    val catatanPelanggaran: String = "", // Keterangan detail pelanggaran
    val metode: String = "QR Scanner",
    val qrPayload: String = ""
)

