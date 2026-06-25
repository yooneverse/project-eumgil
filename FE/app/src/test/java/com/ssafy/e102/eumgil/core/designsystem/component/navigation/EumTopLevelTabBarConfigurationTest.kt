package com.ssafy.e102.eumgil.core.designsystem.component.navigation

import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.app.navigation.TopLevelDestination
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EumTopLevelTabBarConfigurationTest {
    @Test
    fun `top level tab bar uses compact layout spec`() {
        val spec = topLevelTabBarLayoutSpec()

        assertEquals(12, spec.containerHorizontalPaddingDp)
        assertEquals(8, spec.itemVerticalPaddingDp)
        assertEquals(2, spec.itemSpacingDp)
    }

    @Test
    fun `top level tab bar uses selected icon resource for selected state`() {
        assertEquals(
            R.drawable.ic_nav_home_selected,
            topLevelTabIconRes(destination = TopLevelDestination.Map, selected = true),
        )
        assertEquals(
            R.drawable.ic_nav_home,
            topLevelTabIconRes(destination = TopLevelDestination.Map, selected = false),
        )
        assertEquals(
            R.drawable.ic_nav_bookmark_selected,
            topLevelTabIconRes(destination = TopLevelDestination.SavedRoute, selected = true),
        )
    }

    @Test
    fun `selected top level tab uses bright blue tint`() {
        assertEquals(EumPrimary600, topLevelTabContentColor(selected = true))
    }

    @Test
    fun `top level tab selection suppresses ripple for cross screen navigation`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/navigation/EumTopLevelTabBar.kt")
                .readText()

        assertTrue(
            "Top-level tab navigation should not show ripple when switching screens.",
            source.contains("indication = null"),
        )
        assertTrue(
            "Top-level tab navigation should use its own interaction source when ripple is suppressed.",
            source.contains("interactionSource = remember { MutableInteractionSource() }"),
        )
    }

    @Test
    fun `top level tab bar uses a lighter label typography step`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/navigation/EumTopLevelTabBar.kt")
                .readText()

        assertTrue(
            "Top-level tab labels should keep the labelLarge size and lower only the font weight.",
            source.contains("style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)"),
        )
        assertFalse(
            "Top-level tab labels should not switch to labelMedium because that also shrinks the label size.",
            source.contains("style = MaterialTheme.typography.labelMedium"),
        )
    }
}
