package com.ssafy.e102.domain.auth.social.verifier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.user.type.SocialProvider;

@Component
public class CompositeSocialTokenVerifier {

	private final Map<SocialProvider, SocialTokenVerifier> verifiers;

	public CompositeSocialTokenVerifier(List<SocialTokenVerifier> verifiers) {
		this.verifiers = new EnumMap<>(SocialProvider.class);
		for (SocialTokenVerifier verifier : verifiers) {
			this.verifiers.put(verifier.getProvider(), verifier);
		}
	}

	public SocialUserInfo verify(SocialProvider socialProvider, String socialAccessToken) {
		SocialTokenVerifier verifier = verifiers.get(socialProvider);
		if (verifier == null) {
			throw new AuthException(AuthErrorCode.INVALID_AUTH_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다.");
		}
		return verifier.verify(socialAccessToken);
	}
}
