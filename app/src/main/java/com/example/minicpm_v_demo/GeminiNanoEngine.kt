package com.example.minicpm_v_demo

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Wraps the ML Kit GenAI Prompt API (Gemini Nano via AICore).
 * No model download required — pre-installed on S25 Ultra, Pixel 8+, etc.
 */
class GeminiNanoEngine {

    private val client = Generation.getClient()

    suspend fun checkStatus(): Int {
        return try {
            client.checkStatus()
        } catch (e: Exception) {
            Log.e(TAG, "checkStatus failed", e)
            FeatureStatus.UNAVAILABLE
        }
    }

    suspend fun prepareIfNeeded() {
        try {
            if (client.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                client.download().collect { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepareIfNeeded failed", e)
        }
    }

    fun generate(prompt: String): Flow<String> = flow {
        client.generateContentStream(prompt).collect { response ->
            response.candidates.firstOrNull()?.text?.let { text ->
                if (text.isNotEmpty()) emit(text)
            }
        }
    }.catch { e ->
        Log.e(TAG, "generate stream error", e)
        emit("\n[오류] Gemini Nano 추론 실패: ${e.message}")
    }

    companion object {
        private const val TAG = "GeminiNanoEngine"
    }
}
