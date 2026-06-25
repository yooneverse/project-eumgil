package com.ssafy.e102.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.auth.social.verifier.CompositeSocialTokenVerifier;
import com.ssafy.e102.domain.auth.dto.request.SignupRequest;
import com.ssafy.e102.domain.auth.dto.response.SignupResponse;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.auth.token.SignupTokenPayload;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.security.jwt.JwtProperties;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

class AuthServiceSignupTest {

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
	@DisplayName("회원가입 토큰과 온보딩 값으로 사용자를 생성하고 서비스 토큰을 발급한다")
	void signup() {
		UUID userId = UUID.randomUUID();
		when(jwtTokenProvider.getSignupTokenPayload("signup-token"))
			.thenReturn(new SignupTokenPayload(SocialProvider.KAKAO, "kakao-user-id"));
		when(authTokenStore.findSignupToken("signup-token"))
			.thenReturn(Optional.of(new SignupTokenPayload(SocialProvider.KAKAO, "kakao-user-id")));
		when(userRepository.existsBySocialProviderAndSocialProviderUserId(SocialProvider.KAKAO, "kakao-user-id"))
			.thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "userId", userId);
			return user;
		});
		when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
		when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("refresh-token");

		SignupResponse response = authService.signup(new SignupRequest(
			"signup-token",
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR,
			true));

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.selectedPrimaryUserType()).isEqualTo(PrimaryUserType.MOBILITY_IMPAIRED);
		assertThat(response.selectedMobilitySubtype()).isEqualTo(MobilitySubtype.MANUAL_WHEELCHAIR);
		InOrder inOrder = inOrder(authTokenStore);
		inOrder.verify(authTokenStore).saveRefreshToken("refresh-token", userId, REFRESH_TOKEN_TTL);
		inOrder.verify(authTokenStore).deleteSignupToken("signup-token");
	}

	@Test
	@DisplayName("필수 약관에 동의하지 않으면 회원가입을 거부한다")
	void rejectRequiredTermsNotAccepted() {
		assertThatThrownBy(() -> authService.signup(new SignupRequest(
			"signup-token",
			PrimaryUserType.LOW_VISION,
			null,
			false)))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.INVALID_AUTH_REQUEST);
	}

	@Test
	@DisplayName("저장소에 없는 회원가입 토큰은 거부한다")
	void rejectMissingSignupTokenInStore() {
		when(jwtTokenProvider.getSignupTokenPayload("signup-token"))
			.thenReturn(new SignupTokenPayload(SocialProvider.GOOGLE, "google-user-id"));
		when(authTokenStore.findSignupToken("signup-token")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.signup(new SignupRequest(
			"signup-token",
			PrimaryUserType.LOW_VISION,
			null,
			true)))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
	}

	@Test
	@DisplayName("이미 가입된 소셜 계정은 회원가입을 거부한다")
	void rejectAlreadyRegisteredSocialUser() {
		when(jwtTokenProvider.getSignupTokenPayload("signup-token"))
			.thenReturn(new SignupTokenPayload(SocialProvider.NAVER, "naver-user-id"));
		when(authTokenStore.findSignupToken("signup-token"))
			.thenReturn(Optional.of(new SignupTokenPayload(SocialProvider.NAVER, "naver-user-id")));
		when(userRepository.existsBySocialProviderAndSocialProviderUserId(SocialProvider.NAVER, "naver-user-id"))
			.thenReturn(true);

		assertThatThrownBy(() -> authService.signup(new SignupRequest(
			"signup-token",
			PrimaryUserType.LOW_VISION,
			null,
			true)))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.ALREADY_REGISTERED_SOCIAL_USER);
	}
}
