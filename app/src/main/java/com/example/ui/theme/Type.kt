package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Technology font family dari Google Drive (.ttf)
val TechnologyFontFamily = FontFamily(
    Font(R.font.technology, FontWeight.Normal),
    Font(R.font.technology_bold, FontWeight.Bold),
    Font(R.font.technology_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.technology_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

// Custom digital clock font family menggunakan TechnologyFontFamily
val DsDigitalFontFamily = TechnologyFontFamily

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
  )
