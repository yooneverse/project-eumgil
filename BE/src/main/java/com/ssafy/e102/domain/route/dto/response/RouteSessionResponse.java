package com.ssafy.e102.domain.route.dto.response;

import java.util.UUID;

import com.ssafy.e102.domain.route.entity.RouteSession;

public record RouteSessionResponse(
	UUID sessionId) {

	public static RouteSessionResponse from(RouteSession routeSession) {
		return new RouteSessionResponse(routeSession.getSessionId());
	}
}
