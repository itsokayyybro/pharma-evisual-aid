package com.pharma.evisualaid.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** App-private file-system helpers: copy, delete, free-space, thumbnail save, export zip. */
object FileUtils {

    fun catalogsDir(context: Context): File =
        File(context.filesDir, "catalogs").apply { mkdirs() }

    fun thumbnailsDir(context: Context): File =
        File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    /** Copies the picked document into files/catalogs/ as "<id>.pdf". */
    @Throws(IOException::class)
    fun copyUriToCatalogs(context: Context, uri: Uri, id: String): File {
        val target = File(catalogsDir(context), "$id.pdf")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open input stream for $uri" }
            FileOutputStream(target).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
                output.fd.sync()
            }
        }
        return target
    }

    /** Resolves the human-readable display name of a SAF document uri. */
    fun queryDisplayName(context: Context, uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
        }
        return name ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "Catalog.pdf")
    }

    @Throws(IOException::class)
    fun saveBitmapAsJpeg(bitmap: Bitmap, target: File, quality: Int = 85): File {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.fd.sync()
        }
        return target
    }

    fun thumbnailFileFor(context: Context, id: String): File =
        File(thumbnailsDir(context), "$id.jpg")

    /** Free space (bytes) on the volume backing app storage. */
    fun freeSpaceBytes(context: Context): Long = context.filesDir.usableSpace

    fun deleteQuietly(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
    }

    /** Removes every catalog + thumbnail file (used by "Clear All Data"). */
    fun clearCatalogStorage(context: Context) {
        catalogsDir(context).listFiles()?.forEach { it.delete() }
        thumbnailsDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Zips all stored catalog PDFs into cache/exports/ for the Android share sheet.
     * Returns the zip file, or null if there is nothing to export.
     */
    @Throws(IOException::class)
    fun exportAllCatalogs(context: Context, zipName: String): File? {
        val pdfs = catalogsDir(context).listFiles { f -> f.extension.equals("pdf", true) }
            ?.toList().orEmpty()
        if (pdfs.isEmpty()) return null

        val zip = File(exportsDir(context), zipName)
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            pdfs.forEach { pdf ->
                zos.putNextEntry(ZipEntry(pdf.name))
                pdf.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zip
    }

    fun humanReadableBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = -1
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return String.format("%.1f %s", value, units[i])
    }
}
