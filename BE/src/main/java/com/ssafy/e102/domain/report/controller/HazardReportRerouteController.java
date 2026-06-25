package com.ssafy.e102.domain.report.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.report.dto.request.HazardReportRerouteRequest;
import com.ssafy.e102.domain.report.dto.response.HazardReportRerouteResponse;
import com.ssafy.e102.domain.report.service.HazardReportRerouteService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/hazard")
@RequiredArgsConstructor
public class HazardReportRerouteController {

	private final HazardReportRerouteService hazardReportRerouteService;

	@Operation(summary = "request-local reroute after hazard report submission",
		description = "Returns an alternate route that avoids the submitted hazard point only for this request.")
	@PostMapping("/{reportId}/reroute")
	public ApiResponse<HazardReportRerouteResponse> rerouteAfterHazardReport(
		@AuthenticationPrincipal
		AuthPrincipal authPrincipal,
		@PathVariable
		Long reportId,
		@Valid @RequestBody
		HazardReportRerouteRequest request) {
		return ApiResponse.success(hazardReportRerouteService.reroute(authPrincipal.userId(), reportId, request));
	}
}
