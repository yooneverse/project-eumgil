package com.ssafy.e102.domain.admin.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.admin.dto.request.AdminPlaceAccessibilityFeaturesUpdateRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminPlaceUpdateRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminRoutePreviewRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminRoadNetworkEditApplyRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminRoadSegmentAttributesUpdateRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminFacilityPayloadResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminGeoJsonFeatureResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminLineStringGeometryResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminPlaceDetailResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoutePreviewResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkEditApplyResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkEditJobResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkBridgePayloadResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadSegmentPropertiesResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadSegmentUpdateResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStateResponse;
import com.ssafy.e102.domain.admin.service.AdminMapService;
import com.ssafy.e102.domain.admin.service.AdminRoutingApplyService;
import com.ssafy.e102.domain.admin.service.AdminRoutePreviewService;
import com.ssafy.e102.domain.admin.service.AdminRoadNetworkEditJobService;
import com.ssafy.e102.domain.admin.service.AdminRoadNetworkEditService;
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

@Tag(name = "관리자 지도", description = "관리자 보행 네트워크 및 편의시설 조회 API")
@Validated
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMapController {

	private final AdminMapService adminMapService;
	private final AdminRoutingApplyService adminRoutingApplyService;
	private final AdminRoadNetworkEditService adminRoadNetworkEditService;
	private final AdminRoadNetworkEditJobService adminRoadNetworkEditJobService;
	private final AdminRoutePreviewService adminRoutePreviewService;

	@Operation(summary = "관리자 검수 구/동 목록 조회", description = "데이터베이스에 적재된 관리자 검수 구/동 목록을 조회한다.")
	@GetMapping("/areas")
	public ApiResponse<AdminAreaListResponse> getAreas() {
		return ApiResponse.success(adminMapService.getAreas());
	}

	@Operation(summary = "관리자 보행 네트워크 조회", description = "행정동 경계와 교차하는 보행 네트워크 구간을 지도 표시용 형식으로 조회한다.")
	@GetMapping("/road-network/segments")
	public ApiResponse<AdminRoadNetworkResponse> getRoadNetwork(
		@Parameter(description = "구") @RequestParam(required = false)
		String gu,
		@Parameter(description = "동") @RequestParam(required = false)
		String dong,
		@Parameter(description = "클리핑 중심 위도") @RequestParam(required = false)
		Double centerLat,
		@Parameter(description = "클리핑 중심 경도") @RequestParam(required = false)
		Double centerLng,
		@Parameter(description = "클리핑 반경(m)") @RequestParam(required = false)
		Integer radiusMeter,
		@Parameter(description = "구/동 미지정 전체 조회 fallback 개수. 구/동을 지정하면 해당 구/동의 모든 구간을 조회한다. 허용 범위는 1~20000이다.") @RequestParam(defaultValue = "10000") @Min(1) @Max(20000)
		int limit) {
		return ApiResponse.success(adminMapService.getRoadNetwork(gu, dong, limit, centerLat, centerLng, radiusMeter));
	}

	@Operation(summary = "관리자 보행 segment 상세 조회", description = "선택한 segment의 DB 최신 검수 속성을 단건으로 조회한다.")
	@GetMapping("/road-network/segments/{edgeId}")
	public ApiResponse<AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadSegmentPropertiesResponse>> getRoadSegment(
		@Parameter(description = "조회할 segment ID") @PathVariable @Positive
		Long edgeId,
		@Parameter(description = "구") @RequestParam
		String gu,
		@Parameter(description = "동") @RequestParam(required = false)
		String dong) {
		return ApiResponse.success(adminMapService.getRoadSegment(edgeId, gu, dong));
	}

	@Operation(summary = "관리자 보행 네트워크 연결 후보 조회", description = "선택한 구/동에서 서로 다른 보행 네트워크 컴포넌트를 연결할 수 있는 가이드 후보를 조회한다.")
	@GetMapping("/road-network/bridges")
	public ApiResponse<AdminRoadNetworkBridgePayloadResponse> getRoadNetworkBridges(
		@Parameter(description = "구") @RequestParam
		String gu,
		@Parameter(description = "동") @RequestParam
		String dong) {
		return ApiResponse.success(adminMapService.getRoadNetworkBridges(gu, dong));
	}

	@Operation(summary = "관리자 보행 네트워크 편집 반영", description = "관리자 페이지의 추가/삭제 편집안을 보행 네트워크 테이블에 반영한다.")
	@PostMapping("/road-network/edits/apply")
	public ApiResponse<AdminRoadNetworkEditApplyResponse> applyRoadNetworkEdits(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@RequestBody @Valid
		AdminRoadNetworkEditApplyRequest request) {
		return ApiResponse.success(adminRoadNetworkEditService.apply(principal.userId(), request));
	}

	@Operation(summary = "관리자 보행 네트워크 편집 반영 작업 생성", description = "대량 추가/삭제 편집안을 비동기 작업으로 등록하고 작업 ID를 반환한다.")
	@PostMapping("/road-network/edits/jobs")
	public ApiResponse<AdminRoadNetworkEditJobResponse> createRoadNetworkEditJob(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@RequestBody @Valid
		AdminRoadNetworkEditApplyRequest request) {
		return ApiResponse.success(adminRoadNetworkEditJobService.create(principal.userId(), request));
	}

	@Operation(summary = "관리자 보행 네트워크 편집 반영 작업 조회", description = "비동기 편집 반영 작업의 처리 상태와 결과를 조회한다.")
	@GetMapping("/road-network/edits/jobs/{jobId}")
	public ApiResponse<AdminRoadNetworkEditJobResponse> getRoadNetworkEditJob(
		@Parameter(description = "조회할 편집 반영 작업 ID") @PathVariable @Positive
		Long jobId) {
		return ApiResponse.success(adminRoadNetworkEditJobService.findById(jobId));
	}

	@Operation(summary = "관리자 경로 미리보기", description = "선택한 구/동의 DB road_segments 기준으로 보행 사용자 유형별 안전/빠른 경로를 비교한다.")
	@PostMapping("/routes/preview")
	public ApiResponse<AdminRoutePreviewResponse> previewRoute(
		@RequestBody @Valid
		AdminRoutePreviewRequest request) {
		return ApiResponse.success(adminRoutePreviewService.preview(request));
	}

	@Operation(summary = "관리자 보행 segment 속성 수정", description = "선택한 보행 네트워크 segment의 검수 속성을 수정한다.")
	@PatchMapping("/road-network/segments/{edgeId}/attributes")
	public ApiResponse<AdminRoadSegmentUpdateResponse> updateRoadSegmentAttributes(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "수정할 segment ID") @PathVariable @Positive
		Long edgeId,
		@Parameter(description = "담당 구") @RequestParam
		String gu,
		@Parameter(description = "담당 동") @RequestParam
		String dong,
		@RequestBody @Valid
		AdminRoadSegmentAttributesUpdateRequest request) {
		return ApiResponse
			.success(adminMapService.updateRoadSegmentAttributes(principal.userId(), edgeId, gu, dong, request));
	}

	@Operation(summary = "관리자 경로 반영 상태 조회", description = "DB에 저장된 routing override 변경이 runtime에 반영됐는지 현재 상태를 조회한다.")
	@GetMapping("/routing/overrides/apply-state")
	public ApiResponse<AdminRoutingApplyStateResponse> getRoutingApplyState() {
		return ApiResponse.success(adminRoutingApplyService.getCurrentState());
	}

	@Operation(summary = "관리자 경로 반영 실행", description = "저장된 routing override 최신 상태 전체를 기준으로 GraphHopper runtime reload를 1회 수행한다.")
	@PostMapping("/routing/overrides/apply")
	public ApiResponse<AdminRoutingApplyStateResponse> applyRoutingOverrides() {
		return ApiResponse.success(adminRoutingApplyService.applyRoutingOverrides());
	}

	@Operation(summary = "관리자 편의시설 조회", description = "데이터베이스에 적재된 장소를 지도 표시용 형식으로 조회한다.")
	@GetMapping("/places/facilities")
	public ApiResponse<AdminFacilityPayloadResponse> getFacilities(
		@Parameter(description = "구") @RequestParam(required = false)
		String gu,
		@Parameter(description = "동") @RequestParam(required = false)
		String dong,
		@Parameter(description = "조회 개수. 허용 범위는 1~20000이다.") @RequestParam(defaultValue = "20000") @Min(1) @Max(20000)
		int limit) {
		return ApiResponse.success(adminMapService.getFacilities(gu, dong, limit));
	}

	@Operation(summary = "관리자 장소 상세 조회", description = "관리자 페이지에서 장소 기본 정보와 접근성 속성 목록을 조회한다.")
	@GetMapping("/places/{placeId}")
	public ApiResponse<AdminPlaceDetailResponse> getPlace(
		@Parameter(description = "조회할 장소 ID") @PathVariable @Positive
		Long placeId) {
		return ApiResponse.success(adminMapService.getPlace(placeId));
	}

	@Operation(summary = "관리자 장소 기본 정보 수정", description = "관리자 페이지에서 장소명, 카테고리, 주소, 좌표, 외부 제공자 장소 ID를 수정한다. 값이 비어 있는 필드는 기존 값을 유지한다.")
	@PatchMapping("/places/{placeId}")
	public ApiResponse<AdminPlaceDetailResponse> updatePlace(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "수정할 장소 ID") @PathVariable @Positive
		Long placeId,
		@Parameter(description = "담당 구") @RequestParam
		String gu,
		@Parameter(description = "담당 동") @RequestParam
		String dong,
		@RequestBody @Valid
		AdminPlaceUpdateRequest request) {
		return ApiResponse.success(adminMapService.updatePlace(principal.userId(), placeId, gu, dong, request));
	}

	@Operation(summary = "관리자 장소 접근성 속성 교체", description = "관리자 페이지에서 해당 장소의 접근성 속성 목록을 요청 목록으로 전체 교체한다.")
	@PutMapping("/places/{placeId}/accessibility-features")
	public ApiResponse<AdminPlaceDetailResponse> updatePlaceAccessibilityFeatures(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "수정할 장소 ID") @PathVariable @Positive
		Long placeId,
		@Parameter(description = "담당 구") @RequestParam
		String gu,
		@Parameter(description = "담당 동") @RequestParam
		String dong,
		@RequestBody @Valid
		AdminPlaceAccessibilityFeaturesUpdateRequest request) {
		return ApiResponse
			.success(adminMapService.updatePlaceAccessibilityFeatures(principal.userId(), placeId, gu, dong, request));
	}
}
