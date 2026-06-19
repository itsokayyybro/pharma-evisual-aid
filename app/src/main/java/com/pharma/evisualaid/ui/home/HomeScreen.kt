package com.pharma.evisualaid.ui.home

import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pharma.evisualaid.R
import com.pharma.evisualaid.data.model.Catalog
import com.pharma.evisualaid.ui.AppViewModelProvider
import com.pharma.evisualaid.ui.components.ConfirmDeleteBottomSheet
import com.pharma.evisualaid.ui.components.TextEntryDialog
import com.pharma.evisualaid.ui.theme.TextSecondary
import com.pharma.evisualaid.ui.util.LockScreenOrientation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCatalog: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    // Home allows rotation; the Presentation screen will re-lock to landscape.
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Local UI state for transient dialogs / inline edits.
    var renamingId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Catalog?>(null) }
    var bulkDeletePending by remember { mutableStateOf(false) }
    var addTagTarget by remember { mutableStateOf<Catalog?>(null) }

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importPdf(it) } }

    fun launchPicker() = pickPdf.launch(arrayOf("application/pdf"))

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is HomeEvent.StorageFull -> snackbarHostState.showSnackbar(
                    "Not enough storage. Free space: ${event.freeSpace}"
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    count = state.selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onDelete = { bulkDeletePending = true }
                )
            } else {
                CenterAlignedTopAppBar(
                    navigationIcon = { CompanyLogo(state.companyLogoUri) },
                    title = {
                        val appName = stringResource(R.string.app_name)
                        Text(
                            text = state.companyName.ifBlank { appName },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (!state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { launchPicker() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Upload Catalog") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!state.isEmpty) {
                FilterBar(
                    filters = state.availableFilters,
                    activeFilter = state.activeFilter,
                    sortOrder = state.sortOrder,
                    searchActive = state.isSearchActive,
                    searchQuery = state.searchQuery,
                    onFilterSelected = viewModel::setFilter,
                    onAddFilter = viewModel::addCustomFilter,
                    onSortSelected = viewModel::setSort,
                    onToggleSearch = viewModel::toggleSearch,
                    onSearchQueryChange = viewModel::setSearchQuery
                )
            }

            when {
                state.isEmpty -> EmptyState(onUpload = { launchPicker() })
                state.catalogs.isEmpty() -> NoResultsState()
                else -> CatalogGrid(
                    catalogs = state.catalogs,
                    state = state,
                    renamingId = renamingId,
                    onOpen = { catalog ->
                        if (state.selectionMode) viewModel.toggleSelect(catalog.id)
                        else onOpenCatalog(catalog.id)
                    },
                    onLongPress = { catalog ->
                        if (state.selectionMode) viewModel.toggleSelect(catalog.id)
                        else viewModel.enterSelection(catalog.id)
                    },
                    onRenameStart = { renamingId = it.id },
                    onRenameSubmit = { catalog, name ->
                        val error = viewModel.rename(catalog.id, name)
                        if (error == null) renamingId = null
                        error
                    },
                    onRenameCancel = { renamingId = null },
                    onDelete = { deleteTarget = it },
                    onAddTag = { addTagTarget = it }
                )
            }
        }
    }

    // ---- Dialogs / sheets ----

    deleteTarget?.let { target ->
        ConfirmDeleteBottomSheet(
            itemLabel = target.displayName,
            onConfirm = { viewModel.deleteCatalog(target); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }

    if (bulkDeletePending) {
        ConfirmDeleteBottomSheet(
            itemLabel = "",
            count = state.selectedIds.size,
            onConfirm = { viewModel.deleteSelected(); bulkDeletePending = false },
            onDismiss = { bulkDeletePending = false }
        )
    }

    addTagTarget?.let { target ->
        TextEntryDialog(
            title = "Add Tag",
            label = "Tag (e.g. Cardiology)",
            confirmLabel = "Add",
            onConfirm = { viewModel.addTag(target, it); addTagTarget = null },
            onDismiss = { addTagTarget = null }
        )
    }
}

@Composable
private fun CompanyLogo(logoUri: String?) {
    Box(modifier = Modifier.padding(start = 12.dp).size(36.dp)) {
        if (logoUri != null) {
            AsyncImage(
                model = logoUri,
                contentDescription = "Company logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_logo_placeholder),
                contentDescription = "Company logo",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Exit selection")
            }
        },
        title = { Text("$count selected", style = MaterialTheme.typography.titleLarge) },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete selected",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun CatalogGrid(
    catalogs: List<Catalog>,
    state: HomeUiState,
    renamingId: String?,
    onOpen: (Catalog) -> Unit,
    onLongPress: (Catalog) -> Unit,
    onRenameStart: (Catalog) -> Unit,
    onRenameSubmit: (Catalog, String) -> String?,
    onRenameCancel: () -> Unit,
    onDelete: (Catalog) -> Unit,
    onAddTag: (Catalog) -> Unit
) {
    val accent = Color(state.accentArgb)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(catalogs, key = { it.id }) { catalog ->
            CatalogCard(
                catalog = catalog,
                accent = accent,
                selectionMode = state.selectionMode,
                selected = catalog.id in state.selectedIds,
                isRenaming = renamingId == catalog.id,
                onClick = { onOpen(catalog) },
                onLongClick = { onLongPress(catalog) },
                onRenameStart = { onRenameStart(catalog) },
                onRenameSubmit = { name -> onRenameSubmit(catalog, name) },
                onRenameCancel = onRenameCancel,
                onDelete = { onDelete(catalog) },
                onAddTag = { onAddTag(catalog) },
                onView = { onOpen(catalog) }
            )
        }
    }
}

@Composable
private fun EmptyState(onUpload: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = TextSecondary.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onUpload)
                .padding(vertical = 56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.UploadFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "No catalogs yet",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tap to upload your first catalog.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoResultsState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No catalogs match your filters",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Try a different filter or search term.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
