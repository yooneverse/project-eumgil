package com.ssafy.e102.global.external.bims;

public record BusanBimsBusStop(
	String stopId,
	String stopName,
	String arsNo,
	Double lng,
	Double lat,
	String stopType) {
}
