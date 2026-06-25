package com.ssafy.e102.domain.bookmark.entity;

import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.bookmark.exception.FavoriteRouteErrorCode;
import com.ssafy.e102.domain.bookmark.exception.FavoriteRouteException;
import com.ssafy.e102.domain.bookmark.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "favorite_routes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteRoute extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "fav_route_id", nullable = false, updatable = false)
	private Long favRouteId;

	@Column(name = "route_name", nullable = false, length = 511)
	private String routeName;

	@Column(name = "start_label", nullable = false, length = 255)
	private String startLabel;

	@Column(name = "end_label", nullable = false, length = 255)
	private String endLabel;

	@Column(name = "start_point", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point startPoint;

	@Column(name = "end_point", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point endPoint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TransportMode transportMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "route_option", nullable = false, length = 30)
	private RouteOption routeOption;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode routeSnapshotJson;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	public static FavoriteRoute create(
		User user,
		String startLabel,
		String endLabel,
		Point startPoint,
		Point endPoint,
		TransportMode transportMode,
		RouteOption routeOption,
		JsonNode routeSnapshotJson) {
		FavoriteRoute favoriteRoute = new FavoriteRoute();
		favoriteRoute.user = requireUser(user);
		favoriteRoute.changeRouteSnapshot(
			startLabel,
			endLabel,
			startPoint,
			endPoint,
			transportMode,
			routeOption,
			routeSnapshotJson,
			FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_REQUEST);
		return favoriteRoute;
	}

	public void changeLabels(
		String startLabel,
		String endLabel) {
		String normalizedStartLabel = normalizeLabel(
			valueOrDefault(startLabel, this.startLabel),
			"출발지명",
			FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_UPDATE_REQUEST);
		String normalizedEndLabel = normalizeLabel(
			valueOrDefault(endLabel, this.endLabel),
			"도착지명",
			FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_UPDATE_REQUEST);
		this.startLabel = normalizedStartLabel;
		this.endLabel = normalizedEndLabel;
		this.routeName = generateRouteName(normalizedStartLabel, normalizedEndLabel);
	}

	private void changeRouteSnapshot(
		String startLabel,
		String endLabel,
		Point startPoint,
		Point endPoint,
		TransportMode transportMode,
		RouteOption routeOption,
		JsonNode routeSnapshotJson,
		FavoriteRouteErrorCode errorCode) {
		String normalizedStartLabel = normalizeLabel(startLabel, "출발지명", errorCode);
		String normalizedEndLabel = normalizeLabel(endLabel, "도착지명", errorCode);
		this.startLabel = normalizedStartLabel;
		this.endLabel = normalizedEndLabel;
		this.startPoint = requirePoint(startPoint, "출발지 좌표", errorCode);
		this.endPoint = requirePoint(endPoint, "도착지 좌표", errorCode);
		this.transportMode = requireTransportMode(transportMode, errorCode);
		this.routeOption = requireRouteOption(routeOption, errorCode);
		this.routeSnapshotJson = requireRouteSnapshot(routeSnapshotJson, errorCode);
		this.routeName = generateRouteName(normalizedStartLabel, normalizedEndLabel);
	}

	public boolean isOwner(UUID userId) {
		return user != null && Objects.equals(user.getUserId(), userId);
	}

	private static User requireUser(User user) {
		if (user == null) {
			throw invalidRequest("사용자는 필수입니다.");
		}
		return user;
	}

	private static Point requirePoint(Point point, String fieldName, FavoriteRouteErrorCode errorCode) {
		if (point == null) {
			throw invalidRequest(errorCode, fieldName + "는 필수입니다.");
		}
		return point;
	}

	private static RouteOption requireRouteOption(RouteOption routeOption, FavoriteRouteErrorCode errorCode) {
		if (routeOption == null) {
			throw invalidRequest(errorCode, "경로 옵션은 필수입니다.");
		}
		return routeOption;
	}

	private static TransportMode requireTransportMode(TransportMode transportMode, FavoriteRouteErrorCode errorCode) {
		if (transportMode == null) {
			throw invalidRequest(errorCode, "이동 수단은 필수입니다.");
		}
		return transportMode;
	}

	private static JsonNode requireRouteSnapshot(JsonNode routeSnapshotJson, FavoriteRouteErrorCode errorCode) {
		if (routeSnapshotJson == null || routeSnapshotJson.isNull()) {
			throw invalidRequest(errorCode, "경로 스냅샷은 필수입니다.");
		}
		return routeSnapshotJson.deepCopy();
	}

	private static String normalizeLabel(String label, String fieldName, FavoriteRouteErrorCode errorCode) {
		if (label == null || label.isBlank()) {
			throw invalidRequest(errorCode, fieldName + "은 필수입니다.");
		}
		return label.trim();
	}

	private static String generateRouteName(String startLabel, String endLabel) {
		return startLabel + "-" + endLabel;
	}

	private static <T> T valueOrDefault(T value, T defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

	private static FavoriteRouteException invalidRequest(String message) {
		return invalidRequest(FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_REQUEST, message);
	}

	private static FavoriteRouteException invalidRequest(FavoriteRouteErrorCode errorCode, String message) {
		return new FavoriteRouteException(errorCode, message);
	}
}
