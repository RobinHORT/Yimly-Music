package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Yorkmade Pro is the fixed app-wide typeface. Keep the lyric typeface separate.
val YorkmadePro = FontFamily(
    Font(R.font.yorkmadeprodemolight, FontWeight.Light),
    Font(R.font.yorkmadeprodemomedium, FontWeight.Medium),
    Font(R.font.yorkmadeprodemosemibold, FontWeight.SemiBold),
    Font(R.font.yorkmadeprodemobold, FontWeight.Bold),
    Font(R.font.yorkmadeprodemoextrabold, FontWeight.ExtraBold),
    Font(R.font.yorkmadeprodemoblack, FontWeight.Black),
    Font(R.font.yorkmadeprodemo, FontWeight.Normal)
)

val LTKaraoke = FontFamily(
    Font(R.font.ltkaraoke_light, FontWeight.Light),
    Font(R.font.ltkaraoke_regular, FontWeight.Normal),
    Font(R.font.ltkaraoke_medium, FontWeight.Medium),
    Font(R.font.ltkaraoke_semibold, FontWeight.SemiBold),
    Font(R.font.ltkaraoke_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.Light, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = YorkmadePro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
)
