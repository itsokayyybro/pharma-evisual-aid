package com.pharma.evisualaid.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharma.evisualaid.data.repository.CatalogRepository
import com.pharma.evisualaid.util.BitmapCache
import com.pharma.evisualaid.util.FileUtils
import com.pharma.evisualaid.util.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UI state for the immersive viewer of a single catalog opened from the dashboard.
 *
 * Rendered page bitmaps are intentionally *not* held in this immutable state (that would
 * thrash recomposition on every render). Instead the screen pulls them on demand through the
 * suspend [getPage] accessor, backed by an in-memory [BitmapCache] plus an on-disk JPEG cache.
 */
data class ViewerState(
    /** false → nothing loaded yet (still resolving the catalog) or the catalog is missing. */
    val isLoaded: Boolean = false,
    /** true while the catalog is being resolved/opened and the first pages are rendering. */
    val isLoading: Boolean = true,
    /** Set when the catalog could not be opened (deleted file / corrupt PDF). */
    val loadFailed: Boolean = false,
    val filePath: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    /** Page to resume on (last opened page); the screen jumps here once on open. */
    val initialPage: Int = 0
)

/**
 * Owns the open [PdfDocument] and the page-rendering pipeline for the immersive viewer:
 *
 *  - pages 0–2 render synchronously on load (blocks behind a spinner),
 *  - the rest stream to disk in the background ([Dispatchers.Default]),
 *  - recent full-res pages stay in an in-memory [BitmapCache] (bounded to ~¼ heap so mid-range
 *    tablets don't OOM),
 *  - everything else lives as JPEG under cacheDir/pages/<catalogId>/ and is decoded on demand.
 *
 * The catalog to display is selected on the dashboard and passed in via [open].
 */
