package com.ssafy.e102.global.external.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiVoiceAnalyzeCommandContractTest {

	@Test
	@DisplayName("AI 음성 분석 호출 계약은 currentRoute 필드를 포함한다")
	void exposesCurrentRouteField() {
		assertThat(AiVoiceAnalyzeCommand.class.getRecordComponents())
			.extracting(RecordComponent::getName)
			.contains("currentRoute");
	}
}
