package com.titradeck.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isUnspecified
import com.titradeck.app.chemistry.latexParts
import ru.noties.jlatexmath.JLatexMathDrawable

/** Character range in the original text and its corresponding LaTeX expression. */
data class LatexInlineSpan(val start: Int, val end: Int, val latex: String)

@Composable
fun LatexText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    forceFormula: Boolean = false,
) {
    val spans = remember(text, forceFormula) {
        var offset = 0
        latexParts(text, forceFormula).mapNotNull { part ->
            val start = offset
            offset += part.text.length
            part.latex?.let { LatexInlineSpan(start, offset, it) }
        }
    }
    LatexText(text, spans, modifier, style, color)
}

/** Displays LaTeX fragments inline with normal text, preserving normal line wrapping. */
@Composable
fun LatexText(
    text: String,
    spans: List<LatexInlineSpan>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    val actualColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val density = LocalDensity.current
    val size = if (style.fontSize.isUnspecified) 16.sp else style.fontSize
    val pixelSize = with(density) { size.toPx() }
    val rendered = remember(text, spans, pixelSize, actualColor) {
        spans.mapNotNull { span ->
            if (span.start < 0 || span.end > text.length || span.start >= span.end) return@mapNotNull null
            val drawable = runCatching {
                JLatexMathDrawable.builder("\\boldsymbol{\\mathsf{${span.latex}}}").textSize(pixelSize).color(actualColor.toArgb()).build()
            }.getOrNull() ?: return@mapNotNull null
            if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return@mapNotNull null
            span to drawable
        }.sortedBy { it.first.start }
    }
    val inline = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var position = 0
        rendered.forEachIndexed { index, (span, drawable) ->
            if (span.start < position) return@forEachIndexed
            append(text.substring(position, span.start))
            val id = "latex-$index"
            appendInlineContent(id, text.substring(span.start, span.end))
            val width = with(density) { drawable.intrinsicWidth.toFloat().toSp() }
            val height = with(density) { drawable.intrinsicHeight.toFloat().toSp() }
            inline[id] = InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextCenter)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { drawable.draw(it.nativeCanvas) }
                }
            }
            position = span.end
        }
        append(text.substring(position))
    }
    Text(text = annotated, modifier = modifier, color = actualColor, style = style, inlineContent = inline)
}
