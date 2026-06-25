package com.ssafy.e102.domain.place.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoiceAnalyzeRequestContractTest {

	@Test
	@DisplayName("음성 분석 요청 계약은 currentRoute 필드를 노출한다")
	void exposesCurrentRouteField() {
		assertThat(VoiceAnalyzeRequest.class.getRecordComponents())
			.extracting(RecordComponent::getName)
			.contains("currentRoute");
	}
}
