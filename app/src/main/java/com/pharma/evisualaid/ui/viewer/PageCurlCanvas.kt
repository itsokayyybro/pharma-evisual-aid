package com.pharma.evisualaid.ui.viewer

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min
import android.graphics.Matrix as AndroidMatrix

/**
 * Hoisted pinch-zoom/pan state for the flat page. Curl is disabled while [isZoomed].
 */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > 1.05f

    fun apply(zoomChange: Float, panChange: Offset, bounds: IntSize) {
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = clamp(offset + panChange, bounds)
    }

    fun toggleDoubleTap() {
        if (scale > 1.05f) reset() else scale = 2.5f
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(raw: Offset, b: IntSize): Offset {
        if (scale <= 1f || b == IntSize.Zero) return Offset.Zero
        val maxX = b.width * (scale - 1f) / 2f
        val maxY = b.height * (scale - 1f) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }
}

/**
 * A physically-styled, finger-tracked page-curl viewer. Self-contained: it owns its curl and
 * zoom state and only needs the three neighbouring page bitmaps plus page-change callbacks.
 *
 * Per frame the Canvas paints, bottom-to-top: the page underneath (next/previous), the flat
 * remainder of the current page, the shadow the lifting flap casts, the folded-under flap
 * (reflected current page + underside tint), and a faint crease highlight.
 */
