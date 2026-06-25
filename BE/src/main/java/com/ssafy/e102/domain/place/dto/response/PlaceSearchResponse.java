package com.ssafy.e102.domain.place.dto.response;

import java.util.List;

public record PlaceSearchResponse(
	List<PlaceSearchItemResponse> places,
	String nextCursor,
	int size,
	long totalElements,
	boolean hasNext) {
}
