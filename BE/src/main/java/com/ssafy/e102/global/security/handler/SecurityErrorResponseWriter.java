package com.ssafy.e102.global.security.handler;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.global.exception.ErrorCode;
import com.ssafy.e102.global.response.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

@Component
class SecurityErrorResponseWriter {

	private final ObjectMapper objectMapper;

	SecurityErrorResponseWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ErrorResponse.from(errorCode));
	}
}
