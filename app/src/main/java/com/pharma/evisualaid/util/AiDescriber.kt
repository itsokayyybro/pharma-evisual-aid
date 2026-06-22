package com.pharma.evisualaid.util

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object AiDescriber {

    // IMPORTANT: Replace this with your actual Gemini API Key!
    private const val API_KEY = "AQ.Ab8RN6LDez1wppE6w3xviIMpvRHqS-KW3ZaqqydEOq9SrU1ctw"

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = API_KEY
        )
    }

    private const val PROMPT = """
        Analyze this pharmaceutical visual aid page. 
        Generate a concise (max 3 sentences) marketing summary for a medical representative. 
        Use plain text only. No special characters or markdown.
    """

    suspend fun describePage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        Log.d("AiDescriber", "Request started")
        
        if (API_KEY == "YOUR_GEMINI_API_KEY" || API_KEY.isBlank()) {
            return@withContext "Configuration Error: Please add your real Gemini API Key in the AiDescriber file."
        }

        // Fast downscale for upload efficiency
        val scaled = try {
            val max = 512
            val ratio = max.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            if (ratio < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap
        } catch (e: Throwable) { bitmap }

        return@withContext try {
            val response = withTimeoutOrNull(30000) {
                model.generateContent(content {
                    image(scaled)
                    text(PROMPT)
                })
            }
            response?.text ?: "The AI is currently unavailable or returned no text. Please try again."
        } catch (e: Throwable) {
            Log.e("AiDescriber", "Request failed", e)
            "Network Error: ${e.localizedMessage ?: "Please check your internet connection."}"
        }
    }
}
