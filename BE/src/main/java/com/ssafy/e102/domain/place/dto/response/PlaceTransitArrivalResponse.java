package com.ssafy.e102.domain.place.dto.response;

public record PlaceTransitArrivalResponse(
	String transitType,
	String routeName,
	String direction,
	Integer remainingMinute,
	Boolean isLowFloor,
	String source) {
}
