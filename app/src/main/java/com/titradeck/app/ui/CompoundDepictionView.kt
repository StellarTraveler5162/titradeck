package com.titradeck.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titradeck.app.data.PubChemCompound
import kotlin.math.roundToInt

private const val MaxDepictionZoom = 5f
private val DepictionHeight = 240.dp

@Composable
internal fun CompoundDepictionView(
    compound: PubChemCompound,
    image: ImageBitmap,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    var zoom by rememberSaveable(compound.cid) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var fullscreen by rememberSaveable(compound.cid) { mutableStateOf(false) }

    fun setZoom(requestedZoom: Float) {
        val nextZoom = requestedZoom.coerceIn(1f, MaxDepictionZoom)
        offset = if (nextZoom == 1f) Offset.Zero else offset * (nextZoom / zoom)
        zoom = nextZoom
    }

    FullscreenViewer(fullscreen, onDismiss = { fullscreen = false }) { expanded ->
        Column(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { setZoom(zoom / 1.25f) },
                    enabled = zoom > 1f,
                    modifier = Modifier.semantics { contentDescription = "缩小二维结构" },
                ) { Text("−") }
                TextButton(
                    onClick = { setZoom(1f) },
                    modifier = Modifier.semantics { contentDescription = "重置二维结构缩放" },
                ) { Text("${(zoom * 100).roundToInt()}%") }
                IconButton(
                    onClick = { setZoom(zoom * 1.25f) },
                    enabled = zoom < MaxDepictionZoom,
                    modifier = Modifier.semantics { contentDescription = "放大二维结构" },
                ) { Text("+") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSave, enabled = canSave) { Text("保存到相册") }
            }
            val viewportHeight = if (expanded) Modifier.weight(1f) else Modifier.height(DepictionHeight)
            BoxWithConstraints(
                Modifier.fillMaxWidth().then(viewportHeight).clipToBounds().background(Color.White),
            ) {
                val width = constraints.maxWidth.toFloat()
                val height = constraints.maxHeight.toFloat()
                val unit = minOf(width, height).coerceAtLeast(1f)
                val fit = minOf(width / image.width, height / image.height)
                fun boundedOffset(value: Offset, scale: Float): Offset {
                    val limitX = ((image.width * fit * scale - width) / (2f * unit)).coerceAtLeast(0f)
                    val limitY = ((image.height * fit * scale - height) / (2f * unit)).coerceAtLeast(0f)
                    return Offset(value.x.coerceIn(-limitX, limitX), value.y.coerceIn(-limitY, limitY))
                }
                val state = rememberTransformableState { zoomChange, panChange, _ ->
                    offset = boundedOffset(offset, zoom)
                    setZoom(zoom * zoomChange)
                    offset = boundedOffset(offset + panChange / unit, zoom)
                }
                Box(Modifier.fillMaxSize().transformable(state, canPan = { zoom > 1f })) {
                    Image(
                        image, contentDescription = "${compound.name}的二维结构",
                        modifier = Modifier.fillMaxSize().testTag("compound_depiction_${compound.cid}").graphicsLayer {
                            val position = boundedOffset(offset, zoom) * unit
                            scaleX = zoom
                            scaleY = zoom
                            translationX = position.x
                            translationY = position.y
                        },
                    )
                }
                FullscreenButton(expanded, { fullscreen = !expanded }, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
    }
}
