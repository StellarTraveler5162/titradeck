package com.titradeck.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titradeck.app.chemistry.MoleculeModel
import kotlin.math.*

@Composable
fun MoleculeView(model: MoleculeModel) {
    var yaw by remember(model) { mutableFloatStateOf(.3f) }
    var pitch by remember(model) { mutableFloatStateOf(.2f) }
    var zoom by remember(model) { mutableFloatStateOf(1f) }
    val bondColor = MaterialTheme.colorScheme.onSurfaceVariant
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("分子模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { yaw = .3f; pitch = .2f; zoom = 1f }) { Text("复位") }
            }
            Canvas(Modifier.fillMaxWidth().height(280.dp).semantics { contentDescription = "可拖动旋转的分子模型：${model.caption}" }
                .pointerInput(model) { detectTransformGestures { _, pan, gestureZoom, _ -> yaw += pan.x * .01f; pitch = (pitch + pan.y * .01f).coerceIn(-1.5f, 1.5f); zoom = (zoom * gestureZoom).coerceIn(.6f, 2f) } }) {
                if (model.atoms.isEmpty()) return@Canvas
                val cx = model.atoms.map { it.x }.average().toFloat()
                val cy = model.atoms.map { it.y }.average().toFloat()
                val cz = model.atoms.map { it.z }.average().toFloat()
                val bound = model.atoms.maxOf { sqrt((it.x-cx).pow(2) + (it.y-cy).pow(2) + (it.z-cz).pow(2)) }.coerceAtLeast(1f)
                data class Point(val location: Offset, val depth: Float, val scale: Float)
                val points = model.atoms.map { atom ->
                    val x = atom.x-cx; val y = atom.y-cy; val z = atom.z-cz
                    val rx = x*cos(yaw) + z*sin(yaw); val rz = -x*sin(yaw) + z*cos(yaw)
                    val ry = y*cos(pitch) - rz*sin(pitch); val depth = y*sin(pitch) + rz*cos(pitch)
                    val perspective = 4f / (4f + depth/bound)
                    val unit = min(size.width, size.height) / (bound*3.1f) * zoom * perspective
                    Point(Offset(size.width/2 + rx*unit, size.height/2 - ry*unit), depth, perspective)
                }
                model.bonds.forEach { bond ->
                    if (bond.from !in points.indices || bond.to !in points.indices) return@forEach
                    val a = points[bond.from].location; val b = points[bond.to].location
                    val distance = (b-a).getDistance().coerceAtLeast(1f)
                    val perpendicular = Offset(-(b.y-a.y)/distance, (b.x-a.x)/distance) * 5.dp.toPx()
                    val shifts = when (bond.order) { 2 -> listOf(-.5f,.5f); 3 -> listOf(-1f,0f,1f); else -> listOf(0f) }
                    shifts.forEach { shift -> drawLine(bondColor.copy(alpha = .75f), a + perpendicular*shift, b + perpendicular*shift, strokeWidth = 4.dp.toPx()) }
                }
                points.indices.sortedByDescending { points[it].depth }.forEach { index ->
                    val atom = model.atoms[index]; val p = points[index]
                    val color = when (atom.symbol) { "H" -> Color(0xFFE4EBF1); "C" -> Color(0xFF334155); "O" -> Color(0xFFDC4B4F); "N" -> Color(0xFF446DD9); "S" -> Color(0xFFF0C442); "P" -> Color(0xFFDC863C); "F", "Cl" -> Color(0xFF34A97A); else -> Color(0xFF8D63B9) }
                    val radius = (if (atom.symbol == "H") 16 else 21).dp.toPx() * p.scale * sqrt(zoom)
                    drawCircle(Color.Black.copy(alpha=.12f), radius + 2.dp.toPx(), p.location + Offset(0f,2.dp.toPx()))
                    drawCircle(color, radius, p.location)
                    paint.color = (if (atom.symbol in listOf("H", "S")) Color(0xFF172033) else Color.White).toArgb()
                    paint.textSize = 13.dp.toPx() * p.scale * sqrt(zoom)
                    drawIntoCanvas { it.nativeCanvas.drawText(atom.symbol, p.location.x, p.location.y-(paint.ascent()+paint.descent())/2, paint) }
                    val occupied = model.bonds.filter { it.from == index || it.to == index }.map { bond ->
                        val other = points[if (bond.from == index) bond.to else bond.from].location - p.location
                        atan2(other.y, other.x)
                    }.toMutableList()
                    repeat(atom.lonePairs.coerceIn(0, 4)) {
                        val angle = (0 until 16).map { it * PI.toFloat() / 8f }.maxBy { candidate ->
                            occupied.minOfOrNull { abs(atan2(sin(candidate-it), cos(candidate-it))) } ?: PI.toFloat()
                        }
                        occupied += angle
                        val radial = Offset(cos(angle), sin(angle))
                        val tangent = Offset(-sin(angle), cos(angle)) * 3.dp.toPx()
                        val midpoint = p.location + radial * (radius + 8.dp.toPx())
                        drawCircle(bondColor, 1.7.dp.toPx(), midpoint + tangent)
                        drawCircle(bondColor, 1.7.dp.toPx(), midpoint - tangent)
                    }
                    if (atom.formalCharge != 0) {
                        paint.color = bondColor.toArgb(); paint.textSize = 12.dp.toPx()
                        val magnitude = abs(atom.formalCharge).let { if (it == 1) "" else it.toString() }
                        val charge = magnitude + if (atom.formalCharge > 0) "+" else "−"
                        drawIntoCanvas { it.nativeCanvas.drawText(charge, p.location.x + radius + 6.dp.toPx(), p.location.y - radius, paint) }
                    }
                }
            }
            Text("拖动旋转 · 双指缩放", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LatexText(model.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
