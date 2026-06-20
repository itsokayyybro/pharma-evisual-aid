package com.pharma.evisualaid.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharma.evisualaid.data.prefs.SettingsRepository
import com.pharma.evisualaid.util.BitmapCache
import com.pharma.evisualaid.util.FileUtils
import com.pharma.evisualaid.util.PdfDocument
import com.pharma.evisualaid.util.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UI state for the single immersive viewer.
 *
 * Rendered page bitmaps are intentionally *not* held in this immutable state (that would
 * thrash recomposition on every render). Instead the screen pulls them on demand through the
 * suspend [getPage] accessor, backed by an in-memory [BitmapCache] plus an on-disk JPEG cache.
 */
data class ViewerState(
    /** false → no PDF loaded yet; the screen shows only the centered "+" button. */
    val isLoaded: Boolean = false,
    val filePath: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    /** true while pages 0–2 are being rendered after a fresh load. */
    val isLoading: Boolean = false,
    /** One-shot flag: a brand-new catalog was just loaded (drives the snackbar). */
    val justLoadedNew: Boolean = false,
    /** One short display name per page (size == [totalPages]); used by the capsule panel. */
    val pageNames: List<String> = emptyList()
)

/**
 * Owns the open [PdfDocument] and the page-rendering pipeline for the immersive viewer:
 *
 *  - pages 0–2 render synchronously on load (blocks behind a spinner),
 *  - the rest stream to disk in the background ([Dispatchers.Default]),
 *  - recent full-res pages stay in an in-memory [BitmapCache] (bounded to ~¼ heap so mid-range
 *    tablets don't OOM — safer than a hard count of 20 full-resolution bitmaps),
 *  - everything else lives as JPEG under cacheDir/pages/ and is decoded on demand.
 *
 * The single loaded PDF is remembered across launches via [SettingsRepository.currentPdfPath].
 */
class ViewerViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val appContext = app.applicationContext

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private var document: PdfDocument? = null
    private val pageCache = BitmapCache()

    private var viewportWidthPx = DEFAULT_RENDER_WIDTH
    private var viewportLocked = false
    private var backgroundJob: Job? = null
    private var titlesJob: Job? = null

    init {
        // Restore the last loaded PDF, if any, so subsequent launches skip straight in.
        viewModelScope.launch {
            val path = settingsRepository.currentPdfPath.first()
            val file = path?.let(::File)
            if (file != null && file.exists()) {
                open(file, isNew = false)
            }
        }
    }

    /** Picked a new PDF: copy into private storage, replace the current catalog, persist path. */
    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            backgroundJob?.cancel()
            titlesJob?.cancel()
            // The current catalog always lives at the same path, so wipe its page cache first.
            withContext(Dispatchers.IO) { clearDiskPages() }
            val target = withContext(Dispatchers.IO) {
                runCatching { FileUtils.copyUriToCatalogs(appContext, uri, CURRENT_ID) }.getOrNull()
            }
            if (target == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            settingsRepository.setCurrentPdfPath(target.absolutePath)
            open(target, isNew = true)
        }
    }

    private suspend fun open(file: File, isNew: Boolean) {
        _state.update { it.copy(isLoading = true) }
        val doc = withContext(Dispatchers.IO) {
            runCatching { PdfDocument.open(file) }.getOrNull()
        }
        if (doc == null) {
            // Corrupt/unsupported: forget it so we fall back to the "+" button.
            settingsRepository.setCurrentPdfPath(null)
            _state.update { it.copy(isLoaded = false, isLoading = false, filePath = null) }
            return
        }
        document?.close()
        document = doc
        pageCache.clear()

        val total = doc.pageCount
        // Render the first few pages up front so the viewer can show immediately.
        withContext(Dispatchers.IO) {
            for (i in 0 until minOf(EAGER_PAGES, total)) getPage(i)
        }
        _state.update {
            it.copy(
                isLoaded = true,
                isLoading = false,
                filePath = file.absolutePath,
                totalPages = total,
                currentPage = 0,
                justLoadedNew = isNew,
                pageNames = emptyList()
            )
        }
        startBackgroundRender(total)
        startNameExtraction(file, total)
    }

    /** Reads the text layer off the main thread and publishes per-page capsule names. */
    private fun startNameExtraction(file: File, total: Int) {
        titlesJob?.cancel()
        titlesJob = viewModelScope.launch(Dispatchers.Default) {
            val names = runCatching { PdfTextExtractor.extractPageNames(file, total) }
                .getOrDefault(emptyList())
            if (names.isNotEmpty()) _state.update { it.copy(pageNames = names) }
        }
    }

    /** Streams the remaining pages to the on-disk JPEG cache without blocking the UI. */
    private fun startBackgroundRender(total: Int) {
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch(Dispatchers.Default) {
            for (i in EAGER_PAGES until total) {
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

    fun onPageSettled(page: Int) {
        _state.update { it.copy(currentPage = page) }
        // Keep the immediate neighbours warm so a page-curl never waits on I/O.
        val total = _state.value.totalPages
        listOf(page - 1, page + 1)
            .filter { it in 0 until total }
            .forEach { neighbour -> viewModelScope.launch { getPage(neighbour) } }
    }

    /** Acknowledge the "New catalog loaded" snackbar so it doesn't re-show on recomposition. */
    fun consumeNewLoadedEvent() = _state.update { it.copy(justLoadedNew = false) }

    private fun pagesDir(): File = File(appContext.cacheDir, "pages").apply { mkdirs() }
    private fun pageFile(page: Int): File = File(pagesDir(), "$page.jpg")
    private fun clearDiskPages() {
        pagesDir().listFiles()?.forEach { it.delete() }
    }

    override fun onCleared() {
        super.onCleared()
        backgroundJob?.cancel()
        titlesJob?.cancel()
        document?.close()
        document = null
        pageCache.clear()
    }

    companion object {
        private const val CURRENT_ID = "current"
        private const val EAGER_PAGES = 3
        private const val DEFAULT_RENDER_WIDTH = 1920
    }
}
