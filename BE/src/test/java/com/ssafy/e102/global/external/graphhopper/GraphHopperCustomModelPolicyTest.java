package com.ssafy.e102.global.external.graphhopper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GraphHopperCustomModelPolicyTest {

	private static final Path MANUAL_SAFE_MODEL = Path.of("..", "INF", "graphhopper", "custom_models",
		"wheelchair_manual_safe.json");
	private static final Path MANUAL_FAST_MODEL = Path.of("..", "INF", "graphhopper", "custom_models",
		"wheelchair_manual_fast.json");
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("manual wheelchair SAFE blocks ADEQUATE_120 width")
	void manualWheelchairSafeBlocksAdequate120() throws IOException {
		JsonNode modelJson = objectMapper.readTree(Files.readString(MANUAL_SAFE_MODEL));

		assertThat(multiplyByFor(modelJson, "width_state == ADEQUATE_120")).isEqualTo("0");
	}

	@Test
	@DisplayName("manual wheelchair SAFE blocks NARROW width")
	void manualWheelchairSafeBlocksNarrow() throws IOException {
		JsonNode modelJson = objectMapper.readTree(Files.readString(MANUAL_SAFE_MODEL));

		assertThat(multiplyByFor(modelJson, "width_state == NARROW")).isEqualTo("0");
	}

	@Test
	@DisplayName("manual wheelchair FAST does not penalize ADEQUATE_120 width")
	void manualWheelchairFastDoesNotPenalizeAdequate120() throws IOException {
		JsonNode modelJson = objectMapper.readTree(Files.readString(MANUAL_FAST_MODEL));

		assertThat(multiplyByFor(modelJson, "width_state == ADEQUATE_120")).isEqualTo("1.0");
	}

	private String multiplyByFor(JsonNode modelJson, String condition) {
		for (JsonNode rule : modelJson.path("priority")) {
			if (condition.equals(rule.path("if").asText())) {
				return rule.path("multiply_by").asText();
			}
		}
		throw new AssertionError("priority rule not found: " + condition);
	}
}
