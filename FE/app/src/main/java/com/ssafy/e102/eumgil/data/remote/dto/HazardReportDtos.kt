package com.ssafy.e102.eumgil.data.remote.dto

import com.ssafy.e102.eumgil.data.route.RouteDto

data class HazardReportPointDto(
    val lat: Double,
    val lng: Double,
)

data class CreateHazardReportRequestDto(
    val reportType: String,
    val description: String?,
    val reportPoint: HazardReportPointDto,
    // Task 5.6: BE 명세에 따라 presigned 업로드된 S3 object key 배열을 전송한다.
    // 이미지 binary는 별도 presigned PUT API로 먼저 업로드해야 한다.
    val imageObjectKeys: List<String> = emptyList(),
    val thumbnailObjectKeys: List<String> = emptyList(),
)

/**
 * `POST /hazard-reports/images/presigned-upload` 요청 (Task 5.5).
 *
 * BE는 이 요청을 받아 S3/MinIO에 PUT 가능한 presigned URL과 안정 저장값인 `objectKey`를 발급한다.
 * BE 명세: 모든 필드 필수. 허용 contentType은 image/jpeg, image/png, image/webp, image/heic, image/heif.
 * contentLength 기본 상한 10MB.
 */
data class PresignedUploadRequestDto(
    val fileName: String,
    val contentType: String,
    val contentLength: Long,
)

data class PresignedUploadResponseDto(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: String,
)

data class PresignedUploadBatchRequestDto(
    val files: List<PresignedUploadRequestDto>,
)

data class PresignedUploadBatchResponseDto(
    val uploads: List<PresignedUploadResponseDto>,
)

data class CreateHazardReportResponseDto(
    val reportId: Long,
)

data class HazardReportListItemDto(
    val reportId: Long,
    val reportType: String,
    val status: String,
    val reportPoint: HazardReportPointDto,
    val createdAt: String,
    val representativeImageUrl: String?,
    // Task 5.7 — BE list 응답이 내려주는 80자 preview 설명 (nullable).
    val description: String? = null,
    // Task 5.7 — BE가 좌표 역지오코딩으로 채워준 표시 주소 snapshot (nullable).
    val address: String? = null,
)

data class HazardReportPageDto(
    val content: List<HazardReportListItemDto>,
    val size: Int,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

data class HazardReportDetailDto(
    val reportId: Long,
    val reportType: String,
    val status: String,
    val description: String?,
    val reportPoint: HazardReportPointDto,
    val createdAt: String,
    val imageUrls: List<String> = emptyList(),
)

data class HazardMarkersResponseDto(
    val markers: List<HazardMarkerDto> = emptyList(),
)

data class HazardMarkerDto(
    val reportId: Long,
    val reportType: String,
    val lat: Double,
    val lng: Double,
    val description: String? = null,
    val thumbnailUrls: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
)

data class HazardReportRerouteRequestDto(
    val routeId: String,
    val currentPoint: HazardReportPointDto,
    val activeLegSequence: Int? = null,
)

data class HazardReportRerouteResponseDto(
    val rerouted: Boolean,
    val route: RouteDto?,
)
