package com.pharma.evisualaid.data.model

/**
 * Domain representation of a catalog, exposed to the UI layer.
 *
 * Differs from [com.pharma.evisualaid.data.db.CatalogEntity] only in that [tags] is a
 * parsed list rather than a JSON string. The repository maps between the two.
 */
data class Catalog(
    val id: String,
    val originalFileName: String,
    val displayName: String,
    val filePath: String,
    val pageCount: Int,
    val thumbnailPath: String?,
    val uploadedAt: Long,
    val tags: List<String>,
    val lastOpenedAt: Long?,
    val lastOpenedPage: Int?
) {
    /** True until the background thumbnail worker has produced a cached first-page image. */
    val isThumbnailReady: Boolean get() = thumbnailPath != null
}

/** Sort options offered on the Home screen and as a default in Settings. */
enum class SortOrder(val label: String) {
    NAME_ASC("Name A–Z"),
    DATE_NEWEST("Date Newest"),
    DATE_OLDEST("Date Oldest"),
    PAGE_COUNT("Page Count");

    companion object {
        fun fromName(name: String?): SortOrder =
            entries.firstOrNull { it.name == name } ?: DATE_NEWEST
    }
}
