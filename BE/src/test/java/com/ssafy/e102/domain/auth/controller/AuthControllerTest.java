package com.ssafy.e102.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.auth.cookie.RefreshTokenCookieManager;
import com.ssafy.e102.domain.auth.dto.response.SignupResponse;
import com.ssafy.e102.domain.auth.dto.response.SocialLoginResponse;
import com.ssafy.e102.domain.auth.dto.response.TokenResponse;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.service.AuthService;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.global.exception.GlobalExceptionHandler;
import com.ssafy.e102.global.security.jwt.JwtProperties;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class AuthControllerTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		RefreshTokenCookieManager refreshTokenCookieManager = new RefreshTokenCookieManager(
			new JwtProperties(
				"bG9jYWwtand0LXNlY3JldC1mb3ItZTEwMi0zMmJ5dGVzISE=",
				"e102-test",
				Duration.ofMinutes(15),
				Duration.ofDays(14),
				Duration.ofMinutes(10)),
			"refreshToken",
			"/auth",
			"Lax",
			false);
		mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, refreshTokenCookieManager))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("소셜 로그인 요청을 받아 기존 사용자 토큰 응답을 반환한다")
	void socialLogin() throws Exception {
		UUID userId = UUID.randomUUID();
		when(authService.socialLogin(any()))
			.thenReturn(SocialLoginResponse.existingUser(
				"access-token",
				"refresh-token",
				userId,
				PrimaryUserType.MOBILITY_IMPAIRED,
				MobilitySubtype.MANUAL_WHEELCHAIR));

		mockMvc.perform(post("/auth/social-login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"socialProvider\":\"KAKAO\",\"socialAccessToken\":\"kakao-access-token\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.signupRequired").value(false))
			.andExpect(jsonPath("$.data.signupToken").doesNotExist())
			.andExpect(jsonPath("$.data.accessToken").value("access-token"))
			.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
			.andExpect(jsonPath("$.data.userId").value(userId.toString()))
			.andExpect(jsonPath("$.data.selectedPrimaryUserType").value("MOBILITY_IMPAIRED"))
			.andExpect(jsonPath("$.data.selectedMobilitySubtype").value("MANUAL_WHEELCHAIR"))
			.andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-token")));
	}

	@Test
	@DisplayName("회원가입 완료 요청을 받아 생성 응답을 반환한다")
	void signup() throws Exception {
		UUID userId = UUID.randomUUID();
		when(authService.signup(any()))
			.thenReturn(new SignupResponse(
				"access-token",
				"refresh-token",
				userId,
				PrimaryUserType.LOW_VISION,
				null));

		mockMvc.perform(post("/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"signupToken\":\"signup-token\",\"selectedPrimaryUserType\":\"LOW_VISION\","
				+ "\"requiredTermsAccepted\":true}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.accessToken").value("access-token"))
			.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
			.andExpect(jsonPath("$.data.userId").value(userId.toString()))
			.andExpect(jsonPath("$.data.selectedPrimaryUserType").value("LOW_VISION"))
			.andExpect(jsonPath("$.data.selectedMobilitySubtype").doesNotExist())
			.andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-token")));
	}

	@Test
	@DisplayName("토큰 재발급 요청을 받아 새 토큰 쌍을 반환한다")
	void reissue() throws Exception {
		when(authService.reissue(any())).thenReturn(new TokenResponse("new-access-token", "new-refresh-token"));

		mockMvc.perform(post("/auth/reissue")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"refresh-token\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
			.andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
			.andExpect(header().string("Set-Cookie", containsString("refreshToken=new-refresh-token")));
	}

	@Test
	@DisplayName("토큰 재발급 요청에 refresh token이 없으면 A4012로 응답한다")
	void reissueRejectsMissingRefreshToken() throws Exception {
		mockMvc.perform(post("/auth/reissue"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value("A4012"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("토큰 재발급 요청의 refresh token이 유효하지 않으면 A4012로 응답한다")
	void reissueRejectsInvalidRefreshToken() throws Exception {
		when(authService.reissue(any())).thenThrow(new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

		mockMvc.perform(post("/auth/reissue")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"invalid-refresh-token\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value("A4012"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("토큰 재발급 요청은 쿠키의 refresh token도 사용할 수 있다")
	void reissueWithCookie() throws Exception {
		when(authService.reissue(any())).thenReturn(new TokenResponse("new-access-token", "new-refresh-token"));

		mockMvc.perform(post("/auth/reissue")
			.cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookie-refresh-token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
			.andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
			.andExpect(header().string("Set-Cookie", containsString("refreshToken=new-refresh-token")));
	}

	@Test
	@DisplayName("로그아웃 요청은 현재 사용자 세션을 무효화하고 성공 메시지를 반환한다")
	void logout() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		mockMvc.perform(post("/auth/logout")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
			.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

		verify(authService).logout(userId, "access-token");
		SecurityContextHolder.clearContext();
	}
}
