package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionCategoryScreenTest {
    @Test
    fun `category header matches shared low vision header layout`() {
        assertEquals(LowVisionScreenDefaults.headerGap, LowVisionCategoryLayoutDefaults.headerGridGap)
        assertEquals(LowVisionScreenDefaults.headerFontSize, LowVisionCategoryLayoutDefaults.headerFontSize)
        assertEquals(LowVisionScreenDefaults.headerLineHeight, LowVisionCategoryLayoutDefaults.headerLineHeight)
        assertTrue(LowVisionCategoryLayoutDefaults.centersHeaderText)
        assertFalse(LowVisionCategoryLayoutDefaults.showsBackButton)
    }

    @Test
    fun `category screen uses large two by two quadrant ratios`() {
        assertEquals(2, LowVisionCategoryLayoutDefaults.columnCount)
        assertEquals(2, LowVisionCategoryLayoutDefaults.rowCount)
        assertEquals(24.dp, LowVisionCategoryLayoutDefaults.gridGap)
        assertEquals(1f, LowVisionCategoryLayoutDefaults.cardColumnWeight)
        assertEquals(286.dp, LowVisionCategoryLayoutDefaults.cardMinHeight)
    }

    @Test
    fun `category card graphic and text stay large for low vision users`() {
        val contentHeight =
            LowVisionCategoryLayoutDefaults.cardVerticalPadding.value * 2 +
                LowVisionCategoryLayoutDefaults.cardIconSize.value +
                LowVisionCategoryLayoutDefaults.cardIconTextGap.value +
                LowVisionCategoryLayoutDefaults.cardLabelLineHeight.value

        assertTrue(contentHeight <= LowVisionCategoryLayoutDefaults.cardContentBudgetHeightDp)
        assertEquals(96.dp, LowVisionCategoryLayoutDefaults.cardIconSize)
        assertEquals(38.sp, LowVisionCategoryLayoutDefaults.cardLabelFontSize)
    }

    @Test
    fun `category scroll content leaves room to reach lower buttons`() {
        assertEquals(112.dp, LowVisionCategoryLayoutDefaults.scrollBottomSpacer)
    }

    @Test
    fun `category labels prefer one line and allow safe two line wrapping`() {
        assertEquals(2, LowVisionCategoryLayoutDefaults.cardLabelMaxLines)
        assertEquals("Lodging", lowVisionCategoryDisplayLabel("Lodging"))
        assertEquals("Other\nObstacle", lowVisionCategoryDisplayLabel("Other Obstacle"))
        assertEquals("승강기", lowVisionCategoryDisplayLabel("승강기"))
        assertEquals("엘리베이터", lowVisionCategoryDisplayLabel("엘리베이터"))
        assertEquals(
            "\uC219\uBC15\n\uC2DC\uC124",
            lowVisionCategoryDisplayLabel("\uC219\uBC15\uC2DC\uC124"),
        )
    }

    @Test
    fun `category options expose only approved parser categories`() {
        assertEquals(
            listOf("음식점", "관광지", "숙박시설", "병원", "복지관", "관공서"),
            lowVisionCategoryOptions.map { option -> option.label },
        )
    }

    @Test
    fun `category card accessibility hint announces result guidance`() {
        assertEquals(
            "선택 안 됨, 이용할 수 있는 음식점과 카페를 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "음식점" }.resultA11yHint(isSelected = false),
        )
        assertEquals(
            "선택됨, 이용할 수 있는 음식점과 카페를 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "음식점" }.resultA11yHint(isSelected = true),
        )
        assertEquals(
            "선택 안 됨, 이용할 수 있는 관광지를 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "관광지" }.resultA11yHint(isSelected = false),
        )
        assertEquals(
            "선택됨, 이용할 수 있는 관광지를 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "관광지" }.resultA11yHint(isSelected = true),
        )
        assertEquals(
            "선택 안 됨, 이용할 수 있는 숙박시설을 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "숙박시설" }.resultA11yHint(isSelected = false),
        )
        assertEquals(
            "선택됨, 이용할 수 있는 숙박시설을 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "숙박시설" }.resultA11yHint(isSelected = true),
        )
        assertEquals(
            "선택 안 됨, 이용할 수 있는 병원과 의료시설을 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "병원" }.resultA11yHint(isSelected = false),
        )
        assertEquals(
            "선택됨, 이용할 수 있는 병원과 의료시설을 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "병원" }.resultA11yHint(isSelected = true),
        )
        assertEquals(
            "선택 안 됨, 이용할 수 있는 복지시설을 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "복지관" }.resultA11yHint(isSelected = false),
        )
        assertEquals(
            "선택됨, 이용할 수 있는 복지시설을 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "복지관" }.resultA11yHint(isSelected = true),
        )
        assertEquals(
            "선택 안 됨, 이용할 수 있는 관공서를 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "관공서" }.resultA11yHint(isSelected = false),
        )
        assertEquals(
            "선택됨, 이용할 수 있는 관공서를 안내합니다",
            lowVisionCategoryOptions.first { option -> option.label == "관공서" }.resultA11yHint(isSelected = true),
        )
    }

    @Test
    fun `category options use low vision optimized line icons`() {
        assertEquals(
            listOf(
                R.drawable.ic_lowvision_category_restaurant,
                R.drawable.ic_lowvision_category_tourism,
                R.drawable.ic_place_accommodation,
                R.drawable.ic_place_healthcare,
                R.drawable.ic_place_welfare,
                R.drawable.ic_place_public_office,
            ),
            lowVisionCategoryOptions.map { option -> option.iconRes },
        )
    }

    @Test
    fun `vector category icons use stroke width matched to png category marks`() {
        val drawableDir = File("src/main/res/drawable")
        val iconFileNames =
            listOf(
                "ic_lowvision_category_restaurant.xml",
                "ic_lowvision_category_tourism.xml",
            )

        iconFileNames.forEach { fileName ->
            val iconXml = File(drawableDir, fileName).readText()

            assertTrue("$fileName should be a stroked line icon", "android:strokeWidth=\"2\"".toRegex() in iconXml)
        }
    }
}
