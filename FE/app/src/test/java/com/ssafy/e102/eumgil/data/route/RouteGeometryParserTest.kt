package com.ssafy.e102.eumgil.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryParserTest {
    private val parser: RouteGeometryParser = DefaultRouteGeometryParser()

    @Test
    fun `parse converts linestring geometry to renderable polyline using latitude longitude order`() {
        val result = parser.parse("LINESTRING(129.075600 35.179600, 129.076000 35.179900)")

        assertEquals(RouteGeometryParseStatus.SUCCESS, result.status)
        assertEquals(2, result.parsedPointCount)
        assertTrue(result.polyline.isRenderable)
        assertEquals(35.179600, result.polyline.points.first().latitude, 0.0)
        assertEquals(129.075600, result.polyline.points.first().longitude, 0.0)
        assertEquals(35.179900, result.polyline.points.last().latitude, 0.0)
        assertEquals(129.076000, result.polyline.points.last().longitude, 0.0)
        assertEquals(result.polyline.points.first(), result.anchorCoordinate)
    }

    @Test
    fun `parse returns empty polyline fallback when geometry is blank`() {
        val result = parser.parse("  ")

        assertEquals(RouteGeometryParseStatus.EMPTY_INPUT, result.status)
        assertTrue(result.polyline.points.isEmpty())
        assertFalse(result.polyline.isRenderable)
    }

    @Test
    fun `parse returns malformed status when closing parenthesis is missing`() {
        val result = parser.parse("LINESTRING(129.075600 35.179600, 129.076000 35.179900")

        assertEquals(RouteGeometryParseStatus.MALFORMED_GEOMETRY, result.status)
        assertTrue(result.polyline.points.isEmpty())
    }

    @Test
    fun `parse converts point geometry to anchor coordinate without renderable polyline`() {
        val result = parser.parse("POINT(129.075600 35.179600)")

        assertEquals(RouteGeometryParseStatus.SUCCESS, result.status)
        assertEquals(1, result.parsedPointCount)
        assertFalse(result.polyline.isRenderable)
        assertEquals(1, result.polyline.points.size)
        assertEquals(35.179600, result.anchorCoordinate?.latitude ?: -1.0, 0.0)
        assertEquals(129.075600, result.anchorCoordinate?.longitude ?: -1.0, 0.0)
    }

    @Test
    fun `parse returns empty polyline fallback when geometry type is unsupported`() {
        val result = parser.parse("POLYGON((129.075600 35.179600, 129.076000 35.179900, 129.076200 35.179700))")

        assertEquals(RouteGeometryParseStatus.UNSUPPORTED_GEOMETRY, result.status)
        assertTrue(result.polyline.points.isEmpty())
    }

    @Test
    fun `parse returns empty polyline fallback when any coordinate token is malformed`() {
        val result = parser.parse("LINESTRING(129.075600 35.179600, invalid 35.180000)")

        assertEquals(RouteGeometryParseStatus.INVALID_COORDINATE, result.status)
        assertTrue(result.polyline.points.isEmpty())
    }

    @Test
    fun `parse returns empty polyline fallback when coordinate range is invalid`() {
        val result = parser.parse("LINESTRING(229.075600 35.179600, 129.076000 95.179900)")

        assertEquals(RouteGeometryParseStatus.INVALID_COORDINATE, result.status)
        assertTrue(result.polyline.points.isEmpty())
    }

    @Test
    fun `parse returns empty polyline fallback when linestring has fewer than two points`() {
        val result = parser.parse("LINESTRING(129.075600 35.179600)")

        assertEquals(RouteGeometryParseStatus.INSUFFICIENT_POINTS, result.status)
        assertEquals(1, result.polyline.points.size)
        assertEquals(35.179600, result.anchorCoordinate?.latitude ?: -1.0, 0.0)
        assertEquals(129.075600, result.anchorCoordinate?.longitude ?: -1.0, 0.0)
    }

    @Test
    fun `parse accepts repeated whitespace and line breaks between coordinate tokens`() {
        val result =
            parser.parse(
                """
                LINESTRING(
                    129.075600   35.179600,
                    129.076000
                    35.179900
                )
                """.trimIndent(),
            )

        assertEquals(RouteGeometryParseStatus.SUCCESS, result.status)
        assertEquals(2, result.parsedPointCount)
        assertTrue(result.polyline.isRenderable)
    }
}
