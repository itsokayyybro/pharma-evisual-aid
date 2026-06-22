package com.pharma.evisualaid.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharma.evisualaid.data.repository.CatalogRepository
import com.pharma.evisualaid.util.AiDescriber
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

data class ViewerState(
    val isLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val filePath: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val initialPage: Int = 0,
    val isDescribing: Boolean = false,
    val currentDescription: String? = null,
    val statusMessage: String? = null
)

class ViewerViewModel(
    app: Application,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val appContext = app.applicationContext
    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private var document: PdfDocument? = null
    private val pageCache = BitmapCache()
    private val thumbCache = BitmapCache(THUMB_CACHE_BYTES)

    private var catalogId: String? = null
    private var viewportWidthPx = DEFAULT_RENDER_WIDTH
    private var viewportHeightPx = DEFAULT_RENDER_HEIGHT
    private var viewportLocked = false
    private var backgroundJob: Job? = null

    fun open(catalogId: String) {
        if (this.catalogId == catalogId && _state.value.isLoaded) return
        this.catalogId = catalogId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadFailed = false) }
            val catalog = catalogRepository.getCatalog(catalogId)
            val file = catalog?.filePath?.let(::File)
            if (catalog == null || file == null || !file.exists()) {
                _state.update { it.copy(isLoaded = false, isLoading = false, loadFailed = true) }
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
            _state.update { it.copy(isLoaded = false, isLoading = false, loadFailed = true, filePath = null) }
            return
        }
        document?.close()
        document = doc
        pageCache.clear()
        thumbCache.clear()

        val total = doc.pageCount
        val start = resumePage.coerceIn(0, (total - 1).coerceAtLeast(0))
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

    private fun startBackgroundRender(total: Int) {
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch(Dispatchers.Default) {
            for (i in 0 until total) {
                if (!isActive) break
                val doc = document ?: break
                if (pageFile(i).exists()) continue
                runCatching { doc.renderPageToSize(i, viewportWidthPx, viewportHeightPx) }.getOrNull()?.let { bmp ->
                    runCatching { FileUtils.saveBitmapAsJpeg(bmp, pageFile(i)) }
                }
            }
        }
    }

    fun setViewportSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && !viewportLocked) {
            viewportLocked = true
            viewportWidthPx = width
            viewportHeightPx = height
        }
    }

    suspend fun getPage(page: Int): Bitmap? {
        pageCache.get(page)?.let { return it }
        val doc = document ?: return null
        return withContext(Dispatchers.IO) {
            pageCache.get(page)?.let { return@withContext it }
            val file = pageFile(page)
            val bitmap = (if (file.exists()) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            } else null)
                ?: runCatching { doc.renderPageToSize(page, viewportWidthPx, viewportHeightPx) }.getOrNull()
                    ?.also { runCatching { FileUtils.saveBitmapAsJpeg(it, file) } }
            bitmap?.also { pageCache.put(page, it) }
        }
    }

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
        _state.update { it.copy(currentPage = page, currentDescription = null, statusMessage = null) }
        catalogId?.let { id -> viewModelScope.launch { catalogRepository.recordOpened(id, page) } }
        val total = _state.value.totalPages
        listOf(page - 1, page + 1)
            .filter { it in 0 until total }
            .forEach { neighbour -> viewModelScope.launch { getPage(neighbour) } }
    }

    fun describeCurrentPage(bitmap: Bitmap) {
        if (_state.value.isDescribing) return
        _state.update { it.copy(isDescribing = true, statusMessage = "AI is thinking...") }
        
        viewModelScope.launch {
            try {
                val desc = AiDescriber.describePage(bitmap)
                _state.update { it.copy(isDescribing = false, currentDescription = desc, statusMessage = null) }
            } catch (e: Exception) {
                Log.e("ViewerVM", "AI Error", e)
                _state.update { it.copy(isDescribing = false, statusMessage = "AI Error: ${e.localizedMessage}") }
            }
        }
    }

    fun clearDescription() {
        _state.update { it.copy(currentDescription = null, statusMessage = null) }
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
        private const val DEFAULT_RENDER_HEIGHT = 1080
        private const val THUMB_WIDTH = 240
        private const val THUMB_CACHE_BYTES = 24 * 1024 * 1024
    }
}
