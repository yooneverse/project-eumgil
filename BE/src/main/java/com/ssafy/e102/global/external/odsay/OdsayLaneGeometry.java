package com.ssafy.e102.global.external.odsay;

import com.ssafy.e102.domain.route.type.TransportMode;

public record OdsayLaneGeometry(
	TransportMode type,
	String geometry) {
}
