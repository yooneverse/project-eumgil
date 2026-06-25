package com.ssafy.e102.domain.route.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.route.dto.request.RouteRatingRequest;
import com.ssafy.e102.domain.route.dto.response.RouteRatingResponse;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.exception.RouteExceptionHandler;
import com.ssafy.e102.domain.route.service.RouteRatingService;
import com.ssafy.e102.global.exception.GlobalExceptionHandler;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class RouteRatingControllerTest {

	private RouteRatingService routeRatingService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		routeRatingService = Mockito.mock(RouteRatingService.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new RouteRatingController(routeRatingService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.setControllerAdvice(new RouteExceptionHandler(), new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("경로 평가는 인증 사용자와 request body를 service로 넘기고 201을 반환한다")
	void rateRouteReturnsCreatedResponse() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(routeRatingService.rate(eq(userId), any(RouteRatingRequest.class)))
			.thenReturn(new RouteRatingResponse(1L));

		mockMvc.perform(post("/route-ratings")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "sessionId": "%s",
				  "score": 5
				}
				""".formatted(sessionId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.ratingId").value(1))
			.andExpect(jsonPath("$.message").value("생성되었습니다."));

		verify(routeRatingService).rate(eq(userId), any(RouteRatingRequest.class));
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 평가 score 최솟값 1은 성공한다")
	void rateRouteAcceptsMinimumScore() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(routeRatingService.rate(eq(userId), any(RouteRatingRequest.class)))
			.thenReturn(new RouteRatingResponse(2L));

		mockMvc.perform(post("/route-ratings")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "sessionId": "%s",
				  "score": 1
				}
				""".formatted(sessionId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.ratingId").value(2));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 평가 sessionId 누락은 RR4000을 반환한다")
	void rateRouteRejectsMissingSessionId() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(post("/route-ratings")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "score": 5
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("RR4000"))
			.andExpect(jsonPath("$.message").value("경로 평가 요청값이 올바르지 않습니다."));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 평가 score 범위 오류는 RR4000을 반환한다")
	void rateRouteRejectsInvalidScore() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(post("/route-ratings")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "sessionId": "%s",
				  "score": 0
				}
				""".formatted(sessionId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("RR4000"));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("다른 사용자의 route 평가는 A4030을 반환한다")
	void rateRouteRejectsOtherUserRoute() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		Mockito.doThrow(new RouteException(RouteErrorCode.ROUTE_ACCESS_DENIED))
			.when(routeRatingService)
			.rate(eq(userId), any(RouteRatingRequest.class));

		mockMvc.perform(post("/route-ratings")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "sessionId": "%s",
				  "score": 5
				}
				""".formatted(sessionId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value("A4030"))
			.andExpect(jsonPath("$.message").value("접근할 수 없는 경로입니다."));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("route session 없는 경로 평가는 RT4043을 반환한다")
	void rateRouteRejectsMissingRouteSession() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		Mockito.doThrow(new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND))
			.when(routeRatingService)
			.rate(eq(userId), any(RouteRatingRequest.class));

		mockMvc.perform(post("/route-ratings")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "sessionId": "%s",
				  "score": 5
				}
				""".formatted(sessionId)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value("RT4043"))
			.andExpect(jsonPath("$.message").value("선택한 경로 정보를 찾을 수 없습니다."));

		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
