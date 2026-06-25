package com.ssafy.e102.domain.route.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteWarningCode;
import com.ssafy.e102.domain.route.type.TransportMode;

/**
 * 검색 화면에 노출되는 route 후보 하나의 응답 DTO다.
 *
 * <p>GraphHopper path와 segment/step service 결과가 이 shape로 합쳐지고, API 명세에 없는 내부 값은 담지 않는다.
 */
public record RouteSummaryResponse(
	String routeId,
	TransportMode transportMode,
	RouteOption routeOption,
	List<RouteOption> routeOptions,
	String title,
	BigDecimal distanceMeter,
	int durationSecond,
	int estimatedTimeMinute,
	List<RouteBadge> badges,
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	List<RouteWarningCode> warnings,
	String geometry,
	List<RouteLegResponse> legs) {

	public RouteSummaryResponse {
		routeOptions = routeOptions == null ? List.of() : routeOptions;
		badges = badges == null ? List.of() : badges;
		warnings = warnings == null ? List.of() : warnings;
		legs = legs == null ? List.of() : legs;
	}

	public RouteSummaryResponse(
		String routeId,
		TransportMode transportMode,
		RouteOption routeOption,
		List<RouteOption> routeOptions,
		String title,
		BigDecimal distanceMeter,
		int durationSecond,
		int estimatedTimeMinute,
		List<RouteBadge> badges,
		String geometry,
		List<RouteLegResponse> legs) {
		this(
			routeId,
			transportMode,
			routeOption,
			routeOptions,
			title,
			distanceMeter,
			durationSecond,
			estimatedTimeMinute,
			badges,
			List.of(),
			geometry,
			legs);
	}

	public RouteSummaryResponse(
		String routeId,
		TransportMode transportMode,
		RouteOption routeOption,
		String title,
		BigDecimal distanceMeter,
		int durationSecond,
		int estimatedTimeMinute,
		List<RouteBadge> badges,
		String geometry,
		List<RouteLegResponse> legs) {
		this(
			routeId,
			transportMode,
			routeOption,
			List.of(routeOption),
			title,
			distanceMeter,
			durationSecond,
			estimatedTimeMinute,
			badges,
			geometry,
			legs);
	}
}
