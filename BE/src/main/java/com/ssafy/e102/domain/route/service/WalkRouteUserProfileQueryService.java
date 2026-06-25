package com.ssafy.e102.domain.route.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;

@Service
public class WalkRouteUserProfileQueryService {

	private final UserRepository userRepository;

	public WalkRouteUserProfileQueryService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public WalkRouteUserProfile getProfile(UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
		return new WalkRouteUserProfile(
			user.getSelectedPrimaryUserType(),
			user.getSelectedMobilitySubtype());
	}
}
