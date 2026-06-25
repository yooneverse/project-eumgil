package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenSnackbarConfigurationTest {
    @Test
    fun `map route consumes snackbar events through snackbar host state`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt")
                .readText()

        assertTrue(
            "MapRoute should keep a SnackbarHostState for map feedback messages.",
            source.contains("val snackbarHostState = remember { SnackbarHostState() }"),
        )
        assertTrue(
            "MapRoute should surface map snackbar events through SnackbarHostState.showSnackbar.",
            source.contains("is MapUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)"),
        )
    }

    @Test
    fun `map screen renders snackbar host for map feedback messages`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()

        assertTrue(
            "MapScreen should accept a SnackbarHostState so route events can render on screen.",
            source.contains("snackbarHostState: SnackbarHostState"),
        )
        assertTrue(
            "MapScreen should render a SnackbarHost bound to the provided host state.",
            source.contains("hostState = snackbarHostState"),
        )
    }
}
