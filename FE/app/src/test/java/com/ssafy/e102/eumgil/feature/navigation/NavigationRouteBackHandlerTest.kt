package com.ssafy.e102.eumgil.feature.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteBackHandlerTest {
    @Test
    fun `standard navigation route intercepts system back for exit confirmation`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationRoute.kt")
                .readText()

        assertTrue(source.contains("BackHandler("))
        assertTrue(source.contains("enabled = !useLowVisionUi && !uiState.isExitConfirmDialogVisible"))
        assertTrue(source.contains("viewModel.onAction(NavigationUiAction.BackClicked)"))
    }
}
