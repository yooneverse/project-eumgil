package com.ssafy.e102.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.e102.domain.report.dto.response.HazardMarkerListResponse;
import com.ssafy.e102.domain.report.service.HazardReportService;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.global.security.config.CorsProperties;
import com.ssafy.e102.global.exception.CommonErrorCode;
import com.ssafy.e102.global.security.config.SecurityConfig;
import com.ssafy.e102.global.security.filter.JwtAuthenticationFilter;
import com.ssafy.e102.global.security.handler.RestAccessDeniedHandler;
import com.ssafy.e102.global.security.handler.RestAuthenticationEntryPoint;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

@WebMvcTest(HazardMarkerController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, HazardMarkerSecurityTest.TestConfig.class})
class HazardMarkerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HazardReportService hazardReportService;

	@MockitoBean
	private RestAuthenticationEntryPoint authenticationEntryPoint;

	@MockitoBean
	private RestAccessDeniedHandler accessDeniedHandler;

	@MockitoBean
	private CorsProperties corsProperties;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private AuthTokenStore authTokenStore;

	@MockitoBean
	private UserRepository userRepository;

	@BeforeEach
	void setUpSecurityHandlers() throws Exception {
		doAnswer(invocation -> {
			var response = invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1);
			response.setStatus(CommonErrorCode.UNAUTHORIZED.getHttpStatus().value());
			response.setContentType("application/json");
			response.getWriter().write("{\"status\":\"A4010\",\"message\":\"authentication required\"}");
			return null;
		}).when(authenticationEntryPoint).commence(any(), any(), any());

		doAnswer(invocation -> {
			var response = invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1);
			response.setStatus(CommonErrorCode.FORBIDDEN.getHttpStatus().value());
			response.setContentType("application/json");
			response.getWriter().write("{\"status\":\"A4030\",\"message\":\"forbidden\"}");
			return null;
		}).when(accessDeniedHandler).handle(any(), any(), any());
	}

	@TestConfiguration
	static class TestConfig {
		@Bean
		HazardMarkerController hazardMarkerController(HazardReportService hazardReportService) {
			return new HazardMarkerController(hazardReportService);
		}

		@Bean
		JwtAuthenticationFilter jwtAuthenticationFilter(
			JwtTokenProvider jwtTokenProvider,
			AuthTokenStore authTokenStore,
			UserRepository userRepository
		) {
			return new JwtAuthenticationFilter(jwtTokenProvider, authTokenStore, userRepository);
		}
	}

	@Test
	@DisplayName("?듦린 ?쒕낫 留ㅼ뺣낫 API???몄쬆???꾩슂?섎떎")
	void hazardMarkersRequireAuthentication() throws Exception {
		when(corsProperties.allowedOrigins()).thenReturn(List.of("http://localhost:3001"));

		mockMvc.perform(get("/hazard/markers/")
				.param("swLat", "35.09")
				.param("swLng", "129.09")
				.param("neLat", "35.10")
				.param("neLng", "129.10"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value("A4010"));
	}

	@Test
	@DisplayName("?몄쬆???ъ슜?먮뒗 ?듦린 ?쒕낫 留ㅼ뺣낫 API瑜?議고쉶???덈떎")
	void authenticatedUserCanAccessHazardMarkers() throws Exception {
		when(corsProperties.allowedOrigins()).thenReturn(List.of("http://localhost:3001"));
		when(hazardReportService.getApprovedHazardMarkers(any(), any(), any(), any()))
			.thenReturn(new HazardMarkerListResponse(List.of()));

		mockMvc.perform(get("/hazard/markers/")
				.with(user("reviewer"))
				.param("swLat", "35.09")
				.param("swLng", "129.09")
				.param("neLat", "35.10")
				.param("neLng", "129.10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.markers").isArray());
	}
}
