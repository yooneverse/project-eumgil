package com.ssafy.e102.domain.admin.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.route.dto.response.BusStopSyncResponse;
import com.ssafy.e102.domain.route.service.BusStopMasterService;
import com.ssafy.e102.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 대중교통", description = "관리자 대중교통 마스터 데이터 관리 API")
@RestController
@RequestMapping("/admin/transit")
@RequiredArgsConstructor
public class AdminTransitController {

	private final BusStopMasterService busStopMasterService;

	@Operation(summary = "BIMS 버스정류장 마스터 동기화", description = "BIMS busStopList 전체 정류장을 DB 마스터 테이블에 반영한다.")
	@PostMapping("/bus-stops/sync")
	public ApiResponse<BusStopSyncResponse> syncBusStops() {
		return ApiResponse.successMessage(
			busStopMasterService.syncFromBims(),
			"버스정류장 마스터를 동기화했습니다.");
	}
}
