package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.domain.user.type.UserRole;

public record AdminUserResponse(
	UUID userId,
	SocialProvider socialProvider,
	String socialProviderUserId,
	PrimaryUserType selectedPrimaryUserType,
	MobilitySubtype selectedMobilitySubtype,
	UserRole role,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public static AdminUserResponse from(User user) {
		return new AdminUserResponse(
			user.getUserId(),
			user.getSocialProvider(),
			user.getSocialProviderUserId(),
			user.getSelectedPrimaryUserType(),
			user.getSelectedMobilitySubtype(),
			user.getRole(),
			user.getCreatedAt(),
			user.getUpdatedAt());
	}
}
