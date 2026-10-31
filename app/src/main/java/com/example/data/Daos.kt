package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY tanggal DESC, id DESC")
    fun getAllShoppingItems(): Flow<List<ShoppingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ShoppingItem)

    @Update
    suspend fun updateShoppingItem(item: ShoppingItem)

    @Delete
    suspend fun deleteShoppingItem(item: ShoppingItem)

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note_items ORDER BY isPinned DESC, lastSaved DESC")
    fun getAllNotes(): Flow<List<NoteItem>>

    @Query("SELECT * FROM note_items WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): NoteItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteItem): Long

    @Update
    suspend fun updateNote(note: NoteItem)

    @Delete
    suspend fun deleteNote(note: NoteItem)

    @Query("DELETE FROM note_items")
    suspend fun deleteAll()
}

@Dao
interface SavingDao {
    @Query("SELECT * FROM saving_targets WHERE id = 1 LIMIT 1")
    fun getActiveTargetFlow(): Flow<SavingTarget?>

    @Query("SELECT * FROM saving_targets WHERE id = 1 LIMIT 1")
    suspend fun getActiveTarget(): SavingTarget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTarget(target: SavingTarget)

    @Query("SELECT * FROM saving_transactions ORDER BY tanggal DESC, id DESC")
    fun getAllTransactionsFlow(): Flow<List<SavingTransaction>>

    @Query("SELECT * FROM saving_transactions ORDER BY tanggal DESC, id DESC")
    suspend fun getAllTransactions(): List<SavingTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SavingTransaction)

    @Update
    suspend fun updateTransaction(transaction: SavingTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: SavingTransaction)

    @Query("DELETE FROM saving_targets")
    suspend fun deleteAllTargets()

    @Query("DELETE FROM saving_transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface AgendaDao {
    @Query("SELECT * FROM agenda_items ORDER BY tanggal ASC")
    fun getAllAgendas(): Flow<List<AgendaItem>>

    @Query("SELECT * FROM agenda_items WHERE tanggal = :tanggal")
    fun getAgendasByDate(tanggal: String): Flow<List<AgendaItem>>

    @Query("SELECT * FROM agenda_items WHERE tanggal = :tanggal")
    suspend fun getAgendasByDateList(tanggal: String): List<AgendaItem>

    @Query("SELECT * FROM agenda_items WHERE id = :id LIMIT 1")
    suspend fun getAgendaById(id: Int): AgendaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgenda(agenda: AgendaItem)

    @Update
    suspend fun updateAgenda(agenda: AgendaItem)

    @Delete
    suspend fun deleteAgenda(agenda: AgendaItem)

    @Query("DELETE FROM agenda_items")
    suspend fun deleteAll()
}

@Dao
interface MindMapDao {
    @Query("SELECT * FROM mind_maps ORDER BY createdAt DESC")
    fun getAllMindMaps(): Flow<List<MindMap>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMindMap(mindMap: MindMap): Long

    @Update
    suspend fun updateMindMap(mindMap: MindMap)

    @Delete
    suspend fun deleteMindMap(mindMap: MindMap)

    @Query("DELETE FROM mind_maps")
    suspend fun deleteAll()
}

@Dao
interface JadwalDao {
    @Query("SELECT * FROM jadwal_kegiatan ORDER BY waktu ASC")
    fun getAllJadwal(): Flow<List<JadwalKegiatan>>

    @Query("SELECT * FROM jadwal_kegiatan WHERE id = :id LIMIT 1")
    suspend fun getJadwalById(id: Int): JadwalKegiatan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJadwal(jadwal: JadwalKegiatan)

    @Update
    suspend fun updateJadwal(jadwal: JadwalKegiatan)

    @Delete
    suspend fun deleteJadwal(jadwal: JadwalKegiatan)

    @Query("DELETE FROM jadwal_kegiatan")
    suspend fun deleteAll()
}

@Dao
interface AnggotaDao {
    @Query("SELECT * FROM anggota_items ORDER BY id DESC")
    fun getAllAnggota(): Flow<List<AnggotaItem>>

    @Query("SELECT * FROM anggota_items WHERE id = :id LIMIT 1")
    suspend fun getAnggotaById(id: Int): AnggotaItem?

    @Query("SELECT * FROM anggota_items WHERE nomorInduk = :nomorInduk LIMIT 1")
    suspend fun getAnggotaByNomorInduk(nomorInduk: String): AnggotaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnggota(anggota: AnggotaItem): Long

    @Update
    suspend fun updateAnggota(anggota: AnggotaItem)

    @Delete
    suspend fun deleteAnggota(anggota: AnggotaItem)

    @Query("DELETE FROM anggota_items")
    suspend fun deleteAll()
}

@Dao
interface PresensiDao {
    @Query("SELECT * FROM riwayat_presensi_anggota ORDER BY id DESC")
    fun getAllPresensi(): Flow<List<RiwayatPresensi>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresensi(presensi: RiwayatPresensi): Long

    @Update
    suspend fun updatePresensi(presensi: RiwayatPresensi)

    @Delete
    suspend fun deletePresensi(presensi: RiwayatPresensi)

    @Query("DELETE FROM riwayat_presensi_anggota")
    suspend fun deleteAll()
}

@Dao
interface GajiDao {
    @Query("SELECT * FROM gaji_items ORDER BY id DESC")
    fun getAllGaji(): Flow<List<GajiItem>>

    @Query("SELECT * FROM gaji_items WHERE id = :id LIMIT 1")
    suspend fun getGajiById(id: Int): GajiItem?

