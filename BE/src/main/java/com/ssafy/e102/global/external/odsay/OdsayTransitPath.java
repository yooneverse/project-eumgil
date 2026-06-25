package com.ssafy.e102.global.external.odsay;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OdsayTransitPath(
	BigDecimal totalDistanceMeter,
	int totalTimeMinute,
	int totalWalkMeter,
	int busTransitCount,
	int subwayTransitCount,
	String mapObj,
	List<OdsayTransitLeg> legs,
	Map<String, Object> snapshot) {
}
