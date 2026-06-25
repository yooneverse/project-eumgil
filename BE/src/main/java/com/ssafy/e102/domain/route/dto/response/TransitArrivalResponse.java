package com.ssafy.e102.domain.route.dto.response;

public record TransitArrivalResponse(
	String routeNo,
	Integer remainingMinute,
	Boolean isLowFloor) {
}
