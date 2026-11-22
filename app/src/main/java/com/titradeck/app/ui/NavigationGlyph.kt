package com.titradeck.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fixed-size vectors share a 24 x 24 viewport, 2-unit stroke and optical centre. */
@Composable
internal fun NavigationGlyph(index: Int) {
    val tint = LocalContentColor.current
    Canvas(Modifier.size(24.dp).testTag("nav_icon_$index")) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val outline = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (index) {
                0 -> {
                    drawRoundRect(tint, Offset(3f, 3f), Size(18f, 18f), CornerRadius(3f), style = outline)
                    drawLine(tint, Offset(7f, 8f), Offset(17f, 8f), strokeWidth = 2f, cap = StrokeCap.Round)
                    for (x in listOf(7f, 12f, 17f)) for (y in listOf(13f, 17f)) drawCircle(tint, 1f, Offset(x, y))
                }
                1 -> {
                    // Three ellipses, rather than a font-dependent atomic symbol.
                    for (angle in listOf(0f, 60f, 120f)) rotate(angle, Offset(12f, 12f)) {
                        drawOval(tint, Offset(3f, 8f), Size(18f, 8f), style = outline)
                    }
                    drawCircle(tint, 1.7f, Offset(12f, 12f))
                }
                2 -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = outline)
                    drawPath(Path().apply { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f) }, tint, style = outline)
                }
                else -> {
                    val gear = Path()
                    repeat(32) { point ->
                        val angle = (point * 2.0 * PI / 32.0 - PI / 2.0).toFloat()
                        val radius = if (point % 4 in 1..2) 9f else 7.2f
                        val x = 12f + radius * cos(angle)
                        val y = 12f + radius * sin(angle)
                        if (point == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                    }
                    gear.close()
                    drawPath(gear, tint, style = outline)
                    drawCircle(tint, 3f, Offset(12f, 12f), style = outline)
                }
            }
        }
    }
}
