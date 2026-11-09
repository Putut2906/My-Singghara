package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

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
    abstract fun belanjaOnlineDao(): BelanjaOnlineDao
    abstract fun fcmNotificationDao(): FcmNotificationDao
    abstract fun arisanDao(): ArisanDao
    abstract fun catatanKeuanganDao(): CatatanKeuanganDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v15 -> v16: kolom foto pada belanja_online
        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE belanja_online ADD COLUMN foto TEXT NOT NULL DEFAULT ''")
            }
        }

        // v16 -> v17: tabel catatan keuangan
        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catatan_keuangan` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tanggal` TEXT NOT NULL, " +
                        "`keterangan` TEXT NOT NULL, " +
                        "`kategori` TEXT NOT NULL, " +
                        "`sumberDana` TEXT NOT NULL, " +
                        "`jenis` TEXT NOT NULL, " +
                        "`jumlah` REAL NOT NULL)"
                )
            }
        }

        // v17 -> v18: memastikan tabel catatan keuangan ada (idempoten, aman untuk
        // instalasi yang pernah memakai skema v17 versi lama).
        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catatan_keuangan` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tanggal` TEXT NOT NULL, " +
                        "`keterangan` TEXT NOT NULL, " +
                        "`kategori` TEXT NOT NULL, " +
                        "`sumberDana` TEXT NOT NULL, " +
                        "`jenis` TEXT NOT NULL, " +
                        "`jumlah` REAL NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_singghara_database"
                )
                .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
