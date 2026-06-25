package com.ssafy.e102.domain.place.service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import com.ssafy.e102.domain.place.dto.request.PlaceClickDetailRequest;
import com.ssafy.e102.domain.place.dto.response.PlaceAccessibilityFeatureResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceClickDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceListResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceMarkerResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceReverseGeocodeResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceSearchItemResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceSearchResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceTransitArrivalResponse;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.repository.BookmarkRepository;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
import com.ssafy.e102.domain.place.support.BookmarkTargetIdFactory;
import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceClickType;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.domain.place.type.PlaceMarkerKind;
import com.ssafy.e102.domain.route.entity.SubwayStation;
import com.ssafy.e102.domain.route.entity.SubwayTimetable;
import com.ssafy.e102.domain.route.repository.SubwayTimetableRepository;
import com.ssafy.e102.domain.route.service.BusStopMasterService;
import com.ssafy.e102.domain.route.service.SubwayStationMasterService;
import com.ssafy.e102.domain.route.type.SubwayServiceDayType;
import com.ssafy.e102.global.external.bims.BusanBimsArrival;
import com.ssafy.e102.global.external.bims.BusanBimsClient;
import com.ssafy.e102.global.external.kakao.KakaoAddressDocument;
import com.ssafy.e102.global.external.kakao.KakaoLocalClient;
import com.ssafy.e102.global.external.kakao.KakaoPlaceDocument;
import com.ssafy.e102.global.external.kakao.KakaoPlaceSearchRequest;
import com.ssafy.e102.global.external.kakao.KakaoPlaceSearchResult;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PlaceService {

	private static final int DEFAULT_KAKAO_SEARCH_PAGE = 1;
	private static final int MAX_KAKAO_SEARCH_PAGE = 45;
	private static final int DEFAULT_SEARCH_SIZE = 10;
	private static final int MAX_SEARCH_SIZE = 15;
	private static final int DEFAULT_PLACE_RADIUS_METER = 1000;
	private static final int DEFAULT_PLACE_DETAIL_RADIUS_METER = 300;
	private static final int DEFAULT_PLACE_DETAIL_SIZE = 5;
	private static final int MAX_PLACE_RADIUS_METER = 3000;
	private static final int PLACE_MARKER_LIMIT = 200;
	private static final double MIN_LAT = -90.0;
	private static final double MAX_LAT = 90.0;
	private static final double MIN_LNG = -180.0;
	private static final double MAX_LNG = 180.0;
	private static final String DEFAULT_PROVIDER = "KAKAO";
	private static final String SEARCH_CURSOR_PREFIX = "kakao:";
	private static final String EMPTY_FILTER_SENTINEL = "__EMPTY_FILTER__";
	private static final String BUS_STOP_PROVIDER_ID_PREFIX = "BS";
	private static final String BUS_STOP_FALLBACK_KEYWORD = "버스정류장";
	private static final String BUS_STOP_PROVIDER_CATEGORY = "교통,수송 > 버스정류장";
	private static final double BUS_STOP_MATCH_MAX_DISTANCE_METER = 150.0;
	private static final String SUBWAY_STATION_PROVIDER = "SUBWAY_STATION";
	private static final String SUBWAY_STATION_PROVIDER_CATEGORY_PREFIX = "교통,수송 > 지하철,전철";
	private static final double SUBWAY_STATION_MARKER_MATCH_MAX_DISTANCE_METER = 30.0;
	private static final double SUBWAY_STATION_MATCH_MAX_DISTANCE_METER = 300.0;
	private static final double SUBWAY_STATION_ADDRESS_MATCH_MAX_DISTANCE_METER = 60.0;
	private static final int TRANSIT_ARRIVAL_PREVIEW_LIMIT = 3;
	private static final int DAY_SECONDS = 24 * 60 * 60;
	private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final String SEARCH_SORT_RELEVANCE = "relevance";
	private static final String KAKAO_SEARCH_SORT_ACCURACY = "accuracy";
	private static final String KAKAO_SEARCH_SORT_DISTANCE = "distance";
	private static final String BUSAN_SEARCH_RECT = "128.75,34.85,129.35,35.40";
	private static final String BUSAN_REGION_PREFIX = "부산";
	private final PlaceRepository placeRepository;
	private final BookmarkRepository bookmarkRepository;
	private final KakaoLocalClient kakaoLocalClient;
	private final BusStopMasterService busStopMasterService;
	private final SubwayStationMasterService subwayStationMasterService;
	private final BusanBimsClient busanBimsClient;
	private final SubwayTimetableRepository subwayTimetableRepository;
	private final GeoPointConverter geoPointConverter;

	public PlaceService(
		PlaceRepository placeRepository,
		BookmarkRepository bookmarkRepository,
		KakaoLocalClient kakaoLocalClient,
		BusStopMasterService busStopMasterService,
		SubwayStationMasterService subwayStationMasterService,
		BusanBimsClient busanBimsClient,
		SubwayTimetableRepository subwayTimetableRepository,
		GeoPointConverter geoPointConverter) {
		this.placeRepository = placeRepository;
		this.bookmarkRepository = bookmarkRepository;
		this.kakaoLocalClient = kakaoLocalClient;
		this.busStopMasterService = busStopMasterService;
		this.subwayStationMasterService = subwayStationMasterService;
		this.busanBimsClient = busanBimsClient;
		this.subwayTimetableRepository = subwayTimetableRepository;
		this.geoPointConverter = geoPointConverter;
	}

	public PlaceSearchResponse searchPlaces(
		String keyword,
		String lat,
		String lng,
		String radius,
		String cursor,
		String sort,
		String size) {
		String normalizedKeyword = normalizeKeyword(keyword);
		Double parsedLat = parseOptionalDouble(lat);
		Double parsedLng = parseOptionalDouble(lng);
		Integer parsedRadius = parseOptionalPositiveInteger(radius);
		validateSearchCoordinateCondition(parsedLat, parsedLng, parsedRadius);
		int kakaoPage = parseSearchCursor(cursor);
		int parsedSize = parseIntegerOrDefault(size, DEFAULT_SEARCH_SIZE);
		validateSearchSize(parsedSize);
		PlaceSearchSort searchSort = parsePlaceSearchSort(sort);
		validateSearchSortCoordinateCondition(searchSort, parsedLat, parsedLng);

		try {
			SearchDocumentBatch searchBatch = searchBusanDocuments(
				normalizedKeyword,
				parsedLat,
				parsedLng,
				parsedRadius,
				kakaoPage,
				parsedSize,
				searchSort);
			List<KakaoPlaceDocument> searchDocuments = searchBatch.documents()
				.stream()
				.limit(parsedSize)
				.toList();
			Map<String, Place> matchedPlaces = getMatchedPlaces(searchDocuments);
			return new PlaceSearchResponse(
				searchDocuments
					.stream()
					.map(place -> PlaceSearchItemResponse.of(place, matchedPlaces))
					.toList(),
				searchBatch.hasNext() ? encodeSearchCursor(searchBatch.nextPage()) : null,
				parsedSize,
				searchBatch.totalElements(),
				searchBatch.hasNext());
		} catch (RestClientException | IllegalArgumentException exception) {
			log.warn("장소 검색 외부 API 호출 실패. kakaoPage={}, size={}",
				kakaoPage,
				parsedSize,
				exception);
			throw new PlaceException(PlaceErrorCode.PLACE_SEARCH_EXTERNAL_API_FAILED, exception);
		}
	}

	private SearchDocumentBatch searchBusanDocuments(
		String keyword,
		Double lat,
		Double lng,
		Integer radius,
		int startPage,
		int size,
		PlaceSearchSort sort) {
		List<KakaoPlaceDocument> documents = new ArrayList<>();
		int page = startPage;
		long totalElements = 0;
		boolean isEnd = false;
		boolean shouldBackfillBusanResults = lat != null && lng != null;
		String rect = sort == PlaceSearchSort.RELEVANCE ? BUSAN_SEARCH_RECT : null;

		while (page <= MAX_KAKAO_SEARCH_PAGE && documents.size() < size) {
			KakaoPlaceSearchResult kakaoResult = kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
				keyword,
				lat,
				lng,
				radius,
				rect,
				page,
				size,
				sort.kakaoSort));
			totalElements = kakaoResult.totalElements();
			documents.addAll(filterBusanSearchDocuments(kakaoResult.documents()));
			isEnd = kakaoResult.isEnd();
			if (isEnd) {
				break;
			}
			if (!shouldBackfillBusanResults) {
				break;
			}
			page++;
		}

		boolean hasNext = !isEnd && page < MAX_KAKAO_SEARCH_PAGE;
		return new SearchDocumentBatch(documents, hasNext, page + 1, totalElements);
	}

	private List<KakaoPlaceDocument> filterBusanSearchDocuments(List<KakaoPlaceDocument> documents) {
		return documents.stream()
			.filter(this::isBusanPlace)
			.toList();
	}

	private boolean isBusanPlace(KakaoPlaceDocument document) {
		return StringUtils.hasText(document.address())
			&& document.address().trim().startsWith(BUSAN_REGION_PREFIX);
	}

	private PlaceSearchSort parsePlaceSearchSort(String sort) {
		if (!StringUtils.hasText(sort)) {
			return PlaceSearchSort.RELEVANCE;
		}
		return switch (sort.trim().toLowerCase()) {
			case SEARCH_SORT_RELEVANCE, KAKAO_SEARCH_SORT_ACCURACY -> PlaceSearchSort.RELEVANCE;
			case KAKAO_SEARCH_SORT_DISTANCE -> PlaceSearchSort.DISTANCE;
			default -> throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		};
	}

	private void validateSearchSortCoordinateCondition(
		PlaceSearchSort sort,
		Double lat,
		Double lng) {
		if (sort == PlaceSearchSort.DISTANCE && (lat == null || lng == null)) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	public PlaceReverseGeocodeResponse reverseGeocode(String lat, String lng) {
		double parsedLat = parseRequiredDouble(lat);
		double parsedLng = parseRequiredDouble(lng);
		validateCoordinateRange(parsedLat, parsedLng);

		try {
			KakaoAddressDocument addressDocument = kakaoLocalClient.reverseGeocode(parsedLat, parsedLng)
				.orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_ADDRESS_NOT_FOUND));
			return PlaceReverseGeocodeResponse.from(addressDocument);
		} catch (RestClientException | IllegalArgumentException exception) {
			log.warn("좌표 주소 변환 외부 API 호출 실패.", exception);
			throw new PlaceException(PlaceErrorCode.PLACE_REVERSE_GEOCODE_EXTERNAL_API_FAILED, exception);
		}
	}

	public PlaceListResponse getPlaces(
		UUID userId,
		String lat,
		String lng,
		String radius,
		String category,
		String featureType) {
		double parsedLat = parseRequiredDouble(lat);
		double parsedLng = parseRequiredDouble(lng);
		int parsedRadius = parsePlaceRadius(radius);
		Set<PlaceCategory> categories = parseCsvEnums(category, PlaceCategory.class);
		Set<AccessibilityFeatureType> featureTypes = parseCsvEnums(featureType, AccessibilityFeatureType.class);

		List<Long> placeIds = placeRepository.findPlaceMarkerIds(
			parsedLat,
			parsedLng,
			parsedRadius,
			toEnumNames(categories),
			categories.isEmpty(),
			toEnumNames(featureTypes),
			featureTypes.isEmpty(),
			PLACE_MARKER_LIMIT);
		if (placeIds.isEmpty()) {
			return new PlaceListResponse(List.of());
		}
		List<Place> places = findPlacesWithAccessibilityFeatures(placeIds);
		Set<Long> bookmarkedPlaceIds = getBookmarkedPlaceIds(userId, places);
		return new PlaceListResponse(places.stream()
			.map(place -> PlaceMarkerResponse.of(
				place,
				bookmarkedPlaceIds,
				geoPointConverter,
				resolveMarkerKind(place)))
			.toList());
	}

	public PlaceDetailResponse getPlace(UUID userId, String placeId) {
		Long parsedPlaceId = parsePositiveLong(placeId);
		Place place = placeRepository.findWithAccessibilityFeaturesByPlaceId(parsedPlaceId)
			.orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));
		boolean isBookmarked = bookmarkRepository.existsByUser_UserIdAndPlace_PlaceId(userId, parsedPlaceId);
		return PlaceDetailResponse.of(place, isBookmarked, geoPointConverter);
	}

	public PlaceClickDetailResponse getPlaceDetail(UUID userId, PlaceClickDetailRequest request) {
		validateBasePlaceClickDetailRequest(request);
		Optional<Place> internalPlace = findMatchedInternalPlace(request.providerPlaceId());
		if (internalPlace.isPresent()) {
			return toInternalClickDetailResponse(userId, request, internalPlace.get());
		}
		if (request.clickType() == PlaceClickType.POI && isBusStopPoi(request)) {
			return getExternalBusStopDetail(userId, request);
		}
		if (request.clickType() == PlaceClickType.POI && isSubwayPoi(request)) {
			return getExternalSubwayStationDetailOrAddressFallback(userId, request);
		}
		if (request.clickType() == PlaceClickType.POI) {
			return getExternalPoiDetailOrAddressFallback(userId, request);
		}
		Optional<SubwayStationMasterService.SubwayStationPlaceDetail> nearbySubwayStation = subwayStationMasterService
			.findNearestPlaceDetail(
				request.lat(),
				request.lng(),
				SUBWAY_STATION_ADDRESS_MATCH_MAX_DISTANCE_METER);
		if (nearbySubwayStation.isPresent()) {
			return toExternalSubwayStationDetailResponse(userId, request, nearbySubwayStation.get());
		}
		return getExternalAddressDetail(userId, request);
	}

	private Map<String, Place> getMatchedPlaces(List<KakaoPlaceDocument> kakaoPlaces) {
		List<String> providerPlaceIds = kakaoPlaces.stream()
			.map(KakaoPlaceDocument::id)
			.toList();
		if (providerPlaceIds.isEmpty()) {
			return Map.of();
		}
		return placeRepository.findAllByProviderPlaceIdIn(providerPlaceIds)
			.stream()
			.collect(Collectors.toMap(Place::getProviderPlaceId, Function.identity(), (first, second) -> first));
	}

	private List<Place> findPlacesWithAccessibilityFeatures(List<Long> placeIds) {
		Map<Long, Place> placesById = placeRepository.findAllByPlaceIdIn(placeIds)
			.stream()
			.collect(Collectors.toMap(Place::getPlaceId, Function.identity(), (first, second) -> first));
		return placeIds.stream()
			.map(placesById::get)
			.filter(place -> place != null)
			.toList();
	}

	private Set<Long> getBookmarkedPlaceIds(UUID userId, List<Place> places) {
		List<Long> placeIds = places.stream()
			.map(Place::getPlaceId)
			.toList();
		if (placeIds.isEmpty()) {
			return Set.of();
		}
		return bookmarkRepository.findBookmarkedPlaceIds(userId, placeIds);
	}

	private int parseSearchCursor(String cursor) {
		if (!StringUtils.hasText(cursor)) {
			return DEFAULT_KAKAO_SEARCH_PAGE;
		}
		try {
			String decodedCursor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			if (!decodedCursor.startsWith(SEARCH_CURSOR_PREFIX)) {
				throw new NumberFormatException("장소 검색 cursor prefix가 올바르지 않습니다.");
			}
			int kakaoPage = Integer.parseInt(decodedCursor.substring(SEARCH_CURSOR_PREFIX.length()));
			if (kakaoPage < DEFAULT_KAKAO_SEARCH_PAGE) {
				throw new NumberFormatException("장소 검색 cursor는 양수여야 합니다.");
			}
			return kakaoPage;
		} catch (IllegalArgumentException exception) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private String encodeSearchCursor(int kakaoPage) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString((SEARCH_CURSOR_PREFIX + kakaoPage).getBytes(StandardCharsets.UTF_8));
	}

	private String normalizeKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			throw new PlaceException(PlaceErrorCode.PLACE_KEYWORD_REQUIRED);
		}
		return keyword.trim();
	}

	private void validateBasePlaceClickDetailRequest(PlaceClickDetailRequest request) {
		if (request == null || request.lat() == null || request.lng() == null || request.clickType() == null) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
		validateCoordinateRange(request.lat(), request.lng());
	}

	private void validateExternalPoiDetailRequest(PlaceClickDetailRequest request) {
		if (!StringUtils.hasText(request.nameHint())) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private Optional<Place> findMatchedInternalPlace(String providerPlaceId) {
		if (!StringUtils.hasText(providerPlaceId)) {
			return Optional.empty();
		}
		return placeRepository.findAllByProviderPlaceIdIn(List.of(providerPlaceId.trim()))
			.stream()
			.findFirst();
	}

	private PlaceClickDetailResponse toInternalClickDetailResponse(UUID userId, PlaceClickDetailRequest request,
		Place place) {
		String bookmarkTargetId = BookmarkTargetIdFactory.fromInternalPlace(place.getPlaceId());
		return new PlaceClickDetailResponse(
			bookmarkTargetId,
			PlaceDetailType.INTERNAL_PLACE,
			place.getPlaceId(),
			normalizeProvider(request.provider(), place.getProviderPlaceId()),
			place.getProviderPlaceId(),
			place.getName(),
			place.getCategory(),
			null,
			null,
			place.getAddress(),
			geoPointConverter.toResponse(place.getPoint()),
			place.getAccessibilityFeatures().stream()
				.map(PlaceAccessibilityFeatureResponse::from)
				.toList(),
			isPlaceBookmarked(userId, place.getPlaceId(), bookmarkTargetId));
	}

	private PlaceClickDetailResponse getExternalPoiDetail(UUID userId, PlaceClickDetailRequest request) {
		try {
			validateExternalPoiDetailRequest(request);
			KakaoPlaceSearchResult result = kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
				request.nameHint().trim(),
				request.lat(),
				request.lng(),
				DEFAULT_PLACE_DETAIL_RADIUS_METER,
				DEFAULT_KAKAO_SEARCH_PAGE,
				DEFAULT_PLACE_DETAIL_SIZE));
			KakaoPlaceDocument selected = selectPoiCandidate(request, result.documents())
				.orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_CLICK_DETAIL_NOT_FOUND));
			return toExternalPoiDetailResponse(userId, request, selected);
		} catch (RestClientException | IllegalArgumentException exception) {
			log.warn("지도 클릭 상세 POI 외부 API 호출 실패. providerPlaceId={}",
				request.providerPlaceId(),
				exception);
			throw new PlaceException(PlaceErrorCode.PLACE_SEARCH_EXTERNAL_API_FAILED, exception);
		}
	}

	private PlaceClickDetailResponse toExternalPoiDetailResponse(
		UUID userId,
		PlaceClickDetailRequest request,
		KakaoPlaceDocument selected) {
		String provider = normalizeProvider(request.provider(), selected.id());
		String bookmarkTargetId = BookmarkTargetIdFactory.fromExternalPoi(
			provider,
			selected.id(),
			selected.placeName(),
			selected.point().lat(),
			selected.point().lng());
		return new PlaceClickDetailResponse(
			bookmarkTargetId,
			PlaceDetailType.EXTERNAL_POI,
			null,
			provider,
			selected.id(),
			selected.placeName(),
			null,
			selected.providerCategory(),
			selected.phone(),
			selected.address(),
			selected.point(),
			List.of(),
			bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId));
	}

	private PlaceClickDetailResponse getExternalPoiDetailOrAddressFallback(UUID userId,
		PlaceClickDetailRequest request) {
		try {
			return getExternalPoiDetail(userId, request);
		} catch (PlaceException exception) {
			if (!isRecoverableExternalPoiDetailFailure(exception)) {
				throw exception;
			}
			log.debug("지도 클릭 POI keyword 상세 조회 실패로 fallback을 시작합니다. errorCode={}, providerPlaceId={}",
				exception.getErrorCode()
					.getStatus(),
				request.providerPlaceId());
			return resolveExternalPoiFallback(userId, request);
		}
	}

	private PlaceClickDetailResponse resolveExternalPoiFallback(UUID userId, PlaceClickDetailRequest request) {
		KakaoAddressDocument addressDocument = getAddressDocument(request);
		Optional<PlaceClickDetailResponse> buildingDetail = tryBuildingNameFallback(userId, request, addressDocument);
		if (buildingDetail.isPresent()) {
			return buildingDetail.get();
		}

		log.debug("지도 클릭 POI fallback 최종 단계로 주소 상세를 반환합니다. fallbackType={}",
			"ADDRESS");
		return toExternalAddressDetailResponse(userId, request, addressDocument);
	}

	private boolean isRecoverableExternalPoiDetailFailure(PlaceException exception) {
		return exception.getErrorCode() == PlaceErrorCode.INVALID_PLACE_REQUEST
			|| exception.getErrorCode() == PlaceErrorCode.PLACE_CLICK_DETAIL_NOT_FOUND
			|| exception.getErrorCode() == PlaceErrorCode.PLACE_SEARCH_EXTERNAL_API_FAILED;
	}

	private PlaceClickDetailResponse getExternalBusStopDetail(UUID userId, PlaceClickDetailRequest request) {
		log.debug("지도 클릭 POI를 버스정류장으로 식별해 정류장 POI로 반환합니다. providerPlaceId={}",
			request.providerPlaceId());
		KakaoAddressDocument addressDocument = getAddressDocumentOrNull(request);
		Optional<BusStopMasterService.BusStopMatch> busStopMatch = busStopMasterService.findNearest(
			request.lat(),
			request.lng(),
			BUS_STOP_MATCH_MAX_DISTANCE_METER);
		String displayName = resolveBusStopDisplayName(request, addressDocument, busStopMatch);
		String provider = normalizeProvider(request.provider(), request.providerPlaceId());
		String providerPlaceId = request.providerPlaceId()
			.trim();
		String bookmarkTargetId = BookmarkTargetIdFactory.fromExternalPoi(
			provider,
			providerPlaceId,
			displayName,
			request.lat(),
			request.lng());
		return new PlaceClickDetailResponse(
			bookmarkTargetId,
			PlaceDetailType.EXTERNAL_POI,
			null,
			provider,
			providerPlaceId,
			displayName,
			null,
			BUS_STOP_PROVIDER_CATEGORY,
			null,
			addressDocument == null ? null : addressDocument.displayAddress(),
			geoPointConverter
				.toResponse(geoPointConverter.toPoint(new GeoPointRequest(request.lat(), request.lng()))),
			List.of(),
			busStopMatch
				.map(this::busStopTransitArrivals)
				.orElse(List.of()),
			bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId));
	}

	private String resolveBusStopDisplayName(
		PlaceClickDetailRequest request,
		KakaoAddressDocument addressDocument,
		Optional<BusStopMasterService.BusStopMatch> busStopMatch) {
		if (hasSpecificBusStopNameHint(request.nameHint())) {
			return request.nameHint()
				.trim();
		}
		return busStopMatch
			.map(BusStopMasterService.BusStopMatch::stopName)
			.or(() -> busStopBuildingName(addressDocument))
			.orElseGet(() -> StringUtils.hasText(request.nameHint())
				? request.nameHint()
					.trim()
				: BUS_STOP_FALLBACK_KEYWORD);
	}

	private List<PlaceTransitArrivalResponse> busStopTransitArrivals(BusStopMasterService.BusStopMatch busStopMatch) {
		try {
			return busanBimsClient.findArrivalsByStopId(busStopMatch.stopId())
				.stream()
				.filter(arrival -> arrival.remainingMinute() != null)
				.limit(TRANSIT_ARRIVAL_PREVIEW_LIMIT)
				.map(this::toBusTransitArrival)
				.toList();
		} catch (RuntimeException exception) {
			log.warn("버스정류장 도착 정보를 조회할 수 없어 상세 시간 표시를 생략합니다. stopId={}",
				busStopMatch.stopId(),
				exception);
			return List.of();
		}
	}

	private PlaceTransitArrivalResponse toBusTransitArrival(BusanBimsArrival arrival) {
		return new PlaceTransitArrivalResponse(
			"BUS",
			arrival.routeNo(),
			null,
			arrival.remainingMinute(),
			arrival.isLowFloor(),
			"REALTIME");
	}

	private boolean hasSpecificBusStopNameHint(String nameHint) {
		return StringUtils.hasText(nameHint) && !isGenericBusStopName(nameHint);
	}

	private boolean isGenericBusStopName(String value) {
		return normalizeComparableText(value).replace(" ", "").equals(BUS_STOP_FALLBACK_KEYWORD);
	}

	private Optional<String> busStopBuildingName(KakaoAddressDocument addressDocument) {
		if (addressDocument == null || !StringUtils.hasText(addressDocument.buildingName())) {
			return Optional.empty();
		}
		return Optional.of(addressDocument.buildingName()
			.trim());
	}

	private PlaceClickDetailResponse getExternalSubwayStationDetailOrAddressFallback(UUID userId,
		PlaceClickDetailRequest request) {
		return subwayStationMasterService.findPlaceDetail(
			request.nameHint(),
			request.lat(),
			request.lng(),
			SUBWAY_STATION_MATCH_MAX_DISTANCE_METER)
			.map(detail -> toExternalSubwayStationDetailResponse(userId, request, detail))
			.orElseGet(() -> {
				log.debug("지도 클릭 POI 지하철역 마스터 매칭 실패로 일반 POI 상세 fallback을 사용합니다. nameHint={}, providerPlaceId={}",
					request.nameHint(),
					request.providerPlaceId());
				return getExternalPoiDetailOrAddressFallback(userId, request);
			});
	}

	private PlaceClickDetailResponse toExternalSubwayStationDetailResponse(
		UUID userId,
		PlaceClickDetailRequest request,
		SubwayStationMasterService.SubwayStationPlaceDetail detail) {
		SubwayStation station = detail.station();
		String displayName = subwayStationDisplayName(station);
		String providerCategory = subwayStationProviderCategory(detail.lineNames());
		String bookmarkTargetId = BookmarkTargetIdFactory.fromExternalPoi(
			SUBWAY_STATION_PROVIDER,
			detail.groupProviderPlaceId(),
			displayName,
			station.getPoint().getY(),
			station.getPoint().getX());
		KakaoAddressDocument addressDocument = getAddressDocumentOrNull(request);
		return new PlaceClickDetailResponse(
			bookmarkTargetId,
			PlaceDetailType.EXTERNAL_POI,
			null,
			SUBWAY_STATION_PROVIDER,
			detail.groupProviderPlaceId(),
			displayName,
			null,
			providerCategory,
			null,
			addressDocument == null ? null : addressDocument.displayAddress(),
			geoPointConverter.toResponse(station.getPoint()),
			detail.accessibilityFeatures()
				.stream()
				.map(feature -> new PlaceAccessibilityFeatureResponse(feature.featureType(), feature.isAvailable()))
				.toList(),
			subwayTransitArrivals(detail),
			bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId));
	}

	private List<PlaceTransitArrivalResponse> subwayTransitArrivals(
		SubwayStationMasterService.SubwayStationPlaceDetail detail) {
		LocalDateTime now = LocalDateTime.now(SEOUL_ZONE_ID);
		int secondOfDay = now.toLocalTime()
			.toSecondOfDay();
		SubwayServiceDayType serviceDayType = serviceDayType(now.getDayOfWeek());
		SubwayServiceDayType nextServiceDayType = serviceDayType(now.toLocalDate()
			.plusDays(1)
			.getDayOfWeek());
		try {
			return detail.stationGroup()
				.stream()
				.flatMap(
					station -> nextSubwayDepartures(station, serviceDayType, nextServiceDayType, secondOfDay).stream()
						.map(departure -> toSubwayTransitArrival(station, departure, secondOfDay)))
				.sorted(Comparator.comparing(PlaceTransitArrivalResponse::remainingMinute,
					Comparator.nullsLast(Integer::compareTo)))
				.limit(TRANSIT_ARRIVAL_PREVIEW_LIMIT)
				.toList();
		} catch (RuntimeException exception) {
			log.warn("지하철역 시간표 정보를 조회할 수 없어 상세 시간 표시를 생략합니다. providerPlaceId={}",
				detail.groupProviderPlaceId(),
				exception);
			return List.of();
		}
	}

	private List<SubwayTimetable> nextSubwayDepartures(
		SubwayStation station,
		SubwayServiceDayType serviceDayType,
		SubwayServiceDayType nextServiceDayType,
		int secondOfDay) {
		List<SubwayTimetable> departures = new ArrayList<>();
		for (int wayCode : List.of(1, 2)) {
			List<SubwayTimetable> nextDepartures = subwayTimetableRepository.findNextDepartures(
				station.getOdsayStationId(),
				serviceDayType,
				wayCode,
				secondOfDay,
				PageRequest.of(0, 1));
			if (nextDepartures.isEmpty()) {
				nextDepartures = subwayTimetableRepository.findFirstDepartures(
					station.getOdsayStationId(),
					nextServiceDayType,
					wayCode,
					PageRequest.of(0, 1));
			}
			departures.addAll(nextDepartures);
		}
		return departures;
	}

	private PlaceTransitArrivalResponse toSubwayTransitArrival(
		SubwayStation station,
		SubwayTimetable departure,
		int secondOfDay) {
		return new PlaceTransitArrivalResponse(
			"SUBWAY",
			station.getLineName(),
			departure.getEndStationName() + "행",
			remainingMinute(secondOfDay, departure.getDepartureSecondOfDay()),
			null,
			"TIMETABLE");
	}

	private int remainingMinute(int nowSecondOfDay, int departureSecondOfDay) {
		int remainSecond = departureSecondOfDay - nowSecondOfDay;
		if (remainSecond < 0) {
			remainSecond += DAY_SECONDS;
		}
		return Math.max(0, (int)Math.ceil(remainSecond / 60.0));
	}

	private SubwayServiceDayType serviceDayType(DayOfWeek dayOfWeek) {
		if (dayOfWeek == DayOfWeek.SATURDAY) {
			return SubwayServiceDayType.SATURDAY;
		}
		if (dayOfWeek == DayOfWeek.SUNDAY) {
			return SubwayServiceDayType.HOLIDAY;
		}
		return SubwayServiceDayType.WEEKDAY;
	}

	private String subwayStationDisplayName(SubwayStation station) {
		return station.getStationName()
			.endsWith("역") ? station.getStationName() : station.getStationName() + "역";
	}

	private String subwayStationProviderCategory(List<String> lineNames) {
		if (lineNames == null || lineNames.isEmpty()) {
			return SUBWAY_STATION_PROVIDER_CATEGORY_PREFIX;
		}
		return SUBWAY_STATION_PROVIDER_CATEGORY_PREFIX + " > " + String.join(" · ", lineNames);
	}

	private Optional<PlaceClickDetailResponse> tryBuildingNameFallback(
		UUID userId,
		PlaceClickDetailRequest request,
		KakaoAddressDocument addressDocument) {
		if (!StringUtils.hasText(addressDocument.buildingName())
			|| !StringUtils.hasText(addressDocument.roadAddress())) {
			log.debug("지도 클릭 POI 건물명 fallback을 생략합니다. fallbackType={}",
				"BUILDING_NAME");
			return Optional.empty();
		}
		log.debug("지도 클릭 POI fallback으로 건물명 검색을 시도합니다. fallbackType={}",
			"BUILDING_NAME");
		try {
			KakaoPlaceSearchResult result = kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
				addressDocument.buildingName()
					.trim(),
				request.lat(),
				request.lng(),
				DEFAULT_PLACE_DETAIL_RADIUS_METER,
				DEFAULT_KAKAO_SEARCH_PAGE,
				DEFAULT_PLACE_DETAIL_SIZE));
			Optional<KakaoPlaceDocument> selected = selectBuildingNameCandidate(addressDocument, result.documents());
			if (selected.isEmpty()) {
				log.debug("지도 클릭 POI 건물명 fallback 후보가 없습니다. fallbackType={}, resultCount={}",
					"BUILDING_NAME",
					result.documents()
						.size());
				return Optional.empty();
			}
			KakaoPlaceDocument candidate = selected.get();
			log.debug("지도 클릭 POI 건물명 fallback 후보를 선택했습니다. fallbackType={}, providerPlaceId={}",
				"BUILDING_NAME",
				candidate.id());
			return Optional.of(toExternalPoiDetailResponse(userId, request, candidate));
		} catch (RestClientException | IllegalArgumentException exception) {
			log.debug("지도 클릭 POI 건물명 fallback 검색 실패. fallbackType={}, providerPlaceId={}",
				"BUILDING_NAME",
				request.providerPlaceId(),
				exception);
			return Optional.empty();
		}
	}

	private Optional<KakaoPlaceDocument> selectBuildingNameCandidate(
		KakaoAddressDocument addressDocument,
		List<KakaoPlaceDocument> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return Optional.empty();
		}
		List<KakaoPlaceDocument> sameRoadAddressCandidates = candidates.stream()
			.filter(candidate -> isSameAddress(addressDocument.roadAddress(), candidate.address()))
			.toList();
		if (sameRoadAddressCandidates.isEmpty()) {
			return Optional.empty();
		}
		String normalizedBuildingName = normalizeComparableText(addressDocument.buildingName());
		return sameRoadAddressCandidates.stream()
			.filter(candidate -> normalizedBuildingName.equals(normalizeComparableText(candidate.placeName())))
			.findFirst()
			.or(() -> sameRoadAddressCandidates.stream()
				.min(
					Comparator.comparing(KakaoPlaceDocument::distanceMeter, Comparator.nullsLast(Integer::compareTo))));
	}

	private Optional<KakaoPlaceDocument> selectPoiCandidate(
		PlaceClickDetailRequest request,
		List<KakaoPlaceDocument> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return Optional.empty();
		}
		if (StringUtils.hasText(request.providerPlaceId())) {
			Optional<KakaoPlaceDocument> exactProviderMatch = candidates.stream()
				.filter(candidate -> request.providerPlaceId().trim().equals(candidate.id()))
				.findFirst();
			if (exactProviderMatch.isPresent()) {
				return exactProviderMatch;
			}
		}
		String normalizedName = normalizeComparableText(request.nameHint());
		Optional<KakaoPlaceDocument> exactNameMatch = candidates.stream()
			.filter(candidate -> normalizedName.equals(normalizeComparableText(candidate.placeName())))
			.findFirst();
		if (exactNameMatch.isPresent()) {
			return exactNameMatch;
		}
		return candidates.stream()
			.min(Comparator.comparing(KakaoPlaceDocument::distanceMeter, Comparator.nullsLast(Integer::compareTo)));
	}

	private PlaceClickDetailResponse getExternalAddressDetail(UUID userId, PlaceClickDetailRequest request) {
		return toExternalAddressDetailResponse(userId, request, getAddressDocument(request));
	}

	private KakaoAddressDocument getAddressDocument(PlaceClickDetailRequest request) {
		try {
			return kakaoLocalClient.reverseGeocode(request.lat(), request.lng())
				.orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_ADDRESS_NOT_FOUND));
		} catch (RestClientException | IllegalArgumentException exception) {
			log.warn("지도 클릭 상세 주소 외부 API 호출 실패.", exception);
			throw new PlaceException(PlaceErrorCode.PLACE_REVERSE_GEOCODE_EXTERNAL_API_FAILED, exception);
		}
	}

	private KakaoAddressDocument getAddressDocumentOrNull(PlaceClickDetailRequest request) {
		try {
			return kakaoLocalClient.reverseGeocode(request.lat(), request.lng())
				.orElse(null);
		} catch (RestClientException | IllegalArgumentException exception) {
			log.debug("지도 클릭 POI 주소 보강 외부 API 호출 실패. providerPlaceId={}",
				request.providerPlaceId(),
				exception);
			return null;
		}
	}

	private PlaceClickDetailResponse toExternalAddressDetailResponse(
		UUID userId,
		PlaceClickDetailRequest request,
		KakaoAddressDocument addressDocument) {
		return toExternalAddressDetailResponse(userId, request, addressDocument, addressDocument.displayAddress());
	}

	private PlaceClickDetailResponse toExternalAddressDetailResponse(
		UUID userId,
		PlaceClickDetailRequest request,
		KakaoAddressDocument addressDocument,
		String displayName) {
		String displayAddress = addressDocument.displayAddress();
		String provider = normalizeProvider(request.provider(), null);
		String bookmarkTargetId = BookmarkTargetIdFactory.fromExternalAddress(
			provider,
			displayName,
			request.lat(),
			request.lng(),
			displayAddress);
		return new PlaceClickDetailResponse(
			bookmarkTargetId,
			PlaceDetailType.EXTERNAL_ADDRESS,
			null,
			provider,
			null,
			displayName,
			null,
			null,
			null,
			displayAddress,
			geoPointConverter
				.toResponse(geoPointConverter.toPoint(new GeoPointRequest(request.lat(), request.lng()))),
			List.of(),
			bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId));
	}

	private boolean isPlaceBookmarked(UUID userId, Long placeId, String bookmarkTargetId) {
		if (bookmarkRepository.existsByUser_UserIdAndPlace_PlaceId(userId, placeId)) {
			return true;
		}
		return bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId);
	}

	private PlaceMarkerKind resolveMarkerKind(Place place) {
		if (place.getCategory() != PlaceCategory.ETC) {
			return PlaceMarkerKind.DEFAULT;
		}
		if (isBusStopProviderPlaceId(place.getProviderPlaceId())) {
			return PlaceMarkerKind.BUS_STOP;
		}
		if (isSubwayStationMarker(place)) {
			return PlaceMarkerKind.SUBWAY_STATION;
		}
		return PlaceMarkerKind.DEFAULT;
	}

	private boolean isSubwayStationMarker(Place place) {
		if (place.getPoint() == null) {
			return false;
		}
		return subwayStationMasterService.findNearestPlaceDetail(
			place.getPoint().getY(),
			place.getPoint().getX(),
			SUBWAY_STATION_MARKER_MATCH_MAX_DISTANCE_METER)
			.isPresent();
	}

	private String normalizeProvider(String requestedProvider, String providerPlaceId) {
		if (StringUtils.hasText(requestedProvider)) {
			return requestedProvider.trim().toUpperCase();
		}
		if (StringUtils.hasText(providerPlaceId)) {
			return DEFAULT_PROVIDER;
		}
		return null;
	}

	private String normalizeComparableText(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private boolean isBusStopPoi(PlaceClickDetailRequest request) {
		return isBusStopProviderPlaceId(request.providerPlaceId());
	}

	private boolean isBusStopProviderPlaceId(String providerPlaceId) {
		return StringUtils.hasText(providerPlaceId)
			&& providerPlaceId.trim().toUpperCase().startsWith(BUS_STOP_PROVIDER_ID_PREFIX);
	}

	private boolean isSubwayPoi(PlaceClickDetailRequest request) {
		String normalizedName = normalizeComparableText(request.nameHint());
		return isSubwayStationLikeName(normalizedName) && !isBusStopPoi(request);
	}

	private boolean isSubwayStationLikeName(String normalizedName) {
		int stationNameEndIndex = normalizedName.lastIndexOf("역");
		if (stationNameEndIndex <= 0) {
			return false;
		}
		String tail = normalizedName.substring(stationNameEndIndex + 1)
			.trim();
		return !StringUtils.hasText(tail)
			|| tail.contains("출구")
			|| tail.contains("호선")
			|| tail.contains("경전철");
	}

	private boolean isSameAddress(String first, String second) {
		return StringUtils.hasText(first)
			&& StringUtils.hasText(second)
			&& normalizeAddress(first).equals(normalizeAddress(second));
	}

	private String normalizeAddress(String value) {
		return value.trim()
			.replace("특별시", "")
			.replace("광역시", "")
			.replaceAll("\\s+", "");
	}

	private void validateSearchCoordinateCondition(Double lat, Double lng, Integer radius) {
		if ((lat == null) != (lng == null)) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
		if (radius != null && (lat == null || lng == null)) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private double parseRequiredDouble(String value) {
		if (!StringUtils.hasText(value)) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
		return parseDouble(value);
	}

	private void validateCoordinateRange(double lat, double lng) {
		if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private Double parseOptionalDouble(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return parseDouble(value);
	}

	private double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private Integer parseOptionalPositiveInteger(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		int parsedValue = parseInteger(value);
		if (parsedValue <= 0) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
		return parsedValue;
	}

	private int parsePlaceRadius(String value) {
		if (!StringUtils.hasText(value)) {
			return DEFAULT_PLACE_RADIUS_METER;
		}
		int parsedRadius = parseOptionalPositiveInteger(value);
		if (parsedRadius > MAX_PLACE_RADIUS_METER) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
		return parsedRadius;
	}

	private int parseIntegerOrDefault(String value, int defaultValue) {
		if (!StringUtils.hasText(value)) {
			return defaultValue;
		}
		return parseInteger(value);
	}

	private int parseInteger(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private void validateSearchSize(int size) {
		if (size < 1 || size > MAX_SEARCH_SIZE) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private Long parsePositiveLong(String value) {
		try {
			long parsedValue = Long.parseLong(value);
			if (parsedValue <= 0) {
				throw new NumberFormatException("placeId는 양수여야 합니다.");
			}
			return parsedValue;
		} catch (NumberFormatException exception) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private <E extends Enum<E>> Set<E> parseCsvEnums(String value, Class<E> enumType) {
		if (!StringUtils.hasText(value)) {
			return Set.of();
		}
		try {
			return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.map(token -> Enum.valueOf(enumType, token))
				.collect(Collectors.toSet());
		} catch (IllegalArgumentException exception) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST);
		}
	}

	private <E extends Enum<E>> Set<String> toEnumNames(Set<E> values) {
		if (values.isEmpty()) {
			return Set.of(EMPTY_FILTER_SENTINEL);
		}
		return values.stream()
			.map(Enum::name)
			.collect(Collectors.toSet());
	}

	private record SearchDocumentBatch(
		List<KakaoPlaceDocument> documents,
		boolean hasNext,
		int nextPage,
		long totalElements) {
	}

	private enum PlaceSearchSort {
		RELEVANCE(KAKAO_SEARCH_SORT_ACCURACY),
		DISTANCE(KAKAO_SEARCH_SORT_DISTANCE);

		private final String kakaoSort;

		PlaceSearchSort(String kakaoSort) {
			this.kakaoSort = kakaoSort;
		}
	}
}
