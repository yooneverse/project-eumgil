package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class LowVisionMyPageScreenTest {
    @Test
    fun `my page actions divide the area below header into three equal sections`() {
        assertEquals(3, LowVisionMyPageLayoutDefaults.actionCount)
        assertEquals(1f, LowVisionMyPageLayoutDefaults.actionSectionWeight)
        assertEquals(112.dp, LowVisionMyPageLayoutDefaults.actionMinHeight)
    }

    @Test
    fun `my page actions keep slightly smaller two line labels to avoid clipping`() {
        assertEquals(36.sp, LowVisionMyPageLayoutDefaults.actionLabelFontSize)
        assertEquals(42.sp, LowVisionMyPageLayoutDefaults.actionLabelLineHeight)
    }

    @Test
    fun `app info keeps two information panels text size entry and one withdrawal action`() {
        assertEquals(2, LowVisionAppInfoLayoutDefaults.infoPanelCount)
        assertEquals(1, LowVisionAppInfoLayoutDefaults.textSizeActionCount)
        assertEquals(1, LowVisionAppInfoLayoutDefaults.withdrawActionCount)
        assertEquals(112.dp, LowVisionAppInfoLayoutDefaults.textSizeActionMinHeight)
        assertEquals(112.dp, LowVisionAppInfoLayoutDefaults.withdrawActionMinHeight)
    }
}
