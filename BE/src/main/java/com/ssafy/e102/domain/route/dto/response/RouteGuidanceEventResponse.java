package com.ssafy.e102.domain.route.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * leg 시작점과 route 시작점 기준 누적 거리/시간으로 표현하는 안내 이벤트 응답 DTO다.
 */
public record RouteGuidanceEventResponse(
	int sequence,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	RouteGuidanceEventType type,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	RouteGuidanceDirection direction,
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	List<RouteGuidanceFeature> features,
	BigDecimal distanceFromLegStartMeter,
	int durationFromLegStartSecond,
	BigDecimal distanceFromRouteStartMeter,
	int durationFromRouteStartSecond,
	String geometry) {

	public RouteGuidanceEventResponse {
		features = features == null ? List.of() : List.copyOf(features);
	}

	public RouteGuidanceEventResponse(
		int sequence,
		RouteGuidanceEventType type,
		BigDecimal distanceFromLegStartMeter,
		int durationFromLegStartSecond,
		String geometry) {
		this(
			sequence,
			type,
			null,
			List.of(),
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			geometry);
	}

	public RouteGuidanceEventResponse(
		int sequence,
		RouteGuidanceEventType type,
		RouteGuidanceDirection direction,
		BigDecimal distanceFromLegStartMeter,
		int durationFromLegStartSecond,
		String geometry) {
		this(
			sequence,
			type,
			direction,
			List.of(),
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			geometry);
	}

	public RouteGuidanceEventResponse(
		int sequence,
		RouteGuidanceEventType type,
		RouteGuidanceDirection direction,
		List<RouteGuidanceFeature> features,
		BigDecimal distanceFromLegStartMeter,
		int durationFromLegStartSecond,
		String geometry) {
		this(
			sequence,
			type,
			direction,
			features,
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			geometry);
	}

	public RouteGuidanceEventResponse(
		int sequence,
		RouteGuidanceEventType type,
		RouteGuidanceDirection direction,
		BigDecimal distanceFromLegStartMeter,
		int durationFromLegStartSecond,
		BigDecimal distanceFromRouteStartMeter,
		int durationFromRouteStartSecond,
		String geometry) {
		this(
			sequence,
			type,
			direction,
			List.of(),
			distanceFromLegStartMeter,
			durationFromLegStartSecond,
			distanceFromRouteStartMeter,
			durationFromRouteStartSecond,
			geometry);
	}

}
