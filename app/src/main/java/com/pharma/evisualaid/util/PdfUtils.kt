package com.pharma.evisualaid.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Thin wrappers around the native [PdfRenderer]. Rendering is preferred over PdfBox for
 * performance (hardware-accelerated, no extra dependency).
 *
 * [PdfRenderer] is not thread-safe and only allows one open page at a time, so [PdfDocument]
 * guards all access with a lock and is intended to be owned by a single screen/ViewModel.
 */
object PdfUtils {

    /** Reads just the page count. Throws [IOException] if the file is corrupt/unsupported. */
    @Throws(IOException::class)
    fun getPageCount(file: File): Int = PdfDocument.open(file).use { it.pageCount }

    /**
     * Renders page 0 into a thumbnail bitmap that fits within [maxWidthPx] x [maxHeightPx]
     * while preserving aspect ratio.
     */
    @Throws(IOException::class)
    fun renderThumbnail(file: File, maxWidthPx: Int, maxHeightPx: Int): Bitmap =
        PdfDocument.open(file).use { doc ->
            doc.renderPageFitting(0, maxWidthPx, maxHeightPx)
        }
}

/**
 * A reusable, lock-guarded handle to an open PDF. Render pages repeatedly without paying
 * the open/close cost each time; remember to [close] it when the screen goes away.
 */
class PdfDocument private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    private val lock = Any()

    @Volatile
    private var closed = false

    val pageCount: Int get() = renderer.pageCount

    /**
     * Renders [index] to a bitmap exactly [targetWidthPx] wide, with height derived from the
     * page aspect ratio. White-filled first, since transparent PDF regions render clear.
     */
    fun renderPageByWidth(index: Int, targetWidthPx: Int): Bitmap = synchronized(lock) {
        check(!closed) { "PdfDocument already closed" }
        renderer.openPage(index).use { page ->
            val safeWidth = targetWidthPx.coerceAtLeast(1)
            val aspect = page.height.toFloat() / page.width.toFloat()
            val height = (safeWidth * aspect).toInt().coerceAtLeast(1)
            renderToBitmap(page, safeWidth, height)
        }
    }

    /**
     * Renders [index] to a bitmap exactly [width] x [height]. This may stretch the page if
     * the requested aspect ratio differs from the PDF page aspect ratio.
     */
    fun renderPageToSize(index: Int, width: Int, height: Int): Bitmap = synchronized(lock) {
        check(!closed) { "PdfDocument already closed" }
        renderer.openPage(index).use { page ->
            renderToBitmap(page, width.coerceAtLeast(1), height.coerceAtLeast(1))
        }
    }

    /** Renders [index] scaled to fit inside the given box, preserving aspect ratio. */
    fun renderPageFitting(index: Int, maxWidthPx: Int, maxHeightPx: Int): Bitmap =
        synchronized(lock) {
            check(!closed) { "PdfDocument already closed" }
            renderer.openPage(index).use { page ->
                val scale = minOf(
                    maxWidthPx.toFloat() / page.width,
                    maxHeightPx.toFloat() / page.height
                )
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                renderToBitmap(page, w, h)
            }
        }

    private fun renderToBitmap(page: PdfRenderer.Page, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        // null transform + null clip renders the whole page scaled to the bitmap bounds.
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    companion object {
        @Throws(IOException::class)
        fun open(file: File): PdfDocument {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfDocument(pfd, PdfRenderer(pfd))
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                if (t is IOException) throw t else throw IOException("Unable to open PDF", t)
            }
        }
    }
}
