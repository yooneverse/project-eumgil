package com.ssafy.e102.domain.route.dto.response;

public record LowFloorBusReservationResponse(
	String stopName,
	String arsNo,
	String routeNo,
	String vehicleNo,
	Integer remainingMinute,
	Integer remainingStopCount) {
}
