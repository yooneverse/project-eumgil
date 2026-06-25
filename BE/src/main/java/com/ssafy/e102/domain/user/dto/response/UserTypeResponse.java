package com.ssafy.e102.domain.user.dto.response;

import java.util.UUID;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

public record UserTypeResponse(
	UUID userId,
	PrimaryUserType selectedPrimaryUserType,
	MobilitySubtype selectedMobilitySubtype) {

	public static UserTypeResponse from(User user) {
		return new UserTypeResponse(
			user.getUserId(),
			user.getSelectedPrimaryUserType(),
			user.getSelectedMobilitySubtype());
	}
}
