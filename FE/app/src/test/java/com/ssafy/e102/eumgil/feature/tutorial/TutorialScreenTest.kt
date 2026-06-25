package com.ssafy.e102.eumgil.feature.tutorial

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class TutorialScreenTest {
    @Test
    fun `tutorial uses a full body stage without a white card frame`() {
        assertEquals(420.dp, TutorialLayoutDefaults.visualPanelMaxWidth)
        assertEquals(1f, TutorialLayoutDefaults.visualPanelWeight, 0f)
        assertEquals(12.dp, TutorialLayoutDefaults.visualPanelButtonGap)
        assertEquals(false, TutorialLayoutDefaults.usesWhitePanelFrame)
        assertEquals(3, TutorialLayoutDefaults.distinctIllustrationSceneCount)
        assertEquals(true, TutorialLayoutDefaults.usesNavigationSafeZone)
    }

    @Test
    fun `tutorial keeps the hero mark fixed without a background tile`() {
        assertEquals(false, TutorialLayoutDefaults.hasHeroIconBackground)
        assertEquals(56.dp, TutorialLayoutDefaults.heroIconSize)
        assertEquals(88.dp, TutorialLayoutDefaults.previousButtonMinWidth)
        assertEquals(2, TutorialLayoutDefaults.firstStepWithPreviousAction)
        assertEquals(1f, TutorialLayoutDefaults.panelTouchNavigationZoneWeight, 0f)
    }

    @Test
    fun `tutorial keeps concise visual cues while preserving key chips`() {
        assertEquals(3, TutorialLayoutDefaults.totalStepCount)
        assertEquals(0, TutorialLayoutDefaults.supportingItemCount)
        assertEquals(2, TutorialLayoutDefaults.destinationFilterChipCount)
        assertEquals(0, TutorialLayoutDefaults.routeAccessibilityChipCount)
        assertEquals(4, TutorialLayoutDefaults.reportCategoryChipCount)
        assertEquals(false, TutorialLayoutDefaults.showsEmphasisChip)
        assertEquals(true, TutorialLayoutDefaults.usesLayeredFlatIllustration)
        assertEquals(false, TutorialLayoutDefaults.usesIllustrationHalo)
        assertEquals(true, TutorialLayoutDefaults.destinationFiltersAttachToSearch)
        assertEquals(true, TutorialLayoutDefaults.destinationSearchShowsMic)
        assertEquals(false, TutorialLayoutDefaults.destinationShowsMapPreview)
        assertEquals(true, TutorialLayoutDefaults.destinationUsesSearchAndFilterOnly)
        assertEquals(false, TutorialLayoutDefaults.destinationFilterRowScrollable)
        assertEquals(true, TutorialLayoutDefaults.destinationFilterUsesLatestMapIcons)
        assertEquals(true, TutorialLayoutDefaults.destinationFilterUsesOriginalMapChipShape)
        assertEquals(true, TutorialLayoutDefaults.destinationShowsFilterResultPanel)
        assertEquals(false, TutorialLayoutDefaults.usesGeneratedBitmapIllustration)
        assertEquals(true, TutorialLayoutDefaults.usesServiceLikeStatusStrip)
        assertEquals(true, TutorialLayoutDefaults.statusStripUsesTransparentLayer)
        assertEquals(true, TutorialLayoutDefaults.statusStripUsesSoftBlueLayer)
        assertEquals(false, TutorialLayoutDefaults.statusStripUsesBorder)
        assertEquals(true, TutorialLayoutDefaults.statusStripUsesLowEmphasisValue)
        assertEquals(true, TutorialLayoutDefaults.routeStatusUsesMapMarkerIcon)
        assertEquals(1, TutorialLayoutDefaults.statusStripCountPerScene)
        assertEquals(true, TutorialLayoutDefaults.routeCopyUsesRouteWording)
        assertEquals(true, TutorialLayoutDefaults.reportUsesLatestReportTypeIcons)
        assertEquals(true, TutorialLayoutDefaults.reportHighlightsSelectedCategory)
        assertEquals(true, TutorialLayoutDefaults.reportShowsSubmitButtonBelowGrid)
        assertEquals(true, TutorialLayoutDefaults.reportDescriptionMentionsRouteContribution)
    }

    @Test
    fun `tutorial uses readable header and panel text rhythm`() {
        assertEquals(34.sp, TutorialLayoutDefaults.headerHeadlineLineHeight)
        assertEquals(22.sp, TutorialLayoutDefaults.headerDescriptionLineHeight)
        assertEquals(12.dp, TutorialLayoutDefaults.illustrationContentGap)
        assertEquals(24.dp, TutorialLayoutDefaults.headerVisualGap)
        assertEquals(20.dp, TutorialLayoutDefaults.headerTopPadding)
        assertEquals(12.dp, TutorialLayoutDefaults.sceneContentVerticalGap)
        assertEquals(TutorialLayoutDefaults.sceneContentVerticalGap, TutorialLayoutDefaults.destinationControlVerticalGap)
        assertEquals(TutorialLayoutDefaults.sceneContentVerticalGap, TutorialLayoutDefaults.destinationResultPanelGap)
        assertEquals(TutorialLayoutDefaults.sceneContentVerticalGap, TutorialLayoutDefaults.reportGridButtonGap)
        assertEquals(1.dp, TutorialLayoutDefaults.previousButtonBorderWidth)
        assertEquals(96.dp, TutorialLayoutDefaults.routeCardHeight)
        assertEquals(152.dp, TutorialLayoutDefaults.reportTileWidth)
        assertEquals(100.dp, TutorialLayoutDefaults.reportTileHeight)
    }

    @Test
    fun `destination controls sit close together with equal vertical rhythm`() {
        assertEquals(316.dp, TutorialLayoutDefaults.illustrationHeight)
        assertEquals(340.dp, TutorialLayoutDefaults.searchBarMaxWidth)
        assertEquals(12.dp, TutorialLayoutDefaults.destinationControlVerticalGap)
        assertEquals(13.dp, TutorialLayoutDefaults.filterChipHorizontalPadding)
        assertEquals(9.dp, TutorialLayoutDefaults.filterChipVerticalPadding)
        assertEquals(38.dp, TutorialLayoutDefaults.filterChipMinHeight)
        assertEquals(8.dp, TutorialLayoutDefaults.filterChipCornerRadius)
        assertEquals(2, TutorialLayoutDefaults.filterChipRowMaxItemCount)
        assertEquals(1, TutorialLayoutDefaults.destinationFilterRowCount)
        assertEquals(340.dp, TutorialLayoutDefaults.destinationResultPanelMaxWidth)
        assertEquals(12.dp, TutorialLayoutDefaults.destinationResultPanelGap)
        assertEquals(40.dp, TutorialLayoutDefaults.statusStripHeight)
        assertEquals(340.dp, TutorialLayoutDefaults.statusStripMaxWidth)
        assertEquals(0.86f, TutorialLayoutDefaults.statusStripContainerAlpha, 0f)
    }

    @Test
    fun `status strips use product-like short result copy`() {
        val stringsFile = File("src/main/res/values/strings.xml")

        assertEquals("적용", stringsFile.readStringResource(name = "tutorial_destination_status_title"))
        assertEquals("화장실 · 엘리베이터", stringsFile.readStringResource(name = "tutorial_destination_status_value"))
        assertEquals("기준", stringsFile.readStringResource(name = "tutorial_route_status_title"))
        assertEquals("안전 · 효율", stringsFile.readStringResource(name = "tutorial_route_status_value"))
        assertEquals("선택", stringsFile.readStringResource(name = "tutorial_report_status_title"))
        assertEquals("점자블록", stringsFile.readStringResource(name = "tutorial_report_status_value"))
    }

    @Test
    fun `route copy uses route instead of path wording`() {
        val stringsFile = File("src/main/res/values/strings.xml")

        assertEquals("내게 맞는 경로를\\n빠르게 고르기", stringsFile.readStringResource(name = "tutorial_route_headline"))
        assertEquals("완전히 안전한 경로", stringsFile.readStringResource(name = "tutorial_route_recommended"))
        assertEquals("효율적인 경로", stringsFile.readStringResource(name = "tutorial_route_efficient"))
        assertEquals("경로 안내 시작", stringsFile.readStringResource(name = "tutorial_route_start_navigation"))
    }

    @Test
    fun `route description uses a single concise line`() {
        val stringsFile = File("src/main/res/values/strings.xml")

        assertEquals(false, TutorialLayoutDefaults.routeDescriptionBreaksAfterSettingComma)
        assertEquals(true, TutorialLayoutDefaults.routeDescriptionUsesSingleLine)
        assertEquals(
            "상황에 맞는 경로를 선택해 이용하세요.",
            stringsFile.readStringResource(name = "tutorial_route_description"),
        )
    }

    @Test
    fun `route efficient time copy is thirty five minutes`() {
        val routeEfficientTime =
            File("src/main/res/values/strings.xml")
                .readStringResource(name = "tutorial_route_efficient_time")

        assertEquals("35분", routeEfficientTime)
    }

    @Test
    fun `report tutorial mirrors the visible report category grid`() {
        val stringsFile = File("src/main/res/values/strings.xml")

        assertEquals("계단·단차 있음", stringsFile.readStringResource(name = "tutorial_report_stairs_step"))
        assertEquals("점자블록 문제", stringsFile.readStringResource(name = "tutorial_report_braille_block"))
        assertEquals("인도 없음", stringsFile.readStringResource(name = "tutorial_report_sidewalk_missing"))
        assertEquals("경사로 문제", stringsFile.readStringResource(name = "tutorial_report_ramp"))
        assertEquals("제보하기", stringsFile.readStringResource(name = "tutorial_report_action_submit"))
    }

    private fun File.readStringResource(name: String): String {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(this)
        val strings = document.getElementsByTagName("string")

        return (0 until strings.length)
            .asSequence()
            .map { strings.item(it) as Element }
            .first { it.getAttribute("name") == name }
            .textContent
    }
}
