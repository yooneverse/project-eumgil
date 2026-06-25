package com.ssafy.e102.global.test.auth;

import java.io.IOException;
import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@EnableConfigurationProperties(AuthTestConfigProperties.class)
public class AuthTestEndpointAccessFilter extends OncePerRequestFilter {

	private static final Set<String> TEST_ENDPOINTS = Set.of(
		"/auth-test.html",
		"/auth/test-config");

	private final AuthTestConfigProperties properties;

	public AuthTestEndpointAccessFilter(AuthTestConfigProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		if (!properties.enabled() && TEST_ENDPOINTS.contains(request.getRequestURI())) {
			// 운영 환경에서는 테스트 페이지와 public 테스트 설정 API 자체가 노출되지 않도록 404로 숨긴다.
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		filterChain.doFilter(request, response);
	}
}
