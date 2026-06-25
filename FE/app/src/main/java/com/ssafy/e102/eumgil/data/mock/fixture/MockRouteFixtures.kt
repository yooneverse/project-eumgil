package com.ssafy.e102.eumgil.data.mock.fixture

import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto

object MockRouteFixtures {
    val defaultFixture: RouteFixtureTemplate
        get() = MockRouteFixtureCatalog.defaultFixture

    fun resolveSearchRequest(request: RouteSearchRequestDto): RouteFixtureSearchPayload {
        val fixture = defaultFixture
        return RouteFixtureSearchPayload(
            fixtureId = fixture.fixtureId,
            fixtureName = fixture.name,
            request = request,
            response = fixture.resolve(request),
        )
    }

    fun searchRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
        resolveSearchRequest(request).response
}

data class RouteFixtureSearchPayload(
    val fixtureId: String,
    val fixtureName: String,
    val request: RouteSearchRequestDto,
    val response: RouteSearchResponseDto,
)
