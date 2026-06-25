package com.ssafy.e102.eumgil.data.mock.fixture

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.data.repository.BookmarkData

object MockBookmarkFixtures {
    val defaultBookmarks: List<BookmarkData> =
        listOf(
            BookmarkData(
                placeId = "demo-bookmark-busan-station-elevator",
                placeName = "부산역 엘리베이터",
                address = "부산 동구 중앙대로 206",
                latitude = 35.1151,
                longitude = 129.0415,
                category = PlaceCategory.ELEVATOR.name,
            ),
        )
}
