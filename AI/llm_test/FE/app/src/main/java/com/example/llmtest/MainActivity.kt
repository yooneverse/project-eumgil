package com.example.llmtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.llmtest.audio.TTSManager
import com.example.llmtest.compare.CompareActivity
import com.example.llmtest.databinding.ActivityMainBinding
import com.example.llmtest.network.RetrofitClient
import com.example.llmtest.network.models.CompareResult
import com.example.llmtest.network.models.LLMRequest
import com.example.llmtest.stt.AudioRecorder
import com.example.llmtest.stt.SherpaManager
import com.example.llmtest.stt.SttManager
import com.example.llmtest.stt.VadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ttsManager: TTSManager

    private val audioRecorder = AudioRecorder(this)
    private var vadManager: VadManager? = null
    private var sttManager: SttManager? = null
    private var recordingJob: Job? = null
    private var sttStartMs = 0L

    private val modelMap = mapOf(
        "Gemini 2.5 Flash" to "gemini",
        "Claude Haiku 4.5" to "claude",
        "GPT-5 mini"       to "gpt_mini"
    )

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_CODE = 101
        private const val SILENCE_FRAMES_FOR_STOP = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkMicPermission()
        setupTTS()
        setupListeners()
        initSttModels()
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE &&
            (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun initSttModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SherpaManager.ensureModelsExtracted(this@MainActivity)
                if (!SherpaManager.modelsExist(this@MainActivity)) {
                    Log.e(TAG, "STT 모델 파일 없음")
                    return@launch
                }
                vadManager = VadManager(this@MainActivity)
                sttManager = SttManager.getInstance(this@MainActivity)
                Log.d(TAG, "STT 모델 초기화 완료")
            } catch (e: Exception) {
                Log.e(TAG, "STT 초기화 실패: ${e.message}", e)
            }
        }
    }

    private fun setupTTS() {
        ttsManager = TTSManager(this) { success ->
            if (!success) runOnUiThread {
                Toast.makeText(this, "TTS 초기화 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.btnRecord.setOnClickListener {
            if (recordingJob?.isActive == true) stopRecording() else startRecording()
        }
        binding.btnOpenCompare.setOnClickListener {
            startActivity(Intent(this, CompareActivity::class.java))
        }
        binding.btnStartNavigation.setOnClickListener {
            Toast.makeText(this, "길찾기 기능은 아직 구현되지 않았습니다", Toast.LENGTH_SHORT).show()
        }
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (vadManager == null || sttManager == null) {
            Toast.makeText(this, "STT 모델 초기화 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRecord.text = "⏹️\n녹음 중단"
        binding.tvStatus.text = "말씀하세요..."
        binding.cardResult.visibility = View.GONE
        binding.btnStartNavigation.visibility = View.GONE
        sttStartMs = System.currentTimeMillis()

        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            runPipeline()
        }
    }

    private fun stopRecording() {
        audioRecorder.stop()
        recordingJob?.cancel()
        runOnUiThread {
            binding.btnRecord.text = "🎙️\n녹음 시작"
            binding.tvStatus.text = ""
        }
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
                binding.btnRecord.text = "🎙️\n녹음 시작"
            }

            if (!skipStt && voiceDetectedEver && accumulatedSamples.isNotEmpty()) {
                withContext(Dispatchers.Main) { binding.tvStatus.text = "변환 중..." }
                val text = sttManager?.recognize(accumulatedSamples.toFloatArray()).orEmpty()
                if (text.isBlank()) {
                    withContext(Dispatchers.Main) { binding.tvStatus.text = "음성을 인식하지 못했습니다." }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvRecognizedText.text = "\"$text\""
                        binding.tvStatus.text = "LLM 분석 중..."
                    }
                    sendToLLM(text)
                }
            } else {
                withContext(Dispatchers.Main) { binding.tvStatus.text = "발화가 감지되지 않았습니다." }
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT 파이프라인 오류: ${e.message}", e)
            withContext(Dispatchers.Main) {
                binding.btnRecord.text = "🎙️\n녹음 시작"
                binding.tvStatus.text = "오류: ${e.message}"
                showLoading(false)
            }
        }
    }

    private suspend fun sendToLLM(text: String) {
        val selectedModel = binding.spinnerModel.selectedItem.toString()
        val modelId = modelMap[selectedModel] ?: "gemini"

        withContext(Dispatchers.Main) { showLoading(true) }
        try {
            val result = RetrofitClient.apiService.chatWithLLM(
                LLMRequest(text = text, model = modelId, stt_start_ms = sttStartMs)
            )
            withContext(Dispatchers.Main) { displayResult(result) }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 요청 실패", e)
            withContext(Dispatchers.Main) {
                showLoading(false)
                binding.tvStatus.text = "❌ 서버 오류: ${e.message}"
            }
        }
    }

    private fun displayResult(result: CompareResult) {
        showLoading(false)
        binding.cardResult.visibility = View.VISIBLE

        val providerLabel = mapOf(
            "gemini"   to "Gemini 2.5 Flash",
            "claude"   to "Claude Haiku 4.5",
            "gpt_mini" to "GPT-5 mini"
        )[result.provider] ?: result.provider

        binding.tvResultProvider.text = if (result.success) "✅ $providerLabel" else "❌ $providerLabel"
        binding.tvResultDeparture.text = "출발지: ${result.departure ?: "없음"}"
        binding.tvResultDestination.text = "도착지: ${result.destination ?: "없음"}"
        binding.tvResultLatency.text =
            "전체: ${result.total_latency_ms.toLong()}ms  |  LLM: ${result.llm_latency_ms.toLong()}ms"
        binding.tvResultCost.text =
            "크레딧: ${"%.4f".format(result.cost_credit)}  |  토큰: ${result.input_tokens}+${result.output_tokens}"

        binding.tvStatus.text = if (result.success) "✅ 분석 완료" else "❌ ${result.error ?: "분석 실패"}"

        if (result.success && result.intent == "navigation" &&
            (result.departure != null || result.destination != null)) {
            binding.btnStartNavigation.visibility = View.VISIBLE
        }

        result.confirmation_message?.let { ttsManager.speak(it) }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRecord.isEnabled = !show
    }

    override fun onDestroy() {
        audioRecorder.stop()
        recordingJob?.cancel()
        vadManager?.release()
        ttsManager.shutdown()
        super.onDestroy()
    }
}
