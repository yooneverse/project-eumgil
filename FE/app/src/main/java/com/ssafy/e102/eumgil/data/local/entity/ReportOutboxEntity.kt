package com.ssafy.e102.eumgil.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reportOutbox",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updatedAt"]),
    ],
)
data class ReportOutboxEntity(
    @PrimaryKey
    val outboxId: String,
    val reportCategory: String,
    val description: String = "",
    // v8 — 두 컬럼 분리: address는 좌표 → RGC 자동 결과, addressDetail은 사용자 직접 보충 메모.
    val address: String? = null,
    val addressDetail: String? = null,
    val latitude: Double,
    val longitude: Double,
    val photoUri: String? = null,
    val photoMimeType: String? = null,
    val photoSizeBytes: Long? = null,
    // v10 — Task 5.5: 사용자가 첨부한 사진 local URI / mime / size를 JSON 배열로 보존하여
    // 제출 시점에 한꺼번에 presigned 업로드 대상으로 사용한다.
    // 형식: [{"uri":"content://...","mime":"image/jpeg","size":12345}, ...]
    val photosJson: String? = null,
    // v10 — Task 5.5: presigned 업로드 성공 후 BE로 보낼 안정 저장값(`objectKey`) 목록.
    // 형식: JSON 문자열 배열 ["hazard-reports/...", ...]. null이면 아직 업로드 안 됨.
    val imageObjectKeysJson: String? = null,
    val thumbnailObjectKeysJson: String? = null,
    val status: String,
    val serverReportId: Long? = null,
    val lastFailureReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
