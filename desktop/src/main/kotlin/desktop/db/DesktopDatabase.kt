package desktop.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.data.AgendaDao
import com.example.data.AgendaItem
import com.example.data.AnggotaDao
import com.example.data.AnggotaItem
import com.example.data.ArisanDao
import com.example.data.ArisanIuran
import com.example.data.ArisanKasTransaksi
import com.example.data.ArisanKelompok
import com.example.data.ArisanPemenangRiwayat
import com.example.data.ArisanPeserta
import com.example.data.BelanjaOnlineDao
import com.example.data.BelanjaOnlineItem
import com.example.data.BelanjaPasarDao
import com.example.data.BelanjaPasarItem
import com.example.data.CatatanKeuanganDao
import com.example.data.CatatanKeuanganItem
import com.example.data.FcmNotification
import com.example.data.FcmNotificationDao
import com.example.data.GajiDao
import com.example.data.GajiItem
import com.example.data.JadwalDao
import com.example.data.JadwalKegiatan
import com.example.data.MindMap
import com.example.data.MindMapDao
import com.example.data.NoteDao
import com.example.data.NoteItem
import com.example.data.PresensiDao
import com.example.data.QuranBookmark
import com.example.data.QuranDao
import com.example.data.QuranHistory
import com.example.data.RiwayatPresensi
import com.example.data.SavingDao
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.data.ShoppingDao
import com.example.data.ShoppingItem
import java.io.File
import kotlinx.coroutines.Dispatchers

/**
 * Cermin dari AppDatabase Android: daftar entity dan versi (18) HARUS sama, karena entity & DAO-nya
 * adalah berkas yang sama. Jika versi/entity di AppDatabase.kt Android berubah, ubah di sini juga.
 * Pada tahap ini belum ada migrasi: basis data desktop selalu dimulai dari skema versi 18.
 */
@Database(
    entities = [
        ShoppingItem::class,
        NoteItem::class,
        SavingTarget::class,
        SavingTransaction::class,
        AgendaItem::class,
        QuranBookmark::class,
        QuranHistory::class,
        MindMap::class,
        JadwalKegiatan::class,
        AnggotaItem::class,
        RiwayatPresensi::class,
        GajiItem::class,
        BelanjaPasarItem::class,
        BelanjaOnlineItem::class,
        FcmNotification::class,
        ArisanKelompok::class,
        ArisanPeserta::class,
        ArisanIuran::class,
        ArisanKasTransaksi::class,
        ArisanPemenangRiwayat::class,
        CatatanKeuanganItem::class
    ],
    version = 18,
    exportSchema = false
)
abstract class DesktopDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
    abstract fun noteDao(): NoteDao
    abstract fun savingDao(): SavingDao
    abstract fun agendaDao(): AgendaDao
    abstract fun quranDao(): QuranDao
    abstract fun mindMapDao(): MindMapDao
    abstract fun jadwalDao(): JadwalDao
    abstract fun anggotaDao(): AnggotaDao
    abstract fun presensiDao(): PresensiDao
    abstract fun gajiDao(): GajiDao
    abstract fun belanjaPasarDao(): BelanjaPasarDao
    abstract fun belanjaOnlineDao(): BelanjaOnlineDao
    abstract fun fcmNotificationDao(): FcmNotificationDao
    abstract fun arisanDao(): ArisanDao
    abstract fun catatanKeuanganDao(): CatatanKeuanganDao

    companion object {
        /** Membuka (atau membuat) basis data di [file]. Room menemukan DesktopDatabase_Impl lewat refleksi. */
        fun open(file: File): DesktopDatabase {
            file.parentFile?.mkdirs()
            return Room.databaseBuilder<DesktopDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}
