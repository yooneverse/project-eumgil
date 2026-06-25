package com.ssafy.e102.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.domain.user.type.UserRole;

class UserTest {

	@Test
	@DisplayName("저시력자는 보행약자 세부 유형 없이 생성한다")
	void createLowVisionUser() {
		User user = User.create(
			SocialProvider.KAKAO,
			"kakao-user-id",
			PrimaryUserType.LOW_VISION,
			null);

		assertThat(user.getSocialProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(user.getSocialProviderUserId()).isEqualTo("kakao-user-id");
		assertThat(user.getSelectedPrimaryUserType()).isEqualTo(PrimaryUserType.LOW_VISION);
		assertThat(user.getSelectedMobilitySubtype()).isNull();
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
	}

	@Test
	@DisplayName("보행약자는 보행약자 세부 유형이 필요하다")
	void createMobilityImpairedUser() {
		User user = User.create(
			SocialProvider.NAVER,
			"naver-user-id",
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR);

		assertThat(user.getSelectedPrimaryUserType()).isEqualTo(PrimaryUserType.MOBILITY_IMPAIRED);
		assertThat(user.getSelectedMobilitySubtype()).isEqualTo(MobilitySubtype.MANUAL_WHEELCHAIR);
	}

	@Test
	@DisplayName("저시력자는 보행약자 세부 유형을 저장할 수 없다")
	void rejectLowVisionWithMobilitySubtype() {
		assertThatThrownBy(() -> User.create(
			SocialProvider.GOOGLE,
			"google-user-id",
			PrimaryUserType.LOW_VISION,
			MobilitySubtype.OTHER_MOBILITY))
			.isInstanceOf(UserException.class)
			.extracting("errorCode")
			.isEqualTo(UserErrorCode.INVALID_USER_TYPE);
		assertThatThrownBy(() -> User.create(
			SocialProvider.GOOGLE,
			"google-user-id",
			PrimaryUserType.LOW_VISION,
			MobilitySubtype.OTHER_MOBILITY))
			.hasMessage("저시력자는 보행약자 세부 유형을 가질 수 없습니다.");
	}

	@Test
	@DisplayName("보행약자는 보행약자 세부 유형 없이 저장할 수 없다")
	void rejectMobilityImpairedWithoutSubtype() {
		assertThatThrownBy(() -> User.create(
			SocialProvider.KAKAO,
			"kakao-user-id",
			PrimaryUserType.MOBILITY_IMPAIRED,
			null))
			.isInstanceOf(UserException.class)
			.extracting("errorCode")
			.isEqualTo(UserErrorCode.REQUIRED_PROFILE_MISSING);
		assertThatThrownBy(() -> User.create(
			SocialProvider.KAKAO,
			"kakao-user-id",
			PrimaryUserType.MOBILITY_IMPAIRED,
			null))
			.hasMessage("보행약자는 보행약자 세부 유형이 필요합니다.");
	}

	@Test
	@DisplayName("소셜 사용자 ID는 빈 값일 수 없다")
	void rejectBlankSocialProviderUserId() {
		assertThatThrownBy(() -> User.create(
			SocialProvider.KAKAO,
			" ",
			PrimaryUserType.LOW_VISION,
			null))
			.isInstanceOf(UserException.class)
			.extracting("errorCode")
			.isEqualTo(UserErrorCode.INVALID_USER_REQUEST);
		assertThatThrownBy(() -> User.create(
			SocialProvider.KAKAO,
			" ",
			PrimaryUserType.LOW_VISION,
			null))
			.hasMessage("소셜 사용자 ID는 필수입니다.");
	}
}
