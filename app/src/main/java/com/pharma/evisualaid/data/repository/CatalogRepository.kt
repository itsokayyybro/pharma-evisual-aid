package com.pharma.evisualaid.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pharma.evisualaid.data.db.CatalogDao
import com.pharma.evisualaid.data.db.CatalogEntity
import com.pharma.evisualaid.data.model.Catalog
import com.pharma.evisualaid.util.FileUtils
import com.pharma.evisualaid.util.PdfUtils
import com.pharma.evisualaid.work.ThumbnailWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

/** Reason an import failed, mapped to a user-facing message in the UI layer. */
enum class ImportError { CORRUPT, STORAGE_FULL, UNKNOWN }

sealed interface ImportResult {
    data class Success(val catalog: Catalog) : ImportResult
    data class Failure(val reason: ImportError) : ImportResult
}

/**
 * Single source of truth for catalog data. Owns the upload pipeline (copy → page count →
 * insert → enqueue thumbnail), all CRUD operations, and the file-system side effects that
 * must accompany them.
 */
class CatalogRepository(
    private val appContext: Context,
    private val dao: CatalogDao
) {

    fun observeCatalogs(): Flow<List<Catalog>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCatalog(id: String): Flow<Catalog?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getCatalog(id: String): Catalog? = dao.getById(id)?.toDomain()

    /**
     * Imports a PDF selected via the Storage Access Framework:
     *  1. copy into app-private files/catalogs/ under a UUID filename,
     *  2. open with PdfRenderer to read the page count (also validates the file),
     *  3. insert the row, and
     *  4. enqueue a [ThumbnailWorker] to render the first-page thumbnail off the main thread.
     */
    suspend fun importPdf(uri: Uri, originalName: String): ImportResult {
        val id = UUID.randomUUID().toString()
        var copied: java.io.File? = null
        return try {
            copied = FileUtils.copyUriToCatalogs(appContext, uri, id)

            val pageCount = try {
                PdfUtils.getPageCount(copied)
            } catch (io: IOException) {
                FileUtils.deleteQuietly(copied)
                return ImportResult.Failure(ImportError.CORRUPT)
            } catch (t: Throwable) {
                FileUtils.deleteQuietly(copied)
                return ImportResult.Failure(ImportError.CORRUPT)
            }

            val entity = CatalogEntity(
                id = id,
                originalFileName = originalName,
                displayName = originalName.removeSuffix(".pdf").removeSuffix(".PDF"),
                filePath = copied.absolutePath,
                pageCount = pageCount,
                thumbnailPath = null,
                uploadedAt = System.currentTimeMillis(),
                tags = TagCodec.encode(emptyList()),
                lastOpenedAt = null,
                lastOpenedPage = null
            )
            dao.insert(entity)
            enqueueThumbnail(id, copied.absolutePath)
            ImportResult.Success(entity.toDomain())
        } catch (io: IOException) {
            copied?.let { FileUtils.deleteQuietly(it) }
            // ENOSPC and similar surface as IOException on copy.
            ImportResult.Failure(ImportError.STORAGE_FULL)
        } catch (t: Throwable) {
            copied?.let { FileUtils.deleteQuietly(it) }
            ImportResult.Failure(ImportError.UNKNOWN)
        }
    }

    private fun enqueueThumbnail(id: String, pdfPath: String) {
        val request = OneTimeWorkRequestBuilder<ThumbnailWorker>()
            .setInputData(
                workDataOf(
                    ThumbnailWorker.KEY_CATALOG_ID to id,
                    ThumbnailWorker.KEY_PDF_PATH to pdfPath
                )
            )
            .addTag(ThumbnailWorker.TAG)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork("thumb_$id", androidx.work.ExistingWorkPolicy.KEEP, request)
    }

    suspend fun setThumbnailPath(id: String, path: String) = dao.updateThumbnail(id, path)

    suspend fun rename(id: String, newDisplayName: String) =
        dao.updateDisplayName(id, newDisplayName)

    suspend fun updateTags(id: String, tags: List<String>) =
        dao.updateTags(id, TagCodec.encode(tags))

    suspend fun recordOpened(id: String, page: Int) =
        dao.updateLastOpened(id, System.currentTimeMillis(), page)

    suspend fun delete(catalog: Catalog) {
        FileUtils.deleteQuietly(java.io.File(catalog.filePath))
        catalog.thumbnailPath?.let { FileUtils.deleteQuietly(java.io.File(it)) }
        dao.getById(catalog.id)?.let { dao.delete(it) }
    }

    suspend fun deleteByIds(ids: List<String>) {
        ids.forEach { id ->
            dao.getById(id)?.let { e ->
                FileUtils.deleteQuietly(java.io.File(e.filePath))
                e.thumbnailPath?.let { FileUtils.deleteQuietly(java.io.File(it)) }
            }
        }
        dao.deleteByIds(ids)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        FileUtils.clearCatalogStorage(appContext)
    }

    private fun CatalogEntity.toDomain(): Catalog = Catalog(
        id = id,
        originalFileName = originalFileName,
        displayName = displayName,
        filePath = filePath,
        pageCount = pageCount,
        thumbnailPath = thumbnailPath,
        uploadedAt = uploadedAt,
        tags = TagCodec.decode(tags),
        lastOpenedAt = lastOpenedAt,
        lastOpenedPage = lastOpenedPage
    )
}
