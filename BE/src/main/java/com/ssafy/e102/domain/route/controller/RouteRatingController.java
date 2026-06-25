package com.ssafy.e102.domain.route.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.route.dto.request.RouteRatingRequest;
import com.ssafy.e102.domain.route.dto.response.RouteRatingResponse;
import com.ssafy.e102.domain.route.service.RouteRatingService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "경로 평가", description = "경로 안내 결과 평가 API")
@RestController
@RequestMapping("/route-ratings")
@RequiredArgsConstructor
public class RouteRatingController {

	private final RouteRatingService routeRatingService;

	@Operation(summary = "경로 평가 등록", description = "종료된 안내 세션에 대해 사용자가 남긴 별점을 저장하거나 갱신합니다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<RouteRatingResponse> rateRoute(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		RouteRatingRequest request) {
		return ApiResponse.created(routeRatingService.rate(principal.userId(), request));
	}
}
