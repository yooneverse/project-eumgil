package com.ssafy.e102.eumgil.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingMathTest {
    @Test
    fun `heading normalization keeps azimuth in zero inclusive three sixty exclusive range`() {
        assertEquals(0.0, normalizeHeadingDegrees(0.0), 0.0)
        assertEquals(0.0, normalizeHeadingDegrees(360.0), 0.0)
        assertEquals(359.0, normalizeHeadingDegrees(-1.0), 0.0)
        assertEquals(1.0, normalizeHeadingDegrees(721.0), 0.0)
    }

    @Test
    fun `heading delta uses the shortest turn across north`() {
        assertEquals(20.0, shortestHeadingDeltaDegrees(fromDegrees = 350.0, toDegrees = 10.0), 0.0)
        assertEquals(-20.0, shortestHeadingDeltaDegrees(fromDegrees = 10.0, toDegrees = 350.0), 0.0)
    }

    @Test
    fun `heading smoothing follows the shortest turn and ignores tiny jitter`() {
        assertEquals(
            0.0,
            smoothHeadingDegrees(
                previousDegrees = 350.0,
                nextDegrees = 10.0,
                smoothingFactor = 0.5,
                minimumDeltaDegrees = 1.0,
            ),
            0.0001,
        )
        assertEquals(
            20.0,
            smoothHeadingDegrees(
                previousDegrees = 20.0,
                nextDegrees = 22.0,
                smoothingFactor = 0.5,
                minimumDeltaDegrees = 3.0,
            ),
            0.0001,
        )
    }
}
