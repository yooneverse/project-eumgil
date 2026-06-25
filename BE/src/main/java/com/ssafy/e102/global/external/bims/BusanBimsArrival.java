package com.ssafy.e102.global.external.bims;

public record BusanBimsArrival(
	String stopId,
	String lineId,
	String routeNo,
	Integer remainingMinute,
	Boolean isLowFloor,
	String vehicleNo,
	Integer remainingStopCount) {

	public BusanBimsArrival(String stopId, String lineId, String routeNo, Integer remainingMinute, Boolean isLowFloor) {
		this(stopId, lineId, routeNo, remainingMinute, isLowFloor, null, null);
	}
}
