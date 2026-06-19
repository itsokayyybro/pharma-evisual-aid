package com.pharma.evisualaid.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Walks the ContextWrapper chain to find the hosting [Activity], if any. */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Locks the screen orientation for as long as this composable is in the tree, restoring the
 * previous orientation on dispose. Used to pin the Presentation screen to landscape while
 * letting Home/Settings rotate freely.
 */
@Composable
fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context.findActivity()
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose {
            if (original != null) activity.requestedOrientation = original
        }
    }
}
