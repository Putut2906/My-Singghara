package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================ TIPS: TATA CARA BUAT SOAL (.md) ============================

/**
 * Prompt siap-pakai untuk semua jenis AI (ChatGPT, Gemini, Claude, Copilot, dll.)
 * agar menghasilkan file .md yang kompatibel dengan importer SIMULASI CAT.
 */
private const val AI_PROMPT =
"""Anda adalah pembuat soal SKD/SIMULASI CAT BKN. Buatkan soal dalam format teks Markdown (.md) yang WAJIB mengikuti aturan parser berikut, tanpa penjelasan tambahan di luar format.

ATURAN FORMAT (persis, baris per baris):
1. Tandai kelompok kategori dengan baris: KATEGORI: TWK  atau  KATEGORI: TIU  atau  KATEGORI: TKP
2. Tiap soal diawali nomor: "1. <teks soal>" (boleh 1..999, titik atau kurung tutup).
3. Opsi jawaban satu baris per huruf, A-E: "A. <opsi>", "B. <opsi>", dst. (minimal 2, maksimal 5).
4. Kunci jawaban: "KUNCI: B" (huruf) ATAU "JAWABAN: 2" (nomor urut).
5. Pembahasan (opsional, 1 baris): "PEMBAHASAN: <penjelasan singkat>".
6. KHUSUS TKP: tambahkan bobot tiap opsi urut sesuai opsi: "BOBOT: 5,4,3,2" (5 = paling baik). Opsi terbaik harus = 5.

CONTOH OUTPUT BENAR:
KATEGORI: TWK
1. Pancasila sebagai dasar negara terkandung dalam Pembukaan UUD 1945 alinea ke-...
A. Pertama
B. Kedua
C. Ketiga
D. Keempat
E. Penutup
KUNCI: A
PEMBAHASAN: Rumusan dasar negara terdapat pada alinea keempat, namun kedudukan sebagai dasar negara ditegaskan pada alinea pertama...

KATEGORI: TIU
1. Jika semua miring adalah licin, dan sebagian licin adalah basah, maka kesimpulan yang tepat adalah...
A. Semua miring basah
B. Sebagian miring basah
C. Tidak ada yang basah
D. Semua basah miring
E. Sebagian basah tidak miring
KUNCI: B
PEMBAHASAN: Silogisme: sebagian anggota himpunan licin juga basah, sehingga sebagian yang miring (licin) basah.

KATEGORI: TKP
1. Atasan meminta laporan selesai hari ini, namun Anda punya dua pekerjaan lain yang juga mendesak. Sikap Anda...
A. Mengerjakan ketiganya sekaligus seadanya
B. Mendahulukan laporan atasan dan menyesuaikan pekerjaan lain
C. Menolak menambah pekerjaan
D. Meminta rekan mengerjakan semuanya
E. Mengerjakan pekerjaan lain dulu
KUNCI: B
BOBOT: 4,5,2,1,3
PEMBAHASAN: Prioritas pada instruksi atasan dengan tetap menata pekerjaan lain.

TUGAS ANDA:
- Buatkan JUMLAH berikut per kategori: TWK = {N_TWK}, TIU = {N_TIU}, TKP = {N_TKP}.
- Topik/level: {TOPIK}.
- Gunakan bahasa Indonesia, tingkat kesulitan bervariasi (mudah-sedang-sulit), jawaban benar bervariasi posisinya (A-E).
- Keluarkan HANYA isi .md siap simpan, tanpa ``` dan tanpa komentar pembuka/penutup.
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatTipsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                "Tips & Tata Cara Buat Soal",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TipsKartu {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Format File .md", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Simpan soal sebagai file .md / .txt, lalu impor lewat menu \"Impor Soal dari File\". " +
                        "Satu blok = satu soal. Baris diawali kata kunci berikut:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FormatRow("KATEGORI: TWK", "Kelompok kategori (TWK/TIU/TKP)")
                FormatRow("1. <teks soal>", "Nomor lalu teks soal")
                FormatRow("A. <opsi> .. E. <opsi>", "2-5 opsi jawaban")
                FormatRow("KUNCI: B", "Jawaban benar (huruf) / JAWABAN: 2 (nomor)")
                FormatRow("PEMBAHASAN: ...", "Penjelasan (opsional, 1 baris)")
                FormatRow("BOBOT: 5,4,3,2", "Khusus TKP, bobot tiap opsi (5=terbaik)")
            }

            TipsKartu {
                Text("Langkah Membuat Soal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Step(1, "Buka AI mana pun (ChatGPT, Gemini, Claude, Copilot, dsb.).")
                Step(2, "Salin PROMPT di bawah, isi bagian {N_TWK}, {N_TIU}, {N_TKP}, dan {TOPIK}.")
                Step(3, "Tempel jawaban AI ke aplikasi Notes/editor, simpan sebagai soal.md (UTF-8).")
                Step(4, "Di halaman Input Soal: pilih paket tryout, lalu \"Pilih File & Impor\" -> soal.md.")
                Step(5, "Cek daftar soal. Untuk TIU bergambar, buka soal lalu gunakan tombol \"Impor Gambar Soal\".")
            }

            TipsKartu {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prompt untuk Semua AI", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        clipboard.setText(AnnotatedString(AI_PROMPT))
                        copied = true
                        Toast.makeText(context, "Prompt disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copied) "Tersalin" else "Salin")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ganti {N_TWK}, {N_TIU}, {N_TKP} dengan jumlah soal, dan {TOPIK} dengan topik yang diinginkan.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        AI_PROMPT,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TipsKartu(content: @Composable ColumnScope.() -> Unit) {
    val light = !androidx.compose.foundation.isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (light) Color.White else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (light) Color(0x140F172A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (light) 8.dp else 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun FormatRow(code: String, desc: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(code, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Step(no: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("$no", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
