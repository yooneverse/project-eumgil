package com.ssafy.e102.global.security.principal;

import java.util.UUID;

public record AuthPrincipal(
	UUID userId,
	String accessToken) {

	public AuthPrincipal(UUID userId) {
		this(userId, null);
	}

	public static AuthPrincipal from(String userId) {
		return new AuthPrincipal(UUID.fromString(userId));
	}

	public static AuthPrincipal from(String userId, String accessToken) {
		return new AuthPrincipal(UUID.fromString(userId), accessToken);
	}
}
