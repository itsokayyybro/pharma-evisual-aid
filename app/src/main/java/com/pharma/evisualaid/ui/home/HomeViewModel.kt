package com.pharma.evisualaid.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharma.evisualaid.data.model.Catalog
import com.pharma.evisualaid.data.model.SortOrder
import com.pharma.evisualaid.data.prefs.SettingsRepository
import com.pharma.evisualaid.data.repository.CatalogRepository
import com.pharma.evisualaid.data.repository.ImportError
import com.pharma.evisualaid.data.repository.ImportResult
import com.pharma.evisualaid.util.FileUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val FILTER_ALL = "All"
const val FILTER_RECENT = "Recent"

data class HomeUiState(
    val isLoading: Boolean = true,
    val catalogs: List<Catalog> = emptyList(),
    val availableFilters: List<String> = emptyList(),
    val activeFilter: String = FILTER_ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val companyName: String = "",
    val companyLogoUri: String? = null,
    val accentArgb: Long = 0xFF00BCD4
) {
    val isEmpty: Boolean get() = !isLoading && catalogs.isEmpty() &&
        searchQuery.isBlank() && activeFilter == FILTER_ALL
}

/** One-shot UI events (snackbars/toasts) that should not re-fire on recomposition. */
sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
    data class StorageFull(val freeSpace: String) : HomeEvent
}

private data class HomeControls(
    val activeFilter: String = FILTER_ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOverride: SortOrder? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet()
)

