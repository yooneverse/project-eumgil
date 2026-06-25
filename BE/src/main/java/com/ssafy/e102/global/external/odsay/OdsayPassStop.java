package com.ssafy.e102.global.external.odsay;

import java.math.BigDecimal;

public record OdsayPassStop(
	String stationId,
	String stationName,
	String localStationId,
	String arsId,
	BigDecimal lat,
	BigDecimal lng) {
}
