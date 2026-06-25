package com.ssafy.e102.eumgil.data.mock.datasource

import com.ssafy.e102.eumgil.data.mock.fixture.MockRouteFixtures
import com.ssafy.e102.eumgil.data.mock.fixture.RouteFixtureSearchPayload
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto

class RouteMockDataSource(
    private val fixturePayloadProvider: (RouteSearchRequestDto) -> RouteFixtureSearchPayload =
        MockRouteFixtures::resolveSearchRequest,
) {
    suspend fun searchRouteFixture(request: RouteSearchRequestDto): RouteFixtureSearchPayload =
        fixturePayloadProvider(request)

    suspend fun searchRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
        searchRouteFixture(request).response
}
