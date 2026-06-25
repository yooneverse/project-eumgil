package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.GeoCoordinate

data class ApprovedReportMapQuery(
    val center: GeoCoordinate,
    val radiusMeters: Int,
)

data class ApprovedReportMapEntry(
    val reportId: Long,
    val reportTypeApiValue: String,
    val statusApiValue: String,
    val coordinate: GeoCoordinate,
    val address: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val approvedAt: String? = null,
)

interface ApprovedReportMapRepository {
    suspend fun getApprovedReports(query: ApprovedReportMapQuery): List<ApprovedReportMapEntry>
}

object EmptyApprovedReportMapRepository : ApprovedReportMapRepository {
    override suspend fun getApprovedReports(query: ApprovedReportMapQuery): List<ApprovedReportMapEntry> =
        emptyList()
}
