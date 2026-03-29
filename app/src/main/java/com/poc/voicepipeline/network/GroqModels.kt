// network/GroqModels.kt
package com.poc.voicepipeline.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens")
    val maxTokens: Int = 2048,
    @SerialName("top_p")
    val topP: Double = 0.9
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqResponse(
    val id: String = "",
    val choices: List<GroqChoice> = emptyList(),
    val usage: GroqUsage? = null
)

@Serializable
data class GroqChoice(
    val index: Int = 0,
    val message: GroqMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class GroqUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
    @SerialName("prompt_time")
    val promptTime: Double = 0.0,
    @SerialName("completion_time")
    val completionTime: Double = 0.0,
    @SerialName("total_time")
    val totalTime: Double = 0.0
)