package com.ssafy.e102.domain.auth.dto.response;

import java.util.UUID;

import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

public record SocialLoginResponse(
	boolean signupRequired,
	String signupToken,
	String accessToken,
	String refreshToken,
	UUID userId,
	PrimaryUserType selectedPrimaryUserType,
	MobilitySubtype selectedMobilitySubtype) {

	public static SocialLoginResponse existingUser(
		String accessToken,
		String refreshToken,
		UUID userId,
		PrimaryUserType selectedPrimaryUserType,
		MobilitySubtype selectedMobilitySubtype) {
		return new SocialLoginResponse(
			false,
			null,
			accessToken,
			refreshToken,
			userId,
			selectedPrimaryUserType,
			selectedMobilitySubtype);
	}

	public static SocialLoginResponse newUser(String signupToken) {
		return new SocialLoginResponse(
			true,
			signupToken,
			null,
			null,
			null,
			null,
			null);
	}
}
