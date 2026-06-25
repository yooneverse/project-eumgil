package com.example.llmtest.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val TAG = "AudioRecorder"
    }

    fun startRecording(): File? {
        try {
            val timestamp = System.currentTimeMillis()
            outputFile = File(context.cacheDir, "recording_$timestamp.m4a")

            Log.d(TAG, "Starting recording to: ${outputFile?.absolutePath}")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile?.absolutePath)

                prepare()
                start()
            }

            Log.d(TAG, "Recording started successfully")
            return outputFile

        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            return null
        }
    }

    fun stopRecording(): File? {
        try {
            Log.d(TAG, "Stopping recording...")

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val file = outputFile
            Log.d(TAG, "Recording stopped. File size: ${file?.length() ?: 0} bytes")

            return file

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            return null
        }
    }

    fun isRecording(): Boolean {
        return mediaRecorder != null
    }
}
