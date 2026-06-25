package com.ssafy.e102.domain.admin.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.admin.dto.response.AdminBottleneckMonitoringResponse;
import com.ssafy.e102.domain.admin.service.AdminBottleneckMonitoringService;
import com.ssafy.e102.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 통계", description = "관리자 병목구간 통계 API")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminBottleneckMonitoringController {

	private final AdminBottleneckMonitoringService adminBottleneckMonitoringService;

	@Operation(summary = "관리자 병목구간 통계 조회", description = "route_sessions와 hazard_reports 실데이터를 기반으로 병목구간 통계 화면 데이터를 조회한다.")
	@GetMapping("/bottleneck-monitoring")
	public ApiResponse<AdminBottleneckMonitoringResponse> getMonitoring(
		@Parameter(description = "조회 시작일. 생략하면 종료일 기준 최근 7일이다.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate from,
		@Parameter(description = "조회 종료일. 생략하면 오늘이다.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate to) {
		return ApiResponse.success(adminBottleneckMonitoringService.getMonitoring(from, to));
	}
}
