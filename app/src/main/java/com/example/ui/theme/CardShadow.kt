package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevasi kartu yang hanya aktif di mode terang. Di mode gelap nilainya 0 dp agar
 * tampilan malam tidak berubah, sementara kartu terang tetap terpisah dari latar.
 */
@Composable
fun lightCardElevation(lightElevation: Dp = 8.dp, darkElevation: Dp = 0.dp): CardElevation =
    CardDefaults.cardElevation(
        defaultElevation = if (isSystemInDarkTheme()) darkElevation else lightElevation
    )

/** Tinggi bayangan untuk Surface yang hanya dipakai di mode terang. */
@Composable
fun lightSurfaceShadow(lightElevation: Dp = 8.dp): Dp =
    if (isSystemInDarkTheme()) 0.dp else lightElevation

/** Putih di mode terang, sementara mode gelap tetap memakai warna aslinya. */
@Composable
fun lightWhiteCard(originalColor: Color): Color =
    if (isSystemInDarkTheme()) originalColor else Color.White
