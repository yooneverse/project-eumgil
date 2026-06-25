package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LowVisionScreenDefaultsTest {
    @Test
    fun `low vision tab header keeps bookmark position with softer type scale`() {
        assertEquals(24.dp, LowVisionScreenDefaults.screenHorizontalPadding)
        assertEquals(36.dp, LowVisionScreenDefaults.screenVerticalPadding)
        assertEquals(40.sp, LowVisionScreenDefaults.headerFontSize)
        assertEquals(48.sp, LowVisionScreenDefaults.headerLineHeight)
        assertEquals(24.dp, LowVisionScreenDefaults.headerGap)
    }

    @Test
    fun `low vision yellow matches home screen brand tone`() {
        assertEquals(Color(0xFFFFCC00), LowVisionScreenDefaults.brandYellow)
    }

    @Test
    fun `low vision typography uses KoddiUD OnGothic font resources`() {
        assertEquals(
            listOf(
                LowVisionFontResource(R.font.koddi_ud_on_gothic_regular, FontWeight.Normal),
                LowVisionFontResource(R.font.koddi_ud_on_gothic_bold, FontWeight.Bold),
                LowVisionFontResource(R.font.koddi_ud_on_gothic_extra_bold, FontWeight.ExtraBold),
                LowVisionFontResource(R.font.koddi_ud_on_gothic_extra_bold, FontWeight.Black),
            ),
            lowVisionFontResources(),
        )
    }
}
