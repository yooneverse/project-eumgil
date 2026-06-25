package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ssafy.e102.eumgil.core.designsystem.theme.BusanEumgilTheme
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedHazardMarker
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ApprovedHazardMarkerBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `viewer state stays hidden for empty image list`() {
        val state = ApprovedHazardMarkerImageViewerState()

        val opened = state.open(imageUrls = emptyList(), initialIndex = 0)

        assertFalse(opened.isVisible)
        assertTrue(opened.imageUrls.isEmpty())
        assertEquals(0, opened.selectedIndex)
    }

    @Test
    fun `viewer state opens at requested thumbnail index`() {
        val state = ApprovedHazardMarkerImageViewerState()

        val opened =
            state.open(
                imageUrls = listOf("image-1", "image-2", "image-3"),
                initialIndex = 1,
            )

        assertTrue(opened.isVisible)
        assertEquals(listOf("image-1", "image-2", "image-3"), opened.imageUrls)
        assertEquals(1, opened.selectedIndex)
    }

    @Test
    fun `viewer state clamps selected index into available range`() {
        val state = ApprovedHazardMarkerImageViewerState()

        val opened =
            state.open(
                imageUrls = listOf("image-1", "image-2"),
                initialIndex = 99,
            )

        assertTrue(opened.isVisible)
        assertEquals(1, opened.selectedIndex)
    }

    @Test
    fun `viewer state closes without clearing the image list`() {
        val state =
            ApprovedHazardMarkerImageViewerState(
                imageUrls = listOf("image-1", "image-2"),
                selectedIndex = 1,
                isVisible = true,
            )

        val closed = state.close()

        assertFalse(closed.isVisible)
        assertEquals(listOf("image-1", "image-2"), closed.imageUrls)
        assertEquals(1, closed.selectedIndex)
    }

    @Test
    fun `bottom sheet shows square empty image placeholder when marker has no photos`() {
        composeRule.setContent {
            BusanEumgilTheme {
                ApprovedHazardMarkerBottomSheet(marker = marker(imageUrls = emptyList()))
            }
        }

        assertTrue(composeRule.onAllNodesWithTag("approvedHazardNoImagePlaceholder").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("approvedHazardHeaderWarningIcon").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("사진 없음").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("승인 제보 사진 1").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `thumbnail click opens viewer at selected image and close button dismisses it`() {
        composeRule.setContent {
            BusanEumgilTheme {
                ApprovedHazardMarkerBottomSheet(
                    marker =
                        marker(
                            imageUrls = listOf("https://example.com/one.jpg", "https://example.com/two.jpg"),
                        ),
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("approvedHazardThumbnail-1").performClick()
        assertTrue(composeRule.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("approvedHazardViewerClose").performClick()
        assertTrue(composeRule.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `viewer backdrop tap dismisses fullscreen viewer`() {
        composeRule.setContent {
            BusanEumgilTheme {
                ApprovedHazardMarkerBottomSheet(
                    marker =
                        marker(
                            imageUrls = listOf("https://example.com/one.jpg", "https://example.com/two.jpg"),
                        ),
                )
            }
        }

        composeRule.onNodeWithTag("approvedHazardThumbnail-0").performClick()
        assertTrue(composeRule.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("approvedHazardViewerBackdrop").performTouchInput {
            click(Offset(8f, 8f))
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `viewer policy uses white foreground on the dark fullscreen surface`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()
        val viewerSection =
            source
                .substringAfter("private fun ApprovedHazardMarkerImageViewer(")
                .substringBefore("internal data class ApprovedHazardMarkerImageViewerState")

        assertTrue(viewerSection.contains("ApprovedHazardMarkerViewerForegroundColor"))
        assertTrue(viewerSection.contains("tint = ApprovedHazardMarkerViewerForegroundColor"))
        assertTrue(viewerSection.contains("color = ApprovedHazardMarkerViewerForegroundColor"))
    }

    @Test
    fun `bottom sheet root keeps fullscreen viewer above later screen overlays`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()
        val rootSection =
            source
                .substringAfter("BoxWithConstraints(")
                .substringBefore("val sheetMaxHeight")

        assertTrue(rootSection.contains("zIndex(ApprovedHazardMarkerOverlayZIndex)"))
    }

    @Test
    fun `viewer backdrop installs a dedicated input barrier`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()
        val viewerSection =
            source
                .substringAfter("private fun ApprovedHazardMarkerImageViewer(")
                .substringBefore("internal data class ApprovedHazardMarkerImageViewerState")

        assertTrue(viewerSection.contains("approvedHazardViewerBackdrop"))
        assertTrue(viewerSection.contains(".clickable("))
        assertTrue(viewerSection.contains("onClick = onDismiss"))
    }

    @Test
    fun `sheet header uses shared warning drawable and no image placeholder uses the square camera empty state`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()

        assertTrue(source.contains("approvedHazardHeaderWarningIcon"))
        assertTrue(source.contains("approvedHazardNoImagePlaceholder"))
        assertTrue(source.contains("R.drawable.ic_approved_hazard_warning"))
        assertFalse(source.contains("Modifier.offset(y = (-10).dp)"))
        assertFalse(source.contains("map_facility_detail_close"))
        assertTrue(source.contains("Color(0xFFD9D9D9)"))
        assertTrue(source.contains("Color(0xFFFFFFFF)"))
        assertTrue(source.contains("Color(0xFFE5E7EB)"))
    }

    @Test
    fun `bottom sheet shows hazard description between report type and images`() {
        composeRule.setContent {
            BusanEumgilTheme {
                ApprovedHazardMarkerBottomSheet(
                    marker =
                        marker(
                            description = "공사 자재가 인도를 막고 있어 우회가 필요합니다.",
                            imageUrls = emptyList(),
                        ),
                )
            }
        }

        assertTrue(
            composeRule
                .onAllNodesWithText("공사 자재가 인도를 막고 있어 우회가 필요합니다.")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(composeRule.onAllNodesWithTag("approvedHazardDescription").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `sheet source renders optional description ahead of the image section`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()

        assertTrue(source.contains("resolvedMarker.description?.takeIf"))
        assertTrue(source.contains("approvedHazardDescription"))
        assertTrue(
            source.indexOf("approvedHazardDescription") <
                source.indexOf("if (resolvedMarker.imageUrls.isEmpty())"),
        )
    }

    @Test
    fun `sheet source prefers thumbnail urls for gallery cards and falls back to original images`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()

        assertTrue(source.contains("resolvedMarker.thumbnailUrls.ifEmpty { resolvedMarker.imageUrls }"))
    }

    @Test
    fun `sheet uses attached bottom edge treatment and drag dismisses through onDismiss`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedHazardMarkerBottomSheet.kt")
                .readText()

        assertTrue(source.contains("edgeTreatment = MapBottomSheetEdgeTreatment.AttachedToBottomBar"))
        assertTrue(source.contains(".draggable("))
        assertTrue(source.contains("onDismiss()"))
    }

    private fun marker(
        description: String? = null,
        imageUrls: List<String>,
    ) =
        ApprovedHazardMarker(
            reportId = 12L,
            reportType = "RAMP",
            coordinate = GeoCoordinate(latitude = 35.1, longitude = 129.1),
            description = description,
            imageUrls = imageUrls,
        )
}