class ViewerViewModel(
    app: Application,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val appContext = app.applicationContext

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private var document: PdfDocument? = null
    private val pageCache = BitmapCache()
    // Small, separate cache for the navigation-rail page previews (see [getThumbnail]).
    private val thumbCache = BitmapCache(THUMB_CACHE_BYTES)

    private var catalogId: String? = null
    private var viewportWidthPx = DEFAULT_RENDER_WIDTH
    private var viewportLocked = false
    private var backgroundJob: Job? = null

    /** Resolve [catalogId] from the database and open its PDF. Idempotent per catalog. */
    fun open(catalogId: String) {
        if (this.catalogId == catalogId && _state.value.isLoaded) return
        this.catalogId = catalogId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadFailed = false) }
            val catalog = catalogRepository.getCatalog(catalogId)
            val file = catalog?.filePath?.let(::File)
            if (catalog == null || file == null || !file.exists()) {
                _state.update {
                    it.copy(isLoaded = false, isLoading = false, loadFailed = true)
                }
                return@launch
            }
            open(file, catalog.lastOpenedPage ?: 0)
        }
    }

    private suspend fun open(file: File, resumePage: Int) {
        backgroundJob?.cancel()
        _state.update { it.copy(isLoading = true) }
        val doc = withContext(Dispatchers.IO) {
            runCatching { PdfDocument.open(file) }.getOrNull()
        }
        if (doc == null) {
            _state.update {
                it.copy(isLoaded = false, isLoading = false, loadFailed = true, filePath = null)
            }
            return
        }
        document?.close()
        document = doc
        pageCache.clear()
        thumbCache.clear()

        val total = doc.pageCount
        val start = resumePage.coerceIn(0, (total - 1).coerceAtLeast(0))
        // Render the pages around the resume point up front so the viewer can show immediately.
        withContext(Dispatchers.IO) {
            for (i in start until minOf(start + EAGER_PAGES, total)) getPage(i)
        }
        _state.update {
            it.copy(
                isLoaded = true,
                isLoading = false,
                loadFailed = false,
                filePath = file.absolutePath,
                totalPages = total,
                currentPage = start,
                initialPage = start
            )
        }
        startBackgroundRender(total)
    }

    /** Streams the remaining pages to the on-disk JPEG cache without blocking the UI. */
    private fun startBackgroundRender(total: Int) {
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch(Dispatchers.Default) {
            for (i in 0 until total) {
                if (!isActive) break
                val doc = document ?: break
                if (pageFile(i).exists()) continue
                runCatching { doc.renderPageByWidth(i, viewportWidthPx) }.getOrNull()?.let { bmp ->
                    runCatching { FileUtils.saveBitmapAsJpeg(bmp, pageFile(i)) }
                }
            }
        }
    }

    /** Locks the render width to the real screen width the first time the viewer is measured. */
    fun setViewportWidth(px: Int) {
        if (px > 0 && !viewportLocked) {
            viewportLocked = true
            viewportWidthPx = px
        }
    }

    /** Full-resolution bitmap for [page]: memory cache → disk JPEG → render (then cache both). */
    suspend fun getPage(page: Int): Bitmap? {
        pageCache.get(page)?.let { return it }
        val doc = document ?: return null
        return withContext(Dispatchers.IO) {
            pageCache.get(page)?.let { return@withContext it }
            val file = pageFile(page)
            val bitmap = (if (file.exists()) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            } else null)
                ?: runCatching { doc.renderPageByWidth(page, viewportWidthPx) }.getOrNull()
                    ?.also { runCatching { FileUtils.saveBitmapAsJpeg(it, file) } }
            bitmap?.also { pageCache.put(page, it) }
        }
    }

    /**
     * Small preview bitmap for [page], used by the navigation rail: memory cache → disk JPEG →
     * render at [THUMB_WIDTH] (then cache both). Kept separate from [getPage] so scrolling the
     * rail never evicts the full-resolution pages the curl is using.
     */
    suspend fun getThumbnail(page: Int): Bitmap? {
        thumbCache.get(page)?.let { return it }
        val doc = document ?: return null
        return withContext(Dispatchers.IO) {
            thumbCache.get(page)?.let { return@withContext it }
            val file = thumbFile(page)
            val bitmap = (if (file.exists()) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            } else null)
                ?: runCatching { doc.renderPageByWidth(page, THUMB_WIDTH) }.getOrNull()
                    ?.also { runCatching { FileUtils.saveBitmapAsJpeg(it, file) } }
            bitmap?.also { thumbCache.put(page, it) }
        }
    }

    fun onPageSettled(page: Int) {
        _state.update { it.copy(currentPage = page) }
        // Persist progress so the dashboard's "Recent" filter and resume-on-open stay accurate.
        catalogId?.let { id -> viewModelScope.launch { catalogRepository.recordOpened(id, page) } }
        // Keep the immediate neighbours warm so a page-curl never waits on I/O.
        val total = _state.value.totalPages
        listOf(page - 1, page + 1)
            .filter { it in 0 until total }
            .forEach { neighbour -> viewModelScope.launch { getPage(neighbour) } }
    }

    private fun pagesDir(): File =
        File(File(appContext.cacheDir, "pages"), catalogId ?: "current").apply { mkdirs() }

    private fun pageFile(page: Int): File = File(pagesDir(), "$page.jpg")

    private fun thumbsDir(): File =
        File(File(appContext.cacheDir, "thumbs"), catalogId ?: "current").apply { mkdirs() }

    private fun thumbFile(page: Int): File = File(thumbsDir(), "$page.jpg")

    override fun onCleared() {
        super.onCleared()
        backgroundJob?.cancel()
        document?.close()
        document = null
        pageCache.clear()
        thumbCache.clear()
    }

    companion object {
        private const val EAGER_PAGES = 3
        private const val DEFAULT_RENDER_WIDTH = 1920
        /** Render width for the navigation-rail previews — small, for a clean compact look. */
        private const val THUMB_WIDTH = 240
        /** ~24 MB ceiling for the in-memory preview cache (LRU-evicted beyond that). */
        private const val THUMB_CACHE_BYTES = 24 * 1024 * 1024
    }
}
