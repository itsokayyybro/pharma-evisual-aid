package com.pharma.evisualaid.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * Extracts a short display name for every page from the PDF's text layer (via PdfBox), for the
 * capsule navigation panel. The name is the first "meaningful" line of the page — non-empty,
 * 2–30 chars, not a bare page number — which is almost always the product/section heading.
 *
 * Image-only / outlined-text pages (common in CorelDRAW exports) yield no text, so those fall
 * back to "Page N". Runs once off the main thread; the panel only ever reads the result.
 */
object PdfTextExtractor {

    private val WHITESPACE = Regex("\\s+")

    /** One name per page (size == [pageCount]); always filled — never throws. */
    fun extractPageNames(file: File, pageCount: Int): List<String> {
        val names = MutableList(pageCount) { "Page ${it + 1}" }
        runCatching {
            val perPage = HashMap<Int, StringBuilder>() // 1-based page → accumulated text
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    perPage.getOrPut(currentPageNo) { StringBuilder() }.append(text).append('\n')
                    super.writeString(text, textPositions)
                }
            }.apply {
                startPage = 1
                endPage = pageCount
                sortByPosition = true
            }

            PDDocument.load(file).use { doc -> stripper.getText(doc) }

            for (page in 1..pageCount) {
                perPage[page]?.toString()?.let { firstMeaningfulLine(it) }?.let { names[page - 1] = it }
            }
        }
        return names
    }

    private fun firstMeaningfulLine(text: String): String? =
        text.lineSequence()
            .map { it.replace(WHITESPACE, " ").trim() }
            .firstOrNull { line ->
                line.length in 2..30 &&
                    line.any { it.isLetter() } &&
                    !line.all { it.isDigit() }
            }
}
