package com.ssafy.e102.eumgil.data.mock.datasource

import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.data.mock.fixture.MockFacilitySeedFixtures

class FacilitySeedMockDataSource {
    suspend fun getSeedCatalog(): FacilitySeedCatalog = MockFacilitySeedFixtures.getSeedCatalog()
}
