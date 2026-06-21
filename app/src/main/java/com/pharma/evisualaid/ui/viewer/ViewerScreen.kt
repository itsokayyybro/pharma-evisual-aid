@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pharma.evisualaid.ui.viewer

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Landscape-locked, edge-to-edge immersive PDF viewer for a single catalog opened from the
 * dashboard.
 */
@Composable
fun ViewerScreen(
    catalogId: String,
    onExit: () -> Unit,
    viewModel: ViewerViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    ImmersiveMode()

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(catalogId) { viewModel.open(catalogId) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loadFailed -> ErrorState(onExit = onExit)
            !state.isLoaded -> LoadingState()
            else -> LoadedViewer(state = state, viewModel = viewModel, onExit = onExit)
        }
    }
}

@Composable
private fun LoadedViewer(
    state: ViewerState,
    viewModel: ViewerViewModel,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentPage by remember { mutableIntStateOf(state.initialPage) }
    var panelOpen by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableIntStateOf(0) }

    var current by remember { mutableStateOf<Bitmap?>(null) }
    var nextPage by remember { mutableStateOf<Bitmap?>(null) }
    var prevPage by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.filePath) { currentPage = state.initialPage }

    LaunchedEffect(currentPage, state.totalPages, state.filePath) {
        current = viewModel.getPage(currentPage)
        nextPage = if (currentPage + 1 < state.totalPages) viewModel.getPage(currentPage + 1) else null
        prevPage = if (currentPage - 1 >= 0) viewModel.getPage(currentPage - 1) else null
        viewModel.onPageSettled(currentPage)
    }

    BackHandler(enabled = panelOpen) { panelOpen = false }

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
            .onSizeChanged { 
                viewModel.setViewportSize(it.width, it.height)
                boxWidthPx = it.width 
            }
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
                onDoubleTap = { offset ->
                    // Open panel ONLY if double-tapped on the right half of the screen
                    if (offset.x > boxWidthPx / 2f) {
                        panelOpen = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        BackButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )

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

        val panelWidthPx = boxWidthPx * 0.26f
        val animOffset by animateFloatAsState(
            targetValue = if (panelOpen) 0f else panelWidthPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "panelOffset"
        )
        
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.26f)
                .fillMaxHeight()
                .offset { IntOffset(animOffset.roundToInt(), 0) }
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
                thumbnailFor = viewModel::getThumbnail,
                isOpen = panelOpen,
                onSelectPage = { page ->
                    panelOpen = false
                    jumpTo(page)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val targetAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0.35f,
        animationSpec = tween(150),
        label = "backAlpha"
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .alpha(targetAlpha)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to catalogs",
            tint = PrimaryTeal,
            modifier = Modifier.size(26.dp)
        )
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
private fun ErrorState(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "This catalog could not be opened.",
                color = Color(0xFFE8EAF6),
                fontSize = 16.sp
            )
            TextButton(onClick = onExit, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back to catalogs", color = PrimaryTeal)
            }
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
