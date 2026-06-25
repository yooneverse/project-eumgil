package com.ssafy.e102.domain.place.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.place.dto.request.PlaceClickDetailRequest;
import com.ssafy.e102.domain.place.dto.response.PlaceClickDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceListResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceReverseGeocodeResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceSearchResponse;
import com.ssafy.e102.domain.place.service.PlaceService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "장소", description = "장소 검색, 주변 장소 목록, 장소 상세 조회 API")
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

	private final PlaceService placeService;

	@Operation(summary = "텍스트 장소 검색", description = "키워드를 기준으로 카카오 장소 검색 결과를 반환하고, 내부 장소와 매칭되면 접근성 정보를 함께 제공한다.")
	@GetMapping("/search")
	public ApiResponse<PlaceSearchResponse> searchPlaces(
		@Parameter(description = "검색어") @RequestParam(required = false)
		String keyword,
		@Parameter(description = "검색 중심 위도") @RequestParam(required = false)
		String lat,
		@Parameter(description = "검색 중심 경도") @RequestParam(required = false)
		String lng,
		@Parameter(description = "검색 반경. 단위는 미터입니다.") @RequestParam(required = false)
		String radius,
		@Parameter(description = "다음 검색 결과 조회를 위한 커서") @RequestParam(required = false)
		String cursor,
		@Parameter(description = "검색 정렬 기준. relevance 또는 distance") @RequestParam(required = false)
		String sort,
		@Parameter(description = "조회 개수") @RequestParam(required = false)
		String size) {
		return ApiResponse.success(placeService.searchPlaces(keyword, lat, lng, radius, cursor, sort, size));
	}

	@Operation(summary = "좌표 주소 변환", description = "위도와 경도를 기준으로 카카오 주소 변환 결과를 반환한다.")
	@GetMapping("/reverse-geocode")
	public ApiResponse<PlaceReverseGeocodeResponse> reverseGeocode(
		@Parameter(description = "변환할 위도") @RequestParam(required = false)
		String lat,
		@Parameter(description = "변환할 경도") @RequestParam(required = false)
		String lng) {
		return ApiResponse.success(placeService.reverseGeocode(lat, lng));
	}

	@Operation(summary = "장소 목록 조회", description = "현재 위치, 카테고리, 접근성 기능 조건으로 내부 장소 목록을 조회하고 로그인 사용자 기준 북마크 여부를 함께 반환한다.")
	@GetMapping
	public ApiResponse<PlaceListResponse> getPlaces(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "조회 중심 위도") @RequestParam(required = false)
		String lat,
		@Parameter(description = "조회 중심 경도") @RequestParam(required = false)
		String lng,
		@Parameter(description = "조회 반경. 단위는 미터입니다.") @RequestParam(required = false)
		String radius,
		@Parameter(description = "장소 카테고리 필터") @RequestParam(required = false)
		String category,
		@Parameter(description = "접근성 기능 유형 필터") @RequestParam(required = false)
		String featureType) {
		return ApiResponse.success(placeService.getPlaces(principal.userId(), lat, lng, radius, category, featureType));
	}

	@Operation(summary = "장소 상세 조회", description = "내부 장소 ID 기준으로 장소 상세 정보, 접근성 기능, 로그인 사용자 기준 북마크 여부를 조회한다.")
	@GetMapping("/{placeId}")
	public ApiResponse<PlaceDetailResponse> getPlace(
		@AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "조회할 장소 ID") @PathVariable
		String placeId) {
		return ApiResponse.success(placeService.getPlace(principal.userId(), placeId));
	}

	@Operation(summary = "지도 클릭 상세 조회", description = "지도에서 선택한 POI 또는 주소 좌표를 기준으로 상세 정보를 조회합니다.")
	@PostMapping("/detail")
	public ApiResponse<PlaceClickDetailResponse> getPlaceDetail(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "지도 클릭 상세 요청") @Valid @RequestBody
		PlaceClickDetailRequest request) {
		return ApiResponse.success(placeService.getPlaceDetail(principal.userId(), request));
	}
}
