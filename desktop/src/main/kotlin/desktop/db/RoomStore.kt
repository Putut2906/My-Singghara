package desktop.db

import com.example.data.AgendaItem
import com.example.data.JadwalKegiatan
import com.example.data.NoteItem
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.data.ShoppingItem
import desktop.server.Store
import kotlinx.coroutines.flow.first

/** [Store] sungguhan di atas DAO Room yang sama dengan aplikasi Android. */
class RoomStore(private val db: DesktopDatabase) : Store {

    override suspend fun notes(): List<NoteItem> = db.noteDao().getAllNotes().first()
    override suspend fun upsertNote(item: NoteItem): Long = db.noteDao().insertNote(item)
    override suspend fun deleteNote(id: Int): Boolean {
        val target = notes().find { it.id == id } ?: return false
        db.noteDao().deleteNote(target)
        return true
    }

    override suspend fun shopping(): List<ShoppingItem> = db.shoppingDao().getAllShoppingItems().first()
    override suspend fun upsertShopping(item: ShoppingItem) = db.shoppingDao().insertShoppingItem(item)
    override suspend fun deleteShopping(id: Int): Boolean {
        val target = shopping().find { it.id == id } ?: return false
        db.shoppingDao().deleteShoppingItem(target)
        return true
    }

    override suspend fun agendas(): List<AgendaItem> = db.agendaDao().getAllAgendas().first()
    override suspend fun upsertAgenda(item: AgendaItem) = db.agendaDao().insertAgenda(item)
    override suspend fun deleteAgenda(id: Int): Boolean {
        val target = agendas().find { it.id == id } ?: return false
        db.agendaDao().deleteAgenda(target)
        return true
    }

    override suspend fun jadwal(): List<JadwalKegiatan> = db.jadwalDao().getAllJadwal().first()
    override suspend fun upsertJadwal(item: JadwalKegiatan) = db.jadwalDao().insertJadwal(item)
    override suspend fun deleteJadwal(id: Int): Boolean {
        val target = jadwal().find { it.id == id } ?: return false
        db.jadwalDao().deleteJadwal(target)
        return true
    }

    override suspend fun savingTarget(): SavingTarget? = db.savingDao().getActiveTarget()
    override suspend fun setSavingTarget(target: SavingTarget) = db.savingDao().setTarget(target)
    override suspend fun savingTransactions(): List<SavingTransaction> = db.savingDao().getAllTransactions()
    override suspend fun upsertSaving(item: SavingTransaction) = db.savingDao().insertTransaction(item)
    override suspend fun deleteSaving(id: Int): Boolean {
        val target = savingTransactions().find { it.id == id } ?: return false
        db.savingDao().deleteTransaction(target)
        return true
    }
}
