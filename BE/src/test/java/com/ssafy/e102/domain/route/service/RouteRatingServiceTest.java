package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;

class RouteRatingServiceTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

	private final ObjectMapper objectMapper = new ObjectMapper();
	private RouteRatingRepository routeRatingRepository;
	private RouteSessionRepository routeSessionRepository;
	private UserRepository userRepository;
	private RouteRatingService service;

	@BeforeEach
	void setUp() {
		routeRatingRepository = mock(RouteRatingRepository.class);
		routeSessionRepository = mock(RouteSessionRepository.class);
		userRepository = mock(UserRepository.class);
		service = new RouteRatingService(routeRatingRepository, routeSessionRepository, userRepository);
	}

	@Test
	@DisplayName("route session snapshot을 route_context_json으로 복사해 rating을 저장한다")
	void rateStoresRouteContextFromSession() {
		JsonNode snapshot = snapshot("rt_selected_001");
		RouteSession routeSession = routeSession("rt_selected_001", snapshot);
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.of(routeSession));
		when(routeRatingRepository.findByRouteSession_SessionId(routeSession.getSessionId()))
			.thenReturn(Optional.empty());
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
		when(routeRatingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(RouteRating.class)))
			.thenAnswer(invocation -> {
				RouteRating rating = invocation.getArgument(0);
				ReflectionTestUtils.setField(rating, "ratingId", 1L);
				return rating;
			});

		RouteRatingResponse response = service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 5));

		ArgumentCaptor<RouteRating> ratingCaptor = ArgumentCaptor.forClass(RouteRating.class);
		verify(routeRatingRepository).saveAndFlush(ratingCaptor.capture());
		assertThat(response.ratingId()).isEqualTo(1L);
		assertThat(ratingCaptor.getValue().getRouteId()).isEqualTo("rt_selected_001");
		assertThat(ratingCaptor.getValue().getRouteSession()).isSameAs(routeSession);
		assertThat(ratingCaptor.getValue().getScore()).isEqualTo((short)5);
		assertThat(ratingCaptor.getValue().getRouteContextJson()).isEqualTo(snapshot);
	}

	@Test
	@DisplayName("sessionId에 해당하는 route session이 없으면 RT4043으로 평가를 차단한다")
	void rateRejectsMissingRouteSession() {
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 4)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("ACTIVE route session은 안내 종료 전 상태이므로 rating을 차단한다")
	void rateRejectsActiveRouteSession() {
		RouteSession routeSession = routeSession("rt_selected_001", snapshot("rt_selected_001"),
			RouteSessionStatus.ACTIVE);
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.of(routeSession));

		assertThatThrownBy(() -> service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 4)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_COMPLETED);
	}

	@Test
	@DisplayName("route session snapshot이 없으면 RT4043으로 평가를 차단한다")
	void rateRejectsRouteSessionWithoutSnapshot() {
		RouteSession routeSession = routeSession("rt_selected_001", null);
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.of(routeSession));

		assertThatThrownBy(() -> service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 4)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("같은 route session 평가는 최신 score로 갱신한다")
	void rateUpdatesExistingRating() {
		RouteSession routeSession = routeSession("rt_selected_001", snapshot("old"));
		RouteRating existingRating = RouteRating.create(user(USER_ID), routeSession, 3, snapshot("old"));
		ReflectionTestUtils.setField(existingRating, "ratingId", 3L);
		JsonNode snapshot = snapshot("rt_selected_001");
		when(routeSession.getRouteSnapshotJson()).thenReturn(snapshot);
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.of(routeSession));
		when(routeRatingRepository.findByRouteSession_SessionId(routeSession.getSessionId()))
			.thenReturn(Optional.of(existingRating));

		RouteRatingResponse response = service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 5));

		assertThat(response.ratingId()).isEqualTo(3L);
		assertThat(existingRating.getScore()).isEqualTo((short)5);
		assertThat(existingRating.getRouteContextJson()).isEqualTo(snapshot);
	}

	@Test
	@DisplayName("다른 사용자의 route session은 A4030으로 차단한다")
	void rateRejectsOtherUserRoute() {
		RouteSession otherUserSession = routeSession("other_route", snapshot("other_route"));
		when(otherUserSession.getUser()).thenReturn(user(UUID.fromString("00000000-0000-0000-0000-000000000002")));
		when(routeSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(otherUserSession));

		assertThatThrownBy(() -> service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 5)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_ACCESS_DENIED);
	}

	@Test
	@DisplayName("동시 중복 저장 unique 충돌은 기존 rating score 갱신으로 흡수한다")
	void rateUpdatesExistingRatingAfterUniqueConflict() {
		JsonNode snapshot = snapshot("rt_selected_001");
		RouteSession routeSession = routeSession("rt_selected_001", snapshot);
		RouteRating existingRating = RouteRating.create(user(USER_ID), routeSession, 2, snapshot("old"));
		ReflectionTestUtils.setField(existingRating, "ratingId", 4L);
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.of(routeSession));
		when(routeRatingRepository.findByRouteSession_SessionId(routeSession.getSessionId()))
			.thenReturn(Optional.empty(), Optional.of(existingRating));
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
		when(routeRatingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(RouteRating.class)))
			.thenThrow(routeRatingUniqueViolation());

		RouteRatingResponse response = service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 5));

		assertThat(response.ratingId()).isEqualTo(4L);
		assertThat(existingRating.getScore()).isEqualTo((short)5);
		assertThat(existingRating.getRouteContextJson()).isEqualTo(snapshot);
	}

	@Test
	@DisplayName("route rating unique 충돌이 아니면 DB 예외를 전파한다")
	void ratePropagatesUnexpectedDataIntegrityViolation() {
		RouteSession routeSession = mock(RouteSession.class);
		when(routeSession.getSessionId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000099"));
		when(routeSession.getRouteSnapshotJson()).thenReturn(snapshot("rt_selected_001"));
		when(routeSession.getUser()).thenReturn(user(USER_ID));
		when(routeSession.getStatus()).thenReturn(RouteSessionStatus.COMPLETED);
		when(routeSessionRepository.findById(SESSION_ID))
			.thenReturn(Optional.of(routeSession));
		when(routeRatingRepository.findByRouteSession_SessionId(routeSession.getSessionId()))
			.thenReturn(Optional.empty());
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
		when(routeRatingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(RouteRating.class)))
			.thenThrow(new DataIntegrityViolationException("unknown constraint"));

		assertThatThrownBy(() -> service.rate(USER_ID, new RouteRatingRequest(SESSION_ID, 5)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private DataIntegrityViolationException routeRatingUniqueViolation() {
		SQLException sqlException = new SQLException(
			"duplicate key value violates unique constraint \"uk_route_ratings_session\"",
			"23505");
		ConstraintViolationException constraintViolationException = new ConstraintViolationException(
			"could not execute statement",
			sqlException,
			"uk_route_ratings_session");
		return new DataIntegrityViolationException("duplicate route rating", constraintViolationException);
	}

	private JsonNode snapshot(String routeId) {
		return objectMapper.createObjectNode().put("routeId", routeId);
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}

	private RouteSession routeSession(String routeId, JsonNode snapshot) {
		return routeSession(routeId, snapshot, RouteSessionStatus.COMPLETED);
	}

	private RouteSession routeSession(String routeId, JsonNode snapshot, RouteSessionStatus status) {
		RouteSession routeSession = mock(RouteSession.class);
		when(routeSession.getSessionId()).thenReturn(SESSION_ID);
		when(routeSession.getRouteId()).thenReturn(routeId);
		when(routeSession.getRouteSnapshotJson()).thenReturn(snapshot);
		when(routeSession.getUser()).thenReturn(user(USER_ID));
		when(routeSession.getStatus()).thenReturn(status);
		return routeSession;
	}
}
