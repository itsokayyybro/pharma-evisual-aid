package com.pharma.evisualaid

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.pharma.evisualaid.data.db.AppDatabase
import com.pharma.evisualaid.data.prefs.SettingsRepository
import com.pharma.evisualaid.data.repository.CatalogRepository

/**
 * Lightweight manual-DI container. Built once at startup and read by the ViewModel factory.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    val catalogRepository: CatalogRepository =
        CatalogRepository(context.applicationContext, database.catalogDao())
    val settingsRepository: SettingsRepository =
        SettingsRepository(context.applicationContext)
}

class EVisualAidApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // On-demand WorkManager initialization (default initializer disabled in the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
