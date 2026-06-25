package com.ssafy.e102.domain.admin.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardSummaryResponse;
import com.ssafy.e102.domain.admin.service.AdminDashboardService;
import com.ssafy.e102.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 홈", description = "관리자 홈 운영 요약 API")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;

	@Operation(summary = "관리자 홈 요약 조회", description = "사용자, 길안내, 제보, 데이터 품질, 운영 로그 지표를 기간 기준으로 조회한다.")
	@GetMapping("/summary")
	public ApiResponse<AdminDashboardSummaryResponse> getSummary(
		@Parameter(description = "조회 시작일. 생략하면 종료일 기준 최근 7일이다.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate from,
		@Parameter(description = "조회 종료일. 생략하면 오늘이다.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate to) {
		return ApiResponse.success(adminDashboardService.getSummary(from, to));
	}

	@Operation(summary = "관리자 병목 후보 지도 조회", description = "선택 경로 스냅샷과 신고 데이터를 기반으로 병목 후보 지도 데이터를 조회한다.")
	@GetMapping("/bottlenecks")
	public ApiResponse<AdminDashboardBottleneckResponse> getBottlenecks(
		@Parameter(description = "조회 시작일. 생략하면 종료일 기준 최근 7일이다.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate from,
		@Parameter(description = "조회 종료일. 생략하면 오늘이다.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate to,
		@Parameter(description = "조회할 병목 후보 수. 기본 12, 최대 50.") @RequestParam(required = false)
		Integer limit) {
		return ApiResponse.success(adminDashboardService.getBottlenecks(from, to, limit));
	}
}
