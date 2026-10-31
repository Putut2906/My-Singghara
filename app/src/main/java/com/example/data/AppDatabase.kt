package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        FcmNotification::class,
        ArisanKelompok::class,
        ArisanPeserta::class,
        ArisanIuran::class,
        ArisanKasTransaksi::class,
        ArisanPemenangRiwayat::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

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
    abstract fun fcmNotificationDao(): FcmNotificationDao
    abstract fun arisanDao(): ArisanDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_singghara_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
