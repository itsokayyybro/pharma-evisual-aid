package com.pharma.evisualaid.util

import android.graphics.Bitmap
import android.util.LruCache

/**
 * A small [LruCache] of rendered pages, keyed by page index. Sized to a fraction of the
 * app's available heap so the pre-render window (current page ± 2) stays resident without
 * risking OOM on large catalogs.
 */
class BitmapCache(maxBytes: Int = defaultMaxBytes()) {

    private val cache = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    fun get(page: Int): Bitmap? = cache.get(page)

    fun put(page: Int, bitmap: Bitmap) {
        if (cache.get(page) == null) cache.put(page, bitmap)
    }

    fun contains(page: Int): Boolean = cache.get(page) != null

    /** Evicts pages outside the keep window to bound memory while scrubbing/jumping. */
    fun trimTo(keep: IntRange) {
        cache.snapshot().keys
            .filter { it !in keep }
            .forEach { cache.remove(it) }
    }

    fun clear() = cache.evictAll()

    companion object {
        fun defaultMaxBytes(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            // Use ~1/4 of heap for the page cache.
            return (maxKb / 4) * 1024
        }
    }
}
