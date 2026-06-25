package com.ssafy.e102.domain.bookmark.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ssafy.e102.domain.bookmark.dto.request.CreatePlaceBookmarkRequest;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkCreateResponse;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkItemResponse;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkListResponse;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkErrorCode;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkException;
import com.ssafy.e102.domain.place.dto.response.PlaceAccessibilityFeatureResponse;
import com.ssafy.e102.domain.place.entity.Bookmark;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.repository.BookmarkRepository;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
import com.ssafy.e102.domain.place.support.BookmarkTargetIdFactory;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

@Service
@Transactional(readOnly = true)
public class PlaceBookmarkService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("bookmarkId"));
	private static final String DEFAULT_PROVIDER = "KAKAO";

	private final BookmarkRepository bookmarkRepository;
	private final PlaceRepository placeRepository;
	private final UserRepository userRepository;
	private final GeoPointConverter geoPointConverter;

	public PlaceBookmarkService(
		BookmarkRepository bookmarkRepository,
		PlaceRepository placeRepository,
		UserRepository userRepository,
		GeoPointConverter geoPointConverter) {
		this.bookmarkRepository = bookmarkRepository;
		this.placeRepository = placeRepository;
		this.userRepository = userRepository;
		this.geoPointConverter = geoPointConverter;
	}

	public PlaceBookmarkListResponse getBookmarks(UUID userId, Long cursor, int size) {
		PageRequest pageRequest = PageRequest.of(0, size, NEWEST_FIRST);
		Slice<Bookmark> bookmarks = cursor == null
			? bookmarkRepository.findAllByUser_UserId(userId, pageRequest)
			: bookmarkRepository.findAllByUser_UserIdAndBookmarkIdLessThan(userId, cursor.intValue(), pageRequest);
		Long nextCursor = bookmarks.hasNext() && !bookmarks.getContent().isEmpty()
			? bookmarks.getContent().get(bookmarks.getContent().size() - 1).getBookmarkId().longValue()
			: null;
		return new PlaceBookmarkListResponse(
			bookmarks.getContent().stream()
				.map(this::toItemResponse)
				.toList(),
			size,
			nextCursor,
			bookmarks.hasNext());
	}

	@Transactional
	public PlaceBookmarkCreateResponse createBookmark(UUID userId, CreatePlaceBookmarkRequest request) {
		validateCreateRequest(request);
		User user = getUser(userId);
		Optional<Place> canonicalPlace = resolveCanonicalPlace(request);
		if (canonicalPlace.isPresent()) {
			return createInternalBookmark(userId, user, canonicalPlace.get());
		}
		return createExternalBookmark(userId, user, request);
	}

	@Transactional
	public void deleteBookmarkByTarget(UUID userId, String bookmarkTargetId) {
		Bookmark bookmark = bookmarkRepository.findByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId)
			.orElseGet(() -> findLegacyInternalBookmarkByTarget(userId, bookmarkTargetId)
				.orElseThrow(() -> new PlaceBookmarkException(PlaceBookmarkErrorCode.PLACE_BOOKMARK_NOT_FOUND)));
		bookmarkRepository.delete(bookmark);
	}

	@Transactional
	public void deleteBookmarkByPlaceId(UUID userId, Long placeId) {
		Bookmark bookmark = bookmarkRepository.findByUser_UserIdAndPlace_PlaceId(userId, placeId)
			.orElseThrow(() -> new PlaceBookmarkException(PlaceBookmarkErrorCode.PLACE_BOOKMARK_NOT_FOUND));
		bookmarkRepository.delete(bookmark);
	}

	private PlaceBookmarkCreateResponse createInternalBookmark(UUID userId, User user, Place place) {
		String bookmarkTargetId = BookmarkTargetIdFactory.fromInternalPlace(place.getPlaceId());
		if (bookmarkRepository.existsByUser_UserIdAndPlace_PlaceId(userId, place.getPlaceId())
			|| bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId)) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.PLACE_BOOKMARK_ALREADY_EXISTS);
		}
		Bookmark savedBookmark = saveBookmarkHandlingDuplicate(
			Bookmark.createInternal(user, place, bookmarkTargetId));
		return new PlaceBookmarkCreateResponse(
			savedBookmark.getBookmarkId().longValue(),
			bookmarkTargetId,
			PlaceDetailType.INTERNAL_PLACE,
			place.getPlaceId());
	}

	private PlaceBookmarkCreateResponse createExternalBookmark(UUID userId, User user,
		CreatePlaceBookmarkRequest request) {
		String provider = normalizeProvider(request.provider());
		PlaceDetailType targetType = resolveExternalTargetType(request);
		GeoPointRequest pointRequest = request.point();
		String bookmarkTargetId = targetType == PlaceDetailType.EXTERNAL_POI
			? BookmarkTargetIdFactory.fromExternalPoi(
				provider,
				request.providerPlaceId(),
				request.name(),
				pointRequest.lat(),
				pointRequest.lng())
			: BookmarkTargetIdFactory.fromExternalAddress(
				provider,
				request.name(),
				pointRequest.lat(),
				pointRequest.lng(),
				request.address());
		if (bookmarkRepository.existsByUser_UserIdAndBookmarkTargetId(userId, bookmarkTargetId)) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.PLACE_BOOKMARK_ALREADY_EXISTS);
		}
		Bookmark savedBookmark = saveBookmarkHandlingDuplicate(Bookmark.createExternal(
			user,
			bookmarkTargetId,
			provider,
			request.providerPlaceId(),
			request.name(),
			request.providerCategory(),
			request.address(),
			geoPointConverter.toPoint(pointRequest)));
		return new PlaceBookmarkCreateResponse(
			savedBookmark.getBookmarkId().longValue(),
			bookmarkTargetId,
			targetType,
			null);
	}

	private PlaceBookmarkItemResponse toItemResponse(Bookmark bookmark) {
		if (bookmark.getPlace() != null) {
			String bookmarkTargetId = bookmark.getBookmarkTargetId();
			if (!StringUtils.hasText(bookmarkTargetId)) {
				bookmarkTargetId = BookmarkTargetIdFactory.fromInternalPlace(bookmark.getPlace().getPlaceId());
			}
			return new PlaceBookmarkItemResponse(
				bookmark.getBookmarkId().longValue(),
				bookmarkTargetId,
				PlaceDetailType.INTERNAL_PLACE,
				bookmark.getPlace().getPlaceId(),
				normalizeProvider(bookmark.getProvider()),
				bookmark.getPlace().getProviderPlaceId(),
				bookmark.getPlace().getName(),
				bookmark.getPlace().getCategory(),
				bookmark.getProviderCategory(),
				bookmark.getPlace().getAddress(),
				geoPointConverter.toResponse(bookmark.getPlace().getPoint()),
				bookmark.getPlace().getAccessibilityFeatures().stream()
					.map(PlaceAccessibilityFeatureResponse::from)
					.toList());
		}
		return new PlaceBookmarkItemResponse(
			bookmark.getBookmarkId().longValue(),
			bookmark.getBookmarkTargetId(),
			resolveExternalTargetType(bookmark),
			null,
			normalizeProvider(bookmark.getProvider()),
			bookmark.getProviderPlaceId(),
			bookmark.getName(),
			null,
			bookmark.getProviderCategory(),
			bookmark.getAddress(),
			geoPointConverter.toResponse(bookmark.getPoint()),
			List.of());
	}

	private Optional<Place> resolveCanonicalPlace(CreatePlaceBookmarkRequest request) {
		if (request.placeId() != null) {
			return Optional.of(placeRepository.findById(request.placeId())
				.orElseThrow(() -> new PlaceBookmarkException(PlaceBookmarkErrorCode.PLACE_BOOKMARK_PLACE_NOT_FOUND)));
		}
		if (StringUtils.hasText(request.providerPlaceId())) {
			return placeRepository.findAllByProviderPlaceIdIn(List.of(request.providerPlaceId().trim()))
				.stream()
				.findFirst();
		}
		return Optional.empty();
	}

	private Bookmark saveBookmarkHandlingDuplicate(Bookmark bookmark) {
		try {
			return bookmarkRepository.save(bookmark);
		} catch (DataIntegrityViolationException exception) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.PLACE_BOOKMARK_ALREADY_EXISTS);
		}
	}

	private Optional<Bookmark> findLegacyInternalBookmarkByTarget(UUID userId, String bookmarkTargetId) {
		return bookmarkRepository.findAllByUser_UserId(userId)
			.stream()
			.filter(bookmark -> bookmark.getPlace() != null)
			.filter(bookmark -> bookmarkTargetId
				.equals(BookmarkTargetIdFactory.fromInternalPlace(bookmark.getPlace().getPlaceId())))
			.findFirst();
	}

	private PlaceDetailType resolveExternalTargetType(CreatePlaceBookmarkRequest request) {
		if (StringUtils.hasText(request.providerPlaceId())) {
			return PlaceDetailType.EXTERNAL_POI;
		}
		return PlaceDetailType.EXTERNAL_ADDRESS;
	}

	private PlaceDetailType resolveExternalTargetType(Bookmark bookmark) {
		if (StringUtils.hasText(bookmark.getProviderPlaceId())) {
			return PlaceDetailType.EXTERNAL_POI;
		}
		return PlaceDetailType.EXTERNAL_ADDRESS;
	}

	private void validateCreateRequest(CreatePlaceBookmarkRequest request) {
		if (request == null) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_REQUEST);
		}
		if (request.placeId() != null) {
			return;
		}
		if (!StringUtils.hasText(request.name()) || request.point() == null) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_REQUEST);
		}
		if (StringUtils.hasText(request.provider()) && !DEFAULT_PROVIDER.equalsIgnoreCase(request.provider().trim())) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_REQUEST);
		}
	}

	private User getUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}

	private String normalizeProvider(String provider) {
		if (!StringUtils.hasText(provider)) {
			return DEFAULT_PROVIDER;
		}
		return provider.trim().toUpperCase();
	}
}
