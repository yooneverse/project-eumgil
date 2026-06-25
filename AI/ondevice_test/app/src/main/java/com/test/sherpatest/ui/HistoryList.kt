package com.test.sherpatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.sherpatest.model.MeasurementRecord

@Composable
fun HistoryList(records: List<MeasurementRecord>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(records, key = { it.id }) { record ->
            HistoryItem(record)
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun HistoryItem(record: MeasurementRecord) {
    val rtfColor = if (record.rtf >= 1.0f) Color(0xFFD32F2F) else Color(0xFF388E3C)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "#${record.id} ${record.timestampStr}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "${record.totalTimeMs}ms", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "RTF=${"%.2f".format(record.rtf)}",
                    fontSize = 11.sp,
                    color = rtfColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "WER=${"%.1f".format(record.wer * 100)}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        if (record.referenceText.isNotEmpty()) {
            Text(
                text = "정답: ${record.referenceText}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "STT: ${record.sttText.ifEmpty { "(인식 결과 없음)" }}",
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
