package com.ssafy.e102.domain.user.dto.request;

import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

import jakarta.validation.constraints.NotNull;

public record UpdateUserTypeRequest(
	@NotNull(message = "1차 사용자 유형은 필수입니다.")
	PrimaryUserType selectedPrimaryUserType,

	MobilitySubtype selectedMobilitySubtype) {
}
