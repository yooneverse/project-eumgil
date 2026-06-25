package com.ssafy.e102.domain.report.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.report.dto.response.HazardMarkerListResponse;
import com.ssafy.e102.domain.report.service.HazardReportService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "지도 경고 마커", description = "관리자 승인 제보를 지도 viewport 기준으로 조회하는 API")
@Validated
@RestController
@RequestMapping("/hazard/markers")
@RequiredArgsConstructor
public class HazardMarkerController {

	private final HazardReportService hazardReportService;

	@Operation(summary = "승인 제보 마커 조회", description = "현재 지도 viewport bbox 안의 관리자 승인 제보 마커를 조회한다.")
	@GetMapping({"", "/"})
	public ApiResponse<HazardMarkerListResponse> getApprovedHazardMarkers(
		@SuppressWarnings("unused") @AuthenticationPrincipal AuthPrincipal principal,
		@Parameter(description = "viewport 남서쪽 위도") @RequestParam
		Double swLat,
		@Parameter(description = "viewport 남서쪽 경도") @RequestParam
		Double swLng,
		@Parameter(description = "viewport 북동쪽 위도") @RequestParam
		Double neLat,
		@Parameter(description = "viewport 북동쪽 경도") @RequestParam
		Double neLng) {
		return ApiResponse.success(hazardReportService.getApprovedHazardMarkers(swLat, swLng, neLat, neLng));
	}
}
