package com.example.ui.screens

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency procedural sound synthesizer for Sliding Puzzle.
 * Produces crisp wooden tile slide clicks and celebratory victory fanfare
 * with zero external assets or media file dependencies.
 */
object SlidingPuzzleAudio {
    var isSoundEnabled: Boolean = true

    private val audioScope = CoroutineScope(Dispatchers.Default + Job())

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("sliding_puzzle_prefs", Context.MODE_PRIVATE)
        isSoundEnabled = prefs.getBoolean("sound_enabled", true)
    }

    fun toggleSound(context: Context): Boolean {
        isSoundEnabled = !isSoundEnabled
        val prefs = context.getSharedPreferences("sliding_puzzle_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sound_enabled", isSoundEnabled).apply()
        if (isSoundEnabled) {
            playWoodClick()
        }
        return isSoundEnabled
    }

    /**
     * Synthesizes an authentic wooden tile click-clack impact.
     * Combines an initial sharp percussive transient with a warm resonant wood ring
     * and a rapid secondary acoustic bounce (~18ms later).
     */
    fun playWoodClick() {
        if (!isSoundEnabled) return

        audioScope.launch {
            var track: AudioTrack? = null
            try {
                val sampleRate = 22050
                val durationMs = 42
                val numSamples = (durationMs * sampleRate) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Primary impact: ~960 Hz dropping to 780 Hz with exponential decay
                    val freq1 = 960.0 - (180.0 * (i.toDouble() / numSamples))
                    val env1 = exp(-t * 110.0)
                    val sample1 = sin(2.0 * PI * freq1 * t) * env1

                    // Secondary bounce (clack) starting at t = 0.016s (~16ms)
                    var sample2 = 0.0
                    if (t >= 0.016) {
                        val t2 = t - 0.016
                        val freq2 = 1250.0
                        val env2 = exp(-t2 * 140.0) * 0.45
                        sample2 = sin(2.0 * PI * freq2 * t2) * env2
                    }

                    // Combined wood resonance
                    val mixed = (sample1 * 0.75 + sample2 * 0.45).coerceIn(-1.0, 1.0)
                    buffer[i] = (mixed * 28000).toInt().toShort()
                }

                track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC
                )
                track.write(buffer, 0, buffer.size)
                track.play()

                delay(durationMs + 60L)
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Rapid shuffle flurry sound effect when the board is randomized.
     */
    fun playShuffleSound() {
        if (!isSoundEnabled) return

        audioScope.launch {
            repeat(4) { idx ->
                playWoodClick()
                delay(45L + idx * 8L)
            }
        }
    }

    /**
     * Joyful celebratory victory fanfare when the puzzle is solved.
     * Ascending major arpeggio chords with sparkling chime finish.
     */
    fun playVictoryFanfare() {
        if (!isSoundEnabled) return

        audioScope.launch {
            // Notes: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz), E6 (1318Hz)
            val notes = listOf(
                Pair(523.25, 120),
                Pair(659.25, 120),
                Pair(783.99, 140),
                Pair(1046.50, 160),
                Pair(1318.51, 380)
            )

            for ((freq, dur) in notes) {
                playChimeTone(freq, dur, volume = 0.22f)
                delay((dur * 0.75).toLong())
            }
        }
    }

    private fun playChimeTone(frequency: Double, durationMs: Int, volume: Float = 0.2f) {
        audioScope.launch {
            var track: AudioTrack? = null
            try {
                val sampleRate = 22050
                val numSamples = (durationMs * sampleRate) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Sine fundamental + overtone shimmer + exponential envelope
                    val env = exp(-t * (4.0 + 800.0 / durationMs))
                    val fundamental = sin(2.0 * PI * frequency * t)
                    val overtone = sin(2.0 * PI * (frequency * 2.0) * t) * 0.28
                    val combined = ((fundamental + overtone) * volume * env).coerceIn(-1.0, 1.0)
                    buffer[i] = (combined * 32767).toInt().toShort()
                }

                track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC
                )
                track.write(buffer, 0, buffer.size)
                track.play()

                delay(durationMs + 60L)
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }
}
