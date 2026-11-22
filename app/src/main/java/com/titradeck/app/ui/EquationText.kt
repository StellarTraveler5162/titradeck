package com.titradeck.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.titradeck.app.chemistry.mathTextToLatex
import ru.noties.jlatexmath.JLatexMathDrawable

/** Renders the whole balanced equation in TeX, wrapping at word boundaries when necessary. */
@Composable
fun EquationText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val initialSize = if (style.fontSize.isUnspecified) 20f else style.fontSize.value
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val lines = remember(text, initialSize, color, widthPx, density.density, density.fontScale) {
            fun drawable(value: String, size: Float): JLatexMathDrawable? {
                val equation = value.replace("→", "=").replace("⇌", "=")
                val expression = mathTextToLatex(equation).replace("=", "\\mathrel{\\scalebox{1.6}[1]{=}}")
                return runCatching {
                    JLatexMathDrawable.builder("\\boldsymbol{\\mathsf{$expression}}")
                        .textSize(with(density) { size.sp.toPx() }).color(color.toArgb()).build()
                }.getOrNull()
            }
            var fontSize = initialSize
            val minimumSize = minOf(initialSize, 14f)
            var whole = drawable(text, fontSize)
            while (whole != null && whole.intrinsicWidth > widthPx && fontSize > minimumSize) {
                fontSize = maxOf(minimumSize, fontSize - 1f)
                whole = drawable(text, fontSize)
            }
            if (whole == null || whole.intrinsicWidth <= widthPx) {
                listOfNotNull(whole)
            } else {
                val result = mutableListOf<JLatexMathDrawable>()
                var current = ""
                text.trim().split(Regex("\\s+")).forEach { word ->
                    val next = if (current.isEmpty()) word else "$current $word"
                    val nextDrawable = drawable(next, fontSize)
                    if (current.isNotEmpty() && nextDrawable != null && nextDrawable.intrinsicWidth > widthPx) {
                        drawable(current, fontSize)?.let(result::add)
                        current = word
                    } else {
                        current = next
                    }
                }
                drawable(current, fontSize)?.let(result::add)
                result
            }
        }
        if (lines.isEmpty()) {
            LatexText(text, style = style, color = color, forceFormula = true)
        } else {
            Column(Modifier.semantics { contentDescription = text }) {
                lines.forEach { drawable ->
                    val width = minOf(drawable.intrinsicWidth, widthPx).toFloat()
                    val scale = width / drawable.intrinsicWidth
                    Canvas(Modifier.size(with(density) { width.toDp() }, with(density) { (drawable.intrinsicHeight * scale).toDp() })) {
                        drawIntoCanvas { canvas ->
                            canvas.save()
                            canvas.scale(scale, scale)
                            drawable.draw(canvas.nativeCanvas)
                            canvas.restore()
                        }
                    }
                }
            }
        }
    }
}
