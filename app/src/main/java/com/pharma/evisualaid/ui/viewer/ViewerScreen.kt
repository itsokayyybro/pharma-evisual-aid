@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pharma.evisualaid.ui.viewer

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pharma.evisualaid.util.VoiceAssistant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPage by remember { mutableIntStateOf(state.initialPage) }
    var panelOpen by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableIntStateOf(0) }

    var current by remember { mutableStateOf<Bitmap?>(null) }
    var nextPage by remember { mutableStateOf<Bitmap?>(null) }
    var prevPage by remember { mutableStateOf<Bitmap?>(null) }

    val voiceAssistant = remember { VoiceAssistant(context) }
    DisposableEffect(Unit) {
        onDispose { voiceAssistant.shutdown() }
    }

    LaunchedEffect(state.filePath) { currentPage = state.initialPage }

    LaunchedEffect(currentPage, state.totalPages, state.filePath) {
        current = viewModel.getPage(currentPage)
        nextPage = if (currentPage + 1 < state.totalPages) viewModel.getPage(currentPage + 1) else null
        prevPage = if (currentPage - 1 >= 0) viewModel.getPage(currentPage - 1) else null
        viewModel.onPageSettled(currentPage)
        voiceAssistant.stop()
        viewModel.clearDescription()
    }

    LaunchedEffect(state.currentDescription) {
        state.currentDescription?.let { desc ->
            voiceAssistant.speak(desc)
        }
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
                    if (offset.x > boxWidthPx / 2f) {
                        panelOpen = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top-left Controls & Status Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .zIndex(100f) // Keep above the curl canvas
        ) {
            BackButton(onClick = onExit)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                val isSpeaking = state.currentDescription != null
                
                IconButton(
                    onClick = {
                        Log.d("ViewerScreen", "AI Button Clicked")
                        if (isSpeaking) {
                            voiceAssistant.stop()
                            viewModel.clearDescription()
                        } else {
                            bmp?.let { viewModel.describeCurrentPage(it) }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = PrimaryTeal
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    if (state.isDescribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = PrimaryTeal,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.AutoAwesome,
                            contentDescription = "AI Description",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Status message overlay - shown when describing or when there is an error
                val status = state.statusMessage
                AnimatedVisibility(
                    visible = status != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status ?: "",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (panelOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { panelOpen = false }
                    .zIndex(50f)
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
                .zIndex(200f)
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
        targetValue = if (pressed) 1f else 0.4f,
        animationSpec = tween(150),
        label = "backAlpha"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(targetAlpha)
            .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = PrimaryTeal,
            modifier = Modifier.size(28.dp)
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
