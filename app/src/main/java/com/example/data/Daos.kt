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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgenda(agenda: AgendaItem)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJadwal(jadwal: JadwalKegiatan)

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

