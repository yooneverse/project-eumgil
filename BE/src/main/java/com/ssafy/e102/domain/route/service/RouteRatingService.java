package com.ssafy.e102.domain.route.service;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.route.dto.request.RouteRatingRequest;
import com.ssafy.e102.domain.route.dto.response.RouteRatingResponse;
import com.ssafy.e102.domain.route.entity.RouteRating;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteRatingRepository;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class RouteRatingService {

	private static final String POSTGRES_UNIQUE_VIOLATION_SQL_STATE = "23505";
	private static final String ROUTE_RATING_UNIQUE_CONSTRAINT = "uk_route_ratings_session";

	private final RouteRatingRepository routeRatingRepository;
	private final RouteSessionRepository routeSessionRepository;
	private final UserRepository userRepository;

	public RouteRatingService(
		RouteRatingRepository routeRatingRepository,
		RouteSessionRepository routeSessionRepository,
		UserRepository userRepository) {
		this.routeRatingRepository = routeRatingRepository;
		this.routeSessionRepository = routeSessionRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public RouteRatingResponse rate(UUID userId, RouteRatingRequest request) {
		RouteSession routeSession = getRouteSession(userId, request.sessionId());
		JsonNode routeContextJson = routeSession.getRouteSnapshotJson();
		if (routeContextJson == null || routeContextJson.isNull()) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}

		RouteRating routeRating = routeRatingRepository.findByRouteSession_SessionId(routeSession.getSessionId())
			.map(existingRating -> {
				existingRating.updateScore(request.score(), routeContextJson);
				return existingRating;
			})
			.orElseGet(
				() -> createRatingOrUpdateAfterUniqueConflict(userId, routeSession, request.score(), routeContextJson));
		return new RouteRatingResponse(routeRating.getRatingId());
	}

	private RouteSession getRouteSession(UUID userId, UUID sessionId) {
		RouteSession routeSession = routeSessionRepository.findById(sessionId)
			.orElseThrow(() -> new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND));
		if (!Objects.equals(routeSession.getUser().getUserId(), userId)) {
			throw new RouteException(RouteErrorCode.ROUTE_ACCESS_DENIED);
		}
		if (routeSession.getStatus() != RouteSessionStatus.COMPLETED) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_COMPLETED);
		}
		return routeSession;
	}

	private RouteRating createRatingOrUpdateAfterUniqueConflict(
		UUID userId,
		RouteSession routeSession,
		int score,
		JsonNode routeContextJson) {
		try {
			return createRating(userId, routeSession, score, routeContextJson);
		} catch (DataIntegrityViolationException exception) {
			if (!isRouteRatingUniqueViolation(exception)) {
				throw exception;
			}
			RouteRating existingRating = routeRatingRepository.findByRouteSession_SessionId(routeSession.getSessionId())
				.orElseThrow(() -> exception);
			existingRating.updateScore(score, routeContextJson);
			return existingRating;
		}
	}

	private RouteRating createRating(UUID userId, RouteSession routeSession, int score, JsonNode routeContextJson) {
		User user = userRepository.getReferenceById(userId);
		return routeRatingRepository.saveAndFlush(RouteRating.create(
			user,
			routeSession,
			score,
			routeContextJson));
	}

	private boolean isRouteRatingUniqueViolation(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof ConstraintViolationException constraintViolationException) {
				if (ROUTE_RATING_UNIQUE_CONSTRAINT.equals(constraintViolationException.getConstraintName())
					&& POSTGRES_UNIQUE_VIOLATION_SQL_STATE.equals(constraintViolationException.getSQLState())) {
					return true;
				}
			}
			if (current instanceof SQLException sqlException && isRouteRatingUniqueViolation(sqlException)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private boolean isRouteRatingUniqueViolation(SQLException exception) {
		SQLException current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (POSTGRES_UNIQUE_VIOLATION_SQL_STATE.equals(current.getSQLState())
				&& message != null
				&& message.contains(ROUTE_RATING_UNIQUE_CONSTRAINT)) {
				return true;
			}
			current = current.getNextException();
		}
		return false;
	}
}
