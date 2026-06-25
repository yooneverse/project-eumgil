package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapKeywordSpottingAutoStartContractTest {
    private val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapKwsViewModel.kt")
            .readText()

    @Test
    fun `map kws initialization does not auto start spotting before resume`() {
        val initializeSource =
            source
                .substringAfter("private suspend fun initializeKeywordSpotting(context: Application) {")
                .substringBefore("override fun onCleared()")

        assertFalse(
            "MapKwsViewModel initialization should only prepare KWS resources and must not start spotting on cold start.",
            initializeSource.contains("startSpotting()"),
        )
    }

    @Test
    fun `map kws resume restarts spotting after both initialization and ready-manager paths`() {
        val resumeSource =
            source
                .substringAfter("fun resumeSpotting() {")
                .substringBefore("fun pauseSpotting() {")

        assertTrue(
            "MapKwsViewModel resume should initialize KWS when the manager is missing.",
            resumeSource.contains("if (kwsManager == null)"),
        )
        assertTrue(
            "MapKwsViewModel resume should start spotting after initialization completes.",
            resumeSource.contains("initializeKeywordSpotting(context)") &&
                resumeSource.contains("startSpotting()"),
        )
        assertEquals(
            "MapKwsViewModel resume should restart spotting in both the initialization path and the ready-manager path.",
            2,
            Regex("startSpotting\\(\\)").findAll(resumeSource).count(),
        )
    }
}
