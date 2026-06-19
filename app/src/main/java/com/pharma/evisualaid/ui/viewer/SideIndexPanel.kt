package com.pharma.evisualaid.ui.viewer

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharma.evisualaid.ui.theme.DisplayFamily
import com.pharma.evisualaid.ui.theme.MonoFamily

private val PanelBackground = Color(0xE8000000)      // 88% opacity black, frosted feel
private val ActivePageTint = Color(0x1A00BCD4)       // subtle teal background on active page

/**
 * The right-side index drawer: a scrollable column of page thumbnails. The active page is
 * highlighted and the list auto-scrolls to keep it visible. The hosting screen supplies the
 * slide-in/out animation; this composable is just the panel card (frosted black, teal left
 * border strip, left-rounded corners).
 *
 * No close button — the panel is dismissed by swiping it back or tapping the main area.
 */
@Composable
fun SideIndexPanel(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
    titles: Map<Int, String>,
    viewModel: ViewerViewModel,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            .background(PanelBackground)
    ) {
        // Teal anchor strip down the left edge.
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pages",
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E)
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items((0 until pageCount).toList(), key = { it }) { page ->
                    IndexThumbnail(
                        page = page,
                        isActive = page == currentPage,
                        accent = accent,
                        title = titles[page],
                        viewModel = viewModel,
                        onClick = { onSelectPage(page) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexThumbnail(
    page: Int,
    isActive: Boolean,
    accent: Color,
    title: String?,
    viewModel: ViewerViewModel,
    onClick: () -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, page) {
        value = viewModel.getThumbnail(page)
    }
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isActive) ActivePageTint else Color.White.copy(alpha = 0.04f))
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) accent else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = title?.let { "Page ${page + 1}: $it" } ?: "Page ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.size(5.dp))
        // The one-word heading the presenter navigates by; falls back to the page number.
        Text(
            text = title ?: "Page ${page + 1}",
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (isActive) accent else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (title != null) {
            Text(
                text = "p.${page + 1}",
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                color = Color(0xFF9E9E9E),
                textAlign = TextAlign.Center
            )
        }
    }
}
