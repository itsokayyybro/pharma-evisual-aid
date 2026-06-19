@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pharma.evisualaid.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pharma.evisualaid.data.model.Catalog
import com.pharma.evisualaid.ui.theme.AccentGlow
import com.pharma.evisualaid.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun CatalogCard(
    catalog: Catalog,
    accent: Color,
    selectionMode: Boolean,
    selected: Boolean,
    isRenaming: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameStart: () -> Unit,
    onRenameSubmit: (String) -> String?, // returns error message or null on success
    onRenameCancel: () -> Unit,
    onDelete: () -> Unit,
    onAddTag: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) accent else Color.Transparent

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .background(AccentGlow.copy(alpha = 0.04f))
            ) {
                ThumbnailImage(catalog = catalog, accent = accent)

                // Page count badge
                PageCountBadge(
                    pageCount = catalog.pageCount,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )

                // Selection check overlay
                AnimatedVisibility(
                    visible = selectionMode,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) accent else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (isRenaming) {
                    InlineRename(
                        initial = catalog.displayName,
                        onSubmit = onRenameSubmit,
                        onCancel = onRenameCancel
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = catalog.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!selectionMode) {
                            OverflowMenu(
                                onRename = onRenameStart,
                                onDelete = onDelete,
                                onAddTag = onAddTag,
                                onView = onView
                            )
                        }
                    }
                }

                Spacer(Modifier.size(4.dp))
                Text(
                    text = dateFormat.format(Date(catalog.uploadedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                if (catalog.tags.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(catalog.tags) { tag ->
                            TagChip(tag = tag, accent = accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailImage(catalog: Catalog, accent: Color) {
    val context = LocalContext.current
    if (catalog.thumbnailPath != null && File(catalog.thumbnailPath).exists()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(catalog.thumbnailPath))
                .crossfade(true)
                .build(),
            contentDescription = "${catalog.displayName} cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Thumbnail still rendering in the background.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.size(10.dp))
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PageCountBadge(pageCount: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC000000)
    ) {
        Text(
            text = "$pageCount ${if (pageCount == 1) "page" else "pages"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TagChip(tag: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.18f)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OverflowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddTag: () -> Unit,
    onView: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "More options",
            tint = TextSecondary,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .combinedClickable(onClick = { expanded = true })
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("View") }, onClick = { expanded = false; onView() })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; onRename() })
            DropdownMenuItem(text = { Text("Add Tag") }, onClick = { expanded = false; onAddTag() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

@Composable
private fun InlineRename(
    initial: String,
    onSubmit: (String) -> String?,
    onCancel: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = value,
        onValueChange = { value = it; error = null },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            val result = onSubmit(value)
            if (result != null) error = result
        }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}
