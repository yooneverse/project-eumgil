package com.ssafy.e102.eumgil.core.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingCopyPolicyTest {
    @Test
    fun `user facing string values do not expose implementation status copy`() {
        val stringValues =
            Regex(
                pattern = "<string name=\"[^\"]+\">(.*?)</string>",
                options = setOf(RegexOption.DOT_MATCHES_ALL),
            ).findAll(File("src/main/res/values/strings.xml").readText())
                .map { match -> match.groupValues[1] }
                .joinToString(separator = "\n")

        val forbiddenTerms =
            listOf(
                "fallback",
                "MOCK",
                "Mock",
                "mock",
                "다음 스레드",
                "이번 작업",
                "route setting",
                "renderable",
                "Preview line",
                "runtime",
                "build config",
                "source:",
            )

        forbiddenTerms.forEach { term ->
            assertFalse(
                "User-facing string values should not expose implementation copy: $term",
                stringValues.contains(term),
            )
        }
    }

    @Test
    fun `route preview state messages do not expose internal diagnostics`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingViewModel.kt")
                .readText()
        val forbiddenDiagnostics =
            listOf(
                "Route preview map is loading.",
                "Destination is required before showing a route preview map.",
                "Destination coordinate is invalid.",
                "No selected route is available for the preview map.",
                "Selected route preview polyline needs at least two points.",
                "Route search is currently limited to Busan Gangseo-gu.",
                "geometry fallback",
            )

        forbiddenDiagnostics.forEach { diagnostic ->
            assertFalse(
                "Route preview state should not expose internal diagnostic copy: $diagnostic",
                source.contains(diagnostic),
            )
        }
    }

    @Test
    fun `route preview fallback descriptions ignore raw geometry diagnostics`() {
        val routeScreen =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val descriptionResolver =
            routeScreen
                .substringAfter("private fun routePreviewFallbackDescription(")
                .substringBefore("@Composable\nprivate fun RouteRiskChip(")

        assertFalse(
            "Polyline-unavailable copy should come from user-facing resources instead of raw fallbackMessage diagnostics.",
            descriptionResolver.contains(
                "previewMap.fallbackMessage ?: stringResource(id = R.string.route_setting_preview_placeholder_description)",
            ),
        )
    }
}
