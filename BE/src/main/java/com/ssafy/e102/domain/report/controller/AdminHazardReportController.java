package com.ssafy.e102.domain.report.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.report.dto.request.StartHazardRouteReviewRequest;
import com.ssafy.e102.domain.report.dto.request.UpdateHazardRouteReviewRequest;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDeleteResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardRouteReviewResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportStatusResponse;
import com.ssafy.e102.domain.report.service.AdminHazardRouteReviewService;
import com.ssafy.e102.domain.report.service.AdminHazardReportService;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 제보", description = "관리자 도로 상태 제보 조회 및 처리 API")
@Validated
@RestController
@RequestMapping("/admin/hazard-reports")
@RequiredArgsConstructor
public class AdminHazardReportController {

	private final AdminHazardReportService adminHazardReportService;
	private final AdminHazardRouteReviewService adminHazardRouteReviewService;

	@Operation(summary = "관리자 제보 목록 조회", description = "관리자 페이지에서 도로 상태 제보를 최신순 커서 기반으로 조회한다.")
	@GetMapping
	public ApiResponse<AdminHazardReportListResponse> getHazardReports(
		@Parameter(description = "처리 상태 필터. 생략하면 전체 상태를 조회한다.") @RequestParam(required = false)
		ReportStatus status,
		@Parameter(description = "마지막으로 조회한 제보 ID. 첫 조회 시 생략한다.") @RequestParam(required = false) @Positive
		Long cursor,
		@Parameter(description = "조회 개수. 허용 범위는 1~100이다.") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
		int size) {
		return ApiResponse.success(adminHazardReportService.getHazardReports(
			status,
			cursor,
			size));
	}

	@Operation(summary = "관리자 제보 상세 조회", description = "관리자 페이지에서 도로 상태 제보 상세 정보와 전체 첨부 이미지를 조회한다.")
	@GetMapping("/{reportId}")
	public ApiResponse<AdminHazardReportDetailResponse> getHazardReportDetail(
		@Parameter(description = "조회할 제보 ID") @PathVariable @Positive
		Long reportId) {
		return ApiResponse.success(adminHazardReportService.getHazardReportDetail(reportId));
	}

	@Operation(summary = "제보 승인", description = "대기 상태의 도로 상태 제보를 승인 상태로 변경한다. 이미 처리된 제보는 다시 처리할 수 없다.")
	@PatchMapping("/{reportId}/approve")
	public ApiResponse<AdminHazardReportStatusResponse> approveHazardReport(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "승인할 제보 ID") @PathVariable @Positive
		Long reportId) {
		return ApiResponse.success(adminHazardReportService.approveHazardReport(
			reportId,
			principal == null ? null : principal.userId()));
	}

	@Operation(summary = "제보 반려", description = "대기 상태의 도로 상태 제보를 반려 상태로 변경한다. 현재 ERD에는 반려 사유를 저장하지 않는다.")
	@PatchMapping("/{reportId}/reject")
	public ApiResponse<AdminHazardReportStatusResponse> rejectHazardReport(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "반려할 제보 ID") @PathVariable @Positive
		Long reportId) {
		return ApiResponse.success(adminHazardReportService.rejectHazardReport(
			reportId,
			principal == null ? null : principal.userId()));
	}

	@Operation(summary = "제보 삭제", description = "승인 완료 또는 반려된 도로 상태 제보를 관리 목록에서 삭제한다. 도로 세그먼트와 라우팅 오버레이는 변경하지 않는다.")
	@DeleteMapping("/{reportId}")
	public ApiResponse<AdminHazardReportDeleteResponse> deleteHazardReport(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "삭제할 제보 ID") @PathVariable @Positive
		Long reportId) {
		return ApiResponse.success(adminHazardReportService.deleteHazardReport(
			reportId,
			principal == null ? null : principal.userId()));
	}

	@Operation(summary = "제보 경로 검수 시작", description = "승인 검수 또는 원상복구 검수를 시작하고 진행 중 draft를 반환한다.")
	@PostMapping("/{reportId}/route-review/start")
	public ApiResponse<AdminHazardRouteReviewResponse> startRouteReview(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "검수를 시작할 제보 ID") @PathVariable @Positive
		Long reportId,
		@RequestBody @Valid
		StartHazardRouteReviewRequest request) {
		return ApiResponse.success(adminHazardRouteReviewService.startRouteReview(
			principal == null ? null : principal.userId(),
			reportId,
			request));
	}

	@Operation(summary = "제보 경로 검수 draft 저장", description = "진행 중인 경로 검수의 선택 세그먼트와 속성 draft를 저장한다.")
	@PatchMapping("/{reportId}/route-review")
	public ApiResponse<AdminHazardRouteReviewResponse> updateRouteReview(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "검수 중인 제보 ID") @PathVariable @Positive
		Long reportId,
		@RequestBody @Valid
		UpdateHazardRouteReviewRequest request) {
		return ApiResponse.success(adminHazardRouteReviewService.updateRouteReview(
			principal == null ? null : principal.userId(),
			reportId,
			request));
	}

	@Operation(summary = "제보 경로 검수 완료", description = "진행 중인 경로 검수를 완료하고 제보 최종 상태를 갱신한다.")
	@PostMapping("/{reportId}/route-review/complete")
	public ApiResponse<AdminHazardRouteReviewResponse> completeRouteReview(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "검수 완료할 제보 ID") @PathVariable @Positive
		Long reportId) {
		return ApiResponse.success(adminHazardRouteReviewService.completeRouteReview(
			principal == null ? null : principal.userId(),
			reportId));
	}
}
