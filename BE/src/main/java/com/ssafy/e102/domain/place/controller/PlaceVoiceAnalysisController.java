package com.ssafy.e102.domain.place.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeRequest;
import com.ssafy.e102.domain.place.dto.response.VoiceAnalyzeResponse;
import com.ssafy.e102.domain.place.service.PlaceVoiceAnalysisService;
import com.ssafy.e102.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "음성 의도 분석", description = "음성 인식 텍스트를 intent와 실행 파라미터로 해석하는 API")
@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class PlaceVoiceAnalysisController {

	private final PlaceVoiceAnalysisService placeVoiceAnalysisService;

	@Operation(summary = "음성 의도 분석", description = "음성 인식 결과와 대화 이력을 분석해 intent와 후속 실행 파라미터를 추출합니다.")
	@PostMapping("/analyze")
	public ApiResponse<VoiceAnalyzeResponse> analyze(
		@Valid @RequestBody
		VoiceAnalyzeRequest request) {
		return ApiResponse.success(placeVoiceAnalysisService.analyze(request));
	}
}