@Composable
fun PageCurlCanvas(
    currentPageBitmap: Bitmap,
    nextPageBitmap: Bitmap?,
    previousPageBitmap: Bitmap?,
    onPageAdvanced: () -> Unit,
    onPageRetreated: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val curl = rememberCurlState()
    val zoom = remember { ZoomState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Reused native paints (allocating per-frame would blow the 8ms budget).
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { isDither = true } }
    val shadowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val creasePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    // Reset zoom/curl whenever the page actually changes underneath us.
    LaunchedEffect(currentPageBitmap) { zoom.reset(); curl.clear() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Single / double tap: chrome toggle and zoom toggle.
            .pointerInput(currentPageBitmap) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (!curl.active) zoom.toggleDoubleTap() }
                )
            }
            // Two-finger pinch zoom.
            .pointerInput(currentPageBitmap) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom.apply(event.calculateZoom(), event.calculatePan(), size)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // One-finger drag: pan when zoomed, otherwise drive the curl.
            .pointerInput(currentPageBitmap, nextPageBitmap, previousPageBitmap) {
                val velocity = VelocityTracker()
                var panning = false
                detectDragGestures(
                    onDragStart = { start ->
                        velocity.resetTracking()
                        if (zoom.isZoomed) {
                            panning = true
                            return@detectDragGestures
                        }
                        panning = false
                        val page = fitRect(currentPageBitmap, size.width.toFloat(), size.height.toFloat())
                        if (start.x > size.width / 2f) {
                            // Forward turn — lift the bottom-right corner.
                            if (nextPageBitmap != null) {
                                curl.begin(CurlDirection.FORWARD, Offset(page.right, page.bottom), start)
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        } else {
                            // Backward turn — lift the bottom-left corner.
                            if (previousPageBitmap != null) {
                                curl.begin(CurlDirection.BACKWARD, Offset(page.left, page.bottom), start)
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (panning) {
                            zoom.apply(1f, change.positionChange(), size)
                        } else if (curl.active) {
                            velocity.addPosition(change.uptimeMillis, change.position)
                            curl.touch = change.position
                        }
                    },
                    onDragEnd = {
                        if (!panning && curl.active) {
                            val page = fitRect(currentPageBitmap, size.width.toFloat(), size.height.toFloat())
                            val speed = velocity.calculateVelocity().let { hypot(it.x.toDouble(), it.y.toDouble()) }
                            val fast = speed > 2000.dp.toPx()
                            val progress = CurlGeometry.progress(curl.corner, curl.touch, page)
                            val complete = fast || progress > 0.42f
                            scope.launch {
                                releaseCurl(curl, page, complete, fast) {
                                    if (curl.direction == CurlDirection.FORWARD) onPageAdvanced()
                                    else onPageRetreated()
                                }
                            }
                        }
                        panning = false
                    },
                    onDragCancel = {
                        if (!panning && curl.active) {
                            val page = fitRect(currentPageBitmap, size.width.toFloat(), size.height.toFloat())
                            scope.launch { releaseCurl(curl, page, complete = false, fast = false) {} }
                        }
                        panning = false
                    }
                )
            }
    ) {
        drawScene(
            curl = curl,
            zoom = zoom,
            current = currentPageBitmap,
            next = nextPageBitmap,
            previous = previousPageBitmap,
            bitmapPaint = bitmapPaint,
            shadowPaint = shadowPaint,
            creasePaint = creasePaint
        )
    }
}

/** Springs the curl forward to completion or back to flat, interpolating [CurlState.touch]. */
private suspend fun releaseCurl(
    curl: CurlState,
    page: Rect,
    complete: Boolean,
    fast: Boolean,
    onCompleted: () -> Unit
) {
    val start = curl.touch
    val target = when {
        !complete -> curl.corner
        curl.direction == CurlDirection.FORWARD -> Offset(page.left, page.bottom)
        else -> Offset(page.right, page.bottom)
    }
    curl.dragging = false
    val anim = Animatable(0f)
    val spec = when {
        fast -> tween<Float>(120)
        complete -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        else -> spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    anim.animateTo(1f, spec) { curl.touch = lerp(start, target, value) }
    if (complete) onCompleted()
    curl.clear()
}

private fun DrawScope.drawScene(
    curl: CurlState,
    zoom: ZoomState,
    current: Bitmap,
    next: Bitmap?,
    previous: Bitmap?,
    bitmapPaint: Paint,
    shadowPaint: Paint,
    creasePaint: Paint
) {
    val w = size.width
    val h = size.height
    val base = fitMatrix(current, w, h)
    val page = fitRect(current, w, h)

    if (!curl.active) {
        // Flat page (with optional zoom).
        drawIntoCanvas { canvas ->
            val m = AndroidMatrix(base)
            if (zoom.scale != 1f) {
                m.postScale(zoom.scale, zoom.scale, w / 2f, h / 2f)
                m.postTranslate(zoom.offset.x, zoom.offset.y)
            }
            canvas.nativeCanvas.drawBitmap(current, m, bitmapPaint)
        }
        return
    }

    val corner = curl.corner
    val touch = curl.touch
    val below = if (curl.direction == CurlDirection.FORWARD) next else previous
    val flap = CurlGeometry.flapPath(corner, touch, page)
    val visible = CurlGeometry.visiblePath(corner, touch, page)
    val reflected = CurlGeometry.reflectedFlapPath(corner, touch, page)
    val foldMid = (corner + touch) / 2f

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas

        // 1 — page underneath.
        if (below != null) nc.drawBitmap(below, fitMatrix(below, w, h), bitmapPaint)
        else nc.drawColor(android.graphics.Color.BLACK)

        // 2 — shadow the lifting flap casts on the page below (dark at the fold, fading out).
        canvas.save()
        canvas.clipPath(flap)
        shadowPaint.shader = LinearGradient(
            foldMid.x, foldMid.y, corner.x, corner.y,
            0x66000000.toInt(), 0x00000000, Shader.TileMode.CLAMP
        )
        nc.drawPaint(shadowPaint)
        shadowPaint.shader = null
        canvas.restore()

        // 3 — flat remainder of the current page.
        canvas.save()
        canvas.clipPath(visible)
        nc.drawBitmap(current, base, bitmapPaint)
        canvas.restore()

        // 4 — the folded-under flap: reflected page + underside tint + soft shading.
        canvas.save()
        canvas.clipPath(reflected)
        val flapMatrix = AndroidMatrix(CurlGeometry.reflectMatrix(corner, touch)).apply { preConcat(base) }
        nc.drawBitmap(current, flapMatrix, bitmapPaint)
        nc.drawColor(0x22000000) // darken the underside
        shadowPaint.shader = LinearGradient(
            touch.x, touch.y, foldMid.x, foldMid.y,
            0x55000000.toInt(), 0x00000000, Shader.TileMode.CLAMP
        )
        nc.drawPaint(shadowPaint)
        shadowPaint.shader = null
        canvas.restore()

        // 5 — faint crease highlight along the fold line (clipped to the page).
        canvas.save()
        canvas.clipRect(page.left, page.top, page.right, page.bottom)
        val (fa, fb) = CurlGeometry.foldLine(corner, touch)
        creasePaint.color = 0x18FFFFFF
        creasePaint.strokeWidth = 3.dp.toPx()
        nc.drawLine(fa.x, fa.y, fb.x, fb.y, creasePaint)
        canvas.restore()
    }
}

/** Matrix that fits [bmp] into the canvas preserving aspect ratio (letterbox-centred). */
private fun fitMatrix(bmp: Bitmap, w: Float, h: Float): AndroidMatrix {
    val s = min(w / bmp.width, h / bmp.height)
    val dw = bmp.width * s
    val dh = bmp.height * s
    return AndroidMatrix().apply {
        setScale(s, s)
        postTranslate((w - dw) / 2f, (h - dh) / 2f)
    }
}

/** The on-screen rectangle [bmp] occupies under [fitMatrix]. */
private fun fitRect(bmp: Bitmap, w: Float, h: Float): Rect {
    val s = min(w / bmp.width, h / bmp.height)
    val dw = bmp.width * s
    val dh = bmp.height * s
    val left = (w - dw) / 2f
    val top = (h - dh) / 2f
    return Rect(left, top, left + dw, top + dh)
}
