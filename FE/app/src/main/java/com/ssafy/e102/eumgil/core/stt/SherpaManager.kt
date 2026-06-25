package com.ssafy.e102.eumgil.core.stt

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * sherpa-onnx 모델 파일을 assets → 내부 저장소(filesDir)로 추출·관리하는 싱글턴 유틸.
 *
 * 필요 모델 (STT/VAD):
 *   assets/models/sense_voice/model.int8.onnx
 *   assets/models/sense_voice/tokens.txt
 *   assets/models/vad/silero_vad.onnx
 *
 * 필요 모델 (KWS):
 *   assets/models/kws/encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx
 *   assets/models/kws/decoder-epoch-13-avg-2-chunk-16-left-64.onnx
 *   assets/models/kws/joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx
 *   assets/models/kws/tokens.txt
 *   assets/models/kws/keywords.txt
 */
internal object SherpaManager {

    private const val TAG = "SherpaManager"

    // ── STT / VAD ─────────────────────────────────────────────────────────────

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

    // ── KWS ───────────────────────────────────────────────────────────────────

    fun ensureKwsModelsExtracted(context: Context) {
        val kwsDir = File(context.filesDir, "models/kws").also { it.mkdirs() }

        copyAssetIfNeeded(
            context,
            "models/kws/encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx",
            File(kwsDir, "encoder.int8.onnx"),
        )
        copyAssetIfNeeded(
            context,
            "models/kws/decoder-epoch-13-avg-2-chunk-16-left-64.onnx",
            File(kwsDir, "decoder.onnx"),
        )
        copyAssetIfNeeded(
            context,
            "models/kws/joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx",
            File(kwsDir, "joiner.int8.onnx"),
        )
        copyAssetIfNeeded(context, "models/kws/tokens.txt", File(kwsDir, "tokens.txt"))
        copyAssetIfNeeded(context, "models/kws/keywords.txt", File(kwsDir, "keywords.txt"))
    }

    fun kwsModelsExist(context: Context): Boolean {
        val base = File(context.filesDir, "models/kws")
        return File(base, "encoder.int8.onnx").exists() &&
            File(base, "decoder.onnx").exists() &&
            File(base, "joiner.int8.onnx").exists() &&
            File(base, "tokens.txt").exists() &&
            File(base, "keywords.txt").exists()
    }

    fun kwsEncoderPath(context: Context): String =
        File(context.filesDir, "models/kws/encoder.int8.onnx").absolutePath

    fun kwsDecoderPath(context: Context): String =
        File(context.filesDir, "models/kws/decoder.onnx").absolutePath

    fun kwsJoinerPath(context: Context): String =
        File(context.filesDir, "models/kws/joiner.int8.onnx").absolutePath

    fun kwsTokensPath(context: Context): String =
        File(context.filesDir, "models/kws/tokens.txt").absolutePath

    fun kwsKeywordsPath(context: Context): String =
        File(context.filesDir, "models/kws/keywords.txt").absolutePath

    // ── 공통 ──────────────────────────────────────────────────────────────────

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
