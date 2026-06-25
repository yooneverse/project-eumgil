package com.ssafy.e102.domain.route.dto.response;

import java.util.List;

import com.ssafy.e102.domain.route.type.TransportMode;

public record TransitRefreshResponse(
	TransportMode type,
	TransitArrivalStatus arrivalStatus,
	List<TransitArrivalResponse> transits) {
}
