package com.example.llmtest

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.llmtest.databinding.ActivityVisuallyBinding
import com.example.llmtest.network.VoiceApiClient
import com.example.llmtest.network.models.VoiceAnalyzeRequest
import com.example.llmtest.stt.AudioRecorder
import com.example.llmtest.stt.SherpaManager
import com.example.llmtest.stt.SttManager
import com.example.llmtest.stt.VadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class VisuallyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisuallyBinding
    private lateinit var tts: TextToSpeech

    private val audioRecorder = AudioRecorder(this)
    private var vadManager: VadManager? = null
    private var sttManager: SttManager? = null
    private var recordingJob: Job? = null

    private val conversationHistory = mutableListOf<Map<String, String>>()
    private val resultLog = StringBuilder()

    companion object {
        private const val TAG = "VisuallyActivity"
        private const val MIC_PERMISSION_REQUEST = 100
        private const val COLOR_YELLOW = "#FFC107"
        private const val COLOR_RED = "#F44336"
        private const val UTTERANCE_CONFIRMATION = "confirmation"
        private const val SILENCE_FRAMES_FOR_STOP = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisuallyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTts()
        initSttModels()

        binding.btnMic.setOnClickListener {
            if (!hasMicPermission()) {
                requestMicPermission()
                return@setOnClickListener
            }
            if (recordingJob?.isActive == true) {
                stopRecording()
            } else {
                resetSession()
                startRecording()
            }
        }
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.KOREAN
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == UTTERANCE_CONFIRMATION) {
                    runOnUiThread {
                        binding.tvStatus.text = "말씀해 주세요"
                        startRecording()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    private fun initSttModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SherpaManager.ensureModelsExtracted(this@VisuallyActivity)
                if (!SherpaManager.modelsExist(this@VisuallyActivity)) {
                    Log.e(TAG, "STT 모델 파일 없음")
                    return@launch
                }
                vadManager = VadManager(this@VisuallyActivity)
                sttManager = SttManager.getInstance(this@VisuallyActivity)
                Log.d(TAG, "STT 모델 초기화 완료")
            } catch (e: Exception) {
                Log.e(TAG, "STT 초기화 실패: ${e.message}", e)
            }
        }
    }

    private fun resetSession() {
        conversationHistory.clear()
        resultLog.clear()
        binding.tvResult.text = ""
    }

    private fun startRecording() {
        if (vadManager == null || sttManager == null) {
            Toast.makeText(this, "STT 모델 초기화 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        setMicColor(COLOR_RED)
        binding.tvStatus.text = "녹음 중..."

        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            runPipeline()
        }
    }

    private fun stopRecording() {
        audioRecorder.stop()
        recordingJob?.cancel()
        setMicColor(COLOR_YELLOW)
        binding.tvStatus.text = "마이크를 눌러 말씀하세요"
    }

    private suspend fun runPipeline() {
        try {
            var voiceDetectedEver = false
            var silenceFrameCount = 0
            val accumulatedSamples = mutableListOf<Float>()
            var skipStt = false

            audioRecorder.startRecording().collect { floatSamples ->
                vadManager?.acceptWaveform(floatSamples)

                var hadSegment = false
                while (vadManager?.isEmpty() == false) {
                    val segment = vadManager?.front() ?: break
                    vadManager?.popSegment()
                    accumulatedSamples.addAll(segment.samples.toList())
                    hadSegment = true
                }

                val currentlySpeaking = vadManager?.isSpeechDetected() ?: false

                when {
                    hadSegment -> {
                        voiceDetectedEver = true
                        silenceFrameCount = 0
                    }
                    currentlySpeaking -> {
                        voiceDetectedEver = true
                        silenceFrameCount = 0
                    }
                    else -> silenceFrameCount++
                }

                when {
                    voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP -> {
                        audioRecorder.stop()
                    }
                    !voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP * 2 -> {
                        skipStt = true
                        audioRecorder.stop()
                    }
                }
            }

            vadManager?.flush()
            while (vadManager?.isEmpty() == false) {
                val segment = vadManager?.front() ?: break
                vadManager?.popSegment()
                accumulatedSamples.addAll(segment.samples.toList())
            }

            withContext(Dispatchers.Main) {
                setMicColor(COLOR_YELLOW)
                binding.tvStatus.text = "음성 인식 중..."
            }

            if (!skipStt && voiceDetectedEver && accumulatedSamples.isNotEmpty()) {
                val text = sttManager?.recognize(accumulatedSamples.toFloatArray()).orEmpty()
                if (text.isBlank()) {
                    withContext(Dispatchers.Main) { binding.tvStatus.text = "음성을 인식하지 못했습니다." }
                } else {
                    withContext(Dispatchers.Main) { binding.tvStatus.text = "분석 중..." }
                    callAnalyzeApi(text)
                }
            } else {
                withContext(Dispatchers.Main) { binding.tvStatus.text = "마이크를 눌러 말씀하세요" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT 파이프라인 오류: ${e.message}", e)
            withContext(Dispatchers.Main) {
                setMicColor(COLOR_YELLOW)
                binding.tvStatus.text = "오류가 발생했습니다. 다시 시도해주세요."
            }
        }
    }

    private suspend fun callAnalyzeApi(text: String) {
        val historySnapshot = conversationHistory.toList()
        try {
            val response = VoiceApiClient.service.analyze(
                VoiceAnalyzeRequest(
                    text = text,
                    model = "gemini",
                    mode = "LOW_VISION",
                    history = historySnapshot
                )
            )

            conversationHistory.add(mapOf("role" to "user", "content" to text))
            val assistantContent = buildString {
                append("{\"intent\":\"${response.intent}\"")
                append(",\"placeName\":${if (response.placeName != null) "\"${response.placeName}\"" else "null"}")
                append(",\"confirmed\":${response.confirmed ?: "null"}")
                append(",\"confirmationMessage\":${if (response.confirmationMessage != null) "\"${response.confirmationMessage}\"" else "null"}")
                append("}")
            }
            conversationHistory.add(mapOf("role" to "assistant", "content" to assistantContent))

            withContext(Dispatchers.Main) {
                if (response.confirmed == true) {
                    resultLog.appendLine("[완료] 장소명: ${response.placeName ?: "-"} / confirmed: true")
                    binding.tvResult.text = resultLog.toString().trimEnd()
                    binding.tvStatus.text = "완료"
                    speakOut("${response.placeName}을 검색합니다", "result")
                } else {
                    val msg = response.confirmationMessage ?: "찾으시는 장소를 말씀해 주세요"
                    resultLog.appendLine("[${conversationHistory.size / 2}턴] intent: ${response.intent} / 장소명: ${response.placeName ?: "-"}")
                    resultLog.appendLine("[TTS] \"$msg\"")
                    binding.tvResult.text = resultLog.toString().trimEnd()
                    if (response.intent == "UNKNOWN") {
                        conversationHistory.clear()
                    }
                    speakOut(msg, UTTERANCE_CONFIRMATION)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                binding.tvStatus.text = "오류가 발생했습니다. 다시 시도해주세요."
            }
        }
    }

    private fun speakOut(text: String, utteranceId: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun setMicColor(hex: String) {
        runOnUiThread {
            binding.btnMic.backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stop()
        recordingJob?.cancel()
        vadManager?.release()
        tts.shutdown()
    }
}
