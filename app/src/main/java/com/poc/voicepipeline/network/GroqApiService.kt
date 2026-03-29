// network/GroqApiService.kt
package com.poc.voicepipeline.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.poc.voicepipeline.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface GroqApiService {

    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: GroqRequest
    ): GroqResponse

    companion object {
        private const val BASE_URL = "https://api.groq.com/"

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun create(): GroqApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Auth interceptor — adds Bearer token to every request
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(
                    json.asConverterFactory("application/json".toMediaType())
                )
                .build()
                .create(GroqApiService::class.java)
        }
    }
}
