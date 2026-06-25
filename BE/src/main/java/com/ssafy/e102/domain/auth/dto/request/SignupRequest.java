package com.ssafy.e102.domain.auth.dto.request;

import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupRequest(
	@NotBlank(message = "회원가입 토큰은 필수입니다.")
	String signupToken,

	@NotNull(message = "1차 사용자 유형은 필수입니다.")
	PrimaryUserType selectedPrimaryUserType,

	MobilitySubtype selectedMobilitySubtype,

	boolean requiredTermsAccepted) {
}
