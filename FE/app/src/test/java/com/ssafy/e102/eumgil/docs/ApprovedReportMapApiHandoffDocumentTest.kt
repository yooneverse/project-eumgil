package com.ssafy.e102.eumgil.docs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedReportMapApiHandoffDocumentTest {
    @Test
    fun `approved report map api handoff documents backend contract`() {
        val file = handoffDocumentFile()

        assertTrue(
            "Approved report map API handoff document should exist for backend handoff.",
            file.isFile,
        )

        val source = file.readText()

        assertTrue(source.contains("GET /hazard-reports/approved?lat={lat}&lng={lng}&radius={meters}"))
        assertTrue(source.contains("separate from `/hazard-reports/me`"))
        assertTrue(source.contains("all user reports"))
        assertTrue(source.contains("APPROVED only"))
        assertTrue(source.contains("reportId"))
        assertTrue(source.contains("reportType"))
        assertTrue(source.contains("status"))
        assertTrue(source.contains("reportPoint"))
        assertTrue(source.contains("imageUrls"))
        assertTrue(source.contains("approvedAt"))
        assertTrue(source.contains("Filtering Rule"))
        assertTrue(source.contains("Error Policy"))
        assertTrue(source.contains("Image URL Policy"))
        assertTrue(source.contains("FE Connection Points"))
    }

    private fun handoffDocumentFile(): File {
        val relativePaths =
            listOf(
                "../../Docs/API/2026-05-19-approved-report-map-api-handoff.md",
                "../Docs/API/2026-05-19-approved-report-map-api-handoff.md",
                "Docs/API/2026-05-19-approved-report-map-api-handoff.md",
            )

        return relativePaths
            .map(::File)
            .firstOrNull(File::isFile)
            ?: File(relativePaths.first())
    }
}
