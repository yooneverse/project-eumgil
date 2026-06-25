package com.ssafy.e102.domain.route.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.route.dto.response.RouteSessionResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;

@Service
public class RouteSessionCommandService {

	private final RouteSessionRepository routeSessionRepository;
	private final UserRepository userRepository;

	public RouteSessionCommandService(RouteSessionRepository routeSessionRepository, UserRepository userRepository) {
		this.routeSessionRepository = routeSessionRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public RouteSessionResponse saveActiveSessionIfAbsent(
		UUID userId,
		String routeId,
		Point startPoint,
		Point endPoint,
		JsonNode routeSnapshotJson) {
		normalizeActiveSessions(userId, routeId);
		Optional<RouteSession> existingActiveSession = findActiveSession(userId, routeId);
		if (existingActiveSession.isPresent()) {
			return RouteSessionResponse.from(existingActiveSession.get());
		}
		User user = userRepository.getReferenceById(userId);
		RouteSession routeSession = routeSessionRepository.saveAndFlush(RouteSession.create(
			user,
			routeId,
			startPoint,
			endPoint,
			routeSnapshotJson));
		return RouteSessionResponse.from(routeSession);
	}

	@Transactional(readOnly = true)
	public boolean hasActiveSession(UUID userId, String routeId) {
		return findActiveSession(userId, routeId).isPresent();
	}

	@Transactional(readOnly = true)
	public RouteSessionResponse getActiveSession(UUID userId, String routeId) {
		return findActiveSession(userId, routeId)
			.map(RouteSessionResponse::from)
			.orElseThrow(() -> new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND));
	}

	@Transactional
	public RouteSessionResponse endSession(UUID userId, String routeId) {
		normalizeActiveSessions(userId, routeId);
		Optional<RouteSession> activeSession = findActiveSession(userId, routeId);
		if (activeSession.isPresent()) {
			RouteSession routeSession = activeSession.get();
			routeSession.complete();
			return RouteSessionResponse.from(routeSession);
		}
		Optional<RouteSession> ownedSession = findOwnedSession(userId, routeId);
		if (ownedSession.isPresent()) {
			return RouteSessionResponse.from(ownedSession.get());
		}
		if (findAnySession(routeId).isPresent()) {
			throw new RouteException(RouteErrorCode.ROUTE_ACCESS_DENIED);
		}
		throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	private void normalizeActiveSessions(UUID userId, String routeId) {
		List<RouteSession> activeSessions = routeSessionRepository
			.findAllByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(userId, routeId, RouteSessionStatus.ACTIVE);
		if (activeSessions == null || activeSessions.isEmpty()) {
			return;
		}

		RouteSession latest = activeSessions.get(0);
		List<RouteSession> duplicates = activeSessions.subList(1, activeSessions.size());
		if (!duplicates.isEmpty()) {
			duplicates.forEach(RouteSession::complete);
			routeSessionRepository.saveAllAndFlush(duplicates);
		}
		if (latest.ensureActiveRouteKey()) {
			routeSessionRepository.saveAndFlush(latest);
		}
	}

	private Optional<RouteSession> findActiveSession(UUID userId, String routeId) {
		return Optional.ofNullable(routeSessionRepository
			.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
				userId,
				routeId,
				RouteSessionStatus.ACTIVE))
			.orElse(Optional.empty());
	}

	private Optional<RouteSession> findOwnedSession(UUID userId, String routeId) {
		return Optional.ofNullable(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(
			userId,
			routeId))
			.orElse(Optional.empty());
	}

	private Optional<RouteSession> findAnySession(String routeId) {
		return Optional.ofNullable(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc(routeId))
			.orElse(Optional.empty());
	}
}
