package com.pharma.evisualaid.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * Derives a one-word title for each page by reading the PDF's text layer (via PdfBox) and
 * picking the word drawn at the largest font size — i.e. the page's visual heading, which is
 * usually the product / section name a presenter would navigate by.
 *
 * Pages with no extractable text (image-only / outlined-text exports such as CorelDRAW PDFs)
 * simply get no entry; the UI falls back to the page number.
 */
object PdfTitleExtractor {

    private val WHITESPACE = Regex("\\s+")
    // Words too generic to be useful as a navigation label.
    private val STOP_WORDS = setOf(
        "THE", "AND", "FOR", "WITH", "FROM", "PAGE", "OF", "IN", "ON", "TO", "A", "AN"
    )

    /**
     * Returns a map of 0-based page index → one-word UPPERCASE title for every page that has
     * usable heading text. Never throws — returns whatever it could read (possibly empty).
     */
    fun extractTitles(file: File, pageCount: Int): Map<Int, String> = runCatching {
        // page (1-based) → (largest font size seen, word from that run)
        val best = HashMap<Int, Pair<Float, String>>()

        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                if (textPositions.isNotEmpty()) {
                    val maxSize = textPositions.maxOf { it.fontSizeInPt }
                    val word = firstUsableWord(text)
                    if (word != null) {
                        val page = currentPageNo
                        val existing = best[page]
                        if (existing == null || maxSize > existing.first) {
                            best[page] = maxSize to word
                        }
                    }
                }
                super.writeString(text, textPositions)
            }
        }.apply {
            startPage = 1
            endPage = pageCount
            sortByPosition = true
        }

        PDDocument.load(file).use { doc ->
            stripper.getText(doc) // drives the writeString callbacks above
        }

        best.entries.associate { (page1Based, value) -> (page1Based - 1) to value.second }
    }.getOrDefault(emptyMap())

    /** First letter-bearing token in [text], cleaned to letters/digits, UPPERCASE, non-stop-word. */
    private fun firstUsableWord(text: String): String? {
        for (token in text.trim().split(WHITESPACE)) {
            val cleaned = token.filter { it.isLetterOrDigit() }.uppercase()
            if (cleaned.length >= 2 && cleaned.any { it.isLetter() } && cleaned !in STOP_WORDS) {
                return cleaned
            }
        }
        return null
    }
}
