package com.ssafy.e102.domain.bookmark.controller;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.bookmark.dto.request.CreateFavoriteRouteRequest;
import com.ssafy.e102.domain.bookmark.dto.request.UpdateFavoriteRouteRequest;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteDetailResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteIdResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteListResponse;
import com.ssafy.e102.domain.bookmark.service.FavoriteRouteService;
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

@Tag(name = "경로 북마크", description = "사용자의 경로 북마크 저장, 조회, 수정, 삭제 API")
@Validated
@RestController
@RequestMapping("/favorite-routes")
@RequiredArgsConstructor
public class FavoriteRouteController {

	private static final String DELETE_SUCCESS_MESSAGE = "경로 북마크가 삭제되었습니다.";

	private final FavoriteRouteService favoriteRouteService;

	@Operation(summary = "경로 북마크 목록 조회", description = "현재 로그인한 사용자의 경로 북마크를 최신순 커서 기반으로 조회한다.")
	@GetMapping
	public ApiResponse<FavoriteRouteListResponse> getFavoriteRoutes(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "마지막으로 조회한 경로 북마크 ID. 첫 조회 시 생략한다.") @RequestParam(required = false) @Positive
		Long cursor,
		@Parameter(description = "조회 개수. 허용 범위는 1~100이다.") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
		int size) {
		return ApiResponse.success(favoriteRouteService.getFavoriteRoutes(principal.userId(), cursor, size));
	}

	@Operation(summary = "경로 북마크 상세 조회", description = "현재 로그인한 사용자의 특정 경로 북마크 상세 정보를 조회한다.")
	@GetMapping("/{favRouteId}")
	public ApiResponse<FavoriteRouteDetailResponse> getFavoriteRouteDetail(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "조회할 경로 북마크 ID") @PathVariable @Positive
		Long favRouteId) {
		return ApiResponse.success(favoriteRouteService.getFavoriteRouteDetail(principal.userId(), favRouteId));
	}

	@Operation(summary = "경로 북마크 저장", description = "출발지, 도착지, 경로 옵션을 경로 북마크로 저장한다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FavoriteRouteIdResponse> createFavoriteRoute(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		CreateFavoriteRouteRequest request) {
		return ApiResponse.created(favoriteRouteService.createFavoriteRoute(principal.userId(), request));
	}

	@Operation(summary = "경로 북마크 수정", description = "현재 로그인한 사용자가 소유한 경로 북마크의 이름, 출발지, 도착지, 경로 옵션을 수정한다.")
	@PatchMapping("/{favRouteId}")
	public ApiResponse<FavoriteRouteIdResponse> updateFavoriteRoute(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "수정할 경로 북마크 ID") @PathVariable @Positive
		Long favRouteId,
		@Valid @RequestBody
		UpdateFavoriteRouteRequest request) {
		return ApiResponse.success(favoriteRouteService.updateFavoriteRoute(principal.userId(), favRouteId, request));
	}

	@Operation(summary = "경로 북마크 삭제", description = "현재 로그인한 사용자가 소유한 경로 북마크를 삭제한다.")
	@DeleteMapping("/{favRouteId}")
	public ApiResponse<Void> deleteFavoriteRoute(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "삭제할 경로 북마크 ID") @PathVariable @Positive
		Long favRouteId) {
		favoriteRouteService.deleteFavoriteRoute(principal.userId(), favRouteId);
		return ApiResponse.successMessage(DELETE_SUCCESS_MESSAGE);
	}
}
