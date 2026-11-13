package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Menampilkan titik ribuan (1.000.000) pada isian nominal tanpa mengubah
 * nilai mentah di state, sehingga penyimpanan dan perhitungan tetap memakai
 * angka tanpa pemisah. Nilai non-digit (mis. desimal) ditampilkan apa adanya.
 */
object RibuanVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length < 4 || raw.first() == '0' || raw.any { !it.isDigit() }) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = StringBuilder(raw.length + raw.length / 3)
        raw.forEachIndexed { index, ch ->
            if (index > 0 && (raw.length - index) % 3 == 0) builder.append('.')
            builder.append(ch)
        }
        val firstGroup = (raw.length % 3).let { if (it == 0) 3 else it }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, raw.length)
                return if (safe <= firstGroup) safe else safe + (safe - firstGroup + 2) / 3
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, builder.length)
                if (safe <= firstGroup) return safe
                val dots = (safe - firstGroup + 3) / 4
                return (safe - dots).coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(builder.toString()), mapping)
    }
}
