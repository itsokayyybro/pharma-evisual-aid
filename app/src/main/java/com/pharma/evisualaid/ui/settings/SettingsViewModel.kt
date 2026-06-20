package com.pharma.evisualaid.ui.settings

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharma.evisualaid.data.model.SortOrder
import com.pharma.evisualaid.data.prefs.AppSettings
import com.pharma.evisualaid.data.prefs.SettingsRepository
import com.pharma.evisualaid.data.repository.CatalogRepository
import com.pharma.evisualaid.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class ShareExport(val uri: Uri) : SettingsEvent
}

class SettingsViewModel(
    private val app: Application,
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

    fun setCompanyName(name: String) =
        viewModelScope.launch { settingsRepository.setCompanyName(name.trim()) }

    fun setCompanyLogo(uri: Uri?) =
        viewModelScope.launch { settingsRepository.setCompanyLogoUri(uri?.toString()) }

    fun setAccentColor(argb: Long) =
        viewModelScope.launch { settingsRepository.setAccentColor(argb) }

    fun setDefaultSort(order: SortOrder) =
        viewModelScope.launch { settingsRepository.setDefaultSort(order) }

    fun setAutoHideDelay(ms: Long) =
        viewModelScope.launch { settingsRepository.setAutoHideDelay(ms) }

    fun exportAllCatalogs() {
        viewModelScope.launch {
            val zip = withContext(Dispatchers.IO) {
                runCatching { FileUtils.exportAllCatalogs(app, "EVisualAid_Catalogs.zip") }
                    .getOrNull()
            }
            if (zip == null) {
                events.send(SettingsEvent.Message("No catalogs to export"))
            } else {
                val uri = FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    zip
                )
                events.send(SettingsEvent.ShareExport(uri))
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            catalogRepository.deleteAll()
            events.send(SettingsEvent.Message("All catalogs cleared"))
        }
    }
}
