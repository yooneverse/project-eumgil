package com.ssafy.e102.eumgil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSizePreferenceTest {
    @Test
    fun `text size preferences expose fixed app scale presets`() {
        assertEquals(1.00f, TextSizePreference.DEFAULT.scale, 0.0f)
        assertEquals(1.15f, TextSizePreference.LARGE.scale, 0.0f)
        assertEquals(1.30f, TextSizePreference.EXTRA_LARGE.scale, 0.0f)
    }

    @Test
    fun `stored value parser falls back to default for null and invalid values`() {
        assertEquals(TextSizePreference.DEFAULT, TextSizePreference.fromStoredValue(null))
        assertEquals(TextSizePreference.DEFAULT, TextSizePreference.fromStoredValue(""))
        assertEquals(TextSizePreference.DEFAULT, TextSizePreference.fromStoredValue("HUGE"))
    }

    @Test
    fun `stored value parser accepts enum names`() {
        assertEquals(TextSizePreference.DEFAULT, TextSizePreference.fromStoredValue("DEFAULT"))
        assertEquals(TextSizePreference.LARGE, TextSizePreference.fromStoredValue("LARGE"))
        assertEquals(TextSizePreference.EXTRA_LARGE, TextSizePreference.fromStoredValue("EXTRA_LARGE"))
    }
}
