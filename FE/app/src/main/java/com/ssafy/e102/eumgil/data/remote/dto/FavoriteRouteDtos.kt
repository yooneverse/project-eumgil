package com.ssafy.e102.eumgil.data.remote.dto

import com.ssafy.e102.eumgil.data.route.RouteDto

data class FavoriteRoutePointDto(
    val lat: Double,
    val lng: Double,
)

data class FavoriteRouteListItemDto(
    val favRouteId: Long,
    val routeName: String,
    val startLabel: String,
    val endLabel: String,
    val startPoint: FavoriteRoutePointDto,
    val endPoint: FavoriteRoutePointDto,
    val transportMode: String?,
    val routeOption: String,
)

data class FavoriteRoutePageDto(
    val content: List<FavoriteRouteListItemDto>,
    val size: Int,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

data class CreateFavoriteRouteResponseDto(
    val favRouteId: Long,
)

data class FavoriteRouteDetailDto(
    val favRouteId: Long,
    val routeName: String,
    val startLabel: String,
    val endLabel: String,
    val startPoint: FavoriteRoutePointDto,
    val endPoint: FavoriteRoutePointDto,
    val transportMode: String?,
    val routeOption: String,
    val route: RouteDto? = null,
)
