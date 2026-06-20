package com.pharma.evisualaid.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import android.graphics.Matrix as AndroidMatrix

/**
 * Pure geometry for the page-curl. The model is the classic "fold across the perpendicular
 * bisector": the page folds along the line that is the perpendicular bisector of the segment
 * from the lifted [corner] to the current finger [touch]. Reflecting the page across that line
 * sends the corner exactly onto the finger, so the flap looks like paper folded under the
 * finger, revealing the page beneath.
 *
 * All coordinates are in canvas pixels. [page] is the fitted rectangle the page occupies on
 * screen (letterbox-centred); corners and touches are expressed in that same space.
 */
object CurlGeometry {

    private const val SPAN = 100_000f // long enough to cross any page

    /** Two points far apart along the fold line (perpendicular bisector of corner→touch). */
    fun foldLine(corner: Offset, touch: Offset): Pair<Offset, Offset> {
        val mid = (corner + touch) / 2f
        val d = touch - corner
        val perp = Offset(-d.y, d.x)
        val len = perp.getDistance().coerceAtLeast(1e-4f)
        val u = perp / len
        return (mid - u * SPAN) to (mid + u * SPAN)
    }

    /** Affine reflection that maps [corner]→[touch] and fixes the fold line (via setPolyToPoly). */
    fun reflectMatrix(corner: Offset, touch: Offset): AndroidMatrix {
        val (a, b) = foldLine(corner, touch)
        val src = floatArrayOf(corner.x, corner.y, a.x, a.y, b.x, b.y)
        val dst = floatArrayOf(touch.x, touch.y, a.x, a.y, b.x, b.y)
        return AndroidMatrix().apply { setPolyToPoly(src, 0, dst, 0, 3) }
    }

    /** The portion of the page that folds over (corner side of the fold line ∩ page). */
    fun flapPath(corner: Offset, touch: Offset, page: Rect): Path {
        val (a, b) = foldLine(corner, touch)
        val cornerSide = halfPlane(a, b, corner)
        val rect = Path().apply { addRect(page) }
        return Path().apply { op(cornerSide, rect, PathOperation.Intersect) }
    }

    /** The flat, still-visible portion of the current page (everything but the flap). */
    fun visiblePath(corner: Offset, touch: Offset, page: Rect): Path {
        val rect = Path().apply { addRect(page) }
        val flap = flapPath(corner, touch, page)
        return Path().apply { op(rect, flap, PathOperation.Difference) }
    }

    /** The flap reflected across the fold line — where the folded-under paper is drawn. */
    fun reflectedFlapPath(corner: Offset, touch: Offset, page: Rect): Path {
        val flap = flapPath(corner, touch, page).asAndroidPath()
        val out = android.graphics.Path(flap)
        out.transform(reflectMatrix(corner, touch))
        return out.asComposePath()
    }

    /** 0 (page flat) → 1 (fully turned): how far the fold has travelled toward the far edge. */
    fun progress(corner: Offset, touch: Offset, page: Rect): Float {
        val travel = (corner - touch).getDistance()
        return (travel / page.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    /** A huge quad covering the half-plane (bounded by line a→b) that contains [towards]. */
    private fun halfPlane(a: Offset, b: Offset, towards: Offset): Path {
        val dir = b - a
        val len = dir.getDistance().coerceAtLeast(1e-4f)
        val u = dir / len
        val n = Offset(-u.y, u.x)
        val side = if (dot(towards - a, n) >= 0f) 1f else -1f
        val out = n * (SPAN * side)
        return Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(b.x + out.x, b.y + out.y)
            lineTo(a.x + out.x, a.y + out.y)
            close()
        }
    }

    private fun dot(a: Offset, b: Offset): Float = a.x * b.x + a.y * b.y
}
