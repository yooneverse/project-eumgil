package com.ssafy.e102.domain.user.dto.response;

import java.util.UUID;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;

public record UserMeResponse(
	UUID userId,
	SocialProvider socialProvider,
	PrimaryUserType selectedPrimaryUserType,
	MobilitySubtype selectedMobilitySubtype) {

	public static UserMeResponse from(User user) {
		return new UserMeResponse(
			user.getUserId(),
			user.getSocialProvider(),
			user.getSelectedPrimaryUserType(),
			user.getSelectedMobilitySubtype());
	}
}
