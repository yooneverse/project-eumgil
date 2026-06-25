package com.ssafy.e102.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import com.ssafy.e102.domain.place.dto.request.PlaceClickDetailRequest;
import com.ssafy.e102.domain.place.dto.response.PlaceClickDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceListResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceReverseGeocodeResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceSearchItemResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceSearchResponse;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;
import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.repository.BookmarkRepository;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
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
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

class PlaceServiceTest {

	private static final String BUSAN_SEARCH_RECT = "128.75,34.85,129.35,35.40";

	@Mock
	private PlaceRepository placeRepository;

	@Mock
	private BookmarkRepository bookmarkRepository;

	@Mock
	private KakaoLocalClient kakaoLocalClient;

	@Mock
	private BusStopMasterService busStopMasterService;

	@Mock
	private SubwayStationMasterService subwayStationMasterService;

	@Mock
	private BusanBimsClient busanBimsClient;

	@Mock
	private SubwayTimetableRepository subwayTimetableRepository;

	private PlaceService placeService;
	private GeoPointConverter geoPointConverter;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		geoPointConverter = new GeoPointConverter();
		placeService = new PlaceService(
			placeRepository,
			bookmarkRepository,
			kakaoLocalClient,
			busStopMasterService,
			subwayStationMasterService,
			busanBimsClient,
			subwayTimetableRepository,
			geoPointConverter);
	}

	@Test
	@DisplayName("장소 검색은 카카오 결과를 내부 장소와 providerPlaceId로 매칭한다")
	void searchPlaces() {
		KakaoPlaceDocument kakaoPlace = new KakaoPlaceDocument(
			"123456789",
			"부산시민공원",
			"부산광역시 부산진구 시민공원로 73",
			"여행 > 관광,명소 > 공원",
			"051-123-4567",
			350,
			new GeoPointResponse(35.1686, 129.0576));
		Place matchedPlace = place(
			10L,
			"부산시민공원",
			PlaceCategory.TOURIST_SPOT,
			"123456789",
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleEntrance);
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"부산시민공원",
			35.1686,
			129.0576,
			1000,
			BUSAN_SEARCH_RECT,
			1,
			10,
			"accuracy")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(kakaoPlace), 1, true));
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("123456789")))
			.thenReturn(List.of(matchedPlace));

		PlaceSearchResponse response = placeService.searchPlaces(
			" 부산시민공원 ",
			"35.1686",
			"129.0576",
			"1000",
			null,
			null,
			"10");

		assertThat(response.nextCursor()).isNull();
		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(response.hasNext()).isFalse();
		assertThat(response.places()).hasSize(1);
		assertThat(response.places().get(0).placeId()).isEqualTo(10L);
		assertThat(response.places().get(0).matched()).isTrue();
		assertThat(response.places().get(0).category()).isEqualTo(PlaceCategory.TOURIST_SPOT);
		assertThat(response.places().get(0).accessibilityFeatures()).hasSize(1);
	}

	@Test
	@DisplayName("관련도순 장소 검색은 부산 결과만 남기고 카카오 정확도 순서를 유지한다")
	void searchPlacesFiltersBusanAndKeepsKakaoAccuracyOrder() {
		KakaoPlaceDocument mobileStore = new KakaoPlaceDocument(
			"mobile",
			"삼성스토어 부산삼성전기모바일",
			"부산 강서구 녹산산업중로 333",
			"서비스",
			"",
			3877,
			new GeoPointResponse(35.1001, 128.9001));
		KakaoPlaceDocument exactPlace = new KakaoPlaceDocument(
			"exact",
			"삼성전기 부산사업장",
			"부산 강서구 녹산산업중로 333",
			"회사",
			"",
			4072,
			new GeoPointResponse(35.1002, 128.9002));
		KakaoPlaceDocument gate = new KakaoPlaceDocument(
			"gate",
			"삼성전기 부산사업장 후문",
			"부산 강서구 송정동 1600",
			"회사",
			"",
			4056,
			new GeoPointResponse(35.1003, 128.9003));
		KakaoPlaceDocument outOfBusan = new KakaoPlaceDocument(
			"suwon",
			"삼성전기 본사",
			"경기 수원시 영통구 매영로 150",
			"회사",
			"",
			291841,
			new GeoPointResponse(37.2520, 127.0550));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기 부산사업장",
			35.1,
			128.9,
			null,
			BUSAN_SEARCH_RECT,
			1,
			15,
			"accuracy")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(mobileStore, gate, exactPlace, outOfBusan), 4, true));
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("mobile", "gate", "exact")))
			.thenReturn(List.of());

		PlaceSearchResponse response = placeService.searchPlaces(
			"삼성전기 부산사업장",
			"35.1",
			"128.9",
			null,
			null,
			null,
			"15");

		assertThat(response.places())
			.extracting(PlaceSearchItemResponse::name)
			.containsExactly(
				"삼성스토어 부산삼성전기모바일",
				"삼성전기 부산사업장 후문",
				"삼성전기 부산사업장");
		assertThat(response.places())
			.extracting(PlaceSearchItemResponse::address)
			.allMatch(address -> address.startsWith("부산"));
	}

	@Test
	@DisplayName("관련도순 장소 검색은 부산 bbox를 카카오 요청에 전달한다")
	void searchPlacesConstrainsRelevanceSearchToBusanRect() {
		KakaoPlaceDocument busanPlace = new KakaoPlaceDocument(
			"busan",
			"삼성전기 부산사업장",
			"부산 강서구 녹산산업중로 333",
			"회사",
			"",
			4072,
			new GeoPointResponse(35.1002, 128.9002));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기",
			35.1,
			128.9,
			null,
			BUSAN_SEARCH_RECT,
			1,
			15,
			"accuracy")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(busanPlace), 1, true));
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("busan")))
			.thenReturn(List.of());

		PlaceSearchResponse response = placeService.searchPlaces(
			"삼성전기",
			"35.1",
			"128.9",
			null,
			null,
			"relevance",
			"15");

		assertThat(response.places())
			.extracting(PlaceSearchItemResponse::name)
			.containsExactly("삼성전기 부산사업장");
	}

	@Test
	@DisplayName("짧은 일반 검색어는 카카오 거리 정렬 순서를 유지한다")
	void searchPlacesKeepsDistanceOrderForShortGenericKeyword() {
		KakaoPlaceDocument nearStore = new KakaoPlaceDocument(
			"near",
			"삼성스토어 부산삼성전기모바일",
			"부산 강서구 녹산산업중로 333",
			"서비스",
			"",
			3877,
			new GeoPointResponse(35.1001, 128.9001));
		KakaoPlaceDocument exactButFarther = new KakaoPlaceDocument(
			"exact",
			"삼성전기",
			"부산 사하구 하신중앙로 40",
			"전기",
			"",
			6285,
			new GeoPointResponse(35.1002, 128.9002));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기",
			35.1,
			128.9,
			null,
			1,
			15,
			"distance")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(nearStore, exactButFarther), 2, true));
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("near", "exact")))
			.thenReturn(List.of());

		PlaceSearchResponse response = placeService.searchPlaces(
			"삼성전기",
			"35.1",
			"128.9",
			null,
			null,
			"distance",
			"15");

		assertThat(response.places())
			.extracting(PlaceSearchItemResponse::name)
			.containsExactly("삼성스토어 부산삼성전기모바일", "삼성전기");
	}

	@Test
	@DisplayName("좌표 기반 검색은 현재 페이지에 부산 결과가 없으면 다음 카카오 페이지에서 부산 결과를 보강한다")
	void searchPlacesBackfillsBusanResultsFromNextKakaoPage() {
		KakaoPlaceDocument outOfBusan = new KakaoPlaceDocument(
			"suwon",
			"삼성전기 본사",
			"경기 수원시 영통구 매영로 150",
			"회사",
			"",
			291841,
			new GeoPointResponse(37.2520, 127.0550));
		KakaoPlaceDocument busanPlace = new KakaoPlaceDocument(
			"busan",
			"삼성전기 부산사업장",
			"부산 강서구 녹산산업중로 333",
			"회사",
			"",
			4072,
			new GeoPointResponse(35.1002, 128.9002));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기 부산사업장",
			35.1,
			128.9,
			null,
			1,
			15,
			"distance")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(outOfBusan), 45, false));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기 부산사업장",
			35.1,
			128.9,
			null,
			2,
			15,
			"distance")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(busanPlace), 45, true));
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("busan")))
			.thenReturn(List.of());

		PlaceSearchResponse response = placeService.searchPlaces(
			"삼성전기 부산사업장",
			"35.1",
			"128.9",
			null,
			null,
			"distance",
			"15");

		assertThat(response.places())
			.extracting(PlaceSearchItemResponse::name)
			.containsExactly("삼성전기 부산사업장");
		assertThat(response.hasNext()).isFalse();
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	@DisplayName("장소 검색은 다음 cursor로 카카오 다음 페이지를 조회한다")
	void searchPlacesWithCursor() {
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"부산시민공원",
			null,
			null,
			null,
			BUSAN_SEARCH_RECT,
			1,
			10,
			"accuracy")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(), 30, false));

		PlaceSearchResponse firstResponse = placeService.searchPlaces(
			"부산시민공원",
			null,
			null,
			null,
			null,
			null,
			"10");

		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"부산시민공원",
			null,
			null,
			null,
			BUSAN_SEARCH_RECT,
			2,
			10,
			"accuracy")))
			.thenReturn(new KakaoPlaceSearchResult(List.of(), 30, true));

		PlaceSearchResponse secondResponse = placeService.searchPlaces(
			"부산시민공원",
			null,
			null,
			null,
			firstResponse.nextCursor(),
			null,
			"10");

		assertThat(firstResponse.hasNext()).isTrue();
		assertThat(firstResponse.nextCursor()).isNotBlank();
		assertThat(secondResponse.hasNext()).isFalse();
		assertThat(secondResponse.nextCursor()).isNull();
	}

	@Test
	@DisplayName("좌표 주소 변환은 카카오 주소 결과를 응답으로 매핑한다")
	void reverseGeocode() {
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 부산진구 범전동 200",
				"부산 부산진구 시민공원로 73",
				null,
				"부산",
				"부산진구",
				"범전동")));

		PlaceReverseGeocodeResponse response = placeService.reverseGeocode("35.1686", "129.0576");

		assertThat(response.displayAddress()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.roadAddress()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.address()).isEqualTo("부산 부산진구 범전동 200");
	}

	@Test
	@DisplayName("외부 상세 조회는 providerPlaceId가 내부 장소와 매칭되면 canonical 내부 장소를 반환한다")
	void getPlaceDetailWithInternalProviderMatch() {
		UUID userId = UUID.randomUUID();
		Place place = place(
			10L,
			"부산시민공원",
			PlaceCategory.TOURIST_SPOT,
			"123456789",
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleEntrance);
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.POI,
			"KAKAO",
			"123456789",
			"부산시민공원");
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("123456789"))).thenReturn(List.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(true);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.INTERNAL_PLACE);
		assertThat(response.bookmarkTargetId()).isNotBlank();
		assertThat(response.placeId()).isEqualTo(10L);
		assertThat(response.name()).isEqualTo("부산시민공원");
		assertThat(response.category()).isEqualTo(PlaceCategory.TOURIST_SPOT);
		assertThat(response.providerCategory()).isNull();
		assertThat(response.isBookmarked()).isTrue();
	}

	@Test
	@DisplayName("외부 상세 조회는 providerPlaceId가 내부 장소와 매칭되면 nameHint 없이도 canonical 내부 장소를 반환한다")
	void getPlaceDetailWithInternalProviderMatchWithoutNameHint() {
		UUID userId = UUID.randomUUID();
		Place place = place(
			10L,
			"부산시민공원",
			PlaceCategory.TOURIST_SPOT,
			"123456789",
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleEntrance);
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.POI,
			"KAKAO",
			"123456789",
			null);
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("123456789"))).thenReturn(List.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.INTERNAL_PLACE);
		assertThat(response.placeId()).isEqualTo(10L);
		assertThat(response.name()).isEqualTo("부산시민공원");
	}

	@Test
	@DisplayName("외부 상세 조회는 POI 클릭을 카카오 keyword search로 보강한다")
	void getPlaceDetailForExternalPoi() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.POI,
			"KAKAO",
			null,
			"부산시민공원");
		KakaoPlaceDocument kakaoPlace = new KakaoPlaceDocument(
			"123456789",
			"부산시민공원",
			"부산광역시 부산진구 시민공원로 73",
			"여행 > 관광,명소 > 공원",
			"051-123-4567",
			12,
			new GeoPointResponse(35.1686, 129.0576));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"부산시민공원",
			35.1686,
			129.0576,
			300,
			1,
			5)))
			.thenReturn(new KakaoPlaceSearchResult(List.of(kakaoPlace), 1, true));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.bookmarkTargetId()).isNotBlank();
		assertThat(response.placeId()).isNull();
		assertThat(response.provider()).isEqualTo("KAKAO");
		assertThat(response.providerPlaceId()).isEqualTo("123456789");
		assertThat(response.providerCategory()).isEqualTo("여행 > 관광,명소 > 공원");
		assertThat(response.phone()).isEqualTo("051-123-4567");
		assertThat(response.category()).isNull();
		assertThat(response.address()).isEqualTo("부산광역시 부산진구 시민공원로 73");
		assertThat(response.isBookmarked()).isFalse();
	}

	@Test
	@DisplayName("외부 상세 조회는 ADDRESS 클릭을 reverse geocode로 보강한다")
	void getPlaceDetailForAddress() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.ADDRESS,
			"KAKAO",
			null,
			null);
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 부산진구 범전동 200",
				"부산 부산진구 시민공원로 73",
				null,
				"부산",
				"부산진구",
				"범전동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_ADDRESS);
		assertThat(response.bookmarkTargetId()).isNotBlank();
		assertThat(response.placeId()).isNull();
		assertThat(response.name()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.address()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.providerCategory()).isNull();
		assertThat(response.isBookmarked()).isFalse();
	}

	@Test
	@DisplayName("외부 상세 조회는 POI 후보를 찾지 못하면 주소 상세로 fallback한다")
	void getPlaceDetailPoiNotFoundFallsBackToAddress() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.POI,
			"KAKAO",
			null,
			"없는장소");
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"없는장소",
			35.1686,
			129.0576,
			300,
			1,
			5)))
			.thenReturn(new KakaoPlaceSearchResult(List.of(), 0, true));
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 부산진구 범전동 200",
				"부산 부산진구 시민공원로 73",
				null,
				"부산",
				"부산진구",
				"범전동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_ADDRESS);
		assertThat(response.providerPlaceId()).isNull();
		assertThat(response.name()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.address()).isEqualTo("부산 부산진구 시민공원로 73");
	}

	@Test
	@DisplayName("외부 상세 조회는 POI 이름 검색 실패 시 좌표의 건물명으로 장소명을 복구한다")
	void getPlaceDetailPoiNotFoundUsesBuildingNameFallback() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.061481,
			128.9793128,
			PlaceClickType.POI,
			"KAKAO",
			null,
			"다대포현대 아파트 2181세대");
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"다대포현대 아파트 2181세대",
			35.061481,
			128.9793128,
			300,
			1,
			5)))
			.thenReturn(new KakaoPlaceSearchResult(List.of(), 0, true));
		when(kakaoLocalClient.reverseGeocode(35.061481, 128.9793128))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 사하구 다대동 120-1",
				"부산광역시 사하구 다대로 473",
				"다대포현대아파트",
				"부산",
				"사하구",
				"다대동")));
		KakaoPlaceDocument apartment = new KakaoPlaceDocument(
			"11201822",
			"다대포현대아파트",
			"부산 사하구 다대로 473",
			"부동산 > 주거시설 > 아파트",
			"051-987-6543",
			117,
			new GeoPointResponse(35.061110251800585, 128.97811023326653));
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"다대포현대아파트",
			35.061481,
			128.9793128,
			300,
			1,
			5)))
			.thenReturn(new KakaoPlaceSearchResult(List.of(apartment), 1, true));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.providerPlaceId()).isEqualTo("11201822");
		assertThat(response.name()).isEqualTo("다대포현대아파트");
		assertThat(response.providerCategory()).isEqualTo("부동산 > 주거시설 > 아파트");
		assertThat(response.phone()).isEqualTo("051-987-6543");
	}

	@Test
	@DisplayName("외부 상세 조회는 POI 이름 힌트가 없으면 주소 상세로 fallback한다")
	void getPlaceDetailPoiWithoutNameHintFallsBackToAddress() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.POI,
			"KAKAO",
			null,
			null);
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 부산진구 범전동 200",
				"부산 부산진구 시민공원로 73",
				null,
				"부산",
				"부산진구",
				"범전동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_ADDRESS);
		assertThat(response.name()).isEqualTo("부산 부산진구 시민공원로 73");
	}

	@Test
	@DisplayName("지도 클릭 버스정류장은 일반 POI 검색을 생략하되 정류장 POI로 반환한다")
	void getPlaceDetailBusStopKeepsClickedPoiWithoutKeywordSearch() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.061481,
			128.9793128,
			PlaceClickType.POI,
			"KAKAO",
			"BS97494",
			"다대현대아파트");
		when(kakaoLocalClient.reverseGeocode(35.061481, 128.9793128))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 사하구 다대동 120-1",
				"부산광역시 사하구 다대로 473",
				null,
				"부산",
				"사하구",
				"다대동")));
		when(busStopMasterService.findNearest(35.061481, 128.9793128, 150.0))
			.thenReturn(Optional.empty());
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.providerPlaceId()).isEqualTo("BS97494");
		assertThat(response.name()).isEqualTo("다대현대아파트");
		assertThat(response.providerCategory()).isEqualTo("교통,수송 > 버스정류장");
		assertThat(response.address()).isEqualTo("부산광역시 사하구 다대로 473");
		verify(kakaoLocalClient, never()).searchKeyword(any(KakaoPlaceSearchRequest.class));
	}

	@Test
	@DisplayName("지도 클릭 버스정류장은 일반명 힌트면 BIMS 정류장 마스터로 이름을 보강한다")
	void getPlaceDetailBusStopResolvesGenericNameWithBimsMaster() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.061481,
			128.9793128,
			PlaceClickType.POI,
			"KAKAO",
			"BS97494",
			"버스정류장");
		when(busStopMasterService.findNearest(35.061481, 128.9793128, 150.0))
			.thenReturn(Optional.of(new BusStopMasterService.BusStopMatch(
				"178700302",
				"다대현대아파트",
				"10175",
				"일반",
				12.0)));
		when(busanBimsClient.findArrivalsByStopId("178700302"))
			.thenReturn(List.of(
				new BusanBimsArrival("178700302", "5200177000", "100", 6, true),
				new BusanBimsArrival("178700302", "5200178000", "200", 12, false)));
		when(kakaoLocalClient.reverseGeocode(35.061481, 128.9793128))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 사하구 다대동 120-1",
				"부산광역시 사하구 다대로 473",
				null,
				"부산",
				"사하구",
				"다대동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.providerPlaceId()).isEqualTo("BS97494");
		assertThat(response.name()).isEqualTo("다대현대아파트");
		assertThat(response.providerCategory()).isEqualTo("교통,수송 > 버스정류장");
		assertThat(response.address()).isEqualTo("부산광역시 사하구 다대로 473");
		assertThat(response.transitArrivals())
			.extracting(arrival -> arrival.routeName(), arrival -> arrival.remainingMinute(),
				arrival -> arrival.isLowFloor())
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("100", 6, true),
				org.assertj.core.groups.Tuple.tuple("200", 12, false));
		verify(kakaoLocalClient, never()).searchKeyword(any(KakaoPlaceSearchRequest.class));
	}

	@Test
	@DisplayName("지도 클릭 버스정류장은 BIMS 정류장 마스터 매칭이 없으면 건물명으로 이름을 보강한다")
	void getPlaceDetailBusStopFallsBackToBuildingNameWhenBimsMasterHasNoMatch() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.061481,
			128.9793128,
			PlaceClickType.POI,
			"KAKAO",
			"BS97494",
			"버스정류장");
		when(busStopMasterService.findNearest(35.061481, 128.9793128, 150.0))
			.thenReturn(Optional.empty());
		when(kakaoLocalClient.reverseGeocode(35.061481, 128.9793128))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 사하구 다대동 120-1",
				"부산광역시 사하구 다대로 473",
				"다대포현대아파트",
				"부산",
				"사하구",
				"다대동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.providerPlaceId()).isEqualTo("BS97494");
		assertThat(response.name()).isEqualTo("다대포현대아파트");
		assertThat(response.providerCategory()).isEqualTo("교통,수송 > 버스정류장");
		assertThat(response.address()).isEqualTo("부산광역시 사하구 다대로 473");
		assertThat(response.transitArrivals()).isEmpty();
		verify(kakaoLocalClient, never()).searchKeyword(any(KakaoPlaceSearchRequest.class));
	}

	@Test
	@DisplayName("지도 클릭 지하철역은 subway_stations 매칭 결과와 접근성 정보를 반환한다")
	void getPlaceDetailSubwayUsesSubwayStationMaster() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.162166,
			128.984611,
			PlaceClickType.POI,
			"KAKAO",
			null,
			"사상역 1번출구");
		SubwayStation station = SubwayStation.create(
			"70227",
			"사상",
			"부산 2호선",
			geoPointConverter.toPoint(new GeoPointRequest(35.162166, 128.984611)));
		when(subwayStationMasterService.findPlaceDetail("사상역 1번출구", 35.162166, 128.984611, 300.0))
			.thenReturn(Optional.of(new SubwayStationMasterService.SubwayStationPlaceDetail(
				station,
				List.of(station),
				"GROUP:70227-70901",
				List.of("부산 2호선", "부산-김해경전철"),
				List.of(
					new SubwayStationMasterService.SubwayAccessibilityFeature(
						AccessibilityFeatureType.accessibleToilet,
						true),
					new SubwayStationMasterService.SubwayAccessibilityFeature(
						AccessibilityFeatureType.elevator,
						true)))));
		when(subwayTimetableRepository.findNextDepartures(anyString(), any(SubwayServiceDayType.class), anyInt(),
			anyInt(), any()))
			.thenReturn(List.of());
		when(subwayTimetableRepository.findFirstDepartures(anyString(), any(SubwayServiceDayType.class), anyInt(),
			any()))
			.thenReturn(List.of());
		when(subwayTimetableRepository.findNextDepartures(eq("70227"), any(SubwayServiceDayType.class), eq(1),
			anyInt(), any()))
			.thenReturn(List.of(SubwayTimetable.create(
				"70227",
				SubwayServiceDayType.WEEKDAY,
				1,
				"23:59",
				86399,
				"장산")));
		when(kakaoLocalClient.reverseGeocode(35.162166, 128.984611))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 사상구 괘법동 529-1",
				"부산광역시 사상구 사상로 지하 203",
				null,
				"부산",
				"사상구",
				"괘법동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.provider()).isEqualTo("SUBWAY_STATION");
		assertThat(response.providerPlaceId()).isEqualTo("GROUP:70227-70901");
		assertThat(response.name()).isEqualTo("사상역");
		assertThat(response.providerCategory()).isEqualTo("교통,수송 > 지하철,전철 > 부산 2호선 · 부산-김해경전철");
		assertThat(response.address()).isEqualTo("부산광역시 사상구 사상로 지하 203");
		assertThat(response.accessibilityFeatures())
			.extracting(feature -> feature.featureType())
			.containsExactly(AccessibilityFeatureType.accessibleToilet, AccessibilityFeatureType.elevator);
		assertThat(response.transitArrivals())
			.extracting(arrival -> arrival.transitType(), arrival -> arrival.routeName(),
				arrival -> arrival.direction())
			.containsExactly(org.assertj.core.groups.Tuple.tuple("SUBWAY", "부산 2호선", "장산행"));
		verify(kakaoLocalClient, never()).searchKeyword(any(KakaoPlaceSearchRequest.class));
		verify(kakaoLocalClient, never()).searchCategory(anyString(), any(), any(), any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("지도 클릭 지하철역이 subway_stations에 매칭되지 않으면 주소 상세로 fallback한다")
	void getPlaceDetailSubwayFallsBackToExternalPoiWhenMasterHasNoMatch() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.162166,
			128.984611,
			PlaceClickType.POI,
			"KAKAO",
			null,
			"사상역 1번출구");
		when(subwayStationMasterService.findPlaceDetail("사상역 1번출구", 35.162166, 128.984611, 300.0))
			.thenReturn(Optional.empty());
		when(kakaoLocalClient.searchKeyword(any(KakaoPlaceSearchRequest.class)))
			.thenReturn(new KakaoPlaceSearchResult(List.of(
				new KakaoPlaceDocument(
					"SUBWAY-POI",
					"사상역",
					"부산광역시 사상구 사상로 지하 203",
					"교통,수송 > 지하철,전철 > 지하철역",
					null,
					null,
					new GeoPointResponse(35.162166, 128.984611))),
				1, false));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.providerPlaceId()).isEqualTo("SUBWAY-POI");
		assertThat(response.name()).isEqualTo("사상역");
		assertThat(response.address()).isEqualTo("부산광역시 사상구 사상로 지하 203");
		verify(kakaoLocalClient, never()).searchCategory(anyString(), any(), any(), any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("주소 클릭도 역 대표 좌표와 가까우면 지하철역 상세로 보정한다")
	void getPlaceDetailAddressNearSubwayStationUsesSubwayMaster() {
		UUID userId = UUID.randomUUID();
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.162166,
			128.984611,
			PlaceClickType.ADDRESS,
			null,
			null,
			null);
		SubwayStation station = SubwayStation.create(
			"70227",
			"사상",
			"부산 2호선",
			geoPointConverter.toPoint(new GeoPointRequest(35.162166, 128.984611)));
		when(subwayStationMasterService.findNearestPlaceDetail(35.162166, 128.984611, 60.0))
			.thenReturn(Optional.of(new SubwayStationMasterService.SubwayStationPlaceDetail(
				station,
				List.of(station),
				"GROUP:70227",
				List.of("부산 2호선"),
				List.of())));
		when(kakaoLocalClient.reverseGeocode(35.162166, 128.984611))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 사상구 괘법동 529-1",
				"부산광역시 사상구 사상로 지하 203",
				null,
				"부산",
				"사상구",
				"괘법동")));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);

		PlaceClickDetailResponse response = placeService.getPlaceDetail(userId, request);

		assertThat(response.detailType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.provider()).isEqualTo("SUBWAY_STATION");
		assertThat(response.name()).isEqualTo("사상역");
		assertThat(response.providerCategory()).isEqualTo("교통,수송 > 지하철,전철 > 부산 2호선");
	}

	@Test
	@DisplayName("좌표 주소 변환 결과가 없으면 장소 주소 없음 에러를 반환한다")
	void reverseGeocodeNotFound() {
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> placeService.reverseGeocode("35.1686", "129.0576"))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.PLACE_ADDRESS_NOT_FOUND);
	}

	@Test
	@DisplayName("좌표 주소 변환 외부 API 실패는 원인 예외를 유지한다")
	void preserveReverseGeocodeExternalApiFailureCause() {
		RestClientException cause = new RestClientException("timeout");
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576)).thenThrow(cause);

		assertThatThrownBy(() -> placeService.reverseGeocode("35.1686", "129.0576"))
			.isInstanceOf(PlaceException.class)
			.hasCause(cause)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.PLACE_REVERSE_GEOCODE_EXTERNAL_API_FAILED);
	}

	@Test
	@DisplayName("좌표 주소 변환 좌표가 범위를 벗어나면 도메인 에러를 반환한다")
	void rejectInvalidReverseGeocodeCoordinateRange() {
		assertThatThrownBy(() -> placeService.reverseGeocode("91", "129.0576"))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.INVALID_PLACE_REQUEST);

		verify(kakaoLocalClient, never()).reverseGeocode(
			org.mockito.ArgumentMatchers.anyDouble(),
			org.mockito.ArgumentMatchers.anyDouble());
	}

	@Test
	@DisplayName("주변 장소 조회는 현재 사용자 북마크 여부와 필터를 반영한다")
	void getPlaces() {
		UUID userId = UUID.randomUUID();
		Place matchedPlace = place(
			10L,
			"부산시민공원",
			PlaceCategory.TOURIST_SPOT,
			null,
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleToilet);
		when(placeRepository.findPlaceMarkerIds(
			35.1686,
			129.0576,
			500,
			Set.of("TOURIST_SPOT"),
			false,
			Set.of("accessibleToilet"),
			false,
			200))
			.thenReturn(List.of(10L));
		when(placeRepository.findAllByPlaceIdIn(List.of(10L))).thenReturn(List.of(matchedPlace));
		when(bookmarkRepository.findBookmarkedPlaceIds(userId, List.of(10L))).thenReturn(Set.of(10L));

		PlaceListResponse response = placeService.getPlaces(
			userId,
			"35.1686",
			"129.0576",
			"500",
			"TOURIST_SPOT",
			"accessibleToilet");

		assertThat(response.places()).hasSize(1);
		assertThat(response.places().get(0).placeId()).isEqualTo(10L);
		assertThat(response.places().get(0).isBookmarked()).isTrue();
		assertThat(response.places().get(0).markerKind()).isEqualTo(PlaceMarkerKind.DEFAULT);
	}

	@Test
	@DisplayName("주변 장소 조회는 ETC 장소의 providerPlaceId가 BS로 시작하면 버스정류장 마커로 반환한다")
	void getPlacesMarksBusStopByProviderPlaceId() {
		UUID userId = UUID.randomUUID();
		Place matchedPlace = place(
			10L,
			"시청 버스정류장",
			PlaceCategory.ETC,
			"BS12345",
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleEntrance);
		when(placeRepository.findPlaceMarkerIds(
			35.1686,
			129.0576,
			500,
			Set.of("ETC"),
			false,
			Set.of("__EMPTY_FILTER__"),
			true,
			200))
			.thenReturn(List.of(10L));
		when(placeRepository.findAllByPlaceIdIn(List.of(10L))).thenReturn(List.of(matchedPlace));
		when(bookmarkRepository.findBookmarkedPlaceIds(userId, List.of(10L))).thenReturn(Set.of());

		PlaceListResponse response = placeService.getPlaces(userId, "35.1686", "129.0576", "500", "ETC", null);

		assertThat(response.places()).hasSize(1);
		assertThat(response.places().get(0).markerKind()).isEqualTo(PlaceMarkerKind.BUS_STOP);
	}

	@Test
	@DisplayName("주변 장소 조회는 ETC 장소가 지하철역 마스터 근처면 지하철역 마커로 반환한다")
	void getPlacesMarksSubwayStationByMasterTable() {
		UUID userId = UUID.randomUUID();
		Place matchedPlace = place(
			10L,
			"서면역",
			PlaceCategory.ETC,
			null,
			35.1686,
			129.0576,
			AccessibilityFeatureType.elevator);
		SubwayStation station = SubwayStation.create(
			"200",
			"서면",
			"부산 1호선",
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)));
		when(placeRepository.findPlaceMarkerIds(
			35.1686,
			129.0576,
			500,
			Set.of("ETC"),
			false,
			Set.of("__EMPTY_FILTER__"),
			true,
			200))
			.thenReturn(List.of(10L));
		when(placeRepository.findAllByPlaceIdIn(List.of(10L))).thenReturn(List.of(matchedPlace));
		when(bookmarkRepository.findBookmarkedPlaceIds(userId, List.of(10L))).thenReturn(Set.of());
		when(subwayStationMasterService.findNearestPlaceDetail(35.1686, 129.0576, 30.0))
			.thenReturn(Optional.of(new SubwayStationMasterService.SubwayStationPlaceDetail(
				station,
				List.of(station),
				"GROUP:200",
				List.of("부산 1호선"),
				List.of())));

		PlaceListResponse response = placeService.getPlaces(userId, "35.1686", "129.0576", "500", "ETC", null);

		assertThat(response.places()).hasSize(1);
		assertThat(response.places().get(0).markerKind()).isEqualTo(PlaceMarkerKind.SUBWAY_STATION);
	}

	@Test
	@DisplayName("주변 장소 조회는 반경이 없으면 기본 반경과 최대 개수로 조회한다")
	void getPlacesWithDefaultRadiusAndLimit() {
		when(placeRepository.findPlaceMarkerIds(
			35.1686,
			129.0576,
			1000,
			Set.of("__EMPTY_FILTER__"),
			true,
			Set.of("__EMPTY_FILTER__"),
			true,
			200))
			.thenReturn(List.of());

		PlaceListResponse response = placeService.getPlaces(UUID.randomUUID(), "35.1686", "129.0576", null, null, null);

		assertThat(response.places()).isEmpty();
		verify(placeRepository, never()).findAllByPlaceIdIn(anyCollection());
		verify(bookmarkRepository, never()).findBookmarkedPlaceIds(org.mockito.ArgumentMatchers.any(), anyCollection());
	}

	@Test
	@DisplayName("시설 상세 조회는 장소와 현재 사용자 북마크 여부를 반환한다")
	void getPlace() {
		UUID userId = UUID.randomUUID();
		Place place = place(
			10L,
			"부산시민공원",
			PlaceCategory.TOURIST_SPOT,
			"123456789",
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleEntrance);
		when(placeRepository.findWithAccessibilityFeaturesByPlaceId(10L)).thenReturn(Optional.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndPlace_PlaceId(userId, 10L)).thenReturn(true);

		PlaceDetailResponse response = placeService.getPlace(userId, "10");

		assertThat(response.placeId()).isEqualTo(10L);
		assertThat(response.providerPlaceId()).isEqualTo("123456789");
		assertThat(response.isBookmarked()).isTrue();
	}

	@Test
	@DisplayName("검색어가 없으면 장소 검색 도메인 에러를 반환한다")
	void rejectBlankKeyword() {
		assertThatThrownBy(() -> placeService.searchPlaces(" ", null, null, null, null, null, null))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.PLACE_KEYWORD_REQUIRED);

		verify(kakaoLocalClient, never()).searchKeyword(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("카카오 API 실패는 원인 예외를 유지한다")
	void preserveExternalApiFailureCause() {
		RestClientException cause = new RestClientException("timeout");
		when(kakaoLocalClient.searchKeyword(new KakaoPlaceSearchRequest(
			"부산시민공원",
			null,
			null,
			null,
			BUSAN_SEARCH_RECT,
			1,
			10,
			"accuracy")))
			.thenThrow(cause);

		assertThatThrownBy(() -> placeService.searchPlaces("부산시민공원", null, null, null, null, null, null))
			.isInstanceOf(PlaceException.class)
			.hasCause(cause)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.PLACE_SEARCH_EXTERNAL_API_FAILED);
	}

	@Test
	@DisplayName("주변 장소 조회 반경이 최대 반경을 넘으면 도메인 에러를 반환한다")
	void rejectTooLargePlaceRadius() {
		assertThatThrownBy(() -> placeService.getPlaces(UUID.randomUUID(), "35.1686", "129.0576", "3001", null, null))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.INVALID_PLACE_REQUEST);

		verify(placeRepository, never()).findPlaceMarkerIds(
			org.mockito.ArgumentMatchers.anyDouble(),
			org.mockito.ArgumentMatchers.anyDouble(),
			org.mockito.ArgumentMatchers.anyInt(),
			anyCollection(),
			org.mockito.ArgumentMatchers.anyBoolean(),
			anyCollection(),
			org.mockito.ArgumentMatchers.anyBoolean(),
			org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("잘못된 placeId는 장소 조회 도메인 에러를 반환한다")
	void rejectInvalidPlaceId() {
		assertThatThrownBy(() -> placeService.getPlace(UUID.randomUUID(), "abc"))
			.isInstanceOf(PlaceException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceErrorCode.INVALID_PLACE_REQUEST);

		verify(placeRepository, never()).findWithAccessibilityFeaturesByPlaceId(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("주변 장소 조회 결과가 없으면 북마크 조회를 생략한다")
	void skipBookmarkLookupWhenEmpty() {
		when(placeRepository.findPlaceMarkerIds(
			35.1686,
			129.0576,
			1000,
			Set.of("__EMPTY_FILTER__"),
			true,
			Set.of("__EMPTY_FILTER__"),
			true,
			200))
			.thenReturn(List.of());

		PlaceListResponse response = placeService.getPlaces(UUID.randomUUID(), "35.1686", "129.0576", null, null, null);

		assertThat(response.places()).isEmpty();
		verify(bookmarkRepository, never()).findBookmarkedPlaceIds(org.mockito.ArgumentMatchers.any(), anyCollection());
	}

	private Place place(
		Long placeId,
		String name,
		PlaceCategory category,
		String providerPlaceId,
		double lat,
		double lng,
		AccessibilityFeatureType featureType) {
		Place place = instantiate(Place.class);
		ReflectionTestUtils.setField(place, "placeId", placeId);
		ReflectionTestUtils.setField(place, "name", name);
		ReflectionTestUtils.setField(place, "category", category);
		ReflectionTestUtils.setField(place, "address", "부산광역시");
		ReflectionTestUtils.setField(place, "point", geoPointConverter.toPoint(new GeoPointRequest(lat, lng)));
		ReflectionTestUtils.setField(place, "providerPlaceId", providerPlaceId);
		ReflectionTestUtils.setField(place, "accessibilityFeatures", List.of(feature(place, featureType)));
		return place;
	}

	private PlaceAccessibilityFeature feature(Place place, AccessibilityFeatureType featureType) {
		PlaceAccessibilityFeature feature = instantiate(PlaceAccessibilityFeature.class);
		ReflectionTestUtils.setField(feature, "id", 1);
		ReflectionTestUtils.setField(feature, "place", place);
		ReflectionTestUtils.setField(feature, "featureType", featureType);
		ReflectionTestUtils.setField(feature, "isAvailable", true);
		return feature;
	}

	private <T> T instantiate(Class<T> type) {
		try {
			Constructor<T> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
