package com.ssafy.e102.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeHistoryRequest;
import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeRequest;
import com.ssafy.e102.domain.place.dto.response.VoiceAnalyzeResponse;
import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.type.VoiceAnalysisMode;
import com.ssafy.e102.domain.place.type.VoiceIntent;
import com.ssafy.e102.global.external.ai.AiVoiceAnalysisClient;
import com.ssafy.e102.global.external.ai.AiVoiceAnalyzeCommand;
import com.ssafy.e102.global.external.ai.AiVoiceAnalyzeResult;

class PlaceVoiceAnalysisServiceTest {

	@Mock
	private AiVoiceAnalysisClient aiVoiceAnalysisClient;

	private PlaceVoiceAnalysisService placeVoiceAnalysisService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		placeVoiceAnalysisService = new PlaceVoiceAnalysisService(aiVoiceAnalysisClient);
	}

	@Test
	@DisplayName("보행약자 음성 분석은 AI가 반환한 확인 정보를 그대로 전달한다")
	void analyzeMobilityImpairedVoiceTextWithAiConfirmation() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.PLACE_SEARCH,
				"이재모피자",
				null,
				null,
				null,
				null,
				null,
				null,
				true,
				"무시되는 문구"));

		VoiceAnalyzeResponse response = placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"마 이재모피자 어디있는지 알려주소",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			List.of(new VoiceAnalyzeHistoryRequest("user", "이전 발화")),
			"navigation/guidance"));

		assertThat(response.intent()).isEqualTo(VoiceIntent.PLACE_SEARCH);
		assertThat(response.placeName()).isEqualTo("이재모피자");
		assertThat(response.confirmed()).isTrue();
		assertThat(response.confirmationMessage()).isEqualTo("무시되는 문구");
		ArgumentCaptor<AiVoiceAnalyzeCommand> captor = ArgumentCaptor.forClass(AiVoiceAnalyzeCommand.class);
		verify(aiVoiceAnalysisClient).analyze(captor.capture());
		assertThat(captor.getValue().history()).hasSize(1);
		assertThat(captor.getValue().currentRoute()).isEqualTo("navigation/guidance");
	}

	@Test
	@DisplayName("저시력자 음성 분석은 이전 대화와 확인 문구를 유지한다")
	void analyzeLowVisionVoiceTextWithHistory() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.PLACE_SEARCH,
				"부산대학교",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"부산대학교를 찾으시나요?"));

		VoiceAnalyzeResponse response = placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"아니 부산대학교",
			VoiceAnalysisMode.LOW_VISION,
			List.of(
				new VoiceAnalyzeHistoryRequest("user", "부산역 어디야"),
				new VoiceAnalyzeHistoryRequest("assistant",
					"{\"intent\":\"PLACE_SEARCH\",\"placeName\":\"부산역\"}")),
			null));

		assertThat(response.placeName()).isEqualTo("부산대학교");
		assertThat(response.confirmed()).isNull();
		assertThat(response.confirmationMessage()).isEqualTo("부산대학교를 찾으시나요?");
		ArgumentCaptor<AiVoiceAnalyzeCommand> captor = ArgumentCaptor.forClass(AiVoiceAnalyzeCommand.class);
		verify(aiVoiceAnalysisClient).analyze(captor.capture());
		assertThat(captor.getValue().history()).hasSize(2);
	}

	@Test
	@DisplayName("보행약자 ASK 응답은 AI가 생성한 follow-up 문구를 그대로 전달한다")
	void passThroughMobilityAskConfirmationMessage() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.ASK,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"어떤 제보인가요?"));

		VoiceAnalyzeResponse response = placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"제보할게요",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			List.of(new VoiceAnalyzeHistoryRequest("user", "길 안내 끝낼게")),
			"navigation/guidance"));

		assertThat(response.intent()).isEqualTo(VoiceIntent.ASK);
		assertThat(response.confirmed()).isNull();
		assertThat(response.confirmationMessage()).isEqualTo("어떤 제보인가요?");
	}

	@Test
	@DisplayName("저시력자 장소 검색 1차 응답에 확인 문구가 없으면 실패로 처리한다")
	void rejectLowVisionPlaceSearchWithoutConfirmationMessage() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.PLACE_SEARCH,
				"부산대학교",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null));

		assertThatThrownBy(() -> placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"부산대학교",
			VoiceAnalysisMode.LOW_VISION,
			List.of(),
			null)))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
	}

	@Test
	@DisplayName("AI 응답에 장소 검색 의도만 있고 장소명이 없으면 실패로 처리한다")
	void rejectInvalidAiResult() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.PLACE_SEARCH,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null));

		assertThatThrownBy(() -> placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"어디야",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			null,
			null)))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
	}

	@Test
	@DisplayName("의도 미확인 응답은 AI가 장소명을 내려줘도 비워서 반환한다")
	void clearPlaceNameForUnknownIntent() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.UNKNOWN,
				"부산역",
				null,
				null,
				null,
				null,
				null,
				null,
				false,
				"찾으시는 장소를 다시 말씀해 주세요"));

		VoiceAnalyzeResponse response = placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"어딘지 모르겠어",
			VoiceAnalysisMode.LOW_VISION,
			List.of(),
			null));

		assertThat(response.intent()).isEqualTo(VoiceIntent.UNKNOWN);
		assertThat(response.placeName()).isNull();
		assertThat(response.confirmed()).isFalse();
		assertThat(response.confirmationMessage()).isEqualTo("찾으시는 장소를 다시 말씀해 주세요");
	}

	@Test
	@DisplayName("카테고리 검색 의도에 카테고리가 없으면 실패로 처리한다")
	void rejectInvalidCategorySearchResult() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.CATEGORY_SEARCH,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null));

		assertThatThrownBy(() -> placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"카페 찾아줘",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			List.of(),
			"map")))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
	}

	@Test
	@DisplayName("경로 안내 의도에 도착지가 없으면 실패로 처리한다")
	void rejectInvalidNavigateResult() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.NAVIGATE,
				null,
				null,
				null,
				null,
				" ",
				null,
				null,
				null,
				null));

		assertThatThrownBy(() -> placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"길 안내해줘",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			List.of(),
			"map")))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
	}

	@Test
	@DisplayName("제보 의도에 reportType이 없으면 실패로 처리한다")
	void rejectInvalidReportResult() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.REPORT,
				null,
				null,
				null,
				null,
				null,
				null,
				"안내와 달리 계단이 있습니다",
				null,
				null));

		assertThatThrownBy(() -> placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"계단 제보할게요",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			List.of(),
			"navigation/guidance")))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
	}

	@Test
	@DisplayName("북마크 추가 의도에 bookmarkAction이 없으면 실패로 처리한다")
	void rejectInvalidBookmarkAddResult() {
		when(aiVoiceAnalysisClient.analyze(any(AiVoiceAnalyzeCommand.class)))
			.thenReturn(new AiVoiceAnalyzeResult(
				VoiceIntent.BOOKMARK_ADD,
				"부산역",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null));

		assertThatThrownBy(() -> placeVoiceAnalysisService.analyze(new VoiceAnalyzeRequest(
			"부산역 북마크해줘",
			VoiceAnalysisMode.MOBILITY_IMPAIRED,
			List.of(),
			"saved_route")))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
	}
}
