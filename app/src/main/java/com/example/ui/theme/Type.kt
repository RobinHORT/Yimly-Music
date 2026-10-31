package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Dongle is the fixed app-wide typeface. Keep the lyric typeface separate.
val Dongle = FontFamily(
    Font(R.font.dongle_light, FontWeight.Light),
    Font(R.font.dongle_regular, FontWeight.Normal),
    Font(R.font.dongle_bold, FontWeight.Bold)
)

val LTKaraoke = FontFamily(
    Font(R.font.ltkaraoke_light, FontWeight.Light),
    Font(R.font.ltkaraoke_regular, FontWeight.Normal),
    Font(R.font.ltkaraoke_medium, FontWeight.Medium),
    Font(R.font.ltkaraoke_semibold, FontWeight.SemiBold),
    Font(R.font.ltkaraoke_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Light, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = Dongle, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
)
