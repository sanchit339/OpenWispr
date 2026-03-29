// speech/SpeechRecognizerManager.kt
package com.poc.voicepipeline.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognizerManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecManager"
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val MAX_ALTERNATIVES = 3
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _finalSegment = MutableSharedFlow<SpeechSegment>(extraBufferCapacity = 10)
    val finalSegment: SharedFlow<SpeechSegment> = _finalSegment.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private var shouldRestart = false

    fun startListening() {
        shouldRestart = true
        initAndStart()
    }

    fun stopListening() {
        shouldRestart = false
        _isListening.value = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun initAndStart() {
        // Destroy previous instance
        speechRecognizer?.destroy()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.tryEmit("Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_ALTERNATIVES)
            // Request confidence scores
            putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
            // Keep listening — longer speech input
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }

        _isListening.value = true
        speechRecognizer?.startListening(intent)
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech begun")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could use for UI audio level visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error: $error"
            }
            Log.e(TAG, "Error: $errorMsg")

            // For no-match or timeout, auto-restart if we should be listening
            if (shouldRestart && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                initAndStart()
            } else if (error != SpeechRecognizer.ERROR_CLIENT) {
                _error.tryEmit(errorMsg)
                if (shouldRestart) {
                    // Try to restart after a brief pause
                    initAndStart()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            results?.let { bundle ->
                val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (!matches.isNullOrEmpty()) {
                    val segment = processResults(matches, confidences)
                    _finalSegment.tryEmit(segment)
                    _partialResult.value = ""
                    Log.d(TAG, "Final: ${segment.toFormattedString()}")
                }
            }

            // Auto-restart for continuous listening
            if (shouldRestart) {
                initAndStart()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.let { bundle ->
                val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _partialResult.value = matches[0]
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Core logic: Process STT results and identify low-confidence words.
     * 
     * Strategy:
     * - We get top N alternative SENTENCES from SpeechRecognizer
     * - We compare word-by-word across alternatives
     * - Where alternatives differ at same position = low confidence word
     * - We bundle those as [word1, word2, word3]
     */
    private fun processResults(
        alternatives: List<String>,
        confidences: FloatArray?
    ): SpeechSegment {
        val primaryText = alternatives[0]
        val primaryWords = primaryText.split("\\s+".toRegex())

        // Get alternative sentences split into words
        val altWordLists = alternatives.map { it.split("\\s+".toRegex()) }

        val wordResults = primaryWords.mapIndexed { index, primaryWord ->
            // Collect alternative words at this position
            val wordAlternatives = mutableSetOf(primaryWord.lowercase())
            altWordLists.forEach { altWords ->
                if (index < altWords.size) {
                    wordAlternatives.add(altWords[index].lowercase())
                }
            }

            val uniqueAlternatives = wordAlternatives.toList().take(MAX_ALTERNATIVES)

            // Determine confidence
            val overallConfidence = confidences?.getOrNull(0) ?: 1.0f
            val isAmbiguous = uniqueAlternatives.size > 1 ||
                    (confidences != null && overallConfidence < CONFIDENCE_THRESHOLD)

            WordResult(
                alternatives = if (isAmbiguous) {
                    // Put the primary word first, then others
                    listOf(primaryWord) + uniqueAlternatives
                        .filter { it != primaryWord.lowercase() }
                        .map { it.replaceFirstChar { c -> c.uppercaseChar() } }
                } else {
                    listOf(primaryWord)
                },
                confidence = overallConfidence,
                isAmbiguous = isAmbiguous
            )
        }

        return SpeechSegment(
            words = wordResults,
            rawText = primaryText,
            isFinal = true
        )
    }

    fun destroy() {
        shouldRestart = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}