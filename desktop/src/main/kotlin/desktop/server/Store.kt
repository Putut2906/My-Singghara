package desktop.server

import com.example.data.AgendaItem
import com.example.data.JadwalKegiatan
import com.example.data.NoteItem
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.data.ShoppingItem

/**
 * Pintu data untuk API desktop. Entity-nya sama persis dengan aplikasi Android (berkas dipakai
 * bersama), jadi tiap modul baru cukup menambah fungsi di sini dan satu rute di [Api].
 *
 * Fungsi `upsert*` memakai insert(REPLACE) seperti LocalHttpServer di Android: id = 0 membuat
 * baris baru, id yang sudah ada menimpa baris lama.
 */
interface Store {
    suspend fun notes(): List<NoteItem>
    suspend fun upsertNote(item: NoteItem): Long
    suspend fun deleteNote(id: Int): Boolean

    suspend fun shopping(): List<ShoppingItem>
    suspend fun upsertShopping(item: ShoppingItem)
    suspend fun deleteShopping(id: Int): Boolean

    suspend fun agendas(): List<AgendaItem>
    suspend fun upsertAgenda(item: AgendaItem)
    suspend fun deleteAgenda(id: Int): Boolean

    suspend fun jadwal(): List<JadwalKegiatan>
    suspend fun upsertJadwal(item: JadwalKegiatan)
    suspend fun deleteJadwal(id: Int): Boolean

    suspend fun savingTarget(): SavingTarget?
    suspend fun setSavingTarget(target: SavingTarget)
    suspend fun savingTransactions(): List<SavingTransaction>
    suspend fun upsertSaving(item: SavingTransaction)
    suspend fun deleteSaving(id: Int): Boolean
}
