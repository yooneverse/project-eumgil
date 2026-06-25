package com.ssafy.e102.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ssafy.e102.global.response.ErrorResponse;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

	@Test
	@DisplayName("존재하지 않는 경로는 500이 아니라 404로 응답한다")
	void handleNoResourceFoundException() {
		ResponseEntity<ErrorResponse> response = exceptionHandler.handleNoResourceFoundException(
			new NoResourceFoundException(HttpMethod.DELETE, "/users/me"));

		assertThat(response.getStatusCode()).isEqualTo(CommonErrorCode.NOT_FOUND.getHttpStatus());
		assertThat(response.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.NOT_FOUND));
	}
}
