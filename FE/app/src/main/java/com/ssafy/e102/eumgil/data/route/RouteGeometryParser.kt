package com.ssafy.e102.eumgil.data.route

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RoutePolyline

interface RouteGeometryParser {
    fun parse(geometry: String?): RouteGeometryParseResult
}

data class RouteGeometryParseResult(
    val polyline: RoutePolyline = RoutePolyline(),
    val anchorCoordinate: GeoCoordinate? = null,
    val status: RouteGeometryParseStatus,
    val parsedPointCount: Int = 0,
)

enum class RouteGeometryParseStatus {
    SUCCESS,
    EMPTY_INPUT,
    MALFORMED_GEOMETRY,
    UNSUPPORTED_GEOMETRY,
    INVALID_COORDINATE,
    INSUFFICIENT_POINTS,
}

class DefaultRouteGeometryParser : RouteGeometryParser {
    override fun parse(geometry: String?): RouteGeometryParseResult {
        val normalizedGeometry = geometry?.trim().orEmpty()
        if (normalizedGeometry.isBlank()) {
            return RouteGeometryParseResult(status = RouteGeometryParseStatus.EMPTY_INPUT)
        }

        val openingParenthesisIndex = normalizedGeometry.indexOf('(')
        val closingParenthesisIndex = normalizedGeometry.lastIndexOf(')')
        if (
            openingParenthesisIndex <= 0 ||
            closingParenthesisIndex <= openingParenthesisIndex ||
            closingParenthesisIndex != normalizedGeometry.lastIndex
        ) {
            return RouteGeometryParseResult(status = RouteGeometryParseStatus.MALFORMED_GEOMETRY)
        }

        val geometryType = normalizedGeometry.substring(0, openingParenthesisIndex).trim()
        if (geometryType.isBlank()) {
            return RouteGeometryParseResult(status = RouteGeometryParseStatus.MALFORMED_GEOMETRY)
        }

        val coordinatePayload =
            normalizedGeometry
                .substring(openingParenthesisIndex + 1, closingParenthesisIndex)
                .trim()

        if (coordinatePayload.isBlank()) {
            return RouteGeometryParseResult(status = RouteGeometryParseStatus.INVALID_COORDINATE)
        }

        return when {
            geometryType.equals(LINESTRING_TYPE, ignoreCase = true) -> {
                val coordinates =
                    parseCoordinates(coordinatePayload)
                        ?: return RouteGeometryParseResult(status = RouteGeometryParseStatus.INVALID_COORDINATE)
                coordinates.toLinestringParseResult()
            }

            geometryType.equals(POINT_TYPE, ignoreCase = true) -> {
                val coordinates =
                    parseCoordinates(coordinatePayload)
                        ?: return RouteGeometryParseResult(status = RouteGeometryParseStatus.INVALID_COORDINATE)
                coordinates.toPointParseResult()
            }

            else -> RouteGeometryParseResult(status = RouteGeometryParseStatus.UNSUPPORTED_GEOMETRY)
        }
    }

    private fun List<GeoCoordinate>.toLinestringParseResult(): RouteGeometryParseResult {
        val polyline = RoutePolyline(points = this)
        if (size < MINIMUM_RENDERABLE_POINT_COUNT) {
            return RouteGeometryParseResult(
                polyline = polyline,
                anchorCoordinate = firstOrNull(),
                status = RouteGeometryParseStatus.INSUFFICIENT_POINTS,
                parsedPointCount = size,
            )
        }

        return RouteGeometryParseResult(
            polyline = polyline,
            anchorCoordinate = first(),
            status = RouteGeometryParseStatus.SUCCESS,
            parsedPointCount = size,
        )
    }

    private fun List<GeoCoordinate>.toPointParseResult(): RouteGeometryParseResult {
        if (size != 1) {
            return RouteGeometryParseResult(
                polyline = RoutePolyline(points = this),
                anchorCoordinate = firstOrNull(),
                status = RouteGeometryParseStatus.INVALID_COORDINATE,
                parsedPointCount = size,
            )
        }

        return RouteGeometryParseResult(
            polyline = RoutePolyline(points = this),
            anchorCoordinate = single(),
            status = RouteGeometryParseStatus.SUCCESS,
            parsedPointCount = 1,
        )
    }

    private fun parseCoordinates(coordinatePayload: String): List<GeoCoordinate>? {
        val coordinates = mutableListOf<GeoCoordinate>()
        var cursor = 0

        while (cursor < coordinatePayload.length) {
            cursor = coordinatePayload.skipWhitespace(cursor)
            if (cursor >= coordinatePayload.length) break

            val longitudeToken = coordinatePayload.readToken(cursor) ?: return null
            val longitude = longitudeToken.value.toDoubleOrNull() ?: return null
            cursor = coordinatePayload.skipWhitespace(longitudeToken.nextIndex)

            val latitudeToken = coordinatePayload.readToken(cursor) ?: return null
            val latitude = latitudeToken.value.toDoubleOrNull() ?: return null
            if (longitude !in MIN_LONGITUDE..MAX_LONGITUDE || latitude !in MIN_LATITUDE..MAX_LATITUDE) {
                return null
            }
            coordinates +=
                GeoCoordinate(
                    latitude = latitude,
                    longitude = longitude,
                )

            cursor = coordinatePayload.skipWhitespace(latitudeToken.nextIndex)
            if (cursor >= coordinatePayload.length) break
            if (coordinatePayload[cursor] != COORDINATE_DELIMITER) {
                return null
            }
            cursor += 1
        }

        return coordinates
    }

    private fun String.skipWhitespace(startIndex: Int): Int {
        var index = startIndex
        while (index < length && this[index].isWhitespace()) {
            index += 1
        }
        return index
    }

    private fun String.readToken(startIndex: Int): ParsedToken? {
        if (startIndex >= length) return null

        var endIndex = startIndex
        while (endIndex < length) {
            val character = this[endIndex]
            if (character.isWhitespace() || character == COORDINATE_DELIMITER) {
                break
            }
            endIndex += 1
        }
        if (endIndex == startIndex) return null

        return ParsedToken(
            value = substring(startIndex, endIndex),
            nextIndex = endIndex,
        )
    }

    companion object {
        private const val LINESTRING_TYPE: String = "LINESTRING"
        private const val POINT_TYPE: String = "POINT"
        private const val MINIMUM_RENDERABLE_POINT_COUNT: Int = 2
        private const val MIN_LATITUDE: Double = -90.0
        private const val MAX_LATITUDE: Double = 90.0
        private const val MIN_LONGITUDE: Double = -180.0
        private const val MAX_LONGITUDE: Double = 180.0
        private const val COORDINATE_DELIMITER: Char = ','
    }
}

private data class ParsedToken(
    val value: String,
    val nextIndex: Int,
)
