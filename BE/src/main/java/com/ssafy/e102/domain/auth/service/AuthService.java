package com.ssafy.e102.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.auth.social.verifier.CompositeSocialTokenVerifier;
import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.auth.dto.request.ReissueRequest;
import com.ssafy.e102.domain.auth.dto.request.SignupRequest;
import com.ssafy.e102.domain.auth.dto.request.SocialLoginRequest;
import com.ssafy.e102.domain.auth.dto.response.SignupResponse;
import com.ssafy.e102.domain.auth.dto.response.SocialLoginResponse;
import com.ssafy.e102.domain.auth.dto.response.TokenResponse;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.auth.token.SignupTokenPayload;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.security.jwt.JwtProperties;
import com.ssafy.e102.global.security.jwt.JwtTokenException;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private final CompositeSocialTokenVerifier socialTokenVerifier;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final AuthTokenStore authTokenStore;
	private final JwtProperties jwtProperties;
	private final AuthSessionService authSessionService;

	public AuthService(
		CompositeSocialTokenVerifier socialTokenVerifier,
		UserRepository userRepository,
		JwtTokenProvider jwtTokenProvider,
		AuthTokenStore authTokenStore,
		JwtProperties jwtProperties,
		AuthSessionService authSessionService) {
		this.socialTokenVerifier = socialTokenVerifier;
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.authTokenStore = authTokenStore;
		this.jwtProperties = jwtProperties;
		this.authSessionService = authSessionService;
	}

	@Transactional
	public SocialLoginResponse socialLogin(SocialLoginRequest request) {
		SocialUserInfo socialUserInfo = socialTokenVerifier.verify(
			request.socialProvider(),
			request.socialAccessToken());

		return userRepository.findBySocialProviderAndSocialProviderUserId(
			socialUserInfo.socialProvider(),
			socialUserInfo.socialProviderUserId())
			.map(this::loginExistingUser)
			.orElseGet(() -> prepareSignup(socialUserInfo));
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (!request.requiredTermsAccepted()) {
			throw new AuthException(AuthErrorCode.INVALID_AUTH_REQUEST, "필수 약관에 동의해야 합니다.");
		}

		SignupTokenPayload signupTokenPayload = validateSignupToken(request.signupToken());
		if (userRepository.existsBySocialProviderAndSocialProviderUserId(
			signupTokenPayload.socialProvider(),
			signupTokenPayload.socialProviderUserId())) {
			throw new AuthException(AuthErrorCode.ALREADY_REGISTERED_SOCIAL_USER);
		}

		User user = User.create(
			signupTokenPayload.socialProvider(),
			signupTokenPayload.socialProviderUserId(),
			request.selectedPrimaryUserType(),
			request.selectedMobilitySubtype());
		User savedUser = userRepository.save(user);

		String accessToken = jwtTokenProvider.createAccessToken(savedUser.getUserId());
		String refreshToken = jwtTokenProvider.createRefreshToken(savedUser.getUserId());
		authTokenStore.saveRefreshToken(refreshToken, savedUser.getUserId(), jwtProperties.refreshTokenTtl());
		authTokenStore.deleteSignupToken(request.signupToken());

		return new SignupResponse(
			accessToken,
			refreshToken,
			savedUser.getUserId(),
			savedUser.getSelectedPrimaryUserType(),
			savedUser.getSelectedMobilitySubtype());
	}

	@Transactional
	public TokenResponse reissue(ReissueRequest request) {
		String oldRefreshToken = request.refreshToken();
		java.util.UUID tokenSubject = getRefreshTokenSubject(oldRefreshToken);

		String newAccessToken = jwtTokenProvider.createAccessToken(tokenSubject);
		String newRefreshToken = jwtTokenProvider.createRefreshToken(tokenSubject);
		boolean rotated = authTokenStore.rotateRefreshToken(oldRefreshToken, newRefreshToken, tokenSubject,
			jwtProperties.refreshTokenTtl());
		if (!rotated) {
			throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		return new TokenResponse(newAccessToken, newRefreshToken);
	}

	public void logout(java.util.UUID userId, String accessToken) {
		authSessionService.invalidateUserSession(userId, accessToken);
	}

	private SocialLoginResponse loginExistingUser(User user) {
		String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
		String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
		authTokenStore.saveRefreshToken(refreshToken, user.getUserId(), jwtProperties.refreshTokenTtl());

		return SocialLoginResponse.existingUser(
			accessToken,
			refreshToken,
			user.getUserId(),
			user.getSelectedPrimaryUserType(),
			user.getSelectedMobilitySubtype());
	}

	private SocialLoginResponse prepareSignup(SocialUserInfo socialUserInfo) {
		String signupToken = jwtTokenProvider.createSignupToken(
			socialUserInfo.socialProvider(),
			socialUserInfo.socialProviderUserId());
		authTokenStore.saveSignupToken(signupToken, new SignupTokenPayload(
			socialUserInfo.socialProvider(),
			socialUserInfo.socialProviderUserId()), jwtProperties.signupTokenTtl());

		return SocialLoginResponse.newUser(signupToken);
	}

	private SignupTokenPayload validateSignupToken(String signupToken) {
		SignupTokenPayload claims = getSignupTokenPayload(signupToken);
		SignupTokenPayload stored = authTokenStore.findSignupToken(signupToken)
			.orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
		if (claims.socialProvider() != stored.socialProvider()
			|| !claims.socialProviderUserId().equals(stored.socialProviderUserId())) {
			throw new AuthException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
		}
		return stored;
	}

	private SignupTokenPayload getSignupTokenPayload(String signupToken) {
		try {
			return jwtTokenProvider.getSignupTokenPayload(signupToken);
		} catch (JwtTokenException exception) {
			throw new AuthException(AuthErrorCode.INVALID_SIGNUP_TOKEN,
				AuthErrorCode.INVALID_SIGNUP_TOKEN.getMessage(), exception);
		}
	}

	private java.util.UUID getRefreshTokenSubject(String refreshToken) {
		try {
			return jwtTokenProvider.getRefreshTokenSubject(refreshToken);
		} catch (JwtTokenException exception) {
			throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN,
				AuthErrorCode.INVALID_REFRESH_TOKEN.getMessage(), exception);
		}
	}
}
