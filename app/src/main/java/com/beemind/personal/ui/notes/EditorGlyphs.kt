package com.beemind.personal.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object EditorGlyphs {
    val link = ImageVector.Builder("Link", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 15f); lineTo(15f, 9f)
            moveTo(7.5f, 13f); lineTo(5.5f, 15f); curveTo(1.5f, 19f, 5f, 22.5f, 9f, 18.5f); lineTo(11f, 16.5f)
            moveTo(13f, 7.5f); lineTo(15f, 5.5f); curveTo(19f, 1.5f, 22.5f, 5f, 18.5f, 9f); lineTo(16.5f, 11f)
        }
    }.build()
    val more = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 12f); lineTo(5.01f, 12f)
            moveTo(12f, 12f); lineTo(12.01f, 12f)
            moveTo(19f, 12f); lineTo(19.01f, 12f)
        }
    }.build()

    val expressive = ImageVector.Builder("Expressive", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f); lineTo(14.7f, 9.3f); lineTo(21f, 12f); lineTo(14.7f, 14.7f)
            lineTo(12f, 21f); lineTo(9.3f, 14.7f); lineTo(3f, 12f); lineTo(9.3f, 9.3f); close()
        }
    }.build()
    val resurface = ImageVector.Builder("Resurface", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 19f); lineTo(19f, 5f); moveTo(7f, 5f); lineTo(19f, 5f); lineTo(19f, 17f)
        }
    }.build()
    val clock = ImageVector.Builder("Clock", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
            moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f); close()
            moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f)
        }
    }.build()
}
