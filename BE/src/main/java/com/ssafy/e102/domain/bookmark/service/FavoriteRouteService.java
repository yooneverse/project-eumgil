package com.ssafy.e102.domain.bookmark.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.bookmark.dto.request.CreateFavoriteRouteRequest;
import com.ssafy.e102.domain.bookmark.dto.request.UpdateFavoriteRouteRequest;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteDetailResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteIdResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteListResponse;
import com.ssafy.e102.domain.bookmark.entity.FavoriteRoute;
import com.ssafy.e102.domain.bookmark.exception.FavoriteRouteErrorCode;
import com.ssafy.e102.domain.bookmark.exception.FavoriteRouteException;
import com.ssafy.e102.domain.bookmark.repository.FavoriteRouteRepository;
import com.ssafy.e102.domain.bookmark.type.RouteOption;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.geo.GeoPointConverter;

@Service
@Transactional(readOnly = true)
public class FavoriteRouteService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "favRouteId");

	private final FavoriteRouteRepository favoriteRouteRepository;
	private final RouteSessionRepository routeSessionRepository;
	private final UserRepository userRepository;
	private final GeoPointConverter geoPointConverter;

	public FavoriteRouteService(
		FavoriteRouteRepository favoriteRouteRepository,
		RouteSessionRepository routeSessionRepository,
		UserRepository userRepository,
		GeoPointConverter geoPointConverter) {
		this.favoriteRouteRepository = favoriteRouteRepository;
		this.routeSessionRepository = routeSessionRepository;
		this.userRepository = userRepository;
		this.geoPointConverter = geoPointConverter;
	}

	public FavoriteRouteListResponse getFavoriteRoutes(UUID userId, Long cursor, int size) {
		PageRequest pageRequest = PageRequest.of(0, size, NEWEST_FIRST);
		Slice<FavoriteRoute> favoriteRoutes = cursor == null
			? favoriteRouteRepository.findAllByUser_UserId(userId, pageRequest)
			: favoriteRouteRepository.findAllByUser_UserIdAndFavRouteIdLessThan(userId, cursor, pageRequest);
		return FavoriteRouteListResponse.of(
			favoriteRoutes.getContent(),
			size,
			favoriteRoutes.hasNext(),
			geoPointConverter);
	}

	public FavoriteRouteDetailResponse getFavoriteRouteDetail(UUID userId, Long favRouteId) {
		FavoriteRoute favoriteRoute = getFavoriteRoute(favRouteId);
		validateOwner(favoriteRoute, userId);
		return FavoriteRouteDetailResponse.of(favoriteRoute, geoPointConverter);
	}

	@Transactional
	public FavoriteRouteIdResponse createFavoriteRoute(UUID userId, CreateFavoriteRouteRequest request) {
		User user = getUser(userId);
		RouteSession routeSession = getRouteSession(userId, request.routeId());
		JsonNode routeSnapshotJson = requireRouteSnapshot(routeSession.getRouteSnapshotJson());

		FavoriteRoute favoriteRoute = FavoriteRoute.create(
			user,
			request.startLabel(),
			request.endLabel(),
			routeSession.getStartPoint(),
			routeSession.getEndPoint(),
			extractEnum(routeSnapshotJson, "transportMode", TransportMode.class),
			extractEnum(routeSnapshotJson, "routeOption", RouteOption.class),
			routeSnapshotJson);
		FavoriteRoute savedFavoriteRoute = favoriteRouteRepository.save(favoriteRoute);
		return new FavoriteRouteIdResponse(savedFavoriteRoute.getFavRouteId());
	}

	@Transactional
	public FavoriteRouteIdResponse updateFavoriteRoute(
		UUID userId,
		Long favRouteId,
		UpdateFavoriteRouteRequest request) {
		if (!request.hasAnyField()) {
			throw new FavoriteRouteException(
				FavoriteRouteErrorCode.INVALID_FAVORITE_ROUTE_UPDATE_REQUEST,
				"수정할 경로 북마크 표시명이 필요합니다.");
		}

		FavoriteRoute favoriteRoute = getFavoriteRoute(favRouteId);
		validateOwner(favoriteRoute, userId);
		favoriteRoute.changeLabels(request.startLabel(), request.endLabel());
		return new FavoriteRouteIdResponse(favoriteRoute.getFavRouteId());
	}

	@Transactional
	public void deleteFavoriteRoute(UUID userId, Long favRouteId) {
		FavoriteRoute favoriteRoute = getFavoriteRoute(favRouteId);
		validateOwner(favoriteRoute, userId);
		favoriteRouteRepository.delete(favoriteRoute);
	}

	private User getUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}

	private FavoriteRoute getFavoriteRoute(Long favRouteId) {
		return favoriteRouteRepository.findById(favRouteId)
			.orElseThrow(() -> new FavoriteRouteException(FavoriteRouteErrorCode.FAVORITE_ROUTE_NOT_FOUND));
	}

	private RouteSession getRouteSession(UUID userId, String routeId) {
		return routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, routeId)
			.orElseThrow(() -> new FavoriteRouteException(FavoriteRouteErrorCode.ROUTE_SESSION_NOT_FOUND));
	}

	private void validateOwner(FavoriteRoute favoriteRoute, UUID userId) {
		if (!favoriteRoute.isOwner(userId)) {
			throw new FavoriteRouteException(FavoriteRouteErrorCode.FAVORITE_ROUTE_FORBIDDEN);
		}
	}

	private static JsonNode requireRouteSnapshot(JsonNode routeSnapshotJson) {
		if (routeSnapshotJson == null || routeSnapshotJson.isNull()) {
			throw new FavoriteRouteException(FavoriteRouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		return routeSnapshotJson;
	}

	private static <E extends Enum<E>> E extractEnum(JsonNode routeSnapshotJson, String fieldName, Class<E> enumType) {
		JsonNode valueNode = routeSnapshotJson.get(fieldName);
		if (valueNode == null || !valueNode.isTextual() || valueNode.asText().isBlank()) {
			throw new FavoriteRouteException(FavoriteRouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		try {
			return Enum.valueOf(enumType, valueNode.asText());
		} catch (IllegalArgumentException exception) {
			throw new FavoriteRouteException(FavoriteRouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
	}
}
