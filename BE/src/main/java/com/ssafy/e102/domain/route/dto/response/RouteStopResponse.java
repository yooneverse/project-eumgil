package com.ssafy.e102.domain.route.dto.response;

import java.math.BigDecimal;

public record RouteStopResponse(
	String name,
	BigDecimal lat,
	BigDecimal lng) {
}
