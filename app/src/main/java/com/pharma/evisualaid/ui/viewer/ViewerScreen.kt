@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pharma.evisualaid.ui.viewer

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pharma.evisualaid.ui.AppViewModelProvider
import com.pharma.evisualaid.ui.theme.PrimaryTeal
import com.pharma.evisualaid.ui.util.LockScreenOrientation
import com.pharma.evisualaid.ui.util.findActivity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The entire app UI: one landscape-locked, edge-to-edge immersive PDF viewer.
 *
 *  - No PDF loaded  → black screen with a single centered "+" button.
 *  - Loading        → black screen with a teal spinner + "Preparing catalog…".
 *  - Loaded         → full-screen book-turn pager, a faint corner "+" to swap catalogs, and a
 *                     swipe-in side index panel.
 */
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    ImmersiveMode()

    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importPdf) }
    val openPicker = { pickPdf.launch(arrayOf("application/pdf")) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.isLoading -> LoadingState()
            !state.isLoaded -> EmptyState(onUpload = openPicker)
            else -> LoadedViewer(state = state, viewModel = viewModel, onUpload = openPicker)
        }
    }
}

@Composable
private fun LoadedViewer(
    state: ViewerState,
    viewModel: ViewerViewModel,
    onUpload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val zoom = remember { ZoomState() }
    var panelOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(pageCount = { state.totalPages })

    LaunchedEffect(pagerState.currentPage) {
        zoom.reset()
        viewModel.onPageSettled(pagerState.currentPage)
    }

    // "New catalog loaded" toast after an import.
    LaunchedEffect(state.justLoadedNew) {
        if (state.justLoadedNew) {
            snackbarHost.showSnackbar("New catalog loaded")
            viewModel.consumeNewLoadedEvent()
        }
    }

    BackHandler(enabled = panelOpen) { panelOpen = false }

    fun jumpTo(target: Int) {
        val safe = target.coerceIn(0, state.totalPages - 1)
        scope.launch {
            // Jumping more than 5 pages snaps (reads as a crossfade since nothing tweens
            // through the gap); nearby jumps spring-turn like a normal page flip.
            if (abs(safe - pagerState.currentPage) > 5) pagerState.scrollToPage(safe)
            else pagerState.animateScrollToPage(safe, animationSpec = spring(stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewModel.setViewportWidth(it.width) }
    ) {
        // A gentle, low-bounce settle makes a released page glide shut instead of snapping.
        val flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        )

        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 1,
            flingBehavior = flingBehavior,
            userScrollEnabled = !zoom.isZoomed && !panelOpen,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // The page being turned must paint on top of the one it reveals.
            val onTop by remember(page) {
                derivedStateOf {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction > 0f
                }
            }
            PageViewer(
                page = page,
                viewModel = viewModel,
                zoom = if (page == pagerState.currentPage) zoom else null,
                pageOffset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction },
                onSingleTap = { if (panelOpen) panelOpen = false },
                modifier = Modifier.zIndex(if (onTop) 1f else 0f)
            )
        }

        // Faint, always-available "+" to swap the loaded catalog (top-left, no label/shadow).
        PlusButton(
            sizeDp = 44,
            iconDp = 26,
            idleAlpha = 0.35f,
            onClick = onUpload,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )

        // Gesture-only entry points for the index panel (no visible handle).
        if (!panelOpen) {
            // Swipe in from the right edge.
            EdgeSwipeCatcher(
                thresholdDp = 60,
                onTrigger = { panelOpen = true },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(40.dp)
            )
            // Swipe in from the top-right corner.
            EdgeSwipeCatcher(
                thresholdDp = 40,
                onTrigger = { panelOpen = true },
                modifier = Modifier.align(Alignment.TopEnd).size(80.dp)
            )
        }

        // Transparent catch layer: tapping the visible catalog closes the panel.
        if (panelOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { panelOpen = false }
            )
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally(animationSpec = tween(250)) { it },
            exit = slideOutHorizontally(animationSpec = tween(250)) { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SideIndexPanel(
                pageCount = state.totalPages,
                currentPage = pagerState.currentPage,
                accent = PrimaryTeal,
                viewModel = viewModel,
                onSelectPage = { page ->
                    panelOpen = false
                    jumpTo(page)
                },
                modifier = Modifier
                    .fillMaxWidth(0.26f)
                    // Drag the panel rightward to dismiss.
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { total = 0f },
                            onHorizontalDrag = { _, drag ->
                                total += drag
                                if (total >= 48.dp.toPx()) panelOpen = false
                            }
                        )
                    }
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
}

/** Detects a leftward swipe that begins inside this box and crosses [thresholdDp]. */
@Composable
private fun EdgeSwipeCatcher(
    thresholdDp: Int,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.pointerInput(Unit) {
            var total = 0f
            var triggered = false
            detectHorizontalDragGestures(
                onDragStart = { total = 0f; triggered = false },
                onDragEnd = { total = 0f; triggered = false },
                onHorizontalDrag = { _, drag ->
                    total += drag
                    if (!triggered && total <= -thresholdDp.dp.toPx()) {
                        triggered = true
                        onTrigger()
                    }
                }
            )
        }
    )
}

/** Borderless, shadowless teal "+" icon button whose opacity rises while pressed. */
@Composable
private fun PlusButton(
    sizeDp: Int,
    iconDp: Int,
    idleAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val targetAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 1f else idleAlpha,
        animationSpec = tween(150),
        label = "plusAlpha"
    )
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .alpha(targetAlpha)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Load catalog",
            tint = PrimaryTeal,
            modifier = Modifier.size(iconDp.dp)
        )
    }
}

/** First-launch state: nothing but a centered "+" on black. */
@Composable
private fun EmptyState(onUpload: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        PlusButton(sizeDp = 56, iconDp = 32, idleAlpha = 1f, onClick = onUpload)
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryTeal, strokeWidth = 3.dp)
            Text(
                text = "Preparing catalog…",
                color = Color(0xFF9E9E9E),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun ImmersiveMode() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = context.findActivity()?.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
