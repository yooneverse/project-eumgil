package com.ssafy.e102.domain.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.auth.cookie.RefreshTokenCookieManager;
import com.ssafy.e102.domain.auth.dto.request.ReissueRequest;
import com.ssafy.e102.domain.auth.dto.request.SignupRequest;
import com.ssafy.e102.domain.auth.dto.request.SocialLoginRequest;
import com.ssafy.e102.domain.auth.dto.response.SignupResponse;
import com.ssafy.e102.domain.auth.dto.response.SocialLoginResponse;
import com.ssafy.e102.domain.auth.dto.response.TokenResponse;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.service.AuthService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "소셜 로그인, 회원가입, 토큰 재발급, 로그아웃 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenCookieManager refreshTokenCookieManager;

	@SecurityRequirements()
	@Operation(summary = "소셜 로그인", description = "카카오 소셜 접근 토큰을 검증하고 가입 완료 사용자에게 서비스 토큰을 발급한다. 신규 사용자는 회원가입 토큰을 반환한다.")
	@PostMapping("/social-login")
	public ResponseEntity<ApiResponse<SocialLoginResponse>> socialLogin(
		@Valid @RequestBody
		SocialLoginRequest request) {
		SocialLoginResponse response = authService.socialLogin(request);
		return withOptionalRefreshCookie(ApiResponse.success(response), response.refreshToken());
	}

	@SecurityRequirements()
	@Operation(summary = "회원가입", description = "회원가입 토큰, 필수 약관 동의, 사용자 유형을 검증한 뒤 가입 완료 사용자 계정을 생성하고 서비스 토큰을 발급한다.")
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(
		@Valid @RequestBody
		SignupRequest request) {
		SignupResponse response = authService.signup(request);
		return withRefreshCookie(ApiResponse.created(response), response.refreshToken(), HttpStatus.CREATED);
	}

	@SecurityRequirements()
	@Operation(summary = "토큰 재발급", description = "요청 body 또는 HttpOnly cookie의 refresh token을 검증하고 access token과 refresh token을 새로 발급한다.")
	@PostMapping("/reissue")
	public ResponseEntity<ApiResponse<TokenResponse>> reissue(
		@Valid @RequestBody(required = false)
		ReissueRequest request,
		HttpServletRequest servletRequest) {
		String refreshToken = resolveRefreshToken(request, findCookieRefreshToken(servletRequest));
		TokenResponse response = authService.reissue(new ReissueRequest(refreshToken));
		return withRefreshCookie(ApiResponse.success(response), response.refreshToken(), HttpStatus.OK);
	}

	@Operation(summary = "로그아웃", description = "현재 접근 토큰을 차단 목록에 등록하고 사용자의 재발급 토큰을 제거한다.")
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal) {
		authService.logout(principal.userId(), principal.accessToken());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.clear().toString())
			.body(ApiResponse.successMessage("로그아웃되었습니다."));
	}

	private <T> ResponseEntity<ApiResponse<T>> withOptionalRefreshCookie(
		ApiResponse<T> response,
		String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			return ResponseEntity.ok(response);
		}
		return withRefreshCookie(response, refreshToken, HttpStatus.OK);
	}

	private <T> ResponseEntity<ApiResponse<T>> withRefreshCookie(
		ApiResponse<T> response,
		String refreshToken,
		HttpStatus httpStatus) {
		ResponseCookie cookie = refreshTokenCookieManager.create(refreshToken);
		return ResponseEntity.status(httpStatus)
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(response);
	}

	private String resolveRefreshToken(ReissueRequest request, String cookieRefreshToken) {
		if (request != null && StringUtils.hasText(request.refreshToken())) {
			return request.refreshToken();
		}
		if (StringUtils.hasText(cookieRefreshToken)) {
			return cookieRefreshToken;
		}
		throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
	}

	private String findCookieRefreshToken(HttpServletRequest servletRequest) {
		Cookie[] cookies = servletRequest.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (refreshTokenCookieManager.cookieName().equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
