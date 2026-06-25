package com.ssafy.e102.global.security.handler;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.ssafy.e102.global.exception.CommonErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityErrorResponseWriter responseWriter;

	public RestAuthenticationEntryPoint(SecurityErrorResponseWriter responseWriter) {
		this.responseWriter = responseWriter;
	}

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException authException) throws IOException, ServletException {
		responseWriter.write(response, CommonErrorCode.UNAUTHORIZED);
	}
}
