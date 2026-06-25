package com.ssafy.e102.domain.user.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.domain.user.type.UserRole;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
	@UniqueConstraint(name = "uk_users_social_provider_user_id", columnNames = {"social_provider",
		"social_provider_user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "social_provider", nullable = false, length = 30)
	private SocialProvider socialProvider;

	@Column(name = "social_provider_user_id", nullable = false, length = 100)
	private String socialProviderUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "selected_primary_user_type", nullable = false, length = 30)
	private PrimaryUserType selectedPrimaryUserType;

	@Enumerated(EnumType.STRING)
	@Column(name = "selected_mobility_subtype", length = 30)
	private MobilitySubtype selectedMobilitySubtype;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 30, columnDefinition = "varchar(30) default 'USER'")
	private UserRole role;

	public static User create(
		SocialProvider socialProvider,
		String socialProviderUserId,
		PrimaryUserType selectedPrimaryUserType,
		MobilitySubtype selectedMobilitySubtype) {
		validateSocialIdentity(socialProvider, socialProviderUserId);

		User user = new User();
		user.socialProvider = socialProvider;
		user.socialProviderUserId = socialProviderUserId;
		user.role = UserRole.USER;
		user.changeUserType(selectedPrimaryUserType, selectedMobilitySubtype);
		return user;
	}

	public void changeUserType(
		PrimaryUserType selectedPrimaryUserType,
		MobilitySubtype selectedMobilitySubtype) {
		// 사용자 유형 조합은 ERD/API 명세의 저장 규칙을 엔티티에서 한 번 더 막는다.
		validateUserType(selectedPrimaryUserType, selectedMobilitySubtype);

		this.selectedPrimaryUserType = selectedPrimaryUserType;
		this.selectedMobilitySubtype = normalizeMobilitySubtype(selectedPrimaryUserType, selectedMobilitySubtype);
	}

	public void changeRole(UserRole role) {
		if (role == null) {
			throw new UserException(UserErrorCode.INVALID_USER_REQUEST, "사용자 권한은 필수입니다.");
		}
		this.role = role;
	}

	private static void validateSocialIdentity(SocialProvider socialProvider, String socialProviderUserId) {
		if (socialProvider == null) {
			throw new UserException(UserErrorCode.INVALID_USER_REQUEST, "소셜 제공자는 필수입니다.");
		}
		if (socialProviderUserId == null || socialProviderUserId.isBlank()) {
			throw new UserException(UserErrorCode.INVALID_USER_REQUEST, "소셜 사용자 ID는 필수입니다.");
		}
	}

	private static void validateUserType(
		PrimaryUserType selectedPrimaryUserType,
		MobilitySubtype selectedMobilitySubtype) {
		if (selectedPrimaryUserType == null) {
			throw new UserException(UserErrorCode.REQUIRED_PROFILE_MISSING, "1차 사용자 유형은 필수입니다.");
		}
		if (selectedPrimaryUserType == PrimaryUserType.LOW_VISION && selectedMobilitySubtype != null) {
			throw new UserException(UserErrorCode.INVALID_USER_TYPE, "저시력자는 보행약자 세부 유형을 가질 수 없습니다.");
		}
		if (selectedPrimaryUserType == PrimaryUserType.MOBILITY_IMPAIRED && selectedMobilitySubtype == null) {
			throw new UserException(UserErrorCode.REQUIRED_PROFILE_MISSING, "보행약자는 보행약자 세부 유형이 필요합니다.");
		}
	}

	private static MobilitySubtype normalizeMobilitySubtype(
		PrimaryUserType selectedPrimaryUserType,
		MobilitySubtype selectedMobilitySubtype) {
		// 저시력자는 DB에도 보행약자 세부 유형을 남기지 않는다.
		if (selectedPrimaryUserType == PrimaryUserType.LOW_VISION) {
			return null;
		}
		return selectedMobilitySubtype;
	}
}
