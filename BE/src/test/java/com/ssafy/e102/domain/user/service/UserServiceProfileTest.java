package com.ssafy.e102.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.admin.repository.AdminAreaAssignmentRepository;
import com.ssafy.e102.domain.auth.service.AuthSessionService;
import com.ssafy.e102.domain.bookmark.repository.FavoriteRouteRepository;
import com.ssafy.e102.domain.place.repository.BookmarkRepository;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.route.repository.RouteRatingRepository;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.user.dto.response.UserMeResponse;
import com.ssafy.e102.domain.user.dto.response.UserTypeResponse;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;

class UserServiceProfileTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private AuthSessionService authSessionService;

	@Mock
	private RouteRatingRepository routeRatingRepository;

	@Mock
	private RouteSessionRepository routeSessionRepository;

	@Mock
	private BookmarkRepository bookmarkRepository;

	@Mock
	private FavoriteRouteRepository favoriteRouteRepository;

	@Mock
	private HazardReportImageRepository hazardReportImageRepository;

	@Mock
	private HazardReportRepository hazardReportRepository;

	@Mock
	private AdminAreaAssignmentRepository adminAreaAssignmentRepository;

	private UserService userService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		userService = new UserService(
			userRepository,
			authSessionService,
			routeRatingRepository,
			routeSessionRepository,
			bookmarkRepository,
			favoriteRouteRepository,
			hazardReportImageRepository,
			hazardReportRepository,
			adminAreaAssignmentRepository);
	}

	@Test
	@DisplayName("내 정보 조회는 현재 사용자 정보를 반환한다")
	void getMe() {
		UUID userId = UUID.randomUUID();
		User user = user(userId, PrimaryUserType.MOBILITY_IMPAIRED, MobilitySubtype.MANUAL_WHEELCHAIR);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UserMeResponse response = userService.getMe(userId);

		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.socialProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(response.selectedPrimaryUserType()).isEqualTo(PrimaryUserType.MOBILITY_IMPAIRED);
		assertThat(response.selectedMobilitySubtype()).isEqualTo(MobilitySubtype.MANUAL_WHEELCHAIR);
	}

	@Test
	@DisplayName("사용자가 없으면 내 정보 조회를 거부한다")
	void rejectMissingUserWhenGetMe() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getMe(userId))
			.isInstanceOf(UserException.class)
			.extracting("errorCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("사용자 유형 수정은 엔티티 규칙으로 유형을 변경한다")
	void updateUserType() {
		UUID userId = UUID.randomUUID();
		User user = user(userId, PrimaryUserType.MOBILITY_IMPAIRED, MobilitySubtype.POWER_WHEELCHAIR);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UserTypeResponse response = userService.updateUserType(userId, PrimaryUserType.LOW_VISION, null);

		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.selectedPrimaryUserType()).isEqualTo(PrimaryUserType.LOW_VISION);
		assertThat(response.selectedMobilitySubtype()).isNull();
		assertThat(user.getSelectedPrimaryUserType()).isEqualTo(PrimaryUserType.LOW_VISION);
		assertThat(user.getSelectedMobilitySubtype()).isNull();
		verify(userRepository).findById(userId);
	}

	@Test
	@DisplayName("사용자가 없으면 사용자 유형 수정을 거부한다")
	void rejectMissingUserWhenUpdateUserType() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updateUserType(
			userId,
			PrimaryUserType.LOW_VISION,
			null))
			.isInstanceOf(UserException.class)
			.extracting("errorCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	private User user(UUID userId, PrimaryUserType primaryUserType, MobilitySubtype mobilitySubtype) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", primaryUserType, mobilitySubtype);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
