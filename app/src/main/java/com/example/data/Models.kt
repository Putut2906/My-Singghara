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

@Entity(tableName = "gaji_items")
data class GajiItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val anggotaId: Long = 0L,
    val nomorInduk: String = "", // NIP / NIM / ID Karyawan
    val nama: String,
    val status: String = "Karyawan Tetap", // Karyawan Tetap, Karyawan Kontrak, Staf, Guru / Dosen, Magang, Freelance
    val peran: String = "Staff Operasional", // Manager, Supervisor, IT / Developer, Akuntan / Finance, Guru, Admin, dll.
    val periode: String = "", // e.g. "Agustus 2026" atau "2026-08"
    val tanggalBayar: String = "", // YYYY-MM-DD
    val gajiPokok: Double = 0.0,
    val tunjanganJabatan: Double = 0.0,
    val tunjanganMakanTransport: Double = 0.0,
    val tunjanganKehadiran: Double = 0.0,
    val bonusLembur: Double = 0.0,
    val tunjanganLain: Double = 0.0,
    val potonganBpjs: Double = 0.0,
    val potonganPph: Double = 0.0,
    val potonganKasbon: Double = 0.0,
    val potonganKeterlambatan: Double = 0.0,
    val potonganLain: Double = 0.0,
    val totalPendapatan: Double = 0.0, // Subtotal Penambahan (Gaji Pokok + Semua Tunjangan + Bonus)
    val totalPotongan: Double = 0.0, // Subtotal Pengurangan
    val gajiBersih: Double = 0.0, // Take Home Pay (totalPendapatan - totalPotongan)
    val metodePembayaran: String = "Transfer Bank", // Transfer Bank, Tunai, E-Wallet
    val statusPembayaran: String = "Lunas", // Lunas, Menunggu, Diproses
    val catatan: String = "",
    val tanggalDibuat: Long = System.currentTimeMillis()
)

@Entity(tableName = "rekap_belanja_pasar")
data class BelanjaPasarItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nama: String,
    val kategori: String = "Sayuran", // Sayuran, Daging & Unggas, Ikan & Seafood, Bumbu Dapur, Buah-buahan, Sembako, Tahu & Tempe, Perlengkapan, Lain-lain
    val jumlah: Double = 1.0,
    val satuan: String = "kg", // kg, gram, ons, pcs, ikat, liter, bks, karung, ekor, butir, sisir, papan, dus
    val perkiraanHarga: Double = 0.0,
    val realisasiHarga: Double = 0.0,
    val sudahDibeli: Boolean = false,
    val catatan: String = "",
    val tanggal: String = "" // YYYY-MM-DD
)

@Entity(tableName = "arisan_kelompok")
data class ArisanKelompok(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val namaArisan: String,
    val nominalIuran: Double = 100000.0,
    val periodeTipe: String = "Bulanan", // "Bulanan", "2 Mingguan", "Mingguan", "Acara Pertemuan"
    val totalPutaranTarget: Int = 10,
    val putaranSaatIni: Int = 1,
    val potonganKasPerPutaran: Double = 0.0, // Potongan kas per putaran untuk kas/konsumsi arisan keluarga
    val lokasiTuanRumah: String = "", // Lokasi pertemuan arisan berikutnya
    val tanggalPertemuanBerikutnya: String = "", // YYYY-MM-DD
    val catatan: String = "",
    val status: String = "Aktif", // "Aktif", "Selesai"
    val tanggalDibuat: Long = System.currentTimeMillis()
)

@Entity(tableName = "arisan_peserta")
data class ArisanPeserta(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val arisanId: Int,
    val nama: String,
    val nomorHp: String = "",
    val hubungan: String = "Keluarga", // Ayah, Ibu, Paman, Tante, Kakak, Adik, Sepupu, Anak, Kakek, Nenek, Kerabat
    val nomorUndian: Int = 1,
    val sudahDapat: Boolean = false,
    val putaranDapat: Int? = null,
    val tanggalDapat: String = "", // YYYY-MM-DD
    val catatan: String = ""
)

@Entity(tableName = "arisan_iuran")
data class ArisanIuran(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val arisanId: Int,
    val pesertaId: Int,
    val namaPeserta: String,
    val putaranKe: Int,
    val nominal: Double,
    val tanggalBayar: String = "", // YYYY-MM-DD
    val statusBayar: String = "Lunas", // "Lunas", "Belum Bayar"
    val metodeBayar: String = "Tunai", // "Tunai", "Transfer Bank", "E-Wallet"
    val catatan: String = ""
)

@Entity(tableName = "arisan_kas_transaksi")
data class ArisanKasTransaksi(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val arisanId: Int,
    val jenis: String = "Masuk", // "Masuk", "Keluar"
    val kategori: String = "Potongan Arisan", // Potongan Arisan, Donasi/Infaq, Konsumsi, Sewa Tempat, Santunan Sosial, Lain-lain
    val nominal: Double = 0.0,
    val tanggal: String = "", // YYYY-MM-DD
    val keterangan: String = ""
)

@Entity(tableName = "arisan_pemenang_riwayat")
data class ArisanPemenangRiwayat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val arisanId: Int,
    val putaranKe: Int,
    val pesertaId: Int,
    val namaPemenang: String,
    val totalUangDiterima: Double = 0.0,
    val potonganKas: Double = 0.0,
    val tanggalKocok: String = "", // YYYY-MM-DD
    val tuanRumahBerikutnya: String = "",
    val catatan: String = ""
)


