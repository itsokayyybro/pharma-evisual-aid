package com.pharma.evisualaid.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pharma.evisualaid.EVisualAidApp
import com.pharma.evisualaid.util.FileUtils
import com.pharma.evisualaid.util.PdfUtils
import java.io.File

/**
 * Renders the first page of an uploaded PDF into a cached JPEG thumbnail, off the main
 * thread, then writes the thumbnail path back to the catalog row. Runs after upload so the
 * UI stays responsive even for large (100+ page) PDFs.
 */
class ThumbnailWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_CATALOG_ID) ?: return Result.failure()
        val pdfPath = inputData.getString(KEY_PDF_PATH) ?: return Result.failure()
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) return Result.failure()

        val repository = (applicationContext as EVisualAidApp).container.catalogRepository

        return try {
            val bitmap = PdfUtils.renderThumbnail(pdfFile, THUMB_MAX_W_PX, THUMB_MAX_H_PX)
            val target = FileUtils.thumbnailFileFor(applicationContext, id)
            FileUtils.saveBitmapAsJpeg(bitmap, target)
            bitmap.recycle()
            repository.setThumbnailPath(id, target.absolutePath)
            Result.success()
        } catch (t: Throwable) {
            // Transient I/O issues get one retry; a corrupt PDF will fail again and stop.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "thumbnail_worker"
        const val KEY_CATALOG_ID = "catalog_id"
        const val KEY_PDF_PATH = "pdf_path"

        // ~400x300dp at xhdpi-ish density; generous so cards look crisp on tablets.
        private const val THUMB_MAX_W_PX = 720
        private const val THUMB_MAX_H_PX = 1000
    }
}
