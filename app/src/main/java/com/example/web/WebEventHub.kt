package com.example.web

import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Jalur Server-Sent Events untuk Web Connect. HP mendorong peristiwa begitu state
 * berubah, jadi halaman PC langsung menampilkan berkas baru tanpa tekan F5.
 */
object WebEventHub {

    // Browser hanya membuka 6 koneksi paralel per alamat (HTTP/1.1). Jalur peristiwa
    // menahan satu koneksi terus-menerus, jadi dibatasi 2 saja supaya permintaan
    // biasa (muat halaman, unduh berkas, polling) tidak pernah mengantri di belakangnya.
    private const val MAX_SUBSCRIBERS = 2

    private class Subscriber(val topic: String, val output: OutputStream)

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    // false berarti terlalu banyak halaman yang terbuka; browser akan mencoba lagi
    // sesuai nilai retry yang dikirim pemanggil.
    fun subscribe(topic: String, output: OutputStream): Boolean {
        if (subscribers.size >= MAX_SUBSCRIBERS) return false
        subscribers.add(Subscriber(topic, output))
        return true
    }

    fun release(output: OutputStream) {
        for (s in subscribers) {
            if (s.output === output) subscribers.remove(s)
        }
    }

    // Ping dikirim dari thread koneksi, peristiwa dikirim dari thread pemanggil,
    // jadi keduanya direbut lewat lock subscriber yang sama supaya baris SSE tidak
    // saling potong. Mengembalikan false bila socket sudah tidak terdaftar.
    fun writeRaw(output: OutputStream, text: String): Boolean {
        val subscriber = find(output) ?: return false
        return try {
            synchronized(subscriber) {
                subscriber.output.write(text.toByteArray(Charsets.UTF_8))
                subscriber.output.flush()
            }
            true
        } catch (e: Exception) {
            subscribers.remove(subscriber)
            false
        }
    }

    private fun find(output: OutputStream): Subscriber? {
        for (s in subscribers) {
            if (s.output === output) return s
        }
        return null
    }

    fun publish(topic: String, event: String, data: String) {
        val text = "event: " + event + "\ndata: " + data + "\n\n"
        for (s in subscribers) {
            if (s.topic != topic) continue
            writeRaw(s.output, text)
        }
    }
}
