package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;

class RouteSearchCacheServiceTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private RouteSearchCacheService cacheService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		cacheService = new RouteSearchCacheService(redisTemplate, new ObjectMapper());
	}

	@Test
	void savesSearchResponseWithThirtyMinuteTtl() {
		WalkRouteSearchResponse response = response();

		cacheService.save(USER_ID, response);

		verify(valueOperations).set(
			org.mockito.ArgumentMatchers.eq("routeSearch:rs_walk_test"),
			org.mockito.ArgumentMatchers.contains("\"userId\":\"00000000-0000-0000-0000-000000000001\""),
			org.mockito.ArgumentMatchers.eq(1800L),
			org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS));
	}

	@Test
	void findsRouteFromCachedSearchResponse() {
		when(valueOperations.get("routeSearch:rs_walk_test")).thenReturn("""
			{"userId":"00000000-0000-0000-0000-000000000001","response":{"searchId":"rs_walk_test","routes":[{"routeId":"rs_walk_test_safe","transportMode":"WALK","routeOption":"SAFE","title":"안전 경로","distanceMeter":100.00,"durationSecond":60,"estimatedTimeMinute":1,"badges":[],"geometry":"LINESTRING(0 0, 1 1)","legs":[]}]}}
			""");

		Optional<RouteSummaryResponse> route = cacheService.findRoute("rs_walk_test", "rs_walk_test_safe");

		assertThat(route).isPresent();
		assertThat(route.get().routeId()).isEqualTo("rs_walk_test_safe");
	}

	@Test
	void getRouteThrowsSearchExpiredWhenSearchKeyIsMissing() {
		when(valueOperations.get("routeSearch:expired")).thenReturn(null);

		assertThatThrownBy(() -> cacheService.getRouteOrThrow("expired", "route-id"))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SEARCH_EXPIRED);
	}

	@Test
	void getRouteThrowsCandidateNotFoundWhenRouteIdIsMissing() {
		when(valueOperations.get("routeSearch:rs_walk_test")).thenReturn("""
			{"userId":"00000000-0000-0000-0000-000000000001","response":{"searchId":"rs_walk_test","routes":[]}}
			""");

		assertThatThrownBy(() -> cacheService.getRouteOrThrow("rs_walk_test", "missing-route"))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_CANDIDATE_NOT_FOUND);
	}

	@Test
	void getOwnedRouteReturnsRouteWhenSearchBelongsToUser() {
		when(valueOperations.get("routeSearch:rs_walk_test")).thenReturn("""
			{"userId":"00000000-0000-0000-0000-000000000001","response":{"searchId":"rs_walk_test","routes":[{"routeId":"rs_walk_test_safe","transportMode":"WALK","routeOption":"SAFE","title":"안전 경로","distanceMeter":100.00,"durationSecond":60,"estimatedTimeMinute":1,"badges":[],"geometry":"LINESTRING(0 0, 1 1)","legs":[]}]}}
			""");

		RouteSummaryResponse route = cacheService.getOwnedRouteOrThrow(USER_ID, "rs_walk_test", "rs_walk_test_safe");

		assertThat(route.routeId()).isEqualTo("rs_walk_test_safe");
	}

	@Test
	void getOwnedRouteThrowsAccessDeniedWhenSearchBelongsToOtherUser() {
		when(valueOperations.get("routeSearch:rs_walk_test")).thenReturn("""
			{"userId":"00000000-0000-0000-0000-000000000002","response":{"searchId":"rs_walk_test","routes":[{"routeId":"rs_walk_test_safe","transportMode":"WALK","routeOption":"SAFE","title":"안전 경로","distanceMeter":100.00,"durationSecond":60,"estimatedTimeMinute":1,"badges":[],"geometry":"LINESTRING(0 0, 1 1)","legs":[]}]}}
			""");

		assertThatThrownBy(() -> cacheService.getOwnedRouteOrThrow(USER_ID, "rs_walk_test", "rs_walk_test_safe"))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_ACCESS_DENIED);
	}

	@Test
	void getOwnedRouteThrowsSearchExpiredWhenCachedSearchHasNoOwner() {
		when(valueOperations.get("routeSearch:legacy")).thenReturn("""
			{"searchId":"legacy","routes":[{"routeId":"legacy_safe","transportMode":"WALK","routeOption":"SAFE","title":"안전 경로","distanceMeter":100.00,"durationSecond":60,"estimatedTimeMinute":1,"badges":[],"geometry":"LINESTRING(0 0, 1 1)","legs":[]}]}
			""");

		assertThatThrownBy(() -> cacheService.getOwnedRouteOrThrow(USER_ID, "legacy", "legacy_safe"))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SEARCH_EXPIRED);
	}

	@Test
	void findsTransitMetadataByRouteId() {
		when(valueOperations.get("routeSearchMeta:rs_transit_test")).thenReturn("""
			[{"routeId":"rt_a","mapObj":"map-a","legs":[{"type":"BUS"}]},{"routeId":"rt_b","mapObj":"map-b","legs":[{"type":"SUBWAY"}]}]
			""");

		Optional<TransitRouteSnapshot> snapshot = cacheService.findTransitMetadata("rs_transit_test", "rt_b");

		assertThat(snapshot).isPresent();
		assertThat(snapshot.get().mapObj()).isEqualTo("map-b");
		assertThat(snapshot.get().legs().get(0)).containsEntry("type", "SUBWAY");
	}

	@Test
	void savesTransitMetadataWithThirtyMinuteTtl() {
		List<TransitRouteSnapshot> snapshots = List.of(new TransitRouteSnapshot(
			"rt_transit",
			"map-obj",
			List.of(Map.of("type", "BUS"))));

		cacheService.saveTransitMetadata("rs_transit_test", snapshots);

		verify(valueOperations).set(
			org.mockito.ArgumentMatchers.eq("routeSearchMeta:rs_transit_test"),
			org.mockito.ArgumentMatchers.contains("\"mapObj\":\"map-obj\""),
			org.mockito.ArgumentMatchers.eq(1800L),
			org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS));
	}

	@Test
	void saveThrowsInternalFailureWhenSearchResponseCannotBeSerialized() throws Exception {
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		when(objectMapper.writeValueAsString(any()))
			.thenThrow(new JsonProcessingException("serialize failed") {});
		RouteSearchCacheService brokenCacheService = new RouteSearchCacheService(redisTemplate, objectMapper);

		assertThatThrownBy(() -> brokenCacheService.save(USER_ID, response()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("경로 검색 후보를 직렬화할 수 없습니다.");
	}

	@Test
	void findSearchThrowsInternalFailureWhenCachedResponseCannotBeDeserialized() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("routeSearch:rs_walk_test")).thenReturn("{broken");
		RouteSearchCacheService brokenCacheService = new RouteSearchCacheService(redisTemplate, new ObjectMapper());

		assertThatThrownBy(() -> brokenCacheService.findSearch("rs_walk_test"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("경로 검색 후보를 역직렬화할 수 없습니다.");
	}

	private WalkRouteSearchResponse response() {
		return new WalkRouteSearchResponse(
			"rs_walk_test",
			List.of(new RouteSummaryResponse(
				"rs_walk_test_safe",
				TransportMode.WALK,
				RouteOption.SAFE,
				"안전 경로",
				BigDecimal.valueOf(100),
				60,
				1,
				List.of(),
				"LINESTRING(0 0, 1 1)",
				List.of())));
	}
}
