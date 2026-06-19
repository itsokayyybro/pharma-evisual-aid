package com.pharma.evisualaid.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pharma.evisualaid.EVisualAidApp
import com.pharma.evisualaid.ui.home.HomeViewModel
import com.pharma.evisualaid.ui.presentation.PresentationViewModel
import com.pharma.evisualaid.ui.settings.SettingsViewModel

/** Manual DI: builds ViewModels from the application's [EVisualAidApp.container]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            val app = evApp()
            HomeViewModel(app, app.container.catalogRepository, app.container.settingsRepository)
        }
        initializer {
            val app = evApp()
            PresentationViewModel(
                app,
                app.container.catalogRepository,
                app.container.settingsRepository
            )
        }
        initializer {
            val app = evApp()
            SettingsViewModel(
                app,
                app.container.settingsRepository,
                app.container.catalogRepository
            )
        }
    }

    private fun CreationExtras.evApp(): EVisualAidApp =
        (this[APPLICATION_KEY] as EVisualAidApp)
}
