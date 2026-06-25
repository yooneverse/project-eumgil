package com.ssafy.e102.global.external.odsay;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.ssafy.e102.domain.route.type.TransportMode;

public record OdsayTransitLeg(
	TransportMode type,
	BigDecimal distanceMeter,
	int sectionTimeMinute,
	String startName,
	BigDecimal startLat,
	BigDecimal startLng,
	String startId,
	String startLocalStationId,
	String startArsId,
	BigDecimal startExitLat,
	BigDecimal startExitLng,
	String endName,
	BigDecimal endLat,
	BigDecimal endLng,
	String endId,
	String endLocalStationId,
	String endArsId,
	BigDecimal endExitLat,
	BigDecimal endExitLng,
	Integer wayCode,
	String wayName,
	List<OdsayTransitLane> lanes,
	List<OdsayPassStop> passStops,
	Map<String, Object> snapshot) {
}
