package com.ssafy.e102.eumgil.feature.map.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDirectionArrowLayoutTest {
    @Test
    fun `arrow placements use whole polyline cumulative distance instead of restarting per segment`() {
        val placements =
            sampleRouteDirectionArrowPlacements(
                points = listOf(0.0, 35.0, 110.0),
                intervalDistance = 30.0,
                edgePaddingDistance = 0.0,
                measureDistance = { start, end -> end - start },
                interpolatePoint = { start, end, fraction -> start + ((end - start) * fraction) },
            )

        assertEquals(3, placements.size)
        assertEquals(15.0, placements[0].point, 0.0001)
        assertEquals(45.0, placements[1].point, 0.0001)
        assertEquals(75.0, placements[2].point, 0.0001)
        assertEquals(35.0, placements[1].segmentStart, 0.0001)
        assertEquals(35.0, placements[2].segmentStart, 0.0001)
        assertEquals(45.0, placements[1].distanceFromStart, 0.0001)
        assertEquals(75.0, placements[2].distanceFromStart, 0.0001)
    }

    @Test
    fun `arrow placements skip polylines that cannot fit one padded interval`() {
        val placements =
            sampleRouteDirectionArrowPlacements(
                points = listOf(0.0, 20.0),
                intervalDistance = 30.0,
                edgePaddingDistance = 0.0,
                measureDistance = { start, end -> end - start },
                interpolatePoint = { start, end, fraction -> start + ((end - start) * fraction) },
            )

        assertTrue(placements.isEmpty())
    }

    @Test
    fun `arrow placements ignore zero length segments while keeping downstream spacing`() {
        val placements =
            sampleRouteDirectionArrowPlacements(
                points = listOf(0.0, 0.0, 100.0),
                intervalDistance = 40.0,
                edgePaddingDistance = 0.0,
                measureDistance = { start, end -> end - start },
                interpolatePoint = { start, end, fraction -> start + ((end - start) * fraction) },
            )

        assertEquals(2, placements.size)
        assertEquals(20.0, placements[0].point, 0.0001)
        assertEquals(60.0, placements[1].point, 0.0001)
    }

    @Test
    fun `arrow placements can guarantee a minimum single placement for short routes`() {
        val placements =
            sampleRouteDirectionArrowPlacements(
                points = listOf(0.0, 20.0),
                intervalDistance = 30.0,
                edgePaddingDistance = 8.0,
                minimumPlacementCount = 1,
                measureDistance = { start, end -> end - start },
                interpolatePoint = { start, end, fraction -> start + ((end - start) * fraction) },
            )

        assertEquals(1, placements.size)
        assertEquals(10.0, placements.single().point, 0.0001)
        assertEquals(10.0, placements.single().distanceFromStart, 0.0001)
    }

    @Test
    fun `arrow placements keep a midpoint arrow when edge padding consumes the whole short route`() {
        val placements =
            sampleRouteDirectionArrowPlacements(
                points = listOf(0.0, 10.0),
                intervalDistance = 30.0,
                edgePaddingDistance = 8.0,
                minimumPlacementCount = 1,
                measureDistance = { start, end -> end - start },
                interpolatePoint = { start, end, fraction -> start + ((end - start) * fraction) },
            )

        assertEquals(1, placements.size)
        assertEquals(5.0, placements.single().point, 0.0001)
        assertEquals(5.0, placements.single().distanceFromStart, 0.0001)
    }
}
