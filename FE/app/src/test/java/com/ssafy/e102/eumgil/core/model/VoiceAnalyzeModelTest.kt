package com.ssafy.e102.eumgil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAnalyzeModelTest {

    // ── Intent ────────────────────────────────────────────────

    @Test
    fun `voice analyze intent defines exactly 14 values`() {
        assertEquals(14, VoiceAnalyzeIntent.values().size)
    }

    @Test
    fun `voice analyze intent contains all required intents in order`() {
        val expected = listOf(
            "PLACE_SEARCH",
            "CATEGORY_SEARCH",
            "BOOKMARK_ADD",
            "BOOKMARK_DELETE",
            "NAVIGATE",
            "SHOW_BOOKMARKS",
            "SHOW_FAVORITE_ROUTES",
            "LOGOUT",
            "REPORT",
            "NAVIGATION_END",
            "OPEN_MY_PAGE",
            "OPEN_MAP",
            "ASK",
            "UNKNOWN",
        )
        assertEquals(expected, VoiceAnalyzeIntent.values().map { it.name })
    }

    @Test
    fun `voice analyze intent unknown can be resolved from string`() {
        val intent = runCatching { enumValueOf<VoiceAnalyzeIntent>("UNKNOWN") }
            .getOrDefault(VoiceAnalyzeIntent.UNKNOWN)
        assertEquals(VoiceAnalyzeIntent.UNKNOWN, intent)
    }

    @Test
    fun `voice analyze intent falls back to unknown for unrecognized string`() {
        val intent = runCatching { enumValueOf<VoiceAnalyzeIntent>("NOT_EXIST") }
            .getOrDefault(VoiceAnalyzeIntent.UNKNOWN)
        assertEquals(VoiceAnalyzeIntent.UNKNOWN, intent)
    }

    // ── toJsonString ──────────────────────────────────────────

    @Test
    fun `toJsonString includes all 10 fields even when all are null`() {
        val result = VoiceAnalyzeResult(
            intent = VoiceAnalyzeIntent.UNKNOWN,
            placeName = null,
            category = null,
            bookmarkAction = null,
            departure = null,
            destination = null,
            reportType = null,
            description = null,
            confirmed = null,
            confirmationMessage = null,
        )
        val json = result.toJsonString()

        listOf(
            "\"intent\"",
            "\"placeName\"",
            "\"category\"",
            "\"bookmarkAction\"",
            "\"departure\"",
            "\"destination\"",
            "\"reportType\"",
            "\"description\"",
            "\"confirmed\"",
            "\"confirmationMessage\"",
        ).forEach { field ->
            assertTrue("$field 필드가 toJsonString() 결과에 없음", json.contains(field))
        }
    }

    @Test
    fun `toJsonString serializes null fields as null not omitted`() {
        val result = VoiceAnalyzeResult(
            intent = VoiceAnalyzeIntent.PLACE_SEARCH,
            placeName = null,
            category = null,
            bookmarkAction = null,
            departure = null,
            destination = null,
            reportType = null,
            description = null,
            confirmed = null,
            confirmationMessage = null,
        )
        val json = result.toJsonString()

        assertTrue(json.contains("\"placeName\":null"))
        assertTrue(json.contains("\"category\":null"))
        assertTrue(json.contains("\"bookmarkAction\":null"))
        assertTrue(json.contains("\"departure\":null"))
        assertTrue(json.contains("\"destination\":null"))
        assertTrue(json.contains("\"reportType\":null"))
        assertTrue(json.contains("\"description\":null"))
        assertTrue(json.contains("\"confirmed\":null"))
        assertTrue(json.contains("\"confirmationMessage\":null"))
    }

    @Test
    fun `toJsonString serializes non-null string fields with quotes`() {
        val result = VoiceAnalyzeResult(
            intent = VoiceAnalyzeIntent.PLACE_SEARCH,
            placeName = "부산역",
            category = "음식점",
            bookmarkAction = "add",
            departure = "서면",
            destination = "해운대",
            reportType = "STAIRS_STEP",
            description = "계단 있음",
            confirmed = true,
            confirmationMessage = "부산역을 찾으시나요?",
        )
        val json = result.toJsonString()

        assertTrue(json.contains("\"placeName\":\"부산역\""))
        assertTrue(json.contains("\"category\":\"음식점\""))
        assertTrue(json.contains("\"bookmarkAction\":\"add\""))
        assertTrue(json.contains("\"departure\":\"서면\""))
        assertTrue(json.contains("\"destination\":\"해운대\""))
        assertTrue(json.contains("\"reportType\":\"STAIRS_STEP\""))
        assertTrue(json.contains("\"description\":\"계단 있음\""))
        assertTrue(json.contains("\"confirmed\":true"))
        assertTrue(json.contains("\"confirmationMessage\":\"부산역을 찾으시나요?\""))
    }

    @Test
    fun `toJsonString serializes intent as string name`() {
        val result = VoiceAnalyzeResult(
            intent = VoiceAnalyzeIntent.CATEGORY_SEARCH,
            placeName = null,
            category = "관광지",
            bookmarkAction = null,
            departure = null,
            destination = null,
            reportType = null,
            description = null,
            confirmed = null,
            confirmationMessage = null,
        )
        val json = result.toJsonString()

        assertTrue(json.contains("\"intent\":\"CATEGORY_SEARCH\""))
    }

    @Test
    fun `toJsonString confirmed false is serialized as boolean false`() {
        val result = VoiceAnalyzeResult(
            intent = VoiceAnalyzeIntent.PLACE_SEARCH,
            placeName = "부산역",
            category = null,
            bookmarkAction = null,
            departure = null,
            destination = null,
            reportType = null,
            description = null,
            confirmed = false,
            confirmationMessage = null,
        )
        val json = result.toJsonString()

        assertTrue(json.contains("\"confirmed\":false"))
    }
}