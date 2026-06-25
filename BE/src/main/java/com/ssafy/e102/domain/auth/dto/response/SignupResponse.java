package com.ssafy.e102.domain.auth.dto.response;

import java.util.UUID;

import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

public record SignupResponse(
	String accessToken,
	String refreshToken,
	UUID userId,
	PrimaryUserType selectedPrimaryUserType,
	MobilitySubtype selectedMobilitySubtype) {
}
