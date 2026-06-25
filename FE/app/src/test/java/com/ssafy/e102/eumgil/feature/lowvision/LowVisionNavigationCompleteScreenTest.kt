package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionNavigationCompleteScreenTest {
    @Test
    fun `navigation complete screen exposes save and complete cards`() {
        assertEquals(
            listOf("\uB3C4\uCC29\uC9C0 \uC800\uC7A5", "\uC644\uB8CC"),
            lowVisionNavigationCompleteCards().map(LowVisionNavigationCompleteCard::label),
        )
    }

    @Test
    fun `navigation complete screen follows mockup proportions`() {
        assertEquals(24.dp, LowVisionNavigationCompleteLayoutDefaults.horizontalPadding)
        assertEquals(44.dp, LowVisionNavigationCompleteLayoutDefaults.verticalPadding)
        assertEquals(28.dp, LowVisionNavigationCompleteLayoutDefaults.cardGap)
        assertEquals(26.dp, LowVisionNavigationCompleteLayoutDefaults.cardCornerRadius)
        assertEquals(20.dp, LowVisionNavigationCompleteLayoutDefaults.cardContentPadding)
        assertEquals(148.dp, LowVisionNavigationCompleteLayoutDefaults.completeIconSize)
        assertEquals(52.sp, LowVisionNavigationCompleteLayoutDefaults.titleFontSize)
    }

    @Test
    fun `navigation complete screen applies system safe zones`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationCompleteScreen.kt")
                .readText()

        assertTrue(source.contains(".statusBarsPadding()"))
        assertTrue(source.contains(".navigationBarsPadding()"))
    }

    @Test
    fun `navigation complete save uses low vision bookmark route without re-ending navigation`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationCompleteRoute.kt")
                .readText()

        assertTrue(source.contains("bookmarkRepository.saveBookmark"))
        assertTrue(source.contains("runCatching"))
        assertTrue(source.contains("}.onSuccess {"))
        assertTrue(source.contains("onNavigateToBookmark()"))
        assertTrue(source.contains("destination.toBookmarkDataOrNull()"))
        assertTrue(!source.contains("SaveBookmarkClicked"))
        assertTrue(!source.contains("NavigationViewModel"))
    }
}
