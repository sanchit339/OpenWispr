// speech/SpeechResult.kt
package com.poc.voicepipeline.speech

/**
 * Represents a single recognized segment from STT.
 * For high-confidence words, alternatives will have 1 entry.
 * For low-confidence words, alternatives will have up to 3 entries.
 */
data class WordResult(
    val alternatives: List<String>,
    val confidence: Float,
    val isAmbiguous: Boolean
) {
    /**
     * Formats this word for the pipeline.
     * High confidence: "hello"
     * Low confidence:  "hello [hello, halo, hullo]"
     */
    fun toFormattedString(): String {
        return if (isAmbiguous && alternatives.size > 1) {
            "${alternatives.first()} [${alternatives.joinToString(", ")}]"
        } else {
            alternatives.first()
        }
    }
}

data class SpeechSegment(
    val words: List<WordResult>,
    val rawText: String,
    val isFinal: Boolean
) {
    fun toFormattedString(): String {
        return words.joinToString(" ") { it.toFormattedString() }
    }
}