package com.pharma.evisualaid.ui.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pharma.evisualaid.ui.theme.TextSecondary

/**
 * The right-side smart index drawer: a vertical list of page thumbnails. The active page
 * gets an accent border and the list auto-scrolls to keep it in view. This composable is
 * just the panel card; the hosting screen supplies the slide-in animation and scrim.
 */
@Composable
fun SideIndexPanel(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
    viewModel: PresentationViewModel,
    onClose: () -> Unit,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Keep the active thumbnail visible whenever the page changes.
    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            .background(Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close index", tint = TextSecondary)
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items((0 until pageCount).toList(), key = { it }) { page ->
                IndexThumbnail(
                    page = page,
                    isActive = page == currentPage,
                    accent = accent,
                    viewModel = viewModel,
                    onClick = { onSelectPage(page) }
                )
            }
        }
    }
}

@Composable
private fun IndexThumbnail(
    page: Int,
    isActive: Boolean,
    accent: Color,
    viewModel: PresentationViewModel,
    onClick: () -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, page) {
        value = viewModel.getThumbnail(page)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isActive) 3.dp else 1.dp,
                    color = if (isActive) accent else TextSecondary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(Color.White.copy(alpha = 0.04f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${page + 1} thumbnail",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = "Pg ${page + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) accent else TextSecondary,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
