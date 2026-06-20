package com.pharma.evisualaid.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** Which way the page is being turned. */
enum class CurlDirection { NONE, FORWARD, BACKWARD }

/**
 * Holds the live curl state that the gesture writes and the Canvas reads each frame. The curl
 * is driven purely by [touch] (finger position, or the interpolated position during the
 * release spring) — never by elapsed time — so a paused finger means a paused curl.
 */
class CurlState {
    /** The page corner being lifted (bottom-right for forward, bottom-left for backward). */
    var corner by mutableStateOf(Offset.Zero)

    /** Current finger position (or the release-animation's interpolated position). */
    var touch by mutableStateOf(Offset.Zero)

    var direction by mutableStateOf(CurlDirection.NONE)

    /** True while a finger is actively dragging (vs. a release spring animating). */
    var dragging by mutableStateOf(false)

    /** A curl is on screen (either dragging or animating its release). */
    val active: Boolean get() = direction != CurlDirection.NONE

    fun begin(direction: CurlDirection, corner: Offset, touch: Offset) {
        this.direction = direction
        this.corner = corner
        this.touch = touch
        this.dragging = true
    }

    fun clear() {
        direction = CurlDirection.NONE
        dragging = false
    }
}

@Composable
fun rememberCurlState(): CurlState = remember { CurlState() }
