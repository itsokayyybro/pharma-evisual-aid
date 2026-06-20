package com.pharma.evisualaid.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val PanelBackground = Color(0xF0000000) // 94% opacity black — deep, not see-through

/**
 * The fast page-navigation panel. A vertical scrollable rail of small page previews
 * ([PagePreview]), one per page, so a presenter can jump straight to the page they need by
 * recognising it. The active page is highlighted and the rail auto-scrolls to centre it
 * whenever the panel opens. The hosting screen supplies the slide offset.
 */
@Composable
fun SideIndexPanel(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
    thumbnailFor: suspend (Int) -> Bitmap?,
    isOpen: Boolean,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val previewStridePx = with(LocalDensity.current) {
        (PagePreviewHeight + PagePreviewSpacing).toPx()
    }

    // Centre the active preview each time the panel opens.
    LaunchedEffect(isOpen, currentPage) {
        if (isOpen && pageCount > 0) {
            val centerOffset = -(panelHeightPx / 2 - previewStridePx / 2).toInt()
            listState.animateScrollToItem(currentPage.coerceIn(0, pageCount - 1), centerOffset)
        }
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(PanelBackground)
            .onSizeChanged { panelHeightPx = it.height }
    ) {
        // Teal anchor strip down the left edge (the visual "handle").
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(PagePreviewSpacing)
        ) {
            itemsIndexed(
                items = (0 until pageCount).toList(),
                key = { _, page -> page }
            ) { _, page ->
                PagePreview(
                    page = page,
                    isActive = page == currentPage,
                    thumbnailFor = thumbnailFor,
                    onClick = { onSelectPage(page) }
                )
            }
        }
    }
}
