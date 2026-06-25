package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionSearchScreenTest {
    @Test
    fun `search result cards keep enough height for compact stacked action buttons`() {
        val minimumButtonStackHeight =
            LowVisionSearchLayoutDefaults.actionButtonHeight * LowVisionSearchLayoutDefaults.actionButtonCount +
                LowVisionSearchLayoutDefaults.actionButtonGap

        assertEquals(2, LowVisionSearchLayoutDefaults.actionButtonCount)
        assertEquals(58.dp, LowVisionSearchLayoutDefaults.actionButtonHeight)
        assertEquals(10.dp, LowVisionSearchLayoutDefaults.actionButtonGap)
        assertTrue(LowVisionSearchLayoutDefaults.resultCardMinHeight >= minimumButtonStackHeight + 160.dp)
        assertEquals(116.dp, LowVisionSearchLayoutDefaults.infoSectionMinHeight)
    }

    @Test
    fun `search result list can show two compact result cards`() {
        val twoCardHeight =
            LowVisionSearchLayoutDefaults.resultCardMinHeight * 2 +
                LowVisionSearchLayoutDefaults.resultCardGap

        assertEquals(288.dp, LowVisionSearchLayoutDefaults.resultCardMinHeight)
        assertEquals(12.dp, LowVisionSearchLayoutDefaults.resultCardGap)
        assertTrue(twoCardHeight <= LowVisionSearchLayoutDefaults.twoCardViewportBudget)
    }

    @Test
    fun `search result card text scale is reduced for two result layout`() {
        assertEquals(34.sp, LowVisionSearchLayoutDefaults.titleLineHeight)
        assertEquals(24.sp, LowVisionSearchLayoutDefaults.addressLineHeight)
        assertEquals(64.dp, LowVisionSearchLayoutDefaults.resultListBottomPadding)
        assertEquals(116.dp, LowVisionSearchLayoutDefaults.infoSectionMinHeight)
        assertEquals(3.dp, LowVisionSearchLayoutDefaults.sectionDividerThickness)
    }

    @Test
    fun `empty search result exposes no result text and retry talkback label`() {
        assertEquals("결과없음", LowVisionSearchLayoutDefaults.noResultText)
        assertEquals(
            "결과없음. 다시 검색하시겠습니까",
            LowVisionSearchLayoutDefaults.noResultTalkBackText,
        )
    }

    @Test
    fun `wide phones allow more result card text before clipping`() {
        val compactMetrics = LowVisionSearchLayoutDefaults.resultCardMetrics(maxWidth = 360.dp)
        val s24UltraMetrics = LowVisionSearchLayoutDefaults.resultCardMetrics(maxWidth = 412.dp)

        assertEquals(2, compactMetrics.titleMaxLines)
        assertEquals(2, compactMetrics.addressMaxLines)
        assertEquals(3, s24UltraMetrics.titleMaxLines)
        assertEquals(3, s24UltraMetrics.addressMaxLines)
    }

    @Test
    fun `search route starts location updates and enters category results as distance sort`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionSearchRoute.kt")
                .readText()

        assertTrue(source.contains("currentLocationManager.startLocationUpdates()"))
        assertTrue(source.contains("currentLocationManager.refreshLatestLocation()"))
        assertTrue(source.contains("SortOptionSelected(sortOption = SearchSortOption.DISTANCE)"))
        assertTrue(!source.contains("retainedCurrentLocationSnapshot"))
        assertTrue(!source.contains("awaitLowVisionSearchLocationSnapshot"))
    }
}
