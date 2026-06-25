package com.ssafy.e102.eumgil.data.mock.datasource

import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.data.mock.fixture.MockPlaceFixtures

class SearchMockDataSource {
    suspend fun search(query: SearchQuery): List<SearchResult> = MockPlaceFixtures.search(query)
}
