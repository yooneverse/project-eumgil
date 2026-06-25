package com.ssafy.e102.global.security.handler;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ssafy.e102.global.exception.CommonErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final SecurityErrorResponseWriter responseWriter;

	public RestAccessDeniedHandler(SecurityErrorResponseWriter responseWriter) {
		this.responseWriter = responseWriter;
	}

	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		AccessDeniedException accessDeniedException) throws IOException, ServletException {
		responseWriter.write(response, CommonErrorCode.FORBIDDEN);
	}
}
