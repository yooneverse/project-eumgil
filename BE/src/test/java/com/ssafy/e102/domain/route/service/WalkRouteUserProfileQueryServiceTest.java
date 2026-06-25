package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;

class WalkRouteUserProfileQueryServiceTest {

	@Mock
	private UserRepository userRepository;

	private WalkRouteUserProfileQueryService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new WalkRouteUserProfileQueryService(userRepository);
	}

	@Test
	@DisplayName("사용자 ID로 route profile 결정에 필요한 사용자 유형만 조회한다")
	void getProfileReturnsRouteUserProfile() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId))
			.thenReturn(Optional.of(user(userId, PrimaryUserType.MOBILITY_IMPAIRED,
				MobilitySubtype.MANUAL_WHEELCHAIR)));

		WalkRouteUserProfile profile = service.getProfile(userId);

		assertThat(profile.primaryUserType()).isEqualTo(PrimaryUserType.MOBILITY_IMPAIRED);
		assertThat(profile.mobilitySubtype()).isEqualTo(MobilitySubtype.MANUAL_WHEELCHAIR);
	}

	@Test
	@DisplayName("사용자가 없으면 USER_NOT_FOUND 예외를 반환한다")
	void getProfileRejectsMissingUser() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getProfile(userId))
			.isInstanceOf(UserException.class)
			.extracting(exception -> ((UserException)exception).getErrorCode())
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	private User user(UUID userId, PrimaryUserType primaryUserType, MobilitySubtype mobilitySubtype) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", primaryUserType, mobilitySubtype);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
