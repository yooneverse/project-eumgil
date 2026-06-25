package com.ssafy.e102.domain.admin.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.admin.dto.request.AdminAreaAssignmentStatusUpdateRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminAreaAssignmentUpsertRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminUserRoleUpdateRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaAssignmentListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaAssignmentResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAuditLogListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminMeResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminUserListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminUserResponse;
import com.ssafy.e102.domain.admin.service.AdminService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자", description = "관리자 인증 주체 및 권한 확인 API")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminService adminService;

	@Operation(summary = "관리자 인증 주체 조회", description = "현재 로그인한 사용자가 관리자 권한인지 확인하고 관리자 권한 정보를 반환한다.")
	@GetMapping("/me")
	public ApiResponse<AdminMeResponse> getMe(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal) {
		return ApiResponse.success(adminService.getMe(principal.userId()));
	}

	@Operation(summary = "관리자 사용자 목록 조회", description = "ADMIN 권한 사용자 목록만 조회한다.")
	@GetMapping("/users")
	public ApiResponse<AdminUserListResponse> getUsers() {
		return ApiResponse.success(adminService.getUsers());
	}

	@Operation(summary = "관리자 사용자 권한 수정", description = "사용자 role을 USER 또는 ADMIN으로 변경한다.")
	@PatchMapping("/users/{userId}/role")
	public ApiResponse<AdminUserResponse> updateUserRole(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "권한을 변경할 사용자 ID") @PathVariable
		UUID userId,
		@RequestBody @Valid
		AdminUserRoleUpdateRequest request) {
		return ApiResponse.success(adminService.updateUserRole(principal.userId(), userId, request));
	}

	@Operation(summary = "관리자 구/동 담당 목록 조회", description = "관리자 화면에서 사용할 구/동 담당자와 작업 상태를 조회한다.")
	@GetMapping("/area-assignments")
	public ApiResponse<AdminAreaAssignmentListResponse> getAreaAssignments() {
		return ApiResponse.success(adminService.getAreaAssignments());
	}

	@Operation(summary = "관리자 구/동 담당자 지정", description = "구/동 담당자와 작업 상태를 생성 또는 수정한다.")
	@PutMapping("/area-assignments")
	public ApiResponse<AdminAreaAssignmentResponse> upsertAreaAssignment(
		@RequestBody @Valid
		AdminAreaAssignmentUpsertRequest request) {
		return ApiResponse.success(adminService.upsertAreaAssignment(request));
	}

	@Operation(summary = "관리자 구/동 작업 상태 수정", description = "구/동 담당 항목의 작업 상태를 수정한다.")
	@PatchMapping("/area-assignments/{assignmentId}/status")
	public ApiResponse<AdminAreaAssignmentResponse> updateAreaAssignmentStatus(
		@Parameter(description = "담당 항목 ID") @PathVariable
		Long assignmentId,
		@RequestBody @Valid
		AdminAreaAssignmentStatusUpdateRequest request) {
		return ApiResponse.success(adminService.updateAreaAssignmentStatus(assignmentId, request));
	}

	@Operation(summary = "관리자 변경 로그 조회", description = "관리자 화면에서 수행한 변경 작업을 최신순으로 조회한다.")
	@GetMapping("/audit-logs")
	public ApiResponse<AdminAuditLogListResponse> getAuditLogs(
		@Parameter(description = "이전 페이지 마지막 logId") @RequestParam(required = false)
		Long cursor,
		@Parameter(description = "작업 종류 필터") @RequestParam(required = false)
		String action,
		@Parameter(description = "구 필터") @RequestParam(required = false)
		String gu,
		@Parameter(description = "동 필터") @RequestParam(required = false)
		String dong,
		@Parameter(description = "작업자 userId 필터") @RequestParam(required = false)
		UUID actorUserId,
		@Parameter(description = "조회 개수. 허용 범위는 1~100이다.") @RequestParam(required = false)
		Integer size) {
		int normalizedSize = size == null ? 50 : Math.min(Math.max(size, 1), 100);
		return ApiResponse.success(adminService.getAuditLogs(cursor, action, gu, dong, actorUserId, normalizedSize));
	}
}
