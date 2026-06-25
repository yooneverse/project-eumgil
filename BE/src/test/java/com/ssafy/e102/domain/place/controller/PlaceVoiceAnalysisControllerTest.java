package com.ssafy.e102.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeRequest;
import com.ssafy.e102.domain.place.dto.response.VoiceAnalyzeResponse;
import com.ssafy.e102.domain.place.service.PlaceVoiceAnalysisService;
import com.ssafy.e102.domain.place.type.VoiceIntent;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;
import com.ssafy.e102.global.exception.GlobalExceptionHandler;

class PlaceVoiceAnalysisControllerTest {

	@Mock
	private PlaceVoiceAnalysisService placeVoiceAnalysisService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new PlaceVoiceAnalysisController(placeVoiceAnalysisService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("STT 텍스트 의미 분석 요청을 받아 추출된 장소명을 반환한다")
	void analyzeVoiceText() throws Exception {
		when(placeVoiceAnalysisService.analyze(any(VoiceAnalyzeRequest.class)))
			.thenReturn(new VoiceAnalyzeResponse(
				VoiceIntent.PLACE_SEARCH,
				"이재모피자",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null));

		mockMvc.perform(post("/voice/analyze")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\":\"마 이재모피자 어디있는지 알려주소\",\"mode\":\"MOBILITY_IMPAIRED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.intent").value("PLACE_SEARCH"))
			.andExpect(jsonPath("$.data.placeName").value("이재모피자"))
			.andExpect(jsonPath("$.data.confirmed").doesNotExist())
			.andExpect(jsonPath("$.data.confirmationMessage").doesNotExist());

		verify(placeVoiceAnalysisService).analyze(any(VoiceAnalyzeRequest.class));
	}

	@Test
	@DisplayName("요청값 검증 실패는 공통 입력 오류로 응답한다")
	void rejectInvalidRequest() throws Exception {
		mockMvc.perform(post("/voice/analyze")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\":\"\",\"mode\":\"MOBILITY_IMPAIRED\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("C4000"));

		verifyNoInteractions(placeVoiceAnalysisService);
	}

	@Test
	@DisplayName("음성 분석 서비스가 입력 오류를 던지면 C4000으로 응답한다")
	void mapServiceInvalidInputToBadRequest() throws Exception {
		when(placeVoiceAnalysisService.analyze(any(VoiceAnalyzeRequest.class)))
			.thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "잘못된 입력입니다."));

		mockMvc.perform(post("/voice/analyze")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\":\"...\",\"mode\":\"LOW_VISION\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("C4000"))
			.andExpect(jsonPath("$.message").value("잘못된 입력입니다."));

		verify(placeVoiceAnalysisService).analyze(any(VoiceAnalyzeRequest.class));
	}
}
