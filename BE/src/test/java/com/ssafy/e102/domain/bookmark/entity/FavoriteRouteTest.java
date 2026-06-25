package com.ssafy.e102.domain.bookmark.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.bookmark.exception.FavoriteRouteErrorCode;
import com.ssafy.e102.domain.bookmark.exception.FavoriteRouteException;
import com.ssafy.e102.domain.bookmark.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class FavoriteRouteTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final GeoPointConverter geoPointConverter = new GeoPointConverter();

	@Test
	@DisplayName("경로 북마크는 출발지와 도착지 기준으로 경로명을 자동 생성하고 route snapshot을 저장한다")
	void createFavoriteRoute() {
		User user = user(UUID.randomUUID());
		Point startPoint = point(35.1686, 129.0576);
		Point endPoint = point(35.1152, 129.0422);
		JsonNode routeSnapshot = routeSnapshot();

		FavoriteRoute favoriteRoute = FavoriteRoute.create(
			user,
			" 부산시민공원 ",
			" 부산역 ",
			startPoint,
			endPoint,
			TransportMode.WALK,
			RouteOption.SAFE,
			routeSnapshot);

		assertThat(favoriteRoute.getUser()).isEqualTo(user);
		assertThat(favoriteRoute.getStartLabel()).isEqualTo("부산시민공원");
		assertThat(favoriteRoute.getEndLabel()).isEqualTo("부산역");
		assertThat(favoriteRoute.getRouteName()).isEqualTo("부산시민공원-부산역");
		assertThat(favoriteRoute.getStartPoint()).isEqualTo(startPoint);
		assertThat(favoriteRoute.getEndPoint()).isEqualTo(endPoint);
		assertThat(favoriteRoute.getTransportMode()).isEqualTo(TransportMode.WALK);
		assertThat(favoriteRoute.getRouteOption()).isEqualTo(RouteOption.SAFE);
		assertThat(favoriteRoute.getRouteSnapshotJson()).isEqualTo(routeSnapshot);
	}

	@Test
	@DisplayName("경로 북마크 표시명 수정은 경로명을 다시 계산하고 snapshot은 유지한다")
	void changeLabels() {
		FavoriteRoute favoriteRoute = FavoriteRoute.create(
			user(UUID.randomUUID()),
			"부산시민공원",
			"부산역",
			point(35.1686, 129.0576),
			point(35.1152, 129.0422),
			TransportMode.WALK,
			RouteOption.SAFE,
			routeSnapshot());

		favoriteRoute.changeLabels("서면역", "광안리");

		assertThat(favoriteRoute.getRouteName()).isEqualTo("서면역-광안리");
		assertThat(favoriteRoute.getRouteOption()).isEqualTo(RouteOption.SAFE);
		assertThat(favoriteRoute.getRouteSnapshotJson().get("routeId").asText()).isEqualTo("walk_rt_safe_001");
	}

	@Test
	@DisplayName("경로명은 출발지명과 도착지명의 최대 길이를 합친 값까지 생성한다")
	void createRouteNameWithMaxLengthLabels() {
		String startLabel = "s".repeat(255);
		String endLabel = "e".repeat(255);

		FavoriteRoute favoriteRoute = FavoriteRoute.create(
			user(UUID.randomUUID()),
			startLabel,
			endLabel,
			point(35.1686, 129.0576),
			point(35.1152, 129.0422),
			TransportMode.WALK,
			RouteOption.SAFE,
			routeSnapshot());

		assertThat(favoriteRoute.getRouteName()).isEqualTo(startLabel + "-" + endLabel);
		assertThat(favoriteRoute.getRouteName()).hasSize(511);
	}

	@Test
	@DisplayName("경로 북마크 생성 시 필수값이 없으면 생성 요청 오류로 거부한다")
	void rejectInvalidCreateRequest() {
		assertThatThrownBy(() -> FavoriteRoute.create(
			user(UUID.randomUUID()),
			" ",
			"부산역",
			point(35.1686, 129.0576),
			point(35.1152, 129.0422),
			TransportMode.WALK,
			RouteOption.SAFE,
			routeSnapshot()))
			.isInstanceOf(FavoriteRouteException.class)
			.extracting("errorCode")
			.isEqualTo(FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_REQUEST);
	}

	@Test
	@DisplayName("경로 북마크 수정 시 필수값이 없으면 수정 요청 오류로 거부한다")
	void rejectInvalidUpdateRequest() {
		FavoriteRoute favoriteRoute = FavoriteRoute.create(
			user(UUID.randomUUID()),
			"부산시민공원",
			"부산역",
			point(35.1686, 129.0576),
			point(35.1152, 129.0422),
			TransportMode.WALK,
			RouteOption.SAFE,
			routeSnapshot());

		assertThatThrownBy(() -> favoriteRoute.changeLabels("", "부산역"))
			.isInstanceOf(FavoriteRouteException.class)
			.extracting("errorCode")
			.isEqualTo(FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_UPDATE_REQUEST);
	}

	@Test
	@DisplayName("경로 북마크 소유자를 판별한다")
	void isOwner() {
		UUID userId = UUID.randomUUID();
		FavoriteRoute favoriteRoute = FavoriteRoute.create(
			user(userId),
			"부산시민공원",
			"부산역",
			point(35.1686, 129.0576),
			point(35.1152, 129.0422),
			TransportMode.WALK,
			RouteOption.SAFE,
			routeSnapshot());

		assertThat(favoriteRoute.isOwner(userId)).isTrue();
		assertThat(favoriteRoute.isOwner(UUID.randomUUID())).isFalse();
	}

	private Point point(double lat, double lng) {
		return geoPointConverter.toPoint(new GeoPointRequest(lat, lng));
	}

	private JsonNode routeSnapshot() {
		return OBJECT_MAPPER.createObjectNode()
			.put("routeId", "walk_rt_safe_001")
			.put("transportMode", "WALK")
			.put("routeOption", "SAFE");
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
