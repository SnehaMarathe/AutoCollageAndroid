package com.example.collage.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    titleLarge = TextStyle(FontFamily.Default, FontWeight.SemiBold, 22.sp, 28.sp),
    titleMedium = TextStyle(FontFamily.Default, FontWeight.SemiBold, 18.sp, 24.sp),
    titleSmall = TextStyle(FontFamily.Default, FontWeight.Medium, 14.sp, 18.sp),
    bodyMedium = TextStyle(FontFamily.Default, FontWeight.Normal, 14.sp, 20.sp),
    labelLarge = TextStyle(FontFamily.Default, FontWeight.SemiBold, 13.sp, 18.sp),
)
