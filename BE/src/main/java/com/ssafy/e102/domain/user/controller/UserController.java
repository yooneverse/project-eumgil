package com.ssafy.e102.domain.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.user.dto.request.UpdateUserTypeRequest;
import com.ssafy.e102.domain.user.dto.response.UserMeResponse;
import com.ssafy.e102.domain.user.dto.response.UserTypeResponse;
import com.ssafy.e102.domain.user.service.UserService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "사용자", description = "내 정보 조회, 사용자 유형 수정, 회원 탈퇴 API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 가입 정보와 선택 사용자 유형을 조회한다.")
	@GetMapping("/me")
	public ApiResponse<UserMeResponse> getMe(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal) {
		return ApiResponse.success(userService.getMe(principal.userId()));
	}

	@Operation(summary = "사용자 유형 수정", description = "현재 로그인한 사용자의 주 사용자 유형과 이동 보조 하위 유형을 수정한다.")
	@PatchMapping("/me/user-type")
	public ApiResponse<UserTypeResponse> updateUserType(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		UpdateUserTypeRequest request) {
		return ApiResponse.success(userService.updateUserType(
			principal.userId(),
			request.selectedPrimaryUserType(),
			request.selectedMobilitySubtype()));
	}

	@Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자를 탈퇴 처리하고 접근 토큰과 재발급 토큰을 더 이상 사용할 수 없게 한다.")
	@DeleteMapping("/me")
	public ApiResponse<Void> withdraw(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal) {
		userService.withdraw(principal.userId(), principal.accessToken());
		return ApiResponse.successMessage("회원탈퇴가 완료되었습니다.");
	}
}
