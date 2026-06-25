package com.test.sherpatest.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeasurementRecord(
    val id: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val referenceText: String,
    val sttText: String,
    val totalTimeMs: Long,
    val vadTimeMs: Long,
    val sttTimeMs: Long,
    val audioLengthMs: Long,
    val rtf: Float,
    val wer: Float
) {
    val timestampStr: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
