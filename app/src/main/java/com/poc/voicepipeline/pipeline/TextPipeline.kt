// pipeline/TextPipeline.kt
package com.poc.voicepipeline.pipeline

import com.poc.voicepipeline.network.GroqApiService
import com.poc.voicepipeline.network.GroqMessage
import com.poc.voicepipeline.network.GroqRequest
import com.poc.voicepipeline.speech.SpeechSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core pipeline that:
 * 1. Collects real-time speech segments
 * 2. Builds formatted text with ambiguous word markers
 * 3. Sends to Groq LLM for intelligent refinement
 */
class TextPipeline(
    private val groqApi: GroqApiService
) {
    private val segments = mutableListOf<SpeechSegment>()

    private val _rawPipelineText = MutableStateFlow("")
    val rawPipelineText: StateFlow<String> = _rawPipelineText.asStateFlow()

    /**
     * Add a finalized speech segment to the pipeline buffer
     */
    fun addSegment(segment: SpeechSegment) {
        segments.add(segment)
        rebuildRawText()
    }

    /**
     * Clear all buffered segments
     */
    fun clear() {
        segments.clear()
        _rawPipelineText.value = ""
    }

    /**
     * Get the current formatted text with ambiguity markers
     */
    private fun rebuildRawText() {
        _rawPipelineText.value = segments.joinToString(" ") { it.toFormattedString() }
    }

    /**
     * Get the formatted pipeline text ready for LLM
     */
    fun getFormattedTextForLLM(): String {
        return segments.joinToString(" ") { it.toFormattedString() }
    }

    /**
     * Send the accumulated text to Groq for refinement.
     * Returns the refined text.
     */
    suspend fun refineWithLLM(): RefinementResult {
        val inputText = getFormattedTextForLLM()

        if (inputText.isBlank()) {
            return RefinementResult(
                success = false,
                originalText = inputText,
                refinedText = "",
                error = "No text to refine"
            )
        }

        val systemPrompt = """
You are a text refinement assistant. You receive speech-to-text output that may contain errors.

YOUR TASK:
1. Fix punctuation, capitalization, and formatting
2. For words marked with alternatives like: word [word1, word2, word3] — choose the BEST fitting word based on context
3. Fix grammatical errors introduced by speech recognition
4. Maintain the speaker's original intent and meaning
5. Do NOT add, remove, or change the meaning of the content
6. Output ONLY the refined text — no explanations, no prefixes, no quotes

EXAMPLE INPUT:
"i went to the store [store, stare, shore] and bought some flower [flower, flour, floor] for baking"

EXAMPLE OUTPUT:
I went to the store and bought some flour for baking.

RULES:
- When you see [option1, option2, option3], pick the most contextually appropriate word
- Add proper punctuation (periods, commas, question marks, etc.)
- Fix capitalization (start of sentences, proper nouns)
- Keep the text natural and fluent
- Output ONLY the final refined text
""".trimIndent()

        return try {
            val startTime = System.currentTimeMillis()

            val request = GroqRequest(
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = inputText)
                )
            )

            val response = groqApi.chatCompletion(request = request)
            val endTime = System.currentTimeMillis()

            val refinedText = response.choices.firstOrNull()?.message?.content ?: ""

            RefinementResult(
                success = true,
                originalText = inputText,
                refinedText = refinedText.trim(),
                latencyMs = endTime - startTime,
                tokensUsed = response.usage?.totalTokens ?: 0,
                groqProcessingTimeMs = ((response.usage?.totalTime ?: 0.0) * 1000).toLong()
            )
        } catch (e: Exception) {
            RefinementResult(
                success = false,
                originalText = inputText,
                refinedText = "",
                error = e.message ?: "Unknown error"
            )
        }
    }
}

data class RefinementResult(
    val success: Boolean,
    val originalText: String,
    val refinedText: String,
    val error: String? = null,
    val latencyMs: Long = 0,
    val tokensUsed: Int = 0,
    val groqProcessingTimeMs: Long = 0
)