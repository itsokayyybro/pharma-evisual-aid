package com.pharma.evisualaid.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pharma.evisualaid.ui.theme.PrimaryTeal
import kotlin.math.abs

/**
 * Hoisted zoom/pan state for the active page, kept at the screen level so the pager can
 * disable horizontal scrolling while a page is zoomed in.
 */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > 1.01f

    fun apply(zoomChange: Float, panChange: Offset, bounds: IntSize) {
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = clampOffset(offset + panChange, bounds)
    }

    fun toggleDoubleTap() {
        if (isZoomed) reset() else scale = 2.5f
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clampOffset(raw: Offset, bounds: IntSize): Offset {
        if (scale <= 1f || bounds == IntSize.Zero) return Offset.Zero
        val maxX = (bounds.width * (scale - 1f)) / 2f
        val maxY = (bounds.height * (scale - 1f)) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}

/**
 * A single full-bleed PDF page inside the pager.
 *
 * The page-turn "book feel" is a depth transform applied to the whole page based on its live
 * offset from the settled page ([pageOffset]): the outgoing page rotates back around the Y
 * axis, dims and shrinks slightly while the incoming page rotates flat — giving turning pages
 * weight instead of a flat carousel slide. (This is the documented fallback to a full
 * finger-tracking canvas curl; it reads as paper while staying robust on mid-range tablets.)
 *
 * Pinch-to-zoom / pan / double-tap-reset apply to the inner image and only for the active page
 * ([zoom] is non-null only there).
 */
@Composable
fun PageViewer(
    page: Int,
    viewModel: ViewerViewModel,
    zoom: ZoomState?,
    pageOffset: () -> Float,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, page) {
        value = viewModel.getPage(page)
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = pageOffset()
                val fraction = abs(offset).coerceIn(0f, 1f)
                // Replace the pager's flat slide with a hinge-style turn.
                translationX = -offset * size.width
                rotationY = offset * 30f
                alpha = 1f - fraction * 0.3f
                scaleY = 1f - fraction * 0.05f
                cameraDistance = 12f * density
            }
            .background(Color.Black)
            .onSizeChanged { boxSize = it }
            .pointerInput(page, zoom) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { zoom?.toggleDoubleTap() }
                )
            }
            // Pinch + pan. Only consume drags while zoomed so 1× horizontal swipes fall
            // through to the pager for page turns.
            .pointerInput(page, zoom) {
                if (zoom == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (pressedCount >= 2 || zoom.isZoomed) {
                            zoom.apply(zoomChange, panChange, boxSize)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator(
                color = PrimaryTeal,
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = zoom?.scale ?: 1f
                        val o = zoom?.offset ?: Offset.Zero
                        scaleX = s
                        scaleY = s
                        translationX = o.x
                        translationY = o.y
                    }
            )
        }
    }
}
