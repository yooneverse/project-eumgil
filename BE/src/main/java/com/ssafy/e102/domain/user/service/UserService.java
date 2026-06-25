package com.ssafy.e102.domain.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final AuthSessionService authSessionService;
	private final RouteRatingRepository routeRatingRepository;
	private final RouteSessionRepository routeSessionRepository;
	private final BookmarkRepository bookmarkRepository;
	private final FavoriteRouteRepository favoriteRouteRepository;
	private final HazardReportImageRepository hazardReportImageRepository;
	private final HazardReportRepository hazardReportRepository;
	private final AdminAreaAssignmentRepository adminAreaAssignmentRepository;

	public UserService(
		UserRepository userRepository,
		AuthSessionService authSessionService,
		RouteRatingRepository routeRatingRepository,
		RouteSessionRepository routeSessionRepository,
		BookmarkRepository bookmarkRepository,
		FavoriteRouteRepository favoriteRouteRepository,
		HazardReportImageRepository hazardReportImageRepository,
		HazardReportRepository hazardReportRepository,
		AdminAreaAssignmentRepository adminAreaAssignmentRepository) {
		this.userRepository = userRepository;
		this.authSessionService = authSessionService;
		this.routeRatingRepository = routeRatingRepository;
		this.routeSessionRepository = routeSessionRepository;
		this.bookmarkRepository = bookmarkRepository;
		this.favoriteRouteRepository = favoriteRouteRepository;
		this.hazardReportImageRepository = hazardReportImageRepository;
		this.hazardReportRepository = hazardReportRepository;
		this.adminAreaAssignmentRepository = adminAreaAssignmentRepository;
	}

	public UserMeResponse getMe(UUID userId) {
		return UserMeResponse.from(getUser(userId));
	}

	@Transactional
	public UserTypeResponse updateUserType(
		UUID userId,
		PrimaryUserType selectedPrimaryUserType,
		MobilitySubtype selectedMobilitySubtype) {
		User user = getUser(userId);
		user.changeUserType(selectedPrimaryUserType, selectedMobilitySubtype);
		return UserTypeResponse.from(user);
	}

	@Transactional
	public void withdraw(UUID userId, String accessToken) {
		if (!userRepository.existsById(userId)) {
			throw new UserException(UserErrorCode.USER_NOT_FOUND);
		}

		// 계정 삭제 후 남은 refresh token과 현재 access token을 함께 막아 재사용 여지를 줄인다.
		routeRatingRepository.deleteAllByUser_UserId(userId);
		routeSessionRepository.deleteAllByUser_UserId(userId);
		bookmarkRepository.deleteAllByUser_UserId(userId);
		favoriteRouteRepository.deleteAllByUser_UserId(userId);
		hazardReportImageRepository.deleteAllByHazardReport_User_UserId(userId);
		hazardReportRepository.deleteAllByUser_UserId(userId);
		adminAreaAssignmentRepository.findAllByAssignee_UserId(userId)
			.forEach(assignment -> assignment.assign(null));
		userRepository.deleteById(userId);
		authSessionService.invalidateUserSession(userId, accessToken);
	}

	private User getUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}
}
