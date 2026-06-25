package com.ssafy.e102.domain.bookmark.dto.response;

import com.ssafy.e102.domain.place.type.PlaceDetailType;

public record PlaceBookmarkCreateResponse(
	Long bookmarkId,
	String bookmarkTargetId,
	PlaceDetailType targetType,
	Long placeId) {
}
