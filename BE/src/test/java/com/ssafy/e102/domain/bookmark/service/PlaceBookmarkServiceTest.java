package com.ssafy.e102.domain.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.bookmark.dto.request.CreatePlaceBookmarkRequest;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkCreateResponse;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkListResponse;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkErrorCode;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkException;
import com.ssafy.e102.domain.place.entity.Bookmark;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;
import com.ssafy.e102.domain.place.repository.BookmarkRepository;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
import com.ssafy.e102.domain.place.support.BookmarkTargetIdFactory;
import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class PlaceBookmarkServiceTest {

	private static final Sort NEWEST_FIRST = Sort.by(
		Sort.Order.desc("bookmarkId"));

	@Mock
	private BookmarkRepository bookmarkRepository;

	@Mock
	private PlaceRepository placeRepository;

	@Mock
	private UserRepository userRepository;

	private PlaceBookmarkService placeBookmarkService;
	private GeoPointConverter geoPointConverter;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		geoPointConverter = new GeoPointConverter();
		placeBookmarkService = new PlaceBookmarkService(bookmarkRepository, placeRepository, userRepository,
			geoPointConverter);
	}

	@Test
	@DisplayName("장소 북마크 목록은 최신 저장순 cursor 기반으로 내부 canonical 데이터와 외부 snapshot을 함께 반환한다")
	void getBookmarks() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Place internalPlace = place(
			10L,
			"부산시민공원",
			PlaceCategory.TOURIST_SPOT,
			"123456789",
			35.1686,
			129.0576,
			AccessibilityFeatureType.accessibleToilet,
			AccessibilityFeatureType.elevator);
		Bookmark internalBookmark = internalBookmark(1, user, internalPlace, null);
		Bookmark externalBookmark = externalPoiBookmark(2, user, "tgt_external_1");
		PageRequest pageRequest = PageRequest.of(0, 10, NEWEST_FIRST);
		when(bookmarkRepository.findAllByUser_UserId(userId, pageRequest))
			.thenReturn(new SliceImpl<>(List.of(externalBookmark, internalBookmark), pageRequest, false));

		PlaceBookmarkListResponse response = placeBookmarkService.getBookmarks(userId, null, 10);

		assertThat(response.content()).hasSize(2);
		assertThat(response.content().get(0).targetType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.content().get(0).providerCategory()).isEqualTo("여행 > 관광,명소 > 공원");
		assertThat(response.content().get(1).targetType()).isEqualTo(PlaceDetailType.INTERNAL_PLACE);
		assertThat(response.content().get(1).placeId()).isEqualTo(10L);
		assertThat(response.content().get(1).bookmarkTargetId())
			.isEqualTo(BookmarkTargetIdFactory.fromInternalPlace(10L));
		assertThat(response.content().get(1).accessibilityFeatures()).extracting("featureType")
			.containsExactly(AccessibilityFeatureType.accessibleToilet, AccessibilityFeatureType.elevator);
		assertThat(response.size()).isEqualTo(10);
		assertThat(response.nextCursor()).isNull();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	@DisplayName("장소 북마크 목록은 마지막 bookmarkId 이후 cursor로 조회한다")
	void getBookmarksWithCursor() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Bookmark bookmark = externalPoiBookmark(3, user, "tgt_external_3");
		PageRequest pageRequest = PageRequest.of(0, 2, NEWEST_FIRST);
		when(bookmarkRepository.findAllByUser_UserIdAndBookmarkIdLessThan(userId, 10, pageRequest))
			.thenReturn(new SliceImpl<>(List.of(bookmark), pageRequest, true));

		PlaceBookmarkListResponse response = placeBookmarkService.getBookmarks(userId, 10L, 2);

		assertThat(response.content()).hasSize(1);
		assertThat(response.nextCursor()).isEqualTo(3L);
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	@DisplayName("장소 북마크 저장은 외부 POI snapshot을 생성한다")
	void createExternalPoiBookmark() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		CreatePlaceBookmarkRequest request = new CreatePlaceBookmarkRequest(
			null,
			"KAKAO",
			"123456789",
			"부산시민공원",
			"여행 > 관광,명소 > 공원",
			"부산광역시 부산진구 시민공원로 73",
			new GeoPointRequest(35.1686, 129.0576));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);
		when(bookmarkRepository.save(any(Bookmark.class))).thenAnswer(invocation -> {
			Bookmark bookmark = invocation.getArgument(0);
			ReflectionTestUtils.setField(bookmark, "bookmarkId", 1);
			return bookmark;
		});

		PlaceBookmarkCreateResponse response = placeBookmarkService.createBookmark(userId, request);

		assertThat(response.bookmarkId()).isEqualTo(1L);
		assertThat(response.targetType()).isEqualTo(PlaceDetailType.EXTERNAL_POI);
		assertThat(response.placeId()).isNull();
		assertThat(response.bookmarkTargetId()).isEqualTo(
			BookmarkTargetIdFactory.fromExternalPoi("KAKAO", "123456789", "부산시민공원", 35.1686, 129.0576));
		verify(bookmarkRepository).save(any(Bookmark.class));
	}

	@Test
	@DisplayName("장소 북마크 저장은 providerPlaceId가 내부 장소와 매칭되면 내부 canonical link로 저장한다")
	void createBookmarkWithInternalMatch() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Place place = place(10L, "부산시민공원", PlaceCategory.TOURIST_SPOT, "123456789", 35.1686, 129.0576);
		CreatePlaceBookmarkRequest request = new CreatePlaceBookmarkRequest(
			null,
			"KAKAO",
			"123456789",
			"부산시민공원",
			"여행 > 관광,명소 > 공원",
			"부산광역시 부산진구 시민공원로 73",
			new GeoPointRequest(35.1686, 129.0576));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(placeRepository.findAllByProviderPlaceIdIn(List.of("123456789"))).thenReturn(List.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(eq(userId), anyString())).thenReturn(false);
		when(bookmarkRepository.save(any(Bookmark.class))).thenAnswer(invocation -> {
			Bookmark bookmark = invocation.getArgument(0);
			ReflectionTestUtils.setField(bookmark, "bookmarkId", 2);
			return bookmark;
		});

		PlaceBookmarkCreateResponse response = placeBookmarkService.createBookmark(userId, request);

		assertThat(response.bookmarkId()).isEqualTo(2L);
		assertThat(response.targetType()).isEqualTo(PlaceDetailType.INTERNAL_PLACE);
		assertThat(response.placeId()).isEqualTo(10L);
		assertThat(response.bookmarkTargetId()).isEqualTo(BookmarkTargetIdFactory.fromInternalPlace(10L));
	}

	@Test
	@DisplayName("장소 북마크 저장은 placeId가 있으면 내부 장소 북마크로 저장한다")
	void createInternalBookmarkByPlaceId() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Place place = place(10L, "부산시민공원", PlaceCategory.TOURIST_SPOT, "123456789", 35.1686, 129.0576);
		CreatePlaceBookmarkRequest request = new CreatePlaceBookmarkRequest(
			10L,
			"KAKAO",
			"123456789",
			"부산시민공원",
			"여행 > 관광,명소 > 공원",
			"부산광역시 부산진구 시민공원로 73",
			new GeoPointRequest(35.1686, 129.0576));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndPlace_PlaceId(userId, 10L)).thenReturn(false);
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId,
			BookmarkTargetIdFactory.fromInternalPlace(10L)))
			.thenReturn(false);
		when(bookmarkRepository.save(any(Bookmark.class))).thenAnswer(invocation -> {
			Bookmark bookmark = invocation.getArgument(0);
			ReflectionTestUtils.setField(bookmark, "bookmarkId", 7);
			return bookmark;
		});

		PlaceBookmarkCreateResponse response = placeBookmarkService.createBookmark(userId, request);

		assertThat(response.bookmarkId()).isEqualTo(7L);
		assertThat(response.targetType()).isEqualTo(PlaceDetailType.INTERNAL_PLACE);
		assertThat(response.placeId()).isEqualTo(10L);
	}

	@Test
	@DisplayName("장소 북마크 저장 중 DB unique 제약이 발생하면 중복 북마크 에러를 반환한다")
	void mapDuplicateConstraintViolationToBookmarkAlreadyExists() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Place place = place(10L, "부산시민공원", PlaceCategory.TOURIST_SPOT, "123456789", 35.1686, 129.0576);
		CreatePlaceBookmarkRequest request = new CreatePlaceBookmarkRequest(
			10L,
			null,
			null,
			null,
			null,
			null,
			null);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndPlace_PlaceId(userId, 10L)).thenReturn(false);
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId,
			BookmarkTargetIdFactory.fromInternalPlace(10L)))
			.thenReturn(false);
		when(bookmarkRepository.save(any(Bookmark.class)))
			.thenThrow(new DataIntegrityViolationException("uk_bookmarks_user_target"));

		assertThatThrownBy(() -> placeBookmarkService.createBookmark(userId, request))
			.isInstanceOf(PlaceBookmarkException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceBookmarkErrorCode.PLACE_BOOKMARK_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("이미 저장한 장소를 다시 저장하면 거부한다")
	void rejectDuplicateBookmark() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Place place = place(10L, "부산시민공원", PlaceCategory.TOURIST_SPOT, "123456789", 35.1686, 129.0576);
		CreatePlaceBookmarkRequest request = new CreatePlaceBookmarkRequest(
			10L,
			null,
			null,
			null,
			null,
			null,
			null);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
		when(bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId,
			BookmarkTargetIdFactory.fromInternalPlace(10L)))
			.thenReturn(true);

		assertThatThrownBy(() -> placeBookmarkService.createBookmark(userId, request))
			.isInstanceOf(PlaceBookmarkException.class)
			.extracting("errorCode")
			.isEqualTo(PlaceBookmarkErrorCode.PLACE_BOOKMARK_ALREADY_EXISTS);

		verify(bookmarkRepository, never()).save(any(Bookmark.class));
	}

	@Test
	@DisplayName("공통 북마크 해제는 bookmarkTargetId로 삭제한다")
	void deleteBookmarkByTarget() {
		UUID userId = UUID.randomUUID();
		Bookmark bookmark = externalPoiBookmark(3, user(userId), "tgt_external_1");
		when(bookmarkRepository.findByUser_UserIdAndBookmarkTargetId(userId, "tgt_external_1"))
			.thenReturn(Optional.of(bookmark));

		placeBookmarkService.deleteBookmarkByTarget(userId, "tgt_external_1");

		verify(bookmarkRepository).delete(bookmark);
	}

	@Test
	@DisplayName("내부 장소 북마크 해제는 placeId로 삭제한다")
	void deleteBookmarkByPlaceId() {
		UUID userId = UUID.randomUUID();
		Place place = place(10L, "부산시민공원", PlaceCategory.TOURIST_SPOT, "123456789", 35.1686, 129.0576);
		Bookmark bookmark = internalBookmark(4, user(userId), place, BookmarkTargetIdFactory.fromInternalPlace(10L));
		when(bookmarkRepository.findByUser_UserIdAndPlace_PlaceId(userId, 10L))
			.thenReturn(Optional.of(bookmark));

		placeBookmarkService.deleteBookmarkByPlaceId(userId, 10L);

		verify(bookmarkRepository).delete(bookmark);
	}

	private Bookmark internalBookmark(Integer bookmarkId, User user, Place place, String bookmarkTargetId) {
		Bookmark bookmark = instantiate(Bookmark.class);
		ReflectionTestUtils.setField(bookmark, "bookmarkId", bookmarkId);
		ReflectionTestUtils.setField(bookmark, "user", user);
		ReflectionTestUtils.setField(bookmark, "bookmarkTargetId", bookmarkTargetId);
		ReflectionTestUtils.setField(bookmark, "place", place);
		return bookmark;
	}

	private Bookmark externalPoiBookmark(Integer bookmarkId, User user, String bookmarkTargetId) {
		Bookmark bookmark = instantiate(Bookmark.class);
		ReflectionTestUtils.setField(bookmark, "bookmarkId", bookmarkId);
		ReflectionTestUtils.setField(bookmark, "user", user);
		ReflectionTestUtils.setField(bookmark, "bookmarkTargetId", bookmarkTargetId);
		ReflectionTestUtils.setField(bookmark, "provider", "KAKAO");
		ReflectionTestUtils.setField(bookmark, "providerPlaceId", "123456789");
		ReflectionTestUtils.setField(bookmark, "name", "부산시민공원");
		ReflectionTestUtils.setField(bookmark, "providerCategory", "여행 > 관광,명소 > 공원");
		ReflectionTestUtils.setField(bookmark, "address", "부산광역시 부산진구 시민공원로 73");
		ReflectionTestUtils.setField(bookmark, "point", point(35.1686, 129.0576));
		return bookmark;
	}

	private Place place(
		Long placeId,
		String name,
		PlaceCategory category,
		String providerPlaceId,
		double lat,
		double lng,
		AccessibilityFeatureType... featureTypes) {
		Place place = instantiate(Place.class);
		ReflectionTestUtils.setField(place, "placeId", placeId);
		ReflectionTestUtils.setField(place, "name", name);
		ReflectionTestUtils.setField(place, "category", category);
		ReflectionTestUtils.setField(place, "address", "부산광역시");
		ReflectionTestUtils.setField(place, "providerPlaceId", providerPlaceId);
		ReflectionTestUtils.setField(place, "point", point(lat, lng));
		ReflectionTestUtils.setField(place, "accessibilityFeatures",
			java.util.Arrays.asList(feature(place, featureTypes)));
		return place;
	}

	private PlaceAccessibilityFeature[] feature(Place place, AccessibilityFeatureType... featureTypes) {
		return java.util.Arrays.stream(featureTypes)
			.map(featureType -> {
				PlaceAccessibilityFeature feature = instantiate(PlaceAccessibilityFeature.class);
				ReflectionTestUtils.setField(feature, "place", place);
				ReflectionTestUtils.setField(feature, "featureType", featureType);
				ReflectionTestUtils.setField(feature, "isAvailable", true);
				return feature;
			})
			.toArray(PlaceAccessibilityFeature[]::new);
	}

	private Point point(double lat, double lng) {
		return geoPointConverter.toPoint(new GeoPointRequest(lat, lng));
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}

	private static <T> T instantiate(Class<T> type) {
		try {
			var constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
