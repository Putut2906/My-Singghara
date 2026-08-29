package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

enum class AbsensiAudioType {
    MASUK_TEPAT,
    MASUK_TERLAMBAT,
    PULANG_TEPAT,
    PULANG_AWAL,
    IZIN_SAKIT,
    DEFAULT_SUCCESS
}

object AbsensiSoundHelper {
    private const val TAG = "AbsensiSoundHelper"

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var toneGenerator: ToneGenerator? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("id", "ID"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    isTtsReady = true
                    Log.d(TAG, "TTS Initialized successfully for Absensi")
                } else {
                    Log.w(TAG, "TTS Initialization failed")
                }
            }
        }

        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create ToneGenerator", e)
            }
        }
    }

    /**
     * Memutar suara khas / nada dan ujaran Text-To-Speech sesuai jenis absensi.
     */
    fun playAbsensiSound(context: Context, type: AbsensiAudioType, namaAnggota: String = "") {
        init(context)

        val cleanName = namaAnggota.trim().ifEmpty { "Anggota" }

        when (type) {
            AbsensiAudioType.MASUK_TEPAT -> {
                // Nada melodi masuk tepat waktu (Chime sukses berurutan)
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                } catch (e: Exception) {
                    Log.w(TAG, "Tone error", e)
                }

                speak(
                    text = "Presensi masuk $cleanName berhasil tercatat tepat waktu. Semangat beraktivitas!",
                    delayMs = 300
                )
            }

            AbsensiAudioType.MASUK_TERLAMBAT -> {
                // Nada peringatan terlambat (Warning alert ganda)
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 400)
                } catch (e: Exception) {
                    Log.w(TAG, "Tone error", e)
                }

                speak(
                    text = "Perhatian! Presensi masuk $cleanName tercatat terlambat.",
                    delayMs = 450
                )
            }

            AbsensiAudioType.PULANG_TEPAT -> {
                // Nada penutup kepulangan (Melodic departure tone)
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                } catch (e: Exception) {
                    Log.w(TAG, "Tone error", e)
                }

                speak(
                    text = "Presensi pulang $cleanName berhasil tercatat. Terima kasih dan selamat beristirahat.",
                    delayMs = 400
                )
            }

            AbsensiAudioType.PULANG_AWAL -> {
                // Nada peringatan pulang lebih awal
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)
                } catch (e: Exception) {
                    Log.w(TAG, "Tone error", e)
                }

                speak(
                    text = "Perhatian! Presensi pulang $cleanName tercatat mendahului jam operasional atau pulang lebih awal.",
                    delayMs = 500
                )
            }

            AbsensiAudioType.IZIN_SAKIT -> {
                // Nada lembut untuk izin / sakit
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                } catch (e: Exception) {
                    Log.w(TAG, "Tone error", e)
                }

                speak(
                    text = "Data kehadiran $cleanName berhasil dicatat.",
                    delayMs = 250
                )
            }

            AbsensiAudioType.DEFAULT_SUCCESS -> {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                } catch (e: Exception) {
                    Log.w(TAG, "Tone error", e)
                }

                speak(
                    text = "Presensi $cleanName berhasil disimpan.",
                    delayMs = 250
                )
            }
        }
    }

    private fun speak(text: String, delayMs: Long = 0) {
        mainHandler.postDelayed({
            try {
                if (isTtsReady && tts != null) {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AbsensiVoiceUtterance_${System.currentTimeMillis()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "TTS speak error", e)
            }
        }, delayMs)
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ToneGenerator", e)
        }
    }
}
