package com.example.llmtest.utils

import android.util.Log

object PerformanceLogger {

    private const val TAG = "PerformanceLogger"

    data class TimeLog(
        val startTime: Long,
        val phase: String
    )

    private val logs = mutableMapOf<String, TimeLog>()

    fun start(id: String, phase: String) {
        val startTime = System.currentTimeMillis()
        logs[id] = TimeLog(startTime, phase)
        Log.d(TAG, "[$phase] Started - ID: $id")
    }

    fun log(id: String, message: String) {
        val timeLog = logs[id] ?: return
        val elapsed = System.currentTimeMillis() - timeLog.startTime
        Log.d(TAG, "[${timeLog.phase}] $message - Elapsed: ${elapsed}ms")
    }

    fun end(id: String): Long {
        val timeLog = logs[id] ?: return 0L
        val elapsed = System.currentTimeMillis() - timeLog.startTime
        Log.d(TAG, "[${timeLog.phase}] Completed - Total time: ${elapsed}ms (${elapsed / 1000f}s)")
        logs.remove(id)
        return elapsed
    }

    fun logStep(phase: String, step: String, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "[$phase] $step - ${elapsed}ms")
    }
}
