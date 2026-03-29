// viewmodel/VoiceViewModel.kt
package com.poc.voicepipeline.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poc.voicepipeline.network.GroqApiService
import com.poc.voicepipeline.pipeline.RefinementResult
import com.poc.voicepipeline.pipeline.TextPipeline
import com.poc.voicepipeline.speech.SpeechRecognizerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class UiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rawPipelineText: String = "",
    val refinedText: String = "",
    val isRefining: Boolean = false,
    val refinementResult: RefinementResult? = null,
    val error: String? = null,
    val segmentCount: Int = 0
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val speechManager = SpeechRecognizerManager(application.applicationContext)
    private val groqApi = GroqApiService.create()
    private val pipeline = TextPipeline(groqApi)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var segmentCount = 0

    init {
        // Observe speech manager flows
        speechManager.isListening
            .onEach { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
            .launchIn(viewModelScope)

        speechManager.partialResult
            .onEach { partial ->
                _uiState.value = _uiState.value.copy(partialText = partial)
            }
            .launchIn(viewModelScope)

        speechManager.finalSegment
            .onEach { segment ->
                segmentCount++
                pipeline.addSegment(segment)
                _uiState.value = _uiState.value.copy(
                    rawPipelineText = pipeline.rawPipelineText.value,
                    segmentCount = segmentCount
                )
            }
            .launchIn(viewModelScope)

        speechManager.error
            .onEach { error ->
                _uiState.value = _uiState.value.copy(error = error)
            }
            .launchIn(viewModelScope)

        // Observe pipeline text changes
        pipeline.rawPipelineText
            .onEach { text ->
                _uiState.value = _uiState.value.copy(rawPipelineText = text)
            }
            .launchIn(viewModelScope)
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            speechManager.stopListening()
        } else {
            // Reset for new session
            _uiState.value = _uiState.value.copy(
                error = null,
                refinedText = "",
                refinementResult = null
            )
            speechManager.startListening()
        }
    }

    /**
     * SEND button — stops listening and triggers LLM refinement
     */
    fun sendForRefinement() {
        speechManager.stopListening()
        _uiState.value = _uiState.value.copy(
            isRefining = true,
            error = null
        )

        viewModelScope.launch {
            val result = pipeline.refineWithLLM()
            _uiState.value = _uiState.value.copy(
                isRefining = false,
                refinedText = result.refinedText,
                refinementResult = result,
                error = if (!result.success) result.error else null
            )
        }
    }

    /**
     * Clear everything for a fresh start
     */
    fun clearAll() {
        speechManager.stopListening()
        pipeline.clear()
        segmentCount = 0
        _uiState.value = UiState()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}