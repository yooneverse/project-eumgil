package com.ssafy.e102.domain.bookmark.dto.response;

import java.util.List;

public record PlaceBookmarkListResponse(
	List<PlaceBookmarkItemResponse> content,
	int size,
	Long nextCursor,
	boolean hasNext) {
}
