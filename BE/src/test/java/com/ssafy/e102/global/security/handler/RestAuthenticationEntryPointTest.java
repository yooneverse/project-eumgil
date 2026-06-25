package com.ssafy.e102.global.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RestAuthenticationEntryPointTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("보호 API 인증 실패는 FE refresh 트리거 코드 A4010으로 응답한다")
	void authenticationFailureReturnsRefreshTriggerCode() throws Exception {
		RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(
			new SecurityErrorResponseWriter(objectMapper));
		MockHttpServletResponse response = new MockHttpServletResponse();

		entryPoint.commence(
			new MockHttpServletRequest(),
			response,
			new AuthenticationCredentialsNotFoundException("missing access token"));

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON))
			.isTrue();
		assertThat(body.get("status").asText()).isEqualTo("A4010");
		assertThat(body.get("message").asText()).isEqualTo("인증이 필요합니다.");
	}
}
