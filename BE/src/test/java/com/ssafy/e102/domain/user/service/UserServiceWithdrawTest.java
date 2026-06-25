package com.ssafy.e102.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.admin.entity.AdminAreaAssignment;
import com.ssafy.e102.domain.admin.repository.AdminAreaAssignmentRepository;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;
import com.ssafy.e102.domain.auth.service.AuthSessionService;
import com.ssafy.e102.domain.bookmark.repository.FavoriteRouteRepository;
import com.ssafy.e102.domain.place.repository.BookmarkRepository;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.route.repository.RouteRatingRepository;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;

class UserServiceWithdrawTest {

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
	@DisplayName("회원탈퇴는 사용자 종속 데이터와 관리자 배정을 정리한 뒤 현재 사용자를 삭제하고 인증 세션을 무효화한다")
	void withdraw() {
		UUID userId = UUID.randomUUID();
		User assignee = User.create(
			SocialProvider.KAKAO,
			"kakao-user-id",
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR);
		AdminAreaAssignment assignment = AdminAreaAssignment.create(
			"부산진구",
			"부전동",
			AdminAreaAssignmentType.ROAD_NETWORK,
			assignee,
			AdminAreaWorkStatus.IN_PROGRESS);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(adminAreaAssignmentRepository.findAllByAssignee_UserId(userId)).thenReturn(List.of(assignment));

		userService.withdraw(userId, "access-token");

		InOrder inOrder = inOrder(
			routeRatingRepository,
			routeSessionRepository,
			bookmarkRepository,
			favoriteRouteRepository,
			hazardReportImageRepository,
			hazardReportRepository,
			adminAreaAssignmentRepository,
			userRepository,
			authSessionService);
		inOrder.verify(routeRatingRepository).deleteAllByUser_UserId(userId);
		inOrder.verify(routeSessionRepository).deleteAllByUser_UserId(userId);
		inOrder.verify(bookmarkRepository).deleteAllByUser_UserId(userId);
		inOrder.verify(favoriteRouteRepository).deleteAllByUser_UserId(userId);
		inOrder.verify(hazardReportImageRepository).deleteAllByHazardReport_User_UserId(userId);
		inOrder.verify(hazardReportRepository).deleteAllByUser_UserId(userId);
		inOrder.verify(adminAreaAssignmentRepository).findAllByAssignee_UserId(userId);
		inOrder.verify(userRepository).deleteById(userId);
		inOrder.verify(authSessionService).invalidateUserSession(userId, "access-token");
		assertThat(assignment.getAssignee()).isNull();
	}

	@Test
	@DisplayName("현재 사용자가 없으면 회원탈퇴를 거부한다")
	void rejectMissingUser() {
		UUID userId = UUID.randomUUID();
		when(userRepository.existsById(userId)).thenReturn(false);

		assertThatThrownBy(() -> userService.withdraw(userId, "access-token"))
			.isInstanceOf(UserException.class)
			.extracting("errorCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);

		verify(userRepository, never()).deleteById(userId);
		verify(adminAreaAssignmentRepository, never()).findAllByAssignee_UserId(userId);
		verify(authSessionService, never()).invalidateUserSession(userId, "access-token");
	}
}