class HomeViewModel(
    private val app: Application,
    private val repository: CatalogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val controls = MutableStateFlow(HomeControls())

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeCatalogs(),
        settingsRepository.settings,
        controls
    ) { catalogs, settings, ctrl ->
        val sort = ctrl.sortOverride ?: settings.defaultSort
        val filtered = applyFilterSearchSort(catalogs, ctrl, sort)
        val discoveredTags = catalogs.flatMap { it.tags }
        val filters = buildList {
            add(FILTER_ALL)
            add(FILTER_RECENT)
            // Categories start empty; they come from tags on catalogs and the user-defined
            // categories managed in Settings (also added via the "Add Filter" chip).
            (discoveredTags + settings.categories).distinct()
                .sorted()
                .forEach { add(it) }
        }
        HomeUiState(
            isLoading = false,
            catalogs = filtered,
            availableFilters = filters,
            activeFilter = ctrl.activeFilter,
            searchQuery = ctrl.searchQuery,
            isSearchActive = ctrl.isSearchActive,
            sortOrder = sort,
            selectionMode = ctrl.selectionMode,
            selectedIds = ctrl.selectedIds,
            companyName = settings.companyName,
            companyLogoUri = settings.companyLogoUri,
            accentArgb = settings.accentColorArgb
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    // ---- Filtering / sorting ----

    private fun applyFilterSearchSort(
        all: List<Catalog>,
        ctrl: HomeControls,
        sort: SortOrder
    ): List<Catalog> {
        val now = System.currentTimeMillis()
        val recentWindow = 14L * 24 * 60 * 60 * 1000

        val byFilter = when (ctrl.activeFilter) {
            FILTER_ALL -> all
            FILTER_RECENT -> all.filter { (it.lastOpenedAt ?: it.uploadedAt) >= now - recentWindow }
            else -> all.filter { c -> c.tags.any { it.equals(ctrl.activeFilter, ignoreCase = true) } }
        }

        val query = ctrl.searchQuery.trim()
        val bySearch = if (query.isEmpty()) byFilter else byFilter.filter { c ->
            fuzzyMatches(query, c.displayName) ||
                c.tags.any { fuzzyMatches(query, it) }
        }

        return when (sort) {
            SortOrder.NAME_ASC -> bySearch.sortedBy { it.displayName.lowercase() }
            SortOrder.DATE_NEWEST -> bySearch.sortedByDescending { it.uploadedAt }
            SortOrder.DATE_OLDEST -> bySearch.sortedBy { it.uploadedAt }
            SortOrder.PAGE_COUNT -> bySearch.sortedByDescending { it.pageCount }
        }
    }

    /** Case-insensitive substring OR in-order subsequence match (lightweight fuzzy). */
    private fun fuzzyMatches(query: String, target: String): Boolean {
        val q = query.lowercase()
        val t = target.lowercase()
        if (t.contains(q)) return true
        var qi = 0
        for (ch in t) {
            if (qi < q.length && ch == q[qi]) qi++
            if (qi == q.length) return true
        }
        return q.isEmpty()
    }

    // ---- Controls ----

    fun setFilter(filter: String) = controls.update { it.copy(activeFilter = filter) }

    fun addCustomFilter(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        // Persist the new category so it survives restarts and shows up in Settings too.
        viewModelScope.launch {
            val current = settingsRepository.settings.first().categories
            if (current.none { it.equals(clean, ignoreCase = true) }) {
                settingsRepository.setCategories(current + clean)
            }
            controls.update { it.copy(activeFilter = clean) }
        }
    }

    fun setSearchQuery(q: String) = controls.update { it.copy(searchQuery = q) }

    fun toggleSearch() = controls.update {
        if (it.isSearchActive) it.copy(isSearchActive = false, searchQuery = "")
        else it.copy(isSearchActive = true)
    }

    fun setSort(order: SortOrder) = controls.update { it.copy(sortOverride = order) }

    // ---- Selection ----

    fun enterSelection(id: String) = controls.update {
        it.copy(selectionMode = true, selectedIds = setOf(id))
    }

    fun toggleSelect(id: String) = controls.update {
        val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
        if (next.isEmpty()) it.copy(selectionMode = false, selectedIds = emptySet())
        else it.copy(selectedIds = next)
    }

    fun clearSelection() = controls.update {
        it.copy(selectionMode = false, selectedIds = emptySet())
    }

    fun deleteSelected() {
        val ids = controls.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteByIds(ids)
            clearSelection()
            events.send(HomeEvent.Message("Deleted ${ids.size} catalog(s)"))
        }
    }

    // ---- CRUD ----

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            val name = FileUtils.queryDisplayName(app, uri)
            when (val result = repository.importPdf(uri, name)) {
                is ImportResult.Success ->
                    events.send(HomeEvent.Message("Catalog uploaded successfully"))
                is ImportResult.Failure -> when (result.reason) {
                    ImportError.CORRUPT -> events.send(
                        HomeEvent.Message("This file could not be opened. It may be corrupted or unsupported.")
                    )
                    ImportError.STORAGE_FULL -> events.send(
                        HomeEvent.StorageFull(FileUtils.humanReadableBytes(FileUtils.freeSpaceBytes(app)))
                    )
                    ImportError.UNKNOWN -> events.send(HomeEvent.Message("Upload failed. Please try again."))
                }
            }
        }
    }

    fun deleteCatalog(catalog: Catalog) {
        viewModelScope.launch {
            repository.delete(catalog)
            events.send(HomeEvent.Message("Deleted '${catalog.displayName}'"))
        }
    }

    /**
     * Validates and applies a rename. Returns null on success or an error message to show.
     * Rules: non-empty, ≤ 60 chars, only letters/digits/space/hyphen/parentheses.
     */
    fun rename(id: String, newName: String): String? {
        val trimmed = newName.trim()
        val error = when {
            trimmed.isEmpty() -> "Name cannot be empty"
            trimmed.length > 60 -> "Name must be 60 characters or fewer"
            !trimmed.matches(Regex("^[A-Za-z0-9 ()\\-]+$")) ->
                "Only letters, numbers, spaces, hyphens and parentheses are allowed"
            else -> null
        }
        if (error == null) {
            viewModelScope.launch { repository.rename(id, trimmed) }
        }
        return error
    }

    fun addTag(catalog: Catalog, tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.updateTags(catalog.id, (catalog.tags + clean).distinct())
        }
    }

    fun removeTag(catalog: Catalog, tag: String) {
        viewModelScope.launch {
            repository.updateTags(catalog.id, catalog.tags - tag)
        }
    }
}
