package com.ssafy.e102.eumgil.data.mock.datasource

import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.data.mock.fixture.MockPlaceFixtures

class PlacesMockDataSource {
    suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> = MockPlaceFixtures.getPlaces(query)

    suspend fun getPlaceDetail(placeId: String): PlaceDetail? = MockPlaceFixtures.getPlaceDetail(placeId)
}
