package com.pharma.evisualaid.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharma.evisualaid.ui.theme.DisplayFamily

/** Active page accent (teal) — overrides the palette so the current page stands out. */
private val ActiveCapsule = Color(0xFF00BCD4)

/** Deterministic, pharma-muted palette cycled by page index. */
private val CapsulePalette = listOf(
    Color(0xFF1976D2), // medical blue
    Color(0xFF388E3C), // clinical green
    Color(0xFF7B1FA2), // deep purple
    Color(0xFF00838F), // teal dark
    Color(0xFFE64A19), // warm red-orange
    Color(0xFF5D4037), // warm brown
    Color(0xFF0288D1), // light blue
    Color(0xFF2E7D32), // forest green
    Color(0xFF6A1B9A), // violet
    Color(0xFF00695C), // dark teal
)

fun capsuleColor(index: Int): Color = CapsulePalette[index % CapsulePalette.size]

/**
 * A single pill in the navigation panel: a full-width, rounded, coloured capsule with the
 * page/product name centred inside. Pure text — no bitmaps. The active page is solid teal.
 */
@Composable
fun PageCapsule(
    name: String,
    isActive: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isActive) ActiveCapsule else capsuleColor(index))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = DisplayFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}
