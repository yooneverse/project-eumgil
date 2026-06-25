package com.ssafy.e102.domain.bookmark.dto.response;

import com.ssafy.e102.domain.bookmark.entity.FavoriteRoute;
import com.ssafy.e102.domain.bookmark.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record FavoriteRouteResponse(
	Long favRouteId,
	String routeName,
	String startLabel,
	String endLabel,
	GeoPointResponse startPoint,
	GeoPointResponse endPoint,
	TransportMode transportMode,
	RouteOption routeOption) {

	public static FavoriteRouteResponse of(FavoriteRoute favoriteRoute, GeoPointConverter geoPointConverter) {
		return new FavoriteRouteResponse(
			favoriteRoute.getFavRouteId(),
			favoriteRoute.getRouteName(),
			favoriteRoute.getStartLabel(),
			favoriteRoute.getEndLabel(),
			geoPointConverter.toResponse(favoriteRoute.getStartPoint()),
			geoPointConverter.toResponse(favoriteRoute.getEndPoint()),
			favoriteRoute.getTransportMode(),
			favoriteRoute.getRouteOption());
	}
}
