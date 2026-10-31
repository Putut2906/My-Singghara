package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testReceiptScannerGoPayPayment() {
    val sampleText = """
        GOPAY
        Pembayaran Berhasil
        Kepada: Indomaret Point
        Total: Rp 45.000
        Tanggal: 19/10/2026
    """.trimIndent()

    val parsed = com.example.util.ReceiptScannerHelper.parseReceiptText(sampleText)
    assertEquals("GoPay", parsed.platformName)
    assertEquals("keluar", parsed.jenis)
    assertEquals(45000.0, parsed.nominal, 0.01)
    assertEquals("Kebutuhan Pokok", parsed.kategori)
  }

  @Test
  fun testReceiptScannerDanaIncomingTransfer() {
    val sampleText = """
        DANA
        Transfer Masuk Berhasil
        Dari: Budi Santoso
        Jumlah: Rp 150.000
        Tanggal: 18 Okt 2026
    """.trimIndent()

    val parsed = com.example.util.ReceiptScannerHelper.parseReceiptText(sampleText)
    assertEquals("DANA", parsed.platformName)
    assertEquals("masuk", parsed.jenis)
    assertEquals(150000.0, parsed.nominal, 0.01)
  }

  @Test
  fun testReceiptScannerOvoBillPayment() {
    val sampleText = """
        OVO
        Pembayaran Tagihan Listrik PLN Berhasil
        Nominal: Rp 200.000
        Tanggal: 19-10-2026
    """.trimIndent()

    val parsed = com.example.util.ReceiptScannerHelper.parseReceiptText(sampleText)
    assertEquals("OVO", parsed.platformName)
    assertEquals("keluar", parsed.jenis)
    assertEquals(200000.0, parsed.nominal, 0.01)
    assertEquals("Tagihan", parsed.kategori)
  }
}



