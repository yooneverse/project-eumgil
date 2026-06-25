package com.ssafy.e102.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.auth.social.verifier.CompositeSocialTokenVerifier;
import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.auth.dto.request.SocialLoginRequest;
import com.ssafy.e102.domain.auth.dto.response.SocialLoginResponse;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.auth.token.SignupTokenPayload;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.security.jwt.JwtProperties;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

class AuthServiceSocialLoginTest {

	private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
	private static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(10);

	@Mock
	private CompositeSocialTokenVerifier socialTokenVerifier;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private AuthTokenStore authTokenStore;

	@Mock
	private AuthSessionService authSessionService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		JwtProperties jwtProperties = new JwtProperties(
			"bG9jYWwtand0LXNlY3JldC1mb3ItZTEwMi0zMmJ5dGVzISE=",
			"e102-test",
			Duration.ofMinutes(15),
			REFRESH_TOKEN_TTL,
			SIGNUP_TOKEN_TTL);
		authService = new AuthService(
			socialTokenVerifier,
			userRepository,
			jwtTokenProvider,
			authTokenStore,
			jwtProperties,
			authSessionService);
	}

	@Test
	@DisplayName("가입 완료 사용자는 서비스 토큰과 사용자 유형을 반환한다")
	void socialLoginExistingUser() {
		UUID userId = UUID.randomUUID();
		User user = User.create(
			SocialProvider.KAKAO,
			"kakao-user-id",
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR);
		ReflectionTestUtils.setField(user, "userId", userId);
		when(socialTokenVerifier.verify(SocialProvider.KAKAO, "kakao-access-token"))
			.thenReturn(new SocialUserInfo(SocialProvider.KAKAO, "kakao-user-id"));
		when(userRepository.findBySocialProviderAndSocialProviderUserId(SocialProvider.KAKAO, "kakao-user-id"))
			.thenReturn(Optional.of(user));
		when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
		when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("refresh-token");

		SocialLoginResponse response = authService.socialLogin(
			new SocialLoginRequest(SocialProvider.KAKAO, "kakao-access-token"));

		assertThat(response.signupRequired()).isFalse();
		assertThat(response.signupToken()).isNull();
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.selectedPrimaryUserType()).isEqualTo(PrimaryUserType.MOBILITY_IMPAIRED);
		assertThat(response.selectedMobilitySubtype()).isEqualTo(MobilitySubtype.MANUAL_WHEELCHAIR);
		verify(authTokenStore).saveRefreshToken("refresh-token", userId, REFRESH_TOKEN_TTL);
		verify(authTokenStore, never()).saveSignupToken(
			"signup-token",
			new SignupTokenPayload(SocialProvider.KAKAO, "kakao-user-id"),
			SIGNUP_TOKEN_TTL);
	}

	@Test
	@DisplayName("신규 소셜 사용자는 users row 없이 회원가입 토큰만 반환한다")
	void socialLoginNewUser() {
		when(socialTokenVerifier.verify(SocialProvider.NAVER, "naver-access-token"))
			.thenReturn(new SocialUserInfo(SocialProvider.NAVER, "naver-user-id"));
		when(userRepository.findBySocialProviderAndSocialProviderUserId(SocialProvider.NAVER, "naver-user-id"))
			.thenReturn(Optional.empty());
		when(jwtTokenProvider.createSignupToken(SocialProvider.NAVER, "naver-user-id"))
			.thenReturn("signup-token");

		SocialLoginResponse response = authService.socialLogin(
			new SocialLoginRequest(SocialProvider.NAVER, "naver-access-token"));

		assertThat(response.signupRequired()).isTrue();
		assertThat(response.signupToken()).isEqualTo("signup-token");
		assertThat(response.accessToken()).isNull();
		assertThat(response.refreshToken()).isNull();
		assertThat(response.userId()).isNull();
		assertThat(response.selectedPrimaryUserType()).isNull();
		assertThat(response.selectedMobilitySubtype()).isNull();
		verify(authTokenStore).saveSignupToken(
			"signup-token",
			new SignupTokenPayload(SocialProvider.NAVER, "naver-user-id"),
			SIGNUP_TOKEN_TTL);
		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
