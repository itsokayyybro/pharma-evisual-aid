package com.pharma.evisualaid.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for an uploaded catalog.
 *
 * [thumbnailPath] is nullable: the page count is extracted synchronously on upload, but
 * the first-page thumbnail is rendered by [com.pharma.evisualaid.work.ThumbnailWorker] in
 * the background, so the path is filled in shortly after the row is inserted.
 */
@Entity(tableName = "catalogs")
data class CatalogEntity(
    @PrimaryKey val id: String,
    val originalFileName: String,
    val displayName: String,
    val filePath: String,
    val pageCount: Int,
    val thumbnailPath: String?,
    val uploadedAt: Long,
    val tags: String,            // JSON array of tag strings, e.g. ["Cardiology","GI"]
    val lastOpenedAt: Long?,
    val lastOpenedPage: Int?
)
