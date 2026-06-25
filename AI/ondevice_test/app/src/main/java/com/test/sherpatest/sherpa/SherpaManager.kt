package com.test.sherpatest.sherpa

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object SherpaManager {

    private const val TAG = "SherpaManager"

    // assets → 내부 저장소 복사 후 절대경로 반환
    fun getModelDir(context: Context): String {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        return modelDir.absolutePath
    }

    fun ensureModelsExtracted(context: Context) {
        val modelDir = File(context.filesDir, "models")

        val senseVoiceDir = File(modelDir, "sense_voice")
        if (!senseVoiceDir.exists()) senseVoiceDir.mkdirs()

        val vadDir = File(modelDir, "vad")
        if (!vadDir.exists()) vadDir.mkdirs()

        copyAssetIfNeeded(context, "models/sense_voice/model.int8.onnx",
            File(senseVoiceDir, "model.int8.onnx"))
        copyAssetIfNeeded(context, "models/sense_voice/tokens.txt",
            File(senseVoiceDir, "tokens.txt"))
        copyAssetIfNeeded(context, "models/vad/silero_vad.onnx",
            File(vadDir, "silero_vad.onnx"))
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, dest: File) {
        if (dest.exists() && dest.length() > 0) {
            Log.d(TAG, "Already exists: ${dest.absolutePath}")
            return
        }
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied: $assetPath → ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $assetPath: ${e.message}")
        }
    }

    fun senseVoiceModelPath(context: Context): String =
        File(context.filesDir, "models/sense_voice/model.int8.onnx").absolutePath

    fun tokensPath(context: Context): String =
        File(context.filesDir, "models/sense_voice/tokens.txt").absolutePath

    fun vadModelPath(context: Context): String =
        File(context.filesDir, "models/vad/silero_vad.onnx").absolutePath

    fun modelsExist(context: Context): Boolean {
        return File(context.filesDir, "models/sense_voice/model.int8.onnx").exists() &&
               File(context.filesDir, "models/sense_voice/tokens.txt").exists() &&
               File(context.filesDir, "models/vad/silero_vad.onnx").exists()
    }
}
