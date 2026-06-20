package com.pharma.evisualaid.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

private val PanelBackground = Color(0xF0000000) // 94% opacity black — deep, not see-through

/**
 * The fast capsule navigation panel. A vertical scrollable list of coloured pill labels, one
 * per page — pure text, zero bitmaps. The active page is highlighted and the list auto-scrolls
 * to centre it whenever the panel opens. The hosting screen supplies the slide offset.
 */
@Composable
fun SideIndexPanel(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
    pageNames: List<String>,
    isOpen: Boolean,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val capsuleStridePx = with(androidx.compose.ui.platform.LocalDensity.current) { (36.dp + 8.dp).toPx() }

    // Centre the active capsule each time the panel opens.
    LaunchedEffect(isOpen, currentPage) {
        if (isOpen && pageCount > 0) {
            val centerOffset = -(panelHeightPx / 2 - capsuleStridePx / 2).toInt()
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = (0 until pageCount).toList(),
                key = { _, page -> page }
            ) { index, page ->
                PageCapsule(
                    name = pageNames.getOrNull(page) ?: "Page ${page + 1}",
                    isActive = page == currentPage,
                    index = index,
                    onClick = { onSelectPage(page) }
                )
            }
        }
    }
}
