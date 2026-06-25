package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.dto.request.TransitRefreshRequest;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.TransitArrivalStatus;
import com.ssafy.e102.domain.route.dto.response.TransitRefreshResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.entity.SubwayTimetable;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.repository.SubwayTimetableRepository;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.route.type.SubwayServiceDayType;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.external.bims.BusanBimsArrival;
import com.ssafy.e102.global.external.bims.BusanBimsClient;

class TransitRefreshServiceTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final Clock MONDAY_NOON_CLOCK = Clock.fixed(Instant.parse("2026-05-04T03:00:00Z"), SEOUL_ZONE_ID);
	private static final Clock SUNDAY_NIGHT_CLOCK = Clock.fixed(Instant.parse("2026-05-10T14:59:00Z"), SEOUL_ZONE_ID);

	private RouteSessionRepository routeSessionRepository;
	private ObjectMapper objectMapper;
	private BimsArrivalCacheService bimsArrivalCacheService;
	private BusanBimsClient busanBimsClient;
	private SubwayTimetableRepository subwayTimetableRepository;
	private TransitRefreshService service;

	@BeforeEach
	void setUp() {
		routeSessionRepository = mock(RouteSessionRepository.class);
		objectMapper = new ObjectMapper();
		bimsArrivalCacheService = mock(BimsArrivalCacheService.class);
		busanBimsClient = mock(BusanBimsClient.class);
		subwayTimetableRepository = mock(SubwayTimetableRepository.class);
		service = new TransitRefreshService(routeSessionRepository, objectMapper, bimsArrivalCacheService,
			busanBimsClient, subwayTimetableRepository, immediateTransaction(), MONDAY_NOON_CLOCK);
	}

	@Test
	@DisplayName("선택된 BUS leg는 route session snapshot에서 복구해 refresh 대상이 된다")
	void refreshRestoresBusLegFromRouteSessionSnapshot() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.empty());
		when(busanBimsClient.findArrival("507700000", "5200177000", "100"))
			.thenReturn(new BusanBimsArrival("507700000", "5200177000", "100", 3, true));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.type()).isEqualTo(TransportMode.BUS);
		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.REALTIME_AVAILABLE);
		assertThat(response.transits()).hasSize(1);
		assertThat(response.transits().get(0).remainingMinute()).isEqualTo(3);
		verify(bimsArrivalCacheService).save(new BusanBimsArrival("507700000", "5200177000", "100", 3, true));
		verifyNoInteractions(subwayTimetableRepository);
	}

	@Test
	@DisplayName("BUS 도착정보가 Redis cache에 있으면 BIMS를 호출하지 않는다")
	void refreshBusUsesCachedArrival() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.of(new BusanBimsArrival("507700000", "5200177000", "100", 2, false)));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.REALTIME_AVAILABLE);
		assertThat(response.transits().get(0).remainingMinute()).isEqualTo(2);
		verify(busanBimsClient, never()).findArrival("507700000", "5200177000", "100");
	}

	@Test
	@DisplayName("BUS Redis cache 조회가 실패하면 BIMS 직접 호출로 우회한다")
	void refreshBusFallsBackToBimsWhenCacheReadFails() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenThrow(new IllegalStateException("redis unavailable"));
		when(busanBimsClient.findArrival("507700000", "5200177000", "100"))
			.thenReturn(new BusanBimsArrival("507700000", "5200177000", "100", 4, true));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.REALTIME_AVAILABLE);
		assertThat(response.transits().get(0).remainingMinute()).isEqualTo(4);
		verify(busanBimsClient).findArrival("507700000", "5200177000", "100");
	}

	@Test
	@DisplayName("BUS Redis cache 저장이 실패해도 BIMS 응답은 성공으로 반환한다")
	void refreshBusIgnoresCacheSaveFailure() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		BusanBimsArrival arrival = new BusanBimsArrival("507700000", "5200177000", "100", 5, true);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.empty());
		when(busanBimsClient.findArrival("507700000", "5200177000", "100"))
			.thenReturn(arrival);
		doThrow(new IllegalStateException("redis unavailable"))
			.when(bimsArrivalCacheService).save(arrival);

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.REALTIME_AVAILABLE);
		assertThat(response.transits().get(0).remainingMinute()).isEqualTo(5);
	}

	@Test
	@DisplayName("BUS 정류장/노선은 확인됐지만 도착 차량이 없으면 NO_CURRENT_ARRIVAL을 반환한다")
	void refreshBusReturnsNoCurrentArrivalWhenBimsHasNoArrival() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.empty());
		when(busanBimsClient.findArrival("507700000", "5200177000", "100"))
			.thenReturn(new BusanBimsArrival("507700000", "5200177000", "100", null, null));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.NO_CURRENT_ARRIVAL);
		assertThat(response.transits()).isEmpty();
	}

	@Test
	@DisplayName("BIMS timeout은 EX5040으로 전파한다")
	void refreshBusPropagatesBimsTimeout() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.empty());
		when(busanBimsClient.findArrival("507700000", "5200177000", "100"))
			.thenThrow(new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT));

		assertThatThrownBy(() -> service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT);
	}

	@Test
	@DisplayName("BIMS 오류는 EX5020으로 전파한다")
	void refreshBusPropagatesBimsFailure() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.empty());
		when(busanBimsClient.findArrival("507700000", "5200177000", "100"))
			.thenThrow(new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED));

		assertThatThrownBy(() -> service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED);
	}

	@Test
	@DisplayName("다른 사용자의 route session이면 A4030을 반환한다")
	void refreshRejectsRouteSessionOwnedByOtherUser() {
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "other_route", RouteSessionStatus.ACTIVE)).thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "other_route"))
			.thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc("other_route"))
			.thenReturn(Optional.of(mock(RouteSession.class)));

		assertThatThrownBy(() -> service.refresh(USER_ID, "other_route", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.ROUTE_ACCESS_DENIED);
	}

	@Test
	@DisplayName("route session이 없으면 RT4043을 반환한다")
	void refreshRejectsMissingRouteSession() {
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "missing_route", RouteSessionStatus.ACTIVE)).thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "missing_route"))
			.thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc("missing_route"))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.refresh(USER_ID, "missing_route", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("COMPLETED route session에는 transit-refresh를 허용하지 않는다")
	void refreshRejectsCompletedRouteSession() {
		RouteSession completedSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, "rt_selected_001", RouteSessionStatus.ACTIVE)).thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(USER_ID, "rt_selected_001"))
			.thenReturn(Optional.of(completedSession));

		assertThatThrownBy(() -> service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("없는 legSequence는 snapshot 복구 실패로 RT4043을 반환한다")
	void refreshRejectsMissingLegSequence() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), null);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));

		assertThatThrownBy(() -> service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(99)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("WALK leg에 transit-refresh를 요청하면 PT4090을 반환한다")
	void refreshRejectsWalkLeg() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.WALK), null);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));

		assertThatThrownBy(() -> service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.NOT_TRANSIT_LEG);
	}

	@Test
	@DisplayName("BUS/SUBWAY 외 leg에 transit-refresh를 요청하면 PT4090을 반환한다")
	void refreshRejectsNonBusOrSubwayLeg() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.PUBLIC_TRANSIT), null);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));

		assertThatThrownBy(() -> service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.NOT_TRANSIT_LEG);
	}

	@Test
	@DisplayName("route session snapshot에 backendMetadata가 있어도 FE route payload를 복구한다")
	void refreshIgnoresBackendMetadataWhenRestoringRoutePayload() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS),
			objectMapper.createObjectNode().put("mapObj", "map-object"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.type()).isEqualTo(TransportMode.BUS);
	}

	@Test
	@DisplayName("BUS backend metadata를 해석할 수 없으면 ARRIVAL_UNKNOWN을 반환한다")
	void refreshBusReturnsUnknownWhenMetadataIsMissing() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), null);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.ARRIVAL_UNKNOWN);
		assertThat(response.transits()).isEmpty();
	}

	@Test
	@DisplayName("refresh는 route snapshot과 geometry를 변경하지 않는다")
	void refreshDoesNotMutateRouteSnapshot() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.BUS), busMetadata());
		JsonNode before = routeSession.getRouteSnapshotJson().deepCopy();
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(bimsArrivalCacheService.find("507700000", "5200177000"))
			.thenReturn(Optional.of(new BusanBimsArrival("507700000", "5200177000", "100", 2, false)));

		service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(routeSession.getRouteSnapshotJson()).isEqualTo(before);
	}

	@Test
	@DisplayName("SUBWAY leg는 시간표 기반으로 다음 출발 정보를 반환한다")
	void refreshSubwayUsesTimetable() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.SUBWAY), subwayMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(subwayTimetableRepository.findNextDepartures(
			eq("301"),
			any(SubwayServiceDayType.class),
			eq(1),
			anyInt(),
			any(Pageable.class)))
			.thenReturn(List.of(subwayTimetable(600)));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.type()).isEqualTo(TransportMode.SUBWAY);
		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.SCHEDULE_BASED);
		assertThat(response.transits()).hasSize(1);
		assertThat(response.transits().get(0).routeNo()).isEqualTo("1호선");
		assertThat(response.transits().get(0).remainingMinute()).isPositive();
		verify(subwayTimetableRepository).findNextDepartures(
			eq("301"),
			eq(SubwayServiceDayType.WEEKDAY),
			eq(1),
			anyInt(),
			any(Pageable.class));
		verifyNoInteractions(bimsArrivalCacheService, busanBimsClient);
	}

	@Test
	@DisplayName("SUBWAY 시간표가 없으면 ARRIVAL_UNKNOWN을 반환한다")
	void refreshSubwayReturnsUnknownWhenTimetableIsMissing() {
		RouteSession routeSession = routeSession(routeSummary(TransportMode.SUBWAY), subwayMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(subwayTimetableRepository.findNextDepartures(
			eq("301"),
			any(SubwayServiceDayType.class),
			eq(1),
			anyInt(),
			any(Pageable.class)))
			.thenReturn(List.of());
		when(subwayTimetableRepository.findFirstDepartures(
			eq("301"),
			any(SubwayServiceDayType.class),
			eq(1),
			any(Pageable.class)))
			.thenReturn(List.of());

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.ARRIVAL_UNKNOWN);
		assertThat(response.transits()).isEmpty();
	}

	@Test
	@DisplayName("SUBWAY 오늘 남은 출발이 없으면 다음 날짜 serviceDayType으로 첫차를 조회한다")
	void refreshSubwayUsesNextDayServiceDayTypeWhenTodayDepartureIsMissing() {
		service = new TransitRefreshService(routeSessionRepository, objectMapper, bimsArrivalCacheService,
			busanBimsClient, subwayTimetableRepository, immediateTransaction(), SUNDAY_NIGHT_CLOCK);
		RouteSession routeSession = routeSession(routeSummary(TransportMode.SUBWAY), subwayMetadata());
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(USER_ID,
			"rt_selected_001", RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(routeSession));
		when(subwayTimetableRepository.findNextDepartures(
			eq("301"),
			eq(SubwayServiceDayType.HOLIDAY),
			eq(1),
			anyInt(),
			any(Pageable.class)))
			.thenReturn(List.of());
		when(subwayTimetableRepository.findFirstDepartures(
			eq("301"),
			eq(SubwayServiceDayType.WEEKDAY),
			eq(1),
			any(Pageable.class)))
			.thenReturn(List.of(SubwayTimetable.create(
				"301",
				SubwayServiceDayType.WEEKDAY,
				1,
				"05:30",
				5 * 60 * 60 + 30 * 60,
				"다대포해수욕장")));

		TransitRefreshResponse response = service.refresh(USER_ID, "rt_selected_001", new TransitRefreshRequest(2));

		assertThat(response.arrivalStatus()).isEqualTo(TransitArrivalStatus.SCHEDULE_BASED);
		assertThat(response.transits().get(0).remainingMinute()).isEqualTo(331);
		verify(subwayTimetableRepository).findNextDepartures(
			eq("301"),
			eq(SubwayServiceDayType.HOLIDAY),
			eq(1),
			anyInt(),
			any(Pageable.class));
		verify(subwayTimetableRepository).findFirstDepartures(
			eq("301"),
			eq(SubwayServiceDayType.WEEKDAY),
			eq(1),
			any(Pageable.class));
		verifyNoInteractions(bimsArrivalCacheService, busanBimsClient);
	}

	private RouteSession routeSession(RouteSummaryResponse route, JsonNode backendMetadata) {
		JsonNode snapshot = objectMapper.valueToTree(route);
		if (backendMetadata != null && snapshot instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
			objectNode.set("backendMetadata", backendMetadata);
		}
		RouteSession routeSession = mock(RouteSession.class);
		when(routeSession.getRouteSnapshotJson()).thenReturn(snapshot);
		return routeSession;
	}

	private TransactionOperations immediateTransaction() {
		return new TransactionOperations() {
			@Override
			public <T> T execute(TransactionCallback<T> action) {
				return action.doInTransaction(new SimpleTransactionStatus());
			}
		};
	}

	private RouteSummaryResponse routeSummary(TransportMode targetLegType) {
		return new RouteSummaryResponse(
			"rt_selected_001",
			TransportMode.PUBLIC_TRANSIT,
			RouteOption.RECOMMENDED,
			List.of(RouteOption.RECOMMENDED),
			"대중교통 경로",
			BigDecimal.valueOf(1200),
			900,
			15,
			List.of(),
			"LINESTRING(128.936 35.12, 128.956 35.14)",
			List.of(
				leg(1, TransportMode.WALK, RouteLegRole.WALK_TO_TRANSIT),
				leg(2, targetLegType, targetLegType == TransportMode.WALK
					? RouteLegRole.WALK_TO_TRANSIT
					: RouteLegRole.TRANSIT)));
	}

	private RouteLegResponse leg(int sequence, TransportMode type, RouteLegRole role) {
		return new RouteLegResponse(
			sequence,
			type,
			role,
			type == TransportMode.WALK ? "정류장까지 이동하세요." : "버스에 탑승하세요.",
			BigDecimal.valueOf(500),
			300,
			5,
			"LINESTRING(128.936 35.12, 128.956 35.14)",
			List.of(),
			switch (type) {
				case BUS -> "100";
				case SUBWAY -> "1호선";
				default -> null;
			},
			List.of(),
			null,
			null,
			null,
			List.of());
	}

	private JsonNode busMetadata() {
		return objectMapper.valueToTree(Map.of(
			"mapObj", "map-object",
			"legs", List.of(Map.of(
				"type", "BUS",
				"lanes", List.of(Map.of(
					"busNo", "100",
					"busLocalBlID", "5200177000")),
				"passStops", List.of(Map.of(
					"localStationID", "507700000",
					"stationName", "부산정류장"))))));
	}

	private JsonNode subwayMetadata() {
		return objectMapper.valueToTree(Map.of(
			"mapObj", "map-object",
			"legs", List.of(Map.of(
				"type", "SUBWAY",
				"odsayStationId", "301",
				"wayCode", 1,
				"lineName", "1호선"))));
	}

	private SubwayTimetable subwayTimetable(int offsetSecond) {
		int secondOfDay = LocalDateTime.now(MONDAY_NOON_CLOCK).toLocalTime().toSecondOfDay();
		int departureSecondOfDay = (secondOfDay + offsetSecond) % (24 * 60 * 60);
		return SubwayTimetable.create(
			"301",
			SubwayServiceDayType.WEEKDAY,
			1,
			"14:30",
			departureSecondOfDay,
			"다대포해수욕장");
	}
}
