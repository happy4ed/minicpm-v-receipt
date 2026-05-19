package com.example.minicpm_v_demo

import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Wraps the ML Kit GenAI Prompt API (Gemini Nano via AICore).
 * No model download required — Gemini Nano is pre-installed on supported
 * devices (S25 Ultra, Pixel 8+, etc.) via the Android AICore system service.
 *
 * Lifecycle: create one instance and reuse it. Call [checkStatus] once on
 * app startup to decide whether to use this engine or fall back to LlamaEngine.
 */
class GeminiNanoEngine {

    private val client = Generation.getClient()

    suspend fun checkStatus(): FeatureStatus = client.checkStatus()

    /** Downloads the Gemini Nano config (~few MB). Only needed when status == DOWNLOADABLE. */
    suspend fun prepareIfNeeded() {
        if (client.checkStatus() == FeatureStatus.DOWNLOADABLE) {
            client.download().collect { }
        }
    }

    /**
     * Runs inference and emits text chunks as a [Flow<String>].
     * Compatible with the same streaming pattern used for LlamaEngine.
     */
    fun generate(prompt: String): Flow<String> = flow {
        client.generateContentStream(
            generateContentRequest(TextPart(prompt)) {
                temperature = 0.2f
                topK = 10
                candidateCount = 1
                maxOutputTokens = 256
            }
        ).collect { response ->
            response.candidates.firstOrNull()?.text?.let { if (it.isNotEmpty()) emit(it) }
        }
    }
}
