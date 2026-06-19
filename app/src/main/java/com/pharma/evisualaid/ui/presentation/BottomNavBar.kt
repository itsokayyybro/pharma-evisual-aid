package com.pharma.evisualaid.ui.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Semi-transparent bottom overlay: Back (left), page indicator + scrubber (center) and the
 * Index toggle (right). Dragging the scrubber shows a live preview thumbnail above the thumb.
 */
@Composable
fun BottomNavBar(
    currentPage: Int,
    pageCount: Int,
    accent: Color,
    viewModel: PresentationViewModel,
    onBack: () -> Unit,
    onIndexToggle: () -> Unit,
    onScrubTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(currentPage.toFloat()) }

    // Follow external page changes (swipe / index tap) while not actively scrubbing.
    LaunchedEffect(currentPage, isDragging) {
        if (!isDragging) sliderPos = currentPage.toFloat()
    }

    val previewPage = sliderPos.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    Box(modifier = modifier.fillMaxWidth()) {
        // Live preview thumbnail while dragging.
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(bottom = 8.dp)
        ) {
            ScrubPreview(page = previewPage, accent = accent, viewModel = viewModel)
        }

        Surface(
            color = Color(0xCC0D0D1A), // frosted-glass fallback tint
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to library",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Page ${currentPage + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    if (pageCount > 1) {
                        Slider(
                            value = sliderPos,
                            onValueChange = {
                                isDragging = true
                                sliderPos = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onScrubTo(previewPage)
                            },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                IconButton(onClick = onIndexToggle) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = "Page index",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrubPreview(page: Int, accent: Color, viewModel: PresentationViewModel) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = page
    ) {
        value = viewModel.getThumbnail(page)
    }
    Surface(
        color = Color(0xFF16213E),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 120.dp, height = 160.dp)
                    )
                }
            }
            Text(
                text = "Pg ${page + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
