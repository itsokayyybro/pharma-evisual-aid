package com.pharma.evisualaid.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import com.pharma.evisualaid.ui.theme.PrimaryTeal

/** Fixed row height for each preview — keeps the rail uniform and the auto-scroll predictable. */
val PagePreviewHeight = 116.dp

/** Vertical gap between previews in the rail. */
val PagePreviewSpacing = 10.dp

private val CardBackground = Color(0xFF15151F)
private val IdleBorder = Color.White.copy(alpha = 0.12f)

/**
 * A single small page preview in the navigation rail: a rendered thumbnail of the page with a
 * subtle rounded border and a compact page-number badge. The active page gets a teal border so
 * it stands out. The thumbnail is loaded lazily via [thumbnailFor] and cached by the ViewModel.
 */
@Composable
fun PagePreview(
    page: Int,
    isActive: Boolean,
    thumbnailFor: suspend (Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page) { bitmap = thumbnailFor(page) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PagePreviewHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBackground)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) PrimaryTeal else IdleBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } else {
            CircularProgressIndicator(
                color = PrimaryTeal,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }

        // Compact page-number badge so a presenter can still tell pages apart at a glance.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${page + 1}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
