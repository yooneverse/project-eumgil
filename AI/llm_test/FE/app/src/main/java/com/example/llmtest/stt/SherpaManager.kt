package com.example.llmtest.stt

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

internal object SherpaManager {

    private const val TAG = "SherpaManager"

    fun ensureModelsExtracted(context: Context) {
        val modelDir = File(context.filesDir, "models")
        val senseVoiceDir = File(modelDir, "sense_voice").also { it.mkdirs() }
        val vadDir = File(modelDir, "vad").also { it.mkdirs() }

        copyAssetIfNeeded(context, "models/sense_voice/model.int8.onnx", File(senseVoiceDir, "model.int8.onnx"))
        copyAssetIfNeeded(context, "models/sense_voice/tokens.txt", File(senseVoiceDir, "tokens.txt"))
        copyAssetIfNeeded(context, "models/vad/silero_vad.onnx", File(vadDir, "silero_vad.onnx"))
    }

    fun modelsExist(context: Context): Boolean =
        File(context.filesDir, "models/sense_voice/model.int8.onnx").exists() &&
            File(context.filesDir, "models/sense_voice/tokens.txt").exists() &&
            File(context.filesDir, "models/vad/silero_vad.onnx").exists()

    fun senseVoiceModelPath(context: Context): String =
        File(context.filesDir, "models/sense_voice/model.int8.onnx").absolutePath

    fun tokensPath(context: Context): String =
        File(context.filesDir, "models/sense_voice/tokens.txt").absolutePath

    fun vadModelPath(context: Context): String =
        File(context.filesDir, "models/vad/silero_vad.onnx").absolutePath

    private fun copyAssetIfNeeded(context: Context, assetPath: String, dest: File) {
        if (dest.exists() && dest.length() > 0) {
            Log.d(TAG, "Already exists: ${dest.absolutePath}")
            return
        }
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Copied: $assetPath → ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $assetPath: ${e.message}")
        }
    }
}
