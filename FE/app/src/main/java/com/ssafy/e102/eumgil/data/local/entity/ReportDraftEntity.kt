package com.ssafy.e102.eumgil.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 제보 임시저장 Entity.
 *
 * `photoUri` / `photoMimeType` / `photoSizeBytes` 단일 컬럼 3종은 v6 이전 스키마의 legacy 필드로,
 * 1장만 보존 가능했다. v7부터는 `photosJson` 컬럼에 사진 리스트를 JSON 배열로 저장하여 5장까지
 * 영속화한다. 마이그레이션·읽기 정합성을 위해 legacy 컬럼은 그대로 유지하되, 새 코드는 `photosJson`을
 * source of truth로 사용한다 (legacy 단일 사진 정보는 fallback 용도).
 */
@Entity(
    tableName = "reportDraft",
    indices = [Index(value = ["updatedAt"])],
)
data class ReportDraftEntity(
    @PrimaryKey
    val draftId: String,
    val reportCategory: String? = null,
    val description: String = "",
    // v8 — 두 컬럼 분리: address는 좌표 → RGC 자동 결과(도로명 등 객관 정보),
    // addressDetail은 사용자가 직접 적은 "건물명·주변 장소" 같은 현장 맥락 보충.
    val address: String? = null,
    val addressDetail: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationSource: String? = null,
    // v6 이전 single-photo 시절의 legacy 컬럼. v7 이후 새 row 저장 시에는 photosJson만 사용.
    val photoUri: String? = null,
    val photoMimeType: String? = null,
    val photoSizeBytes: Long? = null,
    // v7 신설. ReportDraftPhotoItem 리스트의 JSON 표현. null이면 legacy photoUri로 fallback.
    val photosJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
