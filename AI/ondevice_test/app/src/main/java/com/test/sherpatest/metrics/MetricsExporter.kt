package com.test.sherpatest.metrics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.test.sherpatest.model.MeasurementRecord
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MetricsExporter {

    private const val TAG = "MetricsExporter"

    fun exportCsv(context: Context, records: List<MeasurementRecord>): String {
        val filename = "sherpa_metrics_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val csvContent = buildCsvContent(records)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, filename, csvContent)
        } else {
            saveLegacy(filename, csvContent)
        }
    }

    private fun buildCsvContent(records: List<MeasurementRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("id,timestamp,reference,stt_result,total_ms,vad_ms,stt_ms,audio_ms,rtf,wer")
        records.forEach { r ->
            sb.appendLine(
                "${r.id},${r.timestampStr},\"${r.referenceText.replace("\"", "\"\"")}\",\"${r.sttText.replace("\"", "\"\"\"\"")}\",${r.totalTimeMs},${r.vadTimeMs},${r.sttTimeMs},${r.audioLengthMs},${"%.4f".format(r.rtf)},${"%.4f".format(r.wer)}"
            )
        }
        return sb.toString()
    }

    private fun saveViaMediaStore(context: Context, filename: String, content: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return "저장 실패"
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        Log.d(TAG, "Saved to Downloads: $filename")
        return "Downloads/$filename"
    }

    private fun saveLegacy(filename: String, content: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, filename)
        FileWriter(file).use { it.write(content) }
        Log.d(TAG, "Saved: ${file.absolutePath}")
        return file.absolutePath
    }
}
