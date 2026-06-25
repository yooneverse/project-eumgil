package com.ssafy.e102.domain.route.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.route.dto.request.WalkRouteSearchRequest;
import com.ssafy.e102.domain.route.dto.request.RerouteRequest;
import com.ssafy.e102.domain.route.dto.request.SelectRouteRequest;
import com.ssafy.e102.domain.route.dto.request.TransitRefreshRequest;
import com.ssafy.e102.domain.route.dto.response.RerouteResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSelectResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSessionResponse;
import com.ssafy.e102.domain.route.dto.response.TransitRefreshResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.service.RerouteService;
import com.ssafy.e102.domain.route.service.RouteSessionCommandService;
import com.ssafy.e102.domain.route.service.RouteSelectService;
import com.ssafy.e102.domain.route.service.TransitRefreshService;
import com.ssafy.e102.domain.route.service.TransitRouteSearchService;
import com.ssafy.e102.domain.route.service.WalkRouteSearchService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 경로 API의 `/routes/**` 진입점이다.
 *
 * <p>Controller는 인증 주체와 API 요청 DTO만 받고, 사용자 profile 조회, 좌표 검증,
 * GraphHopper 후보 조회, 응답 조립은 {@link WalkRouteSearchService}로 넘긴다.
 */
@Tag(name = "길안내", description = "경로 검색, 선택, 안내 종료, 재탐색, 대중교통 도착정보 갱신 API")
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

	private final WalkRouteSearchService walkRouteSearchService;
	private final TransitRouteSearchService transitRouteSearchService;
	private final RerouteService rerouteService;
	private final RouteSelectService routeSelectService;
	private final RouteSessionCommandService routeSessionCommandService;
	private final TransitRefreshService transitRefreshService;

	@Operation(summary = "도보 경로 검색", description = "출발지와 도착지 좌표를 기준으로 보행 경로 후보를 검색합니다.")
	@PostMapping("/search/walk")
	public ApiResponse<WalkRouteSearchResponse> searchWalkRoutes(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		WalkRouteSearchRequest request) {
		return ApiResponse.success(walkRouteSearchService.search(principal.userId(), request));
	}

	@Operation(summary = "대중교통 경로 검색", description = "출발지와 도착지 좌표를 기준으로 도보, 버스, 지하철을 포함한 대중교통 경로 후보를 검색합니다.")
	@PostMapping("/search/transit")
	public ApiResponse<WalkRouteSearchResponse> searchTransitRoutes(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		WalkRouteSearchRequest request) {
		return ApiResponse.success(transitRouteSearchService.search(principal.userId(), request));
	}

	@Operation(summary = "경로 재탐색", description = "안내 중 현재 위치가 기존 경로에서 이탈했을 때 현재 위치 기준으로 새 경로를 재탐색합니다.")
	@PostMapping("/reroute")
	public ApiResponse<RerouteResponse> reroute(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		RerouteRequest request) {
		return ApiResponse.success(rerouteService.reroute(principal.userId(), request));
	}

	@Operation(summary = "안내 경로 선택", description = "검색 후보 중 사용자가 선택한 경로를 안내 세션으로 확정하고 세션 ID를 반환합니다.")
	@PostMapping("/{routeId}/select")
	public ApiResponse<RouteSelectResponse> selectRoute(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "선택할 경로 ID") @PathVariable
		String routeId,
		@Valid @RequestBody
		SelectRouteRequest request) {
		return new ApiResponse<>(
			"S2000",
			routeSelectService.select(principal.userId(), routeId, request),
			"경로가 선택되었습니다.");
	}

	@Operation(summary = "안내 종료", description = "사용자가 안내를 종료하거나 목적지에 도착했을 때 선택 경로 세션을 완료 상태로 전환합니다.")
	@PostMapping("/{routeId}/end")
	public ApiResponse<RouteSessionResponse> endRoute(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "종료할 경로 ID") @PathVariable
		String routeId) {
		return new ApiResponse<>(
			"S2000",
			routeSessionCommandService.endSession(principal.userId(), routeId),
			"안내가 종료되었습니다.");
	}

	@Operation(summary = "대중교통 도착정보 갱신", description = "선택된 대중교통 경로의 버스 또는 지하철 구간에 대해 최신 도착정보를 조회합니다.")
	@PostMapping("/{routeId}/transit-refresh")
	public ApiResponse<TransitRefreshResponse> refreshTransit(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "도착정보를 갱신할 경로 ID") @PathVariable
		String routeId,
		@Valid @RequestBody
		TransitRefreshRequest request) {
		return ApiResponse.successMessage(
			transitRefreshService.refresh(principal.userId(), routeId, request),
			"대중교통 도착정보를 갱신했습니다.");
	}
}
