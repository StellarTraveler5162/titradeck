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

@Composable
fun MoleculeView(model: MoleculeModel, allowFullscreen: Boolean = false) {
    var yaw by remember(model) { mutableFloatStateOf(.3f) }
    var pitch by remember(model) { mutableFloatStateOf(.2f) }
    var zoom by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var ballStickSize by remember(model) { mutableFloatStateOf(1f) }
    var fullscreen by rememberSaveable(model) { mutableStateOf(false) }
    val bondColor = MaterialTheme.colorScheme.onSurfaceVariant
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }

    fun rotate(pan: Offset) {
        yaw += pan.x * .01f
        pitch = (pitch + pan.y * .01f).coerceIn(-1.5f, 1.5f)
    }

    val drawModel: DrawScope.() -> Unit = drawModel@{
        if (model.atoms.isEmpty()) return@drawModel
        val cx = model.atoms.map { it.x }.average().toFloat()
        val cy = model.atoms.map { it.y }.average().toFloat()
        val cz = model.atoms.map { it.z }.average().toFloat()
        val bound = model.atoms.maxOf { sqrt((it.x-cx).pow(2) + (it.y-cy).pow(2) + (it.z-cz).pow(2)) }.coerceAtLeast(1f)
        val viewportUnit = min(size.width, size.height)
        val origin = center + offset * viewportUnit
        data class Point(val location: Offset, val depth: Float, val scale: Float)
        val points = model.atoms.map { atom ->
            val x = atom.x-cx; val y = atom.y-cy; val z = atom.z-cz
            val rx = x*cos(yaw) + z*sin(yaw); val rz = -x*sin(yaw) + z*cos(yaw)
            val ry = y*cos(pitch) - rz*sin(pitch); val depth = y*sin(pitch) + rz*cos(pitch)
            val perspective = 4f / (4f + depth/bound)
            val unit = viewportUnit / (bound*3.1f) * zoom * perspective
            Point(origin + Offset(rx*unit, -ry*unit), depth, perspective)
        }
        val stickWidth = 4.dp.toPx() * ballStickSize
        val stickSpacing = 5.dp.toPx() * ballStickSize
        model.bonds.forEach { bond ->
            if (bond.from !in points.indices || bond.to !in points.indices) return@forEach
            val a = points[bond.from].location; val b = points[bond.to].location
            val distance = (b-a).getDistance().coerceAtLeast(1f)
            val perpendicular = Offset(-(b.y-a.y)/distance, (b.x-a.x)/distance) * stickSpacing
            val shifts = when (bond.order) { 2 -> listOf(-.5f,.5f); 3 -> listOf(-1f,0f,1f); else -> listOf(0f) }
            shifts.forEach { shift -> drawLine(bondColor.copy(alpha = .75f), a + perpendicular*shift, b + perpendicular*shift, strokeWidth = stickWidth) }
        }
        points.indices.sortedByDescending { points[it].depth }.forEach { index ->
            val atom = model.atoms[index]; val p = points[index]
            val color = when (atom.symbol) { "H" -> Color(0xFFE4EBF1); "C" -> Color(0xFF334155); "O" -> Color(0xFFDC4B4F); "N" -> Color(0xFF446DD9); "S" -> Color(0xFFF0C442); "P" -> Color(0xFFDC863C); "F", "Cl" -> Color(0xFF34A97A); else -> Color(0xFF8D63B9) }
            val atomScale = p.scale * sqrt(zoom) * ballStickSize
            val radius = (if (atom.symbol == "H") 16 else 21).dp.toPx() * atomScale
            val shadow = 2.dp.toPx() * ballStickSize
            drawCircle(Color.Black.copy(alpha=.12f), radius + shadow, p.location + Offset(0f, shadow))
            drawCircle(color, radius, p.location)
            if (atomScale < .55f) return@forEach
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
                        offset = Offset.Zero
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
                                        val unit = min(size.width, size.height).coerceAtLeast(1).toFloat()
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val focalOffset = (focus - center) / unit
                                        val shifted = (offset - focalOffset) * (nextZoom / zoom) + focalOffset + pan / unit
                                        val limit = nextZoom * .45f
                                        offset = Offset(shifted.x.coerceIn(-limit, limit), shifted.y.coerceIn(-limit, limit))
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
