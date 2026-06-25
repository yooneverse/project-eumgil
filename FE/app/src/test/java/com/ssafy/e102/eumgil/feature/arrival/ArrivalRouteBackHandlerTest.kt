package com.ssafy.e102.eumgil.feature.arrival

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalRouteBackHandlerTest {
    @Test
    fun `arrival route intercepts system back and sends users home`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalRoute.kt")
                .readText()

        assertTrue(source.contains("BackHandler {"))
        assertTrue(source.contains("viewModel.onAction(ArrivalUiAction.HomeClicked)"))
    }
}
