package com.ssafy.e102.eumgil.feature.mypage

import com.ssafy.e102.eumgil.core.external.dialUriString
import com.ssafy.e102.eumgil.core.external.duribalDialUriString
import org.junit.Assert.assertEquals
import org.junit.Test

class MyPageRouteDialIntentTest {
    @Test
    fun `duribal dial uri keeps the call center number prefilled`() {
        assertEquals("tel:15551114", duribalDialUriString())
    }

    @Test
    fun `generic dial uri strips non digit characters from place phone numbers`() {
        assertEquals("tel:0511234567", dialUriString("051-123-4567"))
    }
}
