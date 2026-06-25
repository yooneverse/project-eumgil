package com.ssafy.e102.eumgil.feature.map.model

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapEntry

const val APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL = 15
private const val APPROVED_REPORT_CLICK_TARGET_PREFIX = "approved-report:"
private const val APPROVED_REPORT_STATUS = "APPROVED"

data class ApprovedReportMarkerData(
    val reportId: Long,
    val reportTypeApiValue: String,
    val reportTypeLabel: String,
    val coordinate: GeoCoordinate,
    val address: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val approvedAt: String? = null,
) {
    val title: String
        get() = description?.takeIf(String::isNotBlank) ?: reportTypeLabel
}

data class ApprovedReportMarkerUiState(
    val reports: List<ApprovedReportMarkerData> = emptyList(),
    val visibleReports: List<ApprovedReportMarkerData> = emptyList(),
    val selectedReportId: Long? = null,
)

data class ApprovedReportSheetState(
    val report: ApprovedReportMarkerData? = null,
) {
    val isVisible: Boolean
        get() = report != null
}

fun shouldShowApprovedReportMarkers(zoomLevel: Int): Boolean =
    zoomLevel <= APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL

fun approvedReportClickTargetId(reportId: Long): String =
    "$APPROVED_REPORT_CLICK_TARGET_PREFIX$reportId"

fun parseApprovedReportClickTargetId(clickTargetId: String): Long? =
    clickTargetId
        .takeIf { targetId -> targetId.startsWith(APPROVED_REPORT_CLICK_TARGET_PREFIX) }
        ?.removePrefix(APPROVED_REPORT_CLICK_TARGET_PREFIX)
        ?.toLongOrNull()

fun ApprovedReportMapEntry.toApprovedReportMarkerDataOrNull(): ApprovedReportMarkerData? {
    if (statusApiValue != APPROVED_REPORT_STATUS) return null

    return ApprovedReportMarkerData(
        reportId = reportId,
        reportTypeApiValue = reportTypeApiValue,
        reportTypeLabel = reportTypeApiValue.toApprovedReportTypeLabel(),
        coordinate = coordinate,
        address = address,
        description = description,
        imageUrls = imageUrls,
        approvedAt = approvedAt,
    )
}

fun toApprovedReportSheetState(
    reports: List<ApprovedReportMarkerData>,
    selectedReportId: Long?,
): ApprovedReportSheetState =
    ApprovedReportSheetState(report = reports.firstOrNull { report -> report.reportId == selectedReportId })

private fun String.toApprovedReportTypeLabel(): String =
    when (this) {
        "BROKEN_BLOCK" -> "점자블록 파손"
        "OBSTACLE" -> "보행 장애물"
        "DAMAGED_ROAD" -> "보행로 파손"
        "SIGNAL_ISSUE" -> "신호 접근 문제"
        else -> "주의 제보"
    }
