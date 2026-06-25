package com.test.sherpatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.test.sherpatest.MainViewModel
import com.test.sherpatest.audio.NoiseCancelMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMode by viewModel.noiseCancelMode.collectAsState()
    val focusManager = LocalFocusManager.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 상태 / 초기화 배너
            when {
                !uiState.modelsReady -> {
                    StatusBanner("모델 파일을 assets에 배치해주세요.", Color(0xFFF57C00))
                }
                uiState.isInitializing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("  모델 초기화 중...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    StatusBanner("모델 준비 완료", Color(0xFF388E3C))
                }
            }

            // STT 결과 영역
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isRecording && uiState.lastResult == null) {
                        RecordingIndicator()
                    } else {
                        Text(
                            text = uiState.lastResult?.text ?: "녹음 버튼을 눌러 시작하세요.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 18.sp
                            ),
                            textAlign = TextAlign.Center,
                            color = if (uiState.lastResult != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 정답 입력 TextField
            OutlinedTextField(
                value = uiState.referenceText,
                onValueChange = { viewModel.setReferenceText(it) },
                label = { Text("정답 텍스트 (WER 계산용)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )

            // 성능 지표 카드 그리드
            val result = uiState.lastResult
            val rtfColor = if ((result?.rtf ?: 0f) >= 1.0f) Color(0xFFD32F2F)
                           else MaterialTheme.colorScheme.onSurface

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricsCard(
                    label = "총 시간",
                    value = result?.let { "${it.totalTimeMs} ms" } ?: "- ms",
                    modifier = Modifier.weight(1f)
                )
                MetricsCard(
                    label = "RTF",
                    value = result?.let { "%.3f".format(it.rtf) } ?: "-",
                    modifier = Modifier.weight(1f),
                    valueColor = rtfColor
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricsCard(
                    label = "VAD 시간",
                    value = result?.let { "${it.vadTimeMs} ms" } ?: "- ms",
                    modifier = Modifier.weight(1f)
                )
                MetricsCard(
                    label = "STT 시간",
                    value = result?.let { "${it.sttTimeMs} ms" } ?: "- ms",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricsCard(
                    label = "WER",
                    value = uiState.lastWer?.let { "%.1f%%".format(it * 100) } ?: "-",
                    modifier = Modifier.weight(1f),
                    valueColor = werColor(uiState.lastWer)
                )
                MetricsCard(
                    label = "오디오 길이",
                    value = result?.let { "${it.audioLengthMs} ms" } ?: "- ms",
                    modifier = Modifier.weight(1f)
                )
            }

            // 평균 표시
            if (uiState.history.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    val avgLatency = uiState.history.map { it.totalTimeMs }.average()
                    val avgRtf = uiState.history.map { it.rtf }.average()
                    val avgWer = uiState.history.map { it.wer }.average()
                    Text(
                        text = "평균 — latency: ${"%.0f".format(avgLatency)}ms  " +
                               "RTF: ${"%.3f".format(avgRtf)}  " +
                               "WER: ${"%.1f".format(avgWer * 100)}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 노이즈 캔슬링 모드 선택
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoiseCancelMode.entries.forEach { mode ->
                    val label = when (mode) {
                        NoiseCancelMode.NOISE_SUPPRESSOR_ONLY -> "NS만"
                        NoiseCancelMode.GTCRN_ONLY -> "GTCRN만"
                    }
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = { viewModel.setNoiseCancelMode(mode) },
                        label = { Text(label, fontSize = 12.sp) },
                        enabled = !uiState.isRecording,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 버튼 행
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 녹음 버튼
                Button(
                    onClick = {
                        if (uiState.isRecording) viewModel.stopRecording()
                        else viewModel.startRecording()
                    },
                    enabled = uiState.modelsReady && !uiState.isInitializing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRecording) Color(0xFFD32F2F)
                                         else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Text(
                        text = if (uiState.isRecording) "  정지" else "  녹음",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // CSV 내보내기 버튼
                FilledTonalButton(
                    onClick = { viewModel.exportCsv() },
                    enabled = uiState.history.isNotEmpty()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("  CSV", modifier = Modifier.padding(start = 4.dp))
                }
            }

            // CSV 저장 결과 메시지
            uiState.exportMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 측정 이력
            if (uiState.history.isNotEmpty()) {
                Divider()
                Text(
                    text = "측정 이력 (최근 ${uiState.history.size}건)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                HistoryList(
                    records = uiState.history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((uiState.history.size.coerceAtMost(8) * 80).dp)
                )
            }

            // 에러 메시지
            uiState.errorMessage?.let {
                Text(
                    text = "오류: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(8.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecordingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFD32F2F))
        )
        Text(
            "  발화 감지 중...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun werColor(wer: Float?): Color {
    return when {
        wer == null -> Color.Gray
        wer < 0.1f -> Color(0xFF388E3C)
        wer < 0.3f -> Color(0xFFF57C00)
        else -> Color(0xFFD32F2F)
    }
}
