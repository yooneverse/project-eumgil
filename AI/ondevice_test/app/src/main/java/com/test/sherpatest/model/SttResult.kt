package com.test.sherpatest.model

data class SttResult(
    val text: String,
    val totalTimeMs: Long,
    val vadTimeMs: Long,
    val sttTimeMs: Long,
    val audioLengthMs: Long,
    val rtf: Float
)
