package com.ssafy.e102.domain.bookmark.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.bookmark.entity.FavoriteRoute;
import com.ssafy.e102.domain.bookmark.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record FavoriteRouteDetailResponse(
	Long favRouteId,
	String routeName,
	String startLabel,
	String endLabel,
	GeoPointResponse startPoint,
	GeoPointResponse endPoint,
	TransportMode transportMode,
	RouteOption routeOption,
	JsonNode route) {

	public static FavoriteRouteDetailResponse of(FavoriteRoute favoriteRoute, GeoPointConverter geoPointConverter) {
		return new FavoriteRouteDetailResponse(
			favoriteRoute.getFavRouteId(),
			favoriteRoute.getRouteName(),
			favoriteRoute.getStartLabel(),
			favoriteRoute.getEndLabel(),
			geoPointConverter.toResponse(favoriteRoute.getStartPoint()),
			geoPointConverter.toResponse(favoriteRoute.getEndPoint()),
			favoriteRoute.getTransportMode(),
			favoriteRoute.getRouteOption(),
			favoriteRoute.getRouteSnapshotJson());
	}
}
