package com.pharma.evisualaid.ui.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharma.evisualaid.data.prefs.SettingsRepository
import com.pharma.evisualaid.data.repository.CatalogRepository
import com.pharma.evisualaid.util.BitmapCache
import com.pharma.evisualaid.util.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PresentationUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val displayName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    /** Non-null while the "Resume from page N?" prompt should be shown. */
    val resumePage: Int? = null,
    val accentArgb: Long = 0xFF00BCD4,
    val autoHideDelayMs: Long = 3_000L
)

/**
 * Owns the open [PdfDocument], the page [BitmapCache] and the pre-render window (current
 * page ± [PREFETCH_RADIUS]). All rendering happens on [Dispatchers.IO]; bitmaps are released
 * when the ViewModel is cleared.
 */
class PresentationViewModel(
    app: Application,
    private val repository: CatalogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val appContext = app.applicationContext

    private val _uiState = MutableStateFlow(PresentationUiState())
    val uiState: StateFlow<PresentationUiState> = _uiState.asStateFlow()

    private var document: PdfDocument? = null
    private val pageCache = BitmapCache()
    private val thumbCache = BitmapCache(maxBytes = BitmapCache.defaultMaxBytes() / 4)

    private var viewportWidthPx: Int = 1080
    private var loaded = false
    private var catalogId: String? = null
    // The pager auto-settles on page 0 once at startup; skip persisting that so a saved
    // resume position isn't clobbered before the user has actually navigated.
    private var firstSettle = true

    fun load(catalogId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val catalog = repository.getCatalog(catalogId)
            if (catalog == null) {
                _uiState.update { it.copy(isLoading = false, error = "Catalog not found.") }
                return@launch
            }
            val doc = withContext(Dispatchers.IO) {
                runCatching { PdfDocument.open(File(catalog.filePath)) }.getOrNull()
            }
            if (doc == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "This file could not be opened. It may be corrupted or unsupported."
                    )
                }
                return@launch
            }
            document = doc
            val resume = catalog.lastOpenedPage?.takeIf { it in 1 until doc.pageCount }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    displayName = catalog.displayName,
                    pageCount = doc.pageCount,
                    currentPage = 0,
                    resumePage = resume,
                    accentArgb = settings.accentColorArgb,
                    autoHideDelayMs = settings.autoHideDelayMs
                )
            }
            // catalogId retained for last-opened persistence.
            this@PresentationViewModel.catalogId = catalogId
        }
    }

    fun setViewportWidth(px: Int) {
        if (px > 0 && px != viewportWidthPx) {
            viewportWidthPx = px
            pageCache.clear()
        }
    }

    /** Returns a full-resolution bitmap for [page], rendering + caching on demand. */
    suspend fun getPage(page: Int): Bitmap? {
        pageCache.get(page)?.let { return it }
        val doc = document ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { doc.renderPageByWidth(page, viewportWidthPx) }
                .getOrNull()
                ?.also { pageCache.put(page, it) }
        }
    }

    /** Small thumbnail for the side index panel and scrubber preview. */
    suspend fun getThumbnail(page: Int): Bitmap? {
        thumbCache.get(page)?.let { return it }
        val doc = document ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { doc.renderPageByWidth(page, THUMB_WIDTH_PX) }
                .getOrNull()
                ?.also { thumbCache.put(page, it) }
        }
    }

    /** Called when the pager settles on [page]: persist position, prefetch neighbors, trim. */
    fun onPageSettled(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
        if (firstSettle) {
            firstSettle = false
        } else {
            catalogId?.let { id -> viewModelScope.launch { repository.recordOpened(id, page) } }
        }

        val pageCount = _uiState.value.pageCount
        val window = (page - PREFETCH_RADIUS)..(page + PREFETCH_RADIUS)
        pageCache.trimTo(window.first.coerceAtLeast(0)..window.last.coerceAtMost(pageCount - 1))
        for (i in window) {
            if (i in 0 until pageCount && !pageCache.contains(i)) {
                viewModelScope.launch { getPage(i) }
            }
        }
    }

    fun dismissResume() = _uiState.update { it.copy(resumePage = null) }

    override fun onCleared() {
        super.onCleared()
        document?.close()
        document = null
        pageCache.clear()
        thumbCache.clear()
    }

    companion object {
        private const val PREFETCH_RADIUS = 2
        private const val THUMB_WIDTH_PX = 240
    }
}
