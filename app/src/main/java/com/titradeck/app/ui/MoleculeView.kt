package com.titradeck.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.titradeck.app.chemistry.MoleculeModel
import kotlin.math.*

private const val ModelFitScale = 3.1f
private const val MaxRotationCenterDistance = 2f

private data class ModelPoint(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: ModelPoint) = ModelPoint(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: ModelPoint) = ModelPoint(x - other.x, y - other.y, z - other.z)
    operator fun div(scale: Float) = ModelPoint(x / scale, y / scale, z / scale)

    fun length() = sqrt(x * x + y * y + z * z)

    fun rotateX(angle: Float): ModelPoint {
        val c = cos(angle)
        val s = sin(angle)
        return ModelPoint(x, y * c - z * s, y * s + z * c)
    }

    fun rotateY(angle: Float): ModelPoint {
        val c = cos(angle)
        val s = sin(angle)
        return ModelPoint(x * c + z * s, y, -x * s + z * c)
    }
}

@Composable
fun MoleculeView(model: MoleculeModel, allowFullscreen: Boolean = false) {
    var yaw by remember(model) { mutableFloatStateOf(.3f) }
    var pitch by remember(model) { mutableFloatStateOf(.2f) }
    var zoom by remember(model) { mutableFloatStateOf(1f) }
    var rotationCenter by remember(model) { mutableStateOf(ModelPoint(0f, 0f, 0f)) }
    var ballStickSize by remember(model) { mutableFloatStateOf(1f) }
    var fullscreen by rememberSaveable(model) { mutableStateOf(false) }
    val bondColor = MaterialTheme.colorScheme.onSurfaceVariant
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }
    val atomPositions = remember(model) {
        if (model.atoms.isEmpty()) {
            emptyList()
        } else {
            val center = ModelPoint(
                model.atoms.map { it.x }.average().toFloat(),
                model.atoms.map { it.y }.average().toFloat(),
                model.atoms.map { it.z }.average().toFloat(),
            )
            val positions = model.atoms.map { ModelPoint(it.x, it.y, it.z) - center }
            val radius = positions.maxOf { it.length() }.coerceAtLeast(1f)
            positions.map { it / radius }
        }
    }

    fun rotate(pan: Offset) {
        yaw += pan.x * .01f
        pitch = (pitch + pan.y * .01f).coerceIn(-1.5f, 1.5f)
    }

    val drawModel: DrawScope.() -> Unit = drawModel@{
        if (atomPositions.isEmpty()) return@drawModel
        val unit = min(size.width, size.height) * zoom / ModelFitScale
        data class Point(val location: Offset, val depth: Float, val perspective: Float, val atomScale: Float, val radius: Float)
        data class Layer(val depth: Float, val draw: () -> Unit)

        val atomSize = sqrt(zoom) * ballStickSize
        val points = atomPositions.mapIndexed { index, position ->
            val view = (position - rotationCenter).rotateY(yaw).rotateX(pitch)
            val perspective = 4f / (4f + view.z)
            val atomScale = perspective * atomSize
            val radius = (if (model.atoms[index].symbol == "H") 16.dp else 21.dp).toPx() * atomScale
            Point(center + Offset(view.x, -view.y) * (unit * perspective), view.z, perspective, atomScale, radius)
        }
        val layers = mutableListOf<Layer>()
        val stickWidth = 4.dp.toPx() * ballStickSize
        val stickColor = bondColor.copy(alpha = .75f)
        model.bonds.forEach { bond ->
            if (bond.from !in points.indices || bond.to !in points.indices) return@forEach
            val from = points[bond.from]
            val to = points[bond.to]
            val delta = to.location - from.location
            val distance = delta.getDistance()
            if (distance < 1f) return@forEach
            val direction = delta / distance
            val perpendicular = Offset(-direction.y, direction.x)
            val shifts = when (bond.order) {
                2 -> listOf(-4.dp, 4.dp)
                3 -> listOf(-5.dp, 0.dp, 5.dp)
                else -> listOf(0.dp)
            }.map { it.toPx() * ballStickSize }

            // Split where an overlapping atom crosses the bond in depth, accounting for perspective.
            val cuts = mutableListOf(0f, 1f)
            val depthSpan = to.depth - from.depth
            val lineReach = shifts.maxOf { abs(it) } + stickWidth / 2f
            if (depthSpan != 0f) {
                points.forEach atom@{ point ->
                    val fraction = (point.depth - from.depth) / depthSpan
                    if (fraction <= 0f || fraction >= 1f) return@atom
                    val relative = point.location - from.location
                    val along = (relative.x * direction.x + relative.y * direction.y).coerceIn(0f, distance)
                    if ((relative - direction * along).getDistance() <= point.radius + lineReach) {
                        cuts += fraction * from.perspective /
                            ((1f - fraction) * to.perspective + fraction * from.perspective)
                    }
                }
            }
            val segments = cuts.distinct().sorted().zipWithNext()
            shifts.forEach { shift ->
                fun endInset(radius: Float): Float {
                    val edge = radius - stickWidth / 4f
                    return sqrt((edge * edge - shift * shift).coerceAtLeast(0f)) / distance
                }

                val visibleStart = endInset(from.radius)
                val visibleEnd = 1f - endInset(to.radius)
                val lineStart = from.location + perpendicular * shift
                segments.forEach segment@{ (start, end) ->
                    val clippedStart = maxOf(start, visibleStart)
                    val clippedEnd = minOf(end, visibleEnd)
                    if (clippedStart >= clippedEnd) return@segment
                    val middle = (clippedStart + clippedEnd) / 2f
                    val fraction = middle * to.perspective /
                        ((1f - middle) * from.perspective + middle * to.perspective)
                    layers += Layer(from.depth + fraction * depthSpan) {
                        drawLine(stickColor, lineStart + delta * clippedStart, lineStart + delta * clippedEnd, strokeWidth = stickWidth)
                    }
                }
            }
        }

        fun drawAtom(index: Int) {
            val atom = model.atoms[index]; val p = points[index]
            val color = when (atom.symbol) { "H" -> Color(0xFFE4EBF1); "C" -> Color(0xFF334155); "O" -> Color(0xFFDC4B4F); "N" -> Color(0xFF446DD9); "S" -> Color(0xFFF0C442); "P" -> Color(0xFFDC863C); "F", "Cl" -> Color(0xFF34A97A); else -> Color(0xFF8D63B9) }
            val atomScale = p.atomScale
            val radius = p.radius
            val shadow = 2.dp.toPx() * ballStickSize
            drawCircle(Color.Black.copy(alpha=.12f), radius + shadow, p.location + Offset(0f, shadow))
            drawCircle(color, radius, p.location)
            if (atomScale < .55f) return
            paint.color = (if (atom.symbol in listOf("H", "S")) Color(0xFF172033) else Color.White).toArgb()
            paint.textSize = 13.dp.toPx() * atomScale
            drawIntoCanvas { it.nativeCanvas.drawText(atom.symbol, p.location.x, p.location.y-(paint.ascent()+paint.descent())/2, paint) }
            if (atom.lonePairs > 0) {
                val occupied = model.bonds.filter { it.from == index || it.to == index }.map { bond ->
                    val other = points[if (bond.from == index) bond.to else bond.from].location - p.location
                    atan2(other.y, other.x)
                }.toMutableList()
                repeat(atom.lonePairs.coerceAtMost(4)) {
                    val angle = (0 until 16).map { it * PI.toFloat() / 8f }.maxBy { candidate ->
                        occupied.minOfOrNull { abs(atan2(sin(candidate-it), cos(candidate-it))) } ?: PI.toFloat()
                    }
                    occupied += angle
                    val radial = Offset(cos(angle), sin(angle))
                    val tangent = Offset(-sin(angle), cos(angle)) * 3.dp.toPx() * ballStickSize
                    val midpoint = p.location + radial * (radius + 8.dp.toPx() * ballStickSize)
                    val electronRadius = 1.7.dp.toPx() * ballStickSize
                    drawCircle(bondColor, electronRadius, midpoint + tangent)
                    drawCircle(bondColor, electronRadius, midpoint - tangent)
                }
            }
            if (atom.formalCharge != 0) {
                paint.color = bondColor.toArgb(); paint.textSize = 12.dp.toPx() * ballStickSize
                val magnitude = abs(atom.formalCharge).let { if (it == 1) "" else it.toString() }
                val charge = magnitude + if (atom.formalCharge > 0) "+" else "−"
                drawIntoCanvas { it.nativeCanvas.drawText(charge, p.location.x + radius + 6.dp.toPx() * ballStickSize, p.location.y - radius, paint) }
            }
        }
        points.forEachIndexed { index, point -> layers += Layer(point.depth) { drawAtom(index) } }
        layers.sortByDescending { it.depth }
        layers.forEach { it.draw() }
    }

    FullscreenViewer(fullscreen, onDismiss = { fullscreen = false }) { expanded ->
        Card(
            modifier = if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(if (expanded) 12.dp else 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("分子模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        yaw = .3f
                        pitch = .2f
                        zoom = 1f
                        rotationCenter = ModelPoint(0f, 0f, 0f)
                        ballStickSize = 1f
                    }) { Text("复位") }
                }
                val viewportHeight = if (expanded) Modifier.weight(1f) else Modifier.height(280.dp)
                Box(Modifier.fillMaxWidth().then(viewportHeight).clipToBounds()) {
                    Canvas(
                        Modifier.fillMaxSize()
                            .semantics {
                                contentDescription = "可拖动旋转的分子模型：${model.caption}"
                                if (allowFullscreen) stateDescription = "缩放 ${(zoom * 100).roundToInt()}%"
                            }
                            .pointerInput(model, allowFullscreen) {
                                if (allowFullscreen) {
                                    detectMoleculeGestures(onRotate = ::rotate) { scale, pan, focus ->
                                        val nextZoom = (zoom * scale).coerceIn(.6f, 8f)
                                        val unit = min(size.width, size.height).coerceAtLeast(1) / ModelFitScale
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        // Translate the rotation center in the view plane, keeping pinch focus on that plane.
                                        val shift = ((focus - center) * (1f / zoom - 1f / nextZoom) - pan / nextZoom) / unit
                                        val modelShift = ModelPoint(shift.x, -shift.y, 0f).rotateX(-pitch).rotateY(-yaw)
                                        val nextCenter = rotationCenter + modelShift
                                        // Atom positions have unit radius, with the camera four units from the center.
                                        rotationCenter = nextCenter / (nextCenter.length() / MaxRotationCenterDistance).coerceAtLeast(1f)
                                        zoom = nextZoom
                                    }
                                } else {
                                    detectTransformGestures { _, pan, scale, _ ->
                                        rotate(pan)
                                        zoom = (zoom * scale).coerceIn(.6f, 2f)
                                    }
                                }
                            },
                        onDraw = drawModel,
                    )
                    if (allowFullscreen) {
                        FullscreenButton(expanded, { fullscreen = !expanded }, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("球棍大小", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${(ballStickSize * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = ballStickSize,
                    onValueChange = { ballStickSize = it },
                    valueRange = .2f..1.5f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "球棍大小" },
                )
                val modelInfoStyle = MaterialTheme.typography.bodySmall
                val modelInfoColor = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = buildString {
                        appendLine("单指拖动旋转，双指捏合缩放。")
                        if (allowFullscreen) {
                            val fullscreenAction = if (expanded) "退出全屏" else "进入全屏"
                            appendLine("双指同向拖动可平移，点右下角$fullscreenAction。")
                        }
                        append("滑块调节球棍大小，点「复位」恢复初始状态。")
                    },
                    style = modelInfoStyle,
                    color = modelInfoColor,
                )
                Spacer(Modifier.height(4.dp))
                LatexText(model.caption, style = modelInfoStyle, color = modelInfoColor)
            }
        }
    }
}

private suspend fun PointerInputScope.detectMoleculeGestures(
    onRotate: (Offset) -> Unit,
    onTransform: (Float, Offset, Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown()
        var multiTouch = false
        var pastTouchSlop = false
        var accumulatedPan = Offset.Zero
        var accumulatedZoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break
            val fingers = event.changes.count { it.pressed }
            if (fingers > 1 && !multiTouch) {
                multiTouch = true
                accumulatedPan = Offset.Zero
                accumulatedZoom = 1f
            }
            // Wait for a fresh touch before rotating after a pinch or two-finger drag.
            if (multiTouch && fingers < 2) continue

            val pan = event.calculatePan()
            val scale = event.calculateZoom()
            if (!pastTouchSlop) {
                accumulatedPan += pan
                accumulatedZoom *= scale
                val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
                pastTouchSlop = maxOf(accumulatedPan.getDistance(), zoomMotion) > viewConfiguration.touchSlop
            }
            if (!pastTouchSlop) continue

            if (multiTouch) {
                val focus = event.calculateCentroid(useCurrent = false)
                if (focus != Offset.Unspecified) onTransform(scale, pan, focus)
            } else {
                onRotate(pan)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
