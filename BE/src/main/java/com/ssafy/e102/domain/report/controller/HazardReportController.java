package com.ssafy.e102.domain.report.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportImageUploadUrlRequest;
import com.ssafy.e102.domain.report.dto.request.CreateHazardReportImageUploadBatchRequest;
import com.ssafy.e102.domain.report.dto.request.CreateHazardReportRequest;
import com.ssafy.e102.domain.report.dto.response.CreateHazardReportImageUploadBatchResponse;
import com.ssafy.e102.domain.report.dto.response.CreateHazardReportImageUploadUrlResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportIdResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportListResponse;
import com.ssafy.e102.domain.report.service.HazardReportImageUploadService;
import com.ssafy.e102.domain.report.service.HazardReportService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "도로 상태 제보", description = "사용자 도로 상태 제보 등록 및 내 제보 조회 API")
@Validated
@RestController
@RequestMapping("/hazard-reports")
@RequiredArgsConstructor
public class HazardReportController {

	private final HazardReportService hazardReportService;
	private final HazardReportImageUploadService hazardReportImageUploadService;

	@Operation(summary = "제보 이미지 업로드 URL 발급", description = "현재 로그인한 사용자가 제보 사진을 직접 업로드할 수 있는 presigned URL을 발급한다.")
	@PostMapping("/images/presigned-upload")
	public ApiResponse<CreateHazardReportImageUploadUrlResponse> createPresignedUploadUrl(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		CreateHazardReportImageUploadUrlRequest request) {
		return ApiResponse.success(hazardReportImageUploadService.createUploadUrl(principal.userId(), request));
	}

	@PostMapping("/images/presigned-upload/batch")
	public ApiResponse<CreateHazardReportImageUploadBatchResponse> createPresignedUploadUrls(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		CreateHazardReportImageUploadBatchRequest request) {
		return ApiResponse.success(
			new CreateHazardReportImageUploadBatchResponse(
				hazardReportImageUploadService.createUploadUrls(principal.userId(), request.files())));
	}

	@Operation(summary = "도로 상태 제보 등록", description = "현재 로그인한 사용자가 도로 상태 문제 위치, 유형, 설명, 첨부 이미지를 등록한다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<HazardReportIdResponse> createHazardReport(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@RequestHeader(name = "Idempotency-Key", required = false)
		String idempotencyKey,
		@Valid @RequestBody
		CreateHazardReportRequest request) {
		return ApiResponse.created(hazardReportService.createHazardReport(principal.userId(), request, idempotencyKey));
	}

	@Operation(summary = "내 제보 목록 조회", description = "현재 로그인한 사용자가 등록한 도로 상태 제보 목록을 최신순 커서 기반으로 조회한다.")
	@GetMapping("/me")
	public ApiResponse<HazardReportListResponse> getMyHazardReports(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "마지막으로 조회한 제보 ID. 첫 조회 시 생략한다.") @RequestParam(required = false) @Positive
		Long cursor,
		@Parameter(description = "조회 개수. 허용 범위는 1~100이다.") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
		int size) {
		return ApiResponse.success(hazardReportService.getMyHazardReports(principal.userId(), cursor, size));
	}

	@Operation(summary = "내 제보 상세 조회", description = "현재 로그인한 사용자가 등록한 특정 도로 상태 제보의 상세 정보와 첨부 이미지를 조회한다.")
	@GetMapping("/me/{reportId}")
	public ApiResponse<HazardReportDetailResponse> getMyHazardReportDetail(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "조회할 제보 ID") @PathVariable @Positive
		Long reportId) {
		return ApiResponse.success(hazardReportService.getMyHazardReportDetail(principal.userId(), reportId));
	}
}
