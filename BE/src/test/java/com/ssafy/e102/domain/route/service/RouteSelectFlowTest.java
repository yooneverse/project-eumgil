package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.dto.request.SelectRouteRequest;
import com.ssafy.e102.domain.route.dto.request.RouteRatingRequest;
import com.ssafy.e102.domain.route.dto.request.WalkRouteSearchRequest;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.entity.RouteRating;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.repository.RouteRatingRepository;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.external.graphhopper.GraphHopperCoordinate;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class RouteSelectFlowTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final Map<String, String> redis = new HashMap<>();
	private RouteSearchCacheService routeSearchCacheService;
	private RouteSessionRepository routeSessionRepository;
	private RouteRatingRepository routeRatingRepository;
	private UserRepository userRepository;
	private RouteSessionCommandService routeSessionCommandService;
	private RouteSelectService routeSelectService;
	private RouteRatingService routeRatingService;

	@BeforeEach
	void setUp() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		doAnswer(invocation -> {
			redis.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
		when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));

		ObjectMapper objectMapper = new ObjectMapper();
		routeSearchCacheService = new RouteSearchCacheService(redisTemplate, objectMapper);
		routeSessionRepository = mock(RouteSessionRepository.class);
		routeRatingRepository = mock(RouteRatingRepository.class);
		userRepository = mock(UserRepository.class);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			any(), anyString(), any())).thenReturn(Optional.empty());
		when(routeSessionRepository.saveAndFlush(any(RouteSession.class))).thenAnswer(invocation -> {
			RouteSession session = invocation.getArgument(0);
			ReflectionTestUtils.setField(session, "sessionId", UUID.randomUUID());
			return session;
		});
		routeSessionCommandService = new RouteSessionCommandService(routeSessionRepository, userRepository);
		routeSelectService = new RouteSelectService(routeSearchCacheService,
			routeSessionCommandService, objectMapper);
		routeRatingService = new RouteRatingService(routeRatingRepository, routeSessionRepository, userRepository);
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
	}

	@Test
	@DisplayName("walk search 응답 이후 FE select 요청은 ACTIVE route session을 저장한다")
	void walkSearchThenSelectStoresRouteSession() {
		WalkRouteUserProfileQueryService profileQueryService = mock(WalkRouteUserProfileQueryService.class);
		WalkRouteGraphHopperSearchService graphHopperSearchService = mock(WalkRouteGraphHopperSearchService.class);
		WalkRouteSearchService walkRouteSearchService = new WalkRouteSearchService(
			profileQueryService,
			graphHopperSearchService,
			new WalkRoutePayloadService(new RouteTurnInstructionService()),
			routeSearchCacheService);
		GeoPointRequest startPoint = new GeoPointRequest(35.12, 128.936);
		GeoPointRequest endPoint = new GeoPointRequest(35.1315, 128.8823);
		when(profileQueryService.getProfile(USER_ID))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(graphHopperSearchService.searchCandidates(startPoint, endPoint, PrimaryUserType.LOW_VISION, null))
			.thenReturn(List.of(walkCandidate()));

		WalkRouteSearchResponse searchResponse = walkRouteSearchService.search(
			USER_ID,
			new WalkRouteSearchRequest(startPoint, endPoint));
		String routeId = searchResponse.routes().get(0).routeId();

		routeSelectService.select(USER_ID, routeId, new SelectRouteRequest(searchResponse.searchId()));

		ArgumentCaptor<RouteSession> sessionCaptor = ArgumentCaptor.forClass(RouteSession.class);
		verify(routeSessionRepository).saveAndFlush(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getRouteId()).isEqualTo(routeId);
		assertThat(sessionCaptor.getValue().getRouteSnapshotJson().get("routeId").asText()).isEqualTo(routeId);
	}

	@Test
	@DisplayName("transit search cache 이후 FE select 요청은 backend metadata까지 route session에 저장한다")
	void transitSearchCacheThenSelectStoresRouteSessionWithMetadata() {
		RouteSummaryResponse route = transitRoute("rs_transit_test_recommended");
		WalkRouteSearchResponse searchResponse = new WalkRouteSearchResponse("rs_transit_test", List.of(route));
		routeSearchCacheService.save(USER_ID, searchResponse);
		routeSearchCacheService.saveTransitMetadata("rs_transit_test", List.of(new TransitRouteSnapshot(
			route.routeId(),
			"map-object",
			List.of(Map.of("type", "BUS", "lanes", List.of(Map.of("busLocalBlID", "BL1")))))));

		routeSelectService.select(USER_ID, route.routeId(), new SelectRouteRequest(searchResponse.searchId()));

		ArgumentCaptor<RouteSession> sessionCaptor = ArgumentCaptor.forClass(RouteSession.class);
		verify(routeSessionRepository).saveAndFlush(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getRouteSnapshotJson().get("backendMetadata").get("mapObj").asText())
			.isEqualTo("map-object");
	}

	@Test
	@DisplayName("FE select 이후 end와 rating은 선택한 route session snapshot을 기준으로 처리된다")
	void selectEndThenRatingUsesSelectedRouteSessionSnapshot() {
		RouteSummaryResponse route = transitRoute("rs_transit_flow_recommended");
		WalkRouteSearchResponse searchResponse = new WalkRouteSearchResponse("rs_transit_flow", List.of(route));
		routeSearchCacheService.save(USER_ID, searchResponse);
		when(routeRatingRepository.saveAndFlush(any(RouteRating.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		routeSelectService.select(USER_ID, route.routeId(), new SelectRouteRequest(searchResponse.searchId()));
		ArgumentCaptor<RouteSession> sessionCaptor = ArgumentCaptor.forClass(RouteSession.class);
		verify(routeSessionRepository).saveAndFlush(sessionCaptor.capture());
		RouteSession selectedSession = sessionCaptor.getValue();
		when(routeSessionRepository.findById(selectedSession.getSessionId())).thenReturn(Optional.of(selectedSession));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			USER_ID, route.routeId(), RouteSessionStatus.ACTIVE))
			.thenReturn(Optional.of(selectedSession));
		when(routeRatingRepository.findByRouteSession_SessionId(selectedSession.getSessionId()))
			.thenReturn(Optional.empty());

		routeSessionCommandService.endSession(USER_ID, route.routeId());
		routeRatingService.rate(USER_ID, new RouteRatingRequest(selectedSession.getSessionId(), 5));

		ArgumentCaptor<RouteRating> ratingCaptor = ArgumentCaptor.forClass(RouteRating.class);
		verify(routeRatingRepository).saveAndFlush(ratingCaptor.capture());
		assertThat(selectedSession.getStatus()).isEqualTo(RouteSessionStatus.COMPLETED);
		assertThat(ratingCaptor.getValue().getScore()).isEqualTo((short)5);
		assertThat(ratingCaptor.getValue().getRouteContextJson().get("routeId").asText()).isEqualTo(route.routeId());
		assertThat(ratingCaptor.getValue().getRouteContextJson().has("remainingMinute")).isFalse();
	}

	private WalkRouteCandidate walkCandidate() {
		return new WalkRouteCandidate(
			RouteOption.SAFE,
			WalkRouteProfile.VISUAL_SAFE,
			new GraphHopperRoutePath(
				new BigDecimal("120.50"),
				90_000,
				List.of(
					new GraphHopperCoordinate(new BigDecimal("128.936"), new BigDecimal("35.12")),
					new GraphHopperCoordinate(new BigDecimal("128.8823"), new BigDecimal("35.1315"))),
				Map.of()));
	}

	private RouteSummaryResponse transitRoute(String routeId) {
		return new RouteSummaryResponse(
			routeId,
			TransportMode.PUBLIC_TRANSIT,
			RouteOption.RECOMMENDED,
			List.of(RouteOption.RECOMMENDED),
			"강서구13 경로",
			BigDecimal.valueOf(2500),
			900,
			15,
			List.of(RouteBadge.CROSSWALK),
			"LINESTRING(128.936 35.12, 128.956 35.14)",
			List.of(new RouteLegResponse(
				1,
				TransportMode.BUS,
				RouteLegRole.TRANSIT,
				"강서구13번 버스에 탑승하세요.",
				BigDecimal.valueOf(2500),
				900,
				15,
				"LINESTRING(128.936 35.12, 128.956 35.14)",
				List.of())));
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
