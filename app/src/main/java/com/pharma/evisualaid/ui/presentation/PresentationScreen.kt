@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pharma.evisualaid.ui.presentation

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pharma.evisualaid.data.prefs.AppSettings
import com.pharma.evisualaid.ui.AppViewModelProvider
import com.pharma.evisualaid.ui.theme.TextSecondary
import com.pharma.evisualaid.ui.util.LockScreenOrientation
import com.pharma.evisualaid.ui.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PresentationScreen(
    catalogId: String,
    onExit: () -> Unit,
    viewModel: PresentationViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    // Doctor-facing: lock landscape and go fully immersive.
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    ImmersiveMode()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val accent = Color(state.accentArgb)

    LaunchedEffect(catalogId) { viewModel.load(catalogId) }

    var chromeVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var indexOpen by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }

    fun pokeChrome() {
        chromeVisible = true
        interactions++
    }

    // Auto-hide chrome after the configured delay of inactivity.
    LaunchedEffect(interactions, chromeVisible, state.autoHideDelayMs, indexOpen) {
        if (chromeVisible && !indexOpen && state.autoHideDelayMs != AppSettings.NEVER_HIDE) {
            delay(state.autoHideDelayMs)
            chromeVisible = false
        }
    }

    BackHandler(enabled = indexOpen) { indexOpen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewModel.setViewportWidth(it.width) }
    ) {
        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(message = state.error!!, onBack = onExit)
            state.pageCount > 0 -> {
                val pagerState = rememberPagerState(pageCount = { state.pageCount })

                // Persist position, prefetch neighbors and reset zoom on settle.
                LaunchedEffect(pagerState.currentPage) {
                    zoom.reset()
                    viewModel.onPageSettled(pagerState.currentPage)
                }

                fun jumpTo(target: Int) {
                    val safe = target.coerceIn(0, state.pageCount - 1)
                    scope.launch {
                        // >2 pages: fast jump (acts as a crossfade since neighbors are cached);
                        // adjacent: natural spring slide.
                        if (abs(safe - pagerState.currentPage) > 2) pagerState.scrollToPage(safe)
                        else pagerState.animateScrollToPage(
                            safe,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 1,
                    userScrollEnabled = !zoom.isZoomed,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    PageViewer(
                        page = page,
                        viewModel = viewModel,
                        zoom = if (page == pagerState.currentPage) zoom else null,
                        onSingleTap = { pokeChrome() }
                    )
                }

                // Right-edge swipe-in to open the index drawer.
                if (!indexOpen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(28.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount < -18f) indexOpen = true
                                }
                            }
                    )
                }

                // Bottom chrome (auto-hides).
                AnimatedVisibility(
                    visible = chromeVisible && !indexOpen,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomNavBar(
                        currentPage = pagerState.currentPage,
                        pageCount = state.pageCount,
                        accent = accent,
                        viewModel = viewModel,
                        onBack = onExit,
                        onIndexToggle = { indexOpen = true; pokeChrome() },
                        onScrubTo = { jumpTo(it) }
                    )
                }

                // Side index drawer + scrim.
                AnimatedVisibility(
                    visible = indexOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000))
                            .pointerInput(Unit) { detectTapClose { indexOpen = false } }
                    )
                }
                AnimatedVisibility(
                    visible = indexOpen,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    SideIndexPanel(
                        pageCount = state.pageCount,
                        currentPage = pagerState.currentPage,
                        accent = accent,
                        viewModel = viewModel,
                        onClose = { indexOpen = false },
                        onSelectPage = { page ->
                            indexOpen = false
                            jumpTo(page)
                        },
                        modifier = Modifier.fillMaxWidth(0.28f)
                    )
                }

                // Resume prompt.
                state.resumePage?.let { resumePage ->
                    ResumePrompt(
                        page = resumePage,
                        accent = accent,
                        onResume = { viewModel.dismissResume(); jumpTo(resumePage) },
                        onStartOver = { viewModel.dismissResume() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
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
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val window = context.findActivity()?.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Tap anywhere on the scrim to close the drawer. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapClose(
    onTap: () -> Unit
) {
    androidx.compose.foundation.gestures.detectTapGestures(onTap = { onTap() })
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(onClick = onBack) { Text("Back to Library") }
            }
        }
    }
}

@Composable
private fun ResumePrompt(
    page: Int,
    accent: Color,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(24.dp),
        color = Color(0xF21A1A2E),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Resume from page ${page + 1}?",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            OutlinedButton(onClick = onStartOver) { Text("Start Over", color = TextSecondary) }
            Button(
                onClick = onResume,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accent)
            ) { Text("Resume") }
        }
    }
}
