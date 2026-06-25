package com.ssafy.e102.domain.bookmark.dto.request;

public record UpdateFavoriteRouteRequest(
	String startLabel,
	String endLabel) {

	public boolean hasAnyField() {
		return startLabel != null
			|| endLabel != null;
	}
}
