package com.ssafy.e102.domain.auth.social.verifier;

import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.user.type.SocialProvider;

public interface SocialTokenVerifier {

	SocialProvider getProvider();

	SocialUserInfo verify(String socialAccessToken);
}
