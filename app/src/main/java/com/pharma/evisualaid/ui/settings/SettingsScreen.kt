package com.pharma.evisualaid.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pharma.evisualaid.BuildConfig
import com.pharma.evisualaid.data.model.SortOrder
import com.pharma.evisualaid.ui.AppViewModelProvider
import com.pharma.evisualaid.ui.components.ConfirmDialog
import com.pharma.evisualaid.ui.components.TextEntryDialog
import com.pharma.evisualaid.ui.theme.AccentPresets
import com.pharma.evisualaid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showNameDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var clearStep by remember { mutableIntStateOf(0) }

    val pickLogo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setCompanyLogo(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is SettingsEvent.ShareExport -> {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Export Catalogs"))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Company name
            SettingSection("Company Name") {
                ClickableRow(
                    text = settings.companyName.ifBlank { "Not set" },
                    onClick = { showNameDialog = true }
                )
            }

            // Company logo
            SettingSection("Company Logo") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (settings.companyLogoUri != null) "Logo set" else "No logo",
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (settings.companyLogoUri != null) {
                        OutlinedButton(onClick = { viewModel.setCompanyLogo(null) }) {
                            Text("Remove")
                        }
                    }
                    Button(onClick = { pickLogo.launch(arrayOf("image/*")) }) {
                        Text(if (settings.companyLogoUri != null) "Change" else "Choose")
                    }
                }
            }

            // Accent color
            SettingSection("Accent Color") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentPresets.forEach { preset ->
                        val selected = settings.accentColorArgb == preset.argb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color.White else TextSecondary.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setAccentColor(preset.argb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "${preset.label} selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Default sort
            SettingSection("Default Sort Order") {
                SortDropdown(
                    current = settings.defaultSort,
                    onSelected = viewModel::setDefaultSort
                )
            }

            // Categories
            SettingSection("Categories") {
                if (settings.categories.isEmpty()) {
                    Text(
                        text = "No categories yet",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        settings.categories.forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.removeCategory(category) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove $category",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { showCategoryDialog = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("  Add Category")
                }
            }

            // About
            SettingSection("About") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("E-Visual Aid", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Export
            Button(
                onClick = { viewModel.exportAllCatalogs() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export All Catalogs") }

            // Clear all
            OutlinedButton(
                onClick = { clearStep = 1 },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Clear All Data") }

            Spacer(Modifier.size(8.dp))
        }
    }

    if (showNameDialog) {
        TextEntryDialog(
            title = "Company Name",
            label = "Name",
            initial = settings.companyName,
            onConfirm = { viewModel.setCompanyName(it); showNameDialog = false },
            onDismiss = { showNameDialog = false }
        )
    }

    if (showCategoryDialog) {
        TextEntryDialog(
            title = "Add Category",
            label = "Category name (e.g. Cardiology)",
            confirmLabel = "Add",
            onConfirm = { viewModel.addCategory(it); showCategoryDialog = false },
            onDismiss = { showCategoryDialog = false }
        )
    }

    // Double confirmation for the nuclear option.
    when (clearStep) {
        1 -> ConfirmDialog(
            title = "Clear All Data?",
            message = "This permanently deletes every uploaded catalog and its thumbnails.",
            confirmLabel = "Continue",
            onConfirm = { clearStep = 2 },
            onDismiss = { clearStep = 0 }
        )
        2 -> ConfirmDialog(
            title = "Are you absolutely sure?",
            message = "This action cannot be undone. All catalogs will be removed from this device.",
            confirmLabel = "Delete Everything",
            onConfirm = { viewModel.clearAllData(); clearStep = 0 },
            onDismiss = { clearStep = 0 }
        )
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun ClickableRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = TextSecondary, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SortDropdown(current: SortOrder, onSelected: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ClickableRow(text = current.label, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = { onSelected(order); expanded = false }
                )
            }
        }
    }
}
