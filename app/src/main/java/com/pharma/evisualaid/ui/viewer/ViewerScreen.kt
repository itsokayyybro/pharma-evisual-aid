@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pharma.evisualaid.ui.viewer

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pharma.evisualaid.ui.AppViewModelProvider
import com.pharma.evisualaid.ui.theme.PrimaryTeal
import com.pharma.evisualaid.ui.util.LockScreenOrientation
import com.pharma.evisualaid.ui.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    var currentPage by remember { mutableIntStateOf(0) }
    var panelOpen by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableIntStateOf(0) }
    var panelDragging by remember { mutableStateOf(false) }
    var panelDragFraction by remember { mutableFloatStateOf(0f) }
    val snackbarHost = remember { SnackbarHostState() }

    // Pre-decoded page bitmaps for the curl (current + both neighbours). On a completed turn we
    // swap these in place so the new page is on screen the same frame the curl finishes, with
    // no reload flash; the loader below then refreshes the now-stale neighbour.
    var current by remember { mutableStateOf<Bitmap?>(null) }
    var nextPage by remember { mutableStateOf<Bitmap?>(null) }
    var prevPage by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.filePath) { currentPage = 0 }

    LaunchedEffect(currentPage, state.totalPages, state.filePath) {
        current = viewModel.getPage(currentPage)
        nextPage = if (currentPage + 1 < state.totalPages) viewModel.getPage(currentPage + 1) else null
        prevPage = if (currentPage - 1 >= 0) viewModel.getPage(currentPage - 1) else null
        viewModel.onPageSettled(currentPage)
    }

    // "New catalog loaded" toast after an import.
    LaunchedEffect(state.justLoadedNew) {
        if (state.justLoadedNew) {
            snackbarHost.showSnackbar("New catalog loaded")
            viewModel.consumeNewLoadedEvent()
        }
    }

    BackHandler(enabled = panelOpen) { panelOpen = false }

    // Near jumps (≤3) flip through pages quickly; far jumps cut straight there (crossfade).
    fun jumpTo(target: Int) {
        val safe = target.coerceIn(0, state.totalPages - 1)
        if (abs(safe - currentPage) <= 3) {
            scope.launch {
                val step = if (safe > currentPage) 1 else -1
                while (currentPage != safe) {
                    currentPage += step
                    delay(90)
                }
            }
        } else {
            currentPage = safe
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewModel.setViewportWidth(it.width); boxWidthPx = it.width }
    ) {
        val bmp = current
        if (bmp == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryTeal, strokeWidth = 3.dp)
            }
        } else {
            PageCurlCanvas(
                currentPageBitmap = bmp,
                nextPageBitmap = nextPage,
                previousPageBitmap = prevPage,
                onPageAdvanced = {
                    prevPage = current
                    current = nextPage
                    currentPage++
                },
                onPageRetreated = {
                    nextPage = current
                    current = prevPage
                    currentPage--
                },
                onTap = { if (panelOpen) panelOpen = false },
                modifier = Modifier.fillMaxSize()
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

        // Right-edge trigger zone (32dp): tap to open, or drag left to reveal progressively.
        val panelWidthPx = boxWidthPx * 0.26f
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(32.dp)
                .pointerInput(boxWidthPx) {
                    val stripPx = 32.dp.toPx()
                    detectDragGestures(
                        onDragStart = { panelDragging = true; panelDragFraction = 0f },
                        onDrag = { change, _ ->
                            change.consume()
                            if (panelWidthPx > 0f) {
                                panelDragFraction =
                                    ((stripPx - change.position.x) / panelWidthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            panelOpen = panelDragFraction > 0.3f
                            panelDragging = false
                            panelDragFraction = 0f
                        },
                        onDragCancel = { panelDragging = false; panelDragFraction = 0f }
                    )
                }
                .pointerInput(Unit) { detectTapGestures { panelOpen = true } }
        )

        // Tapping the visible catalog (anywhere outside the panel) closes it.
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

        // The capsule panel — always composed, slid off-screen to the right when closed.
        val animOffset by animateFloatAsState(
            targetValue = if (panelOpen) 0f else panelWidthPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "panelOffset"
        )
        val offsetX = if (panelDragging) panelWidthPx * (1f - panelDragFraction) else animOffset
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.26f)
                .fillMaxHeight()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
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
        ) {
            SideIndexPanel(
                pageCount = state.totalPages,
                currentPage = currentPage,
                accent = PrimaryTeal,
                pageNames = state.pageNames,
                isOpen = panelOpen,
                onSelectPage = { page ->
                    panelOpen = false
                    jumpTo(page)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
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
