package com.ssafy.e102.domain.place.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.place.type.VoiceIntent;
import com.ssafy.e102.global.external.ai.AiVoiceAnalyzeResult;

class VoiceAnalyzeResponseJsonTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("음성 분석 intent enum은 v2.3 intent 집합을 노출한다")
	void exposesV23IntentSet() {
		assertThat(VoiceIntent.values())
			.extracting(Enum::name)
			.contains(
				"PLACE_SEARCH",
				"CATEGORY_SEARCH",
				"BOOKMARK_ADD",
				"BOOKMARK_DELETE",
				"NAVIGATE",
				"SHOW_BOOKMARKS",
				"SHOW_FAVORITE_ROUTES",
				"LOGOUT",
				"REPORT",
				"NAVIGATION_END",
				"OPEN_MY_PAGE",
				"OPEN_MAP",
				"ASK",
				"UNKNOWN");
	}

	@Test
	@DisplayName("음성 분석 DTO는 v2.3 응답 필드를 노출한다")
	void exposesV23ResponseFields() {
		assertThat(VoiceAnalyzeResponse.class.getRecordComponents())
			.extracting(RecordComponent::getName)
			.containsExactly(
				"intent",
				"placeName",
				"category",
				"bookmarkAction",
				"departure",
				"destination",
				"reportType",
				"description",
				"confirmed",
				"confirmationMessage");
		assertThat(AiVoiceAnalyzeResult.class.getRecordComponents())
			.extracting(RecordComponent::getName)
			.containsExactly(
				"intent",
				"placeName",
				"category",
				"bookmarkAction",
				"departure",
				"destination",
				"reportType",
				"description",
				"confirmed",
				"confirmationMessage");
	}

	@Test
	@DisplayName("음성 분석 응답은 null 선택 필드도 JSON에 포함한다")
	void serializesNullOptionalFields() throws Exception {
		assertThat(VoiceAnalyzeResponse.class.getRecordComponents())
			.extracting(RecordComponent::getName)
			.containsExactly(
				"intent",
				"placeName",
				"category",
				"bookmarkAction",
				"departure",
				"destination",
				"reportType",
				"description",
				"confirmed",
				"confirmationMessage");

		Constructor<VoiceAnalyzeResponse> constructor = VoiceAnalyzeResponse.class.getDeclaredConstructor(
			Arrays.stream(VoiceAnalyzeResponse.class.getRecordComponents())
				.map(RecordComponent::getType)
				.toArray(Class[]::new));

		VoiceAnalyzeResponse response = constructor.newInstance(
			Enum.valueOf(VoiceIntent.class, "ASK"),
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null);

		JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(root.get("intent").asText()).isEqualTo("ASK");
		assertThat(root.has("placeName")).isTrue();
		assertThat(root.get("placeName").isNull()).isTrue();
		assertThat(root.has("category")).isTrue();
		assertThat(root.get("category").isNull()).isTrue();
		assertThat(root.has("bookmarkAction")).isTrue();
		assertThat(root.get("bookmarkAction").isNull()).isTrue();
		assertThat(root.has("departure")).isTrue();
		assertThat(root.get("departure").isNull()).isTrue();
		assertThat(root.has("destination")).isTrue();
		assertThat(root.get("destination").isNull()).isTrue();
		assertThat(root.has("reportType")).isTrue();
		assertThat(root.get("reportType").isNull()).isTrue();
		assertThat(root.has("description")).isTrue();
		assertThat(root.get("description").isNull()).isTrue();
		assertThat(root.has("confirmed")).isTrue();
		assertThat(root.get("confirmed").isNull()).isTrue();
		assertThat(root.has("confirmationMessage")).isTrue();
		assertThat(root.get("confirmationMessage").isNull()).isTrue();
	}
}
