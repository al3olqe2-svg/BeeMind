package com.beemind.personal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BeeDesign {
    val page = 20.dp
    val gap = 12.dp
    val card = RoundedCornerShape(24.dp)
    val panel = RoundedCornerShape(32.dp)
    val rainbow = Brush.linearGradient(listOf(
        Color(0xFFF6DEE8), Color(0xFFF7EACF), Color(0xFFE0EDDD),
        Color(0xFFDDEAF8), Color(0xFFE8DDF7),
    ))
    val ink = Color(0xFF30243D)
    val cardColors = listOf(Color.White, Color(0xFFF2EBFA), Color(0xFFF8F3EC), Color(0xFFEEF4F3))
    val typography = Typography(
        displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 22.sp),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    )
}
