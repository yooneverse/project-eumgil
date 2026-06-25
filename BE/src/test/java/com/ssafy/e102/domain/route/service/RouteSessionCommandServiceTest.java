package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.dto.response.RouteSessionResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;

class RouteSessionCommandServiceTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private RouteSessionRepository routeSessionRepository;
	private UserRepository userRepository;
	private RouteSessionCommandService service;

	@BeforeEach
	void setUp() {
		routeSessionRepository = mock(RouteSessionRepository.class);
		userRepository = mock(UserRepository.class);
		service = new RouteSessionCommandService(routeSessionRepository, userRepository);
	}

	@Test
	@DisplayName("ACTIVE route session 저장 시 activeRouteKey를 routeId로 채워 unique 제약 대상이 되게 한다")
	void saveActiveSessionUsesActiveRouteKey() {
		User user = user(USER_ID);
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.empty());
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
		when(routeSessionRepository.saveAndFlush(any(RouteSession.class))).thenAnswer(invocation -> {
			RouteSession session = invocation.getArgument(0);
			ReflectionTestUtils.setField(session, "sessionId", sessionId);
			return session;
		});

		RouteSessionResponse response = service.saveActiveSessionIfAbsent(
			USER_ID,
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));

		ArgumentCaptor<RouteSession> captor = ArgumentCaptor.forClass(RouteSession.class);
		verify(routeSessionRepository).saveAndFlush(captor.capture());
		assertThat(response.sessionId()).isEqualTo(captor.getValue().getSessionId());
		assertThat(captor.getValue().getUser()).isEqualTo(user);
		assertThat(captor.getValue().getRouteId()).isEqualTo("rt_selected_001");
		assertThat(captor.getValue().getActiveRouteKey()).isEqualTo("rt_selected_001");
		assertThat(captor.getValue().getStatus()).isEqualTo(RouteSessionStatus.ACTIVE);
	}

	@Test
	@DisplayName("이미 ACTIVE session이 있으면 추가 저장하지 않는다")
	void saveActiveSessionSkipsDuplicateActiveRoute() {
		RouteSession activeSession = mock(RouteSession.class);
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		when(activeSession.getSessionId()).thenReturn(sessionId);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.of(activeSession));

		RouteSessionResponse response = service.saveActiveSessionIfAbsent(
			USER_ID,
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));

		assertThat(response.sessionId()).isEqualTo(sessionId);
		verify(userRepository, never()).getReferenceById(any());
		verify(routeSessionRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("완료된 session은 activeRouteKey를 비워 같은 route를 다시 ACTIVE로 선택할 수 있게 한다")
	void completeClearsActiveRouteKey() {
		RouteSession session = RouteSession.create(
			user(USER_ID),
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));

		session.complete();

		assertThat(session.getStatus()).isEqualTo(RouteSessionStatus.COMPLETED);
		assertThat(session.getActiveRouteKey()).isNull();
	}

	@Test
	@DisplayName("legacy ACTIVE session의 activeRouteKey가 비어 있으면 select 경계에서 보정한다")
	void saveActiveSessionBackfillsLegacyActiveRouteKey() {
		RouteSession legacyActiveSession = RouteSession.create(
			user(USER_ID),
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));
		ReflectionTestUtils.setField(legacyActiveSession, "activeRouteKey", null);
		when(routeSessionRepository.findAllByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(List.of(legacyActiveSession));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.of(legacyActiveSession));

		service.saveActiveSessionIfAbsent(
			USER_ID,
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));

		assertThat(legacyActiveSession.getActiveRouteKey()).isEqualTo("rt_selected_001");
		verify(routeSessionRepository).saveAndFlush(legacyActiveSession);
		verify(userRepository, never()).getReferenceById(any());
	}

	@Test
	@DisplayName("중복 ACTIVE session이 있으면 최신만 유지하고 나머지는 COMPLETED로 보정한다")
	void saveActiveSessionCompletesDuplicateActiveSessions() {
		RouteSession latestSession = RouteSession.create(
			user(USER_ID),
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));
		RouteSession duplicateSession = RouteSession.create(
			user(USER_ID),
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));
		ReflectionTestUtils.setField(latestSession, "activeRouteKey", null);
		ReflectionTestUtils.setField(duplicateSession, "activeRouteKey", null);
		when(routeSessionRepository.findAllByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(List.of(latestSession, duplicateSession));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.of(latestSession));

		service.saveActiveSessionIfAbsent(
			USER_ID,
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));

		assertThat(latestSession.getStatus()).isEqualTo(RouteSessionStatus.ACTIVE);
		assertThat(latestSession.getActiveRouteKey()).isEqualTo("rt_selected_001");
		assertThat(duplicateSession.getStatus()).isEqualTo(RouteSessionStatus.COMPLETED);
		assertThat(duplicateSession.getActiveRouteKey()).isNull();
		verify(routeSessionRepository).saveAllAndFlush(List.of(duplicateSession));
		verify(routeSessionRepository).saveAndFlush(latestSession);
		verify(userRepository, never()).getReferenceById(any());
	}

	@Test
	@DisplayName("ACTIVE session 존재 여부는 status=ACTIVE 조건으로 조회한다")
	void hasActiveSessionUsesActiveStatus() {
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(USER_ID), eq("rt_selected_001"), eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(mock(RouteSession.class)));

		assertThat(service.hasActiveSession(USER_ID, "rt_selected_001")).isTrue();
	}

	@Test
	@DisplayName("ACTIVE route session을 COMPLETED로 전환한다")
	void endSessionCompletesActiveSession() {
		RouteSession session = RouteSession.create(
			user(USER_ID),
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "rt_selected_001"))
			.thenReturn(Optional.of(session));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.of(session));

		RouteSessionResponse response = service.endSession(USER_ID, "rt_selected_001");

		assertThat(response.sessionId()).isEqualTo(session.getSessionId());
		assertThat(session.getStatus()).isEqualTo(RouteSessionStatus.COMPLETED);
		assertThat(session.getActiveRouteKey()).isNull();
	}

	@Test
	@DisplayName("이미 COMPLETED인 route session 종료는 중복 성공으로 처리한다")
	void endSessionTreatsCompletedSessionAsSuccess() {
		RouteSession session = RouteSession.create(
			user(USER_ID),
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			snapshot("rt_selected_001"));
		session.complete();
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "rt_selected_001"))
			.thenReturn(Optional.of(session));

		RouteSessionResponse response = service.endSession(USER_ID, "rt_selected_001");

		assertThat(response.sessionId()).isEqualTo(session.getSessionId());
		assertThat(session.getStatus()).isEqualTo(RouteSessionStatus.COMPLETED);
	}

	@Test
	@DisplayName("다른 사용자의 route session은 A4030으로 차단한다")
	void endSessionRejectsOtherUserRoute() {
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "other_route"))
			.thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc("other_route"))
			.thenReturn(Optional.of(mock(RouteSession.class)));

		assertThatThrownBy(() -> service.endSession(USER_ID, "other_route"))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_ACCESS_DENIED);
	}

	@Test
	@DisplayName("route session이 없으면 RT4043을 반환한다")
	void endSessionRejectsMissingRouteSession() {
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "missing_route"))
			.thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc("missing_route"))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.endSession(USER_ID, "missing_route"))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("route session response exposes only session id")
	void routeSessionResponseExposesOnlySessionId() {
		assertThat(Arrays.stream(RouteSessionResponse.class.getRecordComponents())
			.map(component -> component.getName())
			.toList())
			.containsExactly("sessionId");
	}

	@Test
	@DisplayName("route session response does not expose snapshot distance and duration as remaining metrics")
	void saveActiveSessionResponseDoesNotExposeSnapshotMetrics() {
		User user = user(USER_ID);
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.empty());
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
		when(routeSessionRepository.saveAndFlush(any(RouteSession.class))).thenAnswer(invocation -> {
			RouteSession session = invocation.getArgument(0);
			ReflectionTestUtils.setField(session, "sessionId", sessionId);
			return session;
		});

		RouteSessionResponse response = service.saveActiveSessionIfAbsent(
			USER_ID,
			"rt_selected_001",
			point(128.936, 35.12),
			point(128.956, 35.14),
			objectMapper.createObjectNode()
				.put("routeId", "rt_selected_001")
				.put("distanceMeter", 950)
				.put("durationSecond", 960));

		assertThat(response.sessionId()).isEqualTo(sessionId);
	}

	private Point point(double lng, double lat) {
		Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
		point.setSRID(4326);
		return point;
	}

	private JsonNode snapshot(String routeId) {
		return objectMapper.createObjectNode().put("routeId", routeId);
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
