package com.ssafy.e102.domain.bookmark.dto.response;

import java.util.List;

import com.ssafy.e102.domain.bookmark.entity.FavoriteRoute;
import com.ssafy.e102.global.geo.GeoPointConverter;

public record FavoriteRouteListResponse(
	List<FavoriteRouteResponse> content,
	int size,
	Long nextCursor,
	boolean hasNext) {

	public static FavoriteRouteListResponse of(
		List<FavoriteRoute> favoriteRoutes,
		int size,
		boolean hasNext,
		GeoPointConverter geoPointConverter) {
		List<FavoriteRouteResponse> responses = favoriteRoutes.stream()
			.map(favoriteRoute -> FavoriteRouteResponse.of(favoriteRoute, geoPointConverter))
			.toList();
		Long nextCursor = hasNext && !favoriteRoutes.isEmpty()
			? favoriteRoutes.getLast().getFavRouteId()
			: null;

		return new FavoriteRouteListResponse(
			responses,
			size,
			nextCursor,
			hasNext);
	}
}
