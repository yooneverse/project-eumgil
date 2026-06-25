package com.ssafy.e102.domain.route.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TransitLaneOptionResponse(
	String routeNo,
	Integer remainingMinute,
	Integer durationSecond,
	Integer estimatedTimeMinute,
	Boolean isLowFloor,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	LowFloorBusReservationResponse lowFloorReservation) {

	public TransitLaneOptionResponse(
		String routeNo,
		Integer remainingMinute,
		Integer durationSecond,
		Integer estimatedTimeMinute,
		Boolean isLowFloor) {
		this(routeNo, remainingMinute, durationSecond, estimatedTimeMinute, isLowFloor, null);
	}
}
