package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.LocalRepository
import com.example.data.SavingTransaction
import com.example.util.ParsedReceiptData
import com.example.util.ReceiptScannerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Penerima Sharesheet tanpa membuka UI aplikasi. Bukti transaksi yang dibagikan
 * dari GoPay/DANA/OVO/m-banking langsung dipindai dan dicatat ke Tabungan.
 *
 * Activity memakai tema transparan sehingga tidak ada layar yang tampil. Bila
 * nominal tidak berhasil dibaca, bukti diteruskan ke MainActivity agar pengguna
 * melengkapi datanya secara manual.
 */
class ShareSaveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleShare()
        } catch (t: Throwable) {
            Log.e("ShareSave", "Gagal memproses share sheet", t)
            finish()
        }
    }

    private fun handleShare() {
        val shareIntent = intent ?: return finish()
        if (shareIntent.action != Intent.ACTION_SEND && shareIntent.action != Intent.ACTION_SEND_MULTIPLE) {
            finish()
            return
        }

        val uris = extractStreamUris(shareIntent)
        val text = extractSharedText(shareIntent)
        if (uris.isEmpty() && text.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val parsed = withTimeoutOrNull(25_000) {
                    withContext(Dispatchers.IO) {
                        try {
                            parseShared(applicationContext, uris, text)
                        } catch (t: Throwable) {
                            Log.e("ShareSave", "Pemindaian bukti gagal", t)
                            null
                        }
                    }
                }

                val result = parsed
                // Hanya bukti yang terdeteksi sebagai e-wallet/bank yang disimpan tanpa
                // konfirmasi; teks lain (mis. tautan) tetap lewat dialog manual.
                val saved = result != null &&
                    result.nominal > 0 &&
                    result.platformName != "E-Wallet/Bank" &&
                    saveTransaction(applicationContext, result)

                if (saved && result != null) {
                    val label = if (result.jenis == "masuk") "Pemasukan" else "Pengeluaran"
                    val amount = formatRupiah(result.nominal)
                    Toast.makeText(this@ShareSaveActivity, "$label $amount dicatat ke Tabungan", Toast.LENGTH_LONG).show()
                    notifySaved("$label $amount tersimpan di Tabungan")
                } else {
                    // Nominal tidak terbaca: buka aplikasi dengan dialog yang sudah ada.
                    forwardToManualDialog(shareIntent, uris, text)
                }
            } catch (t: Throwable) {
                Log.e("ShareSave", "Proses share sheet gagal total", t)
            } finally {
                finish()
            }
        }
    }

    private suspend fun parseShared(context: Context, uris: List<Uri>, text: String?): ParsedReceiptData? {
        for (uri in uris) {
            val imageResult = ReceiptScannerHelper.parseReceiptImage(context, uri)
            if (imageResult.nominal > 0) return imageResult
        }
        if (!text.isNullOrBlank()) {
            val textResult = ReceiptScannerHelper.parseReceiptText(text)
            if (textResult.nominal > 0) return textResult
        }
        return null
    }

    private suspend fun saveTransaction(context: Context, parsed: ParsedReceiptData): Boolean {
        return try {
            val repository = LocalRepository(AppDatabase.getDatabase(context))
            repository.insertSavingTransaction(
                SavingTransaction(
                    jenis = parsed.jenis,
                    kategori = parsed.kategori.ifBlank { "Lainnya" },
                    deskripsi = parsed.deskripsi.ifBlank { "Transaksi ${parsed.platformName}" },
                    nominal = parsed.nominal,
                    tanggal = parsed.tanggal
                )
            )
            true
        } catch (t: Throwable) {
            Log.e("ShareSave", "Gagal menyimpan transaksi tabungan", t)
            false
        }
    }

    private fun forwardToManualDialog(shareIntent: Intent, uris: List<Uri>, text: String?) {
        try {
            val forward = Intent(this, MainActivity::class.java).apply {
                // Selalu dikirim sebagai SEND tunggal agar dialog manual menerima
                // satu lampiran yang sama walau asalnya SEND_MULTIPLE.
                action = Intent.ACTION_SEND
                type = shareIntent.type
                if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
                uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(forward)
        } catch (t: Throwable) {
            Log.e("ShareSave", "Gagal membuka dialog manual", t)
            Toast.makeText(this, "Bukti diterima, tetapi belum bisa dibaca otomatis", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractStreamUris(shareIntent: Intent): List<Uri> {
        return try {
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!list.isNullOrEmpty()) return list

            val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                shareIntent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            listOfNotNull(
                single
                    ?: shareIntent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                    ?: shareIntent.data
            )
        } catch (t: Throwable) {
            Log.e("ShareSave", "Gagal membaca lampiran share", t)
            emptyList()
        }
    }

    private fun extractSharedText(shareIntent: Intent): String? {
        val raw = try {
            shareIntent.getStringExtra(Intent.EXTRA_TEXT)
                ?: shareIntent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        } catch (t: Throwable) {
            null
        }
        return raw?.trim()?.take(200_000)?.takeIf { it.isNotEmpty() }
    }

    private fun formatRupiah(value: Double): String =
        "Rp " + String.format(Locale("id", "ID"), "%,.0f", value)

    private fun notifySaved(message: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Tabungan dari Share Sheet",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Konfirmasi bukti transaksi yang dicatat otomatis"
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                    }
                )
            }
            val openApp = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    putExtra("NAVIGATE_TO", "tabungan")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_singghara)
                .setContentTitle("Tabungan diperbarui")
                .setContentText(message)
                .setContentIntent(openApp)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.e("ShareSave", "Gagal menampilkan notifikasi", t)
        }
    }

    private companion object {
        const val CHANNEL_ID = "tabungan_share_channel"
        const val NOTIFICATION_ID = 6011
    }
}
