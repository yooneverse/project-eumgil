package com.ssafy.e102.global.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;

class SecurityConfigPathPatternTest {

	private final PathPatternParser parser = new PathPatternParser();

	@Test
	@DisplayName("hazard reroute security pattern matches a single report id segment")
	void hazardReroutePatternMatchesSingleSegmentPath() {
		PathPattern pattern = parser.parse(SecurityConfig.HAZARD_REPORT_REROUTE_PATTERN);

		assertThat(pattern.matches(PathContainer.parsePath("/hazard/12/reroute"))).isTrue();
		assertThat(pattern.matches(PathContainer.parsePath("/hazard/abc/reroute"))).isTrue();
		assertThat(pattern.matches(PathContainer.parsePath("/hazard/12/extra/reroute"))).isFalse();
	}

	@Test
	@DisplayName("legacy double-star reroute pattern is invalid in Spring path patterns")
	void legacyDoubleStarReroutePatternIsInvalid() {
		assertThatThrownBy(() -> parser.parse("/hazard/**/reroute"))
			.isInstanceOf(PatternParseException.class);
	}
}