    @Query("SELECT * FROM gaji_items WHERE periode = :periode ORDER BY id DESC")
    fun getGajiByPeriode(periode: String): Flow<List<GajiItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGaji(gaji: GajiItem): Long

    @Update
    suspend fun updateGaji(gaji: GajiItem)

    @Delete
    suspend fun deleteGaji(gaji: GajiItem)

    @Query("DELETE FROM gaji_items")
    suspend fun deleteAll()
}

@Dao
interface BelanjaPasarDao {
    @Query("SELECT * FROM rekap_belanja_pasar ORDER BY sudahDibeli ASC, id DESC")
    fun getAllBelanjaPasar(): Flow<List<BelanjaPasarItem>>

    @Query("SELECT * FROM rekap_belanja_pasar WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): BelanjaPasarItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBelanjaPasar(item: BelanjaPasarItem): Long

    @Update
    suspend fun updateBelanjaPasar(item: BelanjaPasarItem)

    @Delete
    suspend fun deleteBelanjaPasar(item: BelanjaPasarItem)

    @Query("DELETE FROM rekap_belanja_pasar WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE rekap_belanja_pasar SET sudahDibeli = :sudahDibeli WHERE id = :id")
    suspend fun setSudahDibeli(id: Int, sudahDibeli: Boolean)

    @Query("DELETE FROM rekap_belanja_pasar WHERE sudahDibeli = 1")
    suspend fun clearCompleted()

    @Query("DELETE FROM rekap_belanja_pasar")
    suspend fun deleteAll()
}

@Dao
interface ArisanDao {
    // Kelompok
    @Query("SELECT * FROM arisan_kelompok ORDER BY id DESC")
    fun getAllKelompok(): Flow<List<ArisanKelompok>>

    @Query("SELECT * FROM arisan_kelompok WHERE id = :id LIMIT 1")
    suspend fun getKelompokById(id: Int): ArisanKelompok?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKelompok(kelompok: ArisanKelompok): Long

    @Update
    suspend fun updateKelompok(kelompok: ArisanKelompok)

    @Delete
    suspend fun deleteKelompok(kelompok: ArisanKelompok)

    // Peserta
    @Query("SELECT * FROM arisan_peserta WHERE arisanId = :arisanId ORDER BY nomorUndian ASC, id ASC")
    fun getPesertaByArisan(arisanId: Int): Flow<List<ArisanPeserta>>

    @Query("SELECT * FROM arisan_peserta WHERE arisanId = :arisanId ORDER BY nomorUndian ASC, id ASC")
    suspend fun getPesertaListByArisan(arisanId: Int): List<ArisanPeserta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeserta(peserta: ArisanPeserta): Long

    @Update
    suspend fun updatePeserta(peserta: ArisanPeserta)

    @Delete
    suspend fun deletePeserta(peserta: ArisanPeserta)

    @Query("DELETE FROM arisan_peserta WHERE arisanId = :arisanId")
    suspend fun deletePesertaByArisan(arisanId: Int)

    // Iuran
    @Query("SELECT * FROM arisan_iuran WHERE arisanId = :arisanId AND putaranKe = :putaranKe")
    fun getIuranByPutaran(arisanId: Int, putaranKe: Int): Flow<List<ArisanIuran>>

    @Query("SELECT * FROM arisan_iuran WHERE arisanId = :arisanId")
    fun getAllIuranByArisan(arisanId: Int): Flow<List<ArisanIuran>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIuran(iuran: ArisanIuran): Long

    @Update
    suspend fun updateIuran(iuran: ArisanIuran)

    @Delete
    suspend fun deleteIuran(iuran: ArisanIuran)

    @Query("DELETE FROM arisan_iuran WHERE arisanId = :arisanId")
    suspend fun deleteIuranByArisan(arisanId: Int)

    // Kas Transaksi
    @Query("SELECT * FROM arisan_kas_transaksi WHERE arisanId = :arisanId ORDER BY tanggal DESC, id DESC")
    fun getKasByArisan(arisanId: Int): Flow<List<ArisanKasTransaksi>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKas(kas: ArisanKasTransaksi): Long

    @Delete
    suspend fun deleteKas(kas: ArisanKasTransaksi)

    @Query("DELETE FROM arisan_kas_transaksi WHERE arisanId = :arisanId")
    suspend fun deleteKasByArisan(arisanId: Int)

    // Pemenang Riwayat
    @Query("SELECT * FROM arisan_pemenang_riwayat WHERE arisanId = :arisanId ORDER BY putaranKe ASC")
    fun getPemenangByArisan(arisanId: Int): Flow<List<ArisanPemenangRiwayat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPemenang(pemenang: ArisanPemenangRiwayat): Long

    @Delete
    suspend fun deletePemenang(pemenang: ArisanPemenangRiwayat)

    @Query("DELETE FROM arisan_pemenang_riwayat WHERE arisanId = :arisanId")
    suspend fun deletePemenangByArisan(arisanId: Int)

    // Delete All
    @Query("DELETE FROM arisan_kelompok")
    suspend fun deleteAllKelompok()

    @Query("DELETE FROM arisan_peserta")
    suspend fun deleteAllPeserta()

    @Query("DELETE FROM arisan_iuran")
    suspend fun deleteAllIuran()

    @Query("DELETE FROM arisan_kas_transaksi")
    suspend fun deleteAllKas()

    @Query("DELETE FROM arisan_pemenang_riwayat")
    suspend fun deleteAllPemenang()
}



