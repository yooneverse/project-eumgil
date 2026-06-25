package com.ssafy.e102.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportRequest;
import com.ssafy.e102.domain.report.dto.response.HazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportIdResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.HazardMarkerListResponse;
import com.ssafy.e102.domain.report.dto.response.HazardMarkerResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.entity.HazardReportImage;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.external.kakao.KakaoAddressDocument;
import com.ssafy.e102.global.external.kakao.KakaoLocalClient;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HazardReportService {

	private static final short REPRESENTATIVE_IMAGE_ORDER = 0;
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "reportId");
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
	private static final long IDEMPOTENCY_RETENTION_HOURS = 24;
	private static final int MAX_MARKER_COUNT = 100;
	private static final double MAX_MARKER_BBOX_DIAGONAL_METERS = 2_000.0;

	private final HazardReportRepository hazardReportRepository;
	private final HazardReportImageRepository hazardReportImageRepository;
	private final UserRepository userRepository;
	private final GeoPointConverter geoPointConverter;
	private final HazardReportImageUploadService hazardReportImageUploadService;
	private final KakaoLocalClient kakaoLocalClient;
	private final Clock clock;
	private final TransactionOperations writeTransaction;

	@Autowired
	public HazardReportService(
		HazardReportRepository hazardReportRepository,
		HazardReportImageRepository hazardReportImageRepository,
		UserRepository userRepository,
		GeoPointConverter geoPointConverter,
		HazardReportImageUploadService hazardReportImageUploadService,
		KakaoLocalClient kakaoLocalClient,
		PlatformTransactionManager transactionManager) {
		this(
			hazardReportRepository,
			hazardReportImageRepository,
			userRepository,
			geoPointConverter,
			hazardReportImageUploadService,
			kakaoLocalClient,
			Clock.systemDefaultZone(),
			new TransactionTemplate(transactionManager));
	}

	HazardReportService(
		HazardReportRepository hazardReportRepository,
		HazardReportImageRepository hazardReportImageRepository,
		UserRepository userRepository,
		GeoPointConverter geoPointConverter,
		HazardReportImageUploadService hazardReportImageUploadService,
		KakaoLocalClient kakaoLocalClient,
		Clock clock,
		TransactionOperations writeTransaction) {
		this.hazardReportRepository = hazardReportRepository;
		this.hazardReportImageRepository = hazardReportImageRepository;
		this.userRepository = userRepository;
		this.geoPointConverter = geoPointConverter;
		this.hazardReportImageUploadService = hazardReportImageUploadService;
		this.kakaoLocalClient = kakaoLocalClient;
		this.clock = clock;
		this.writeTransaction = writeTransaction;
	}

	public HazardReportIdResponse createHazardReport(UUID userId, CreateHazardReportRequest request) {
		return createHazardReport(userId, request, null);
	}

	public HazardReportIdResponse createHazardReport(
		UUID userId,
		CreateHazardReportRequest request,
		String idempotencyKey) {
		String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
		LocalDateTime now = LocalDateTime.now(clock);
		String requestHash = null;
		if (normalizedIdempotencyKey != null) {
			requestHash = HazardReportIdempotencyRequestHash.from(request);
			HazardReportIdResponse existingResponse = findExistingIdempotentResponse(
				userId,
				normalizedIdempotencyKey,
				request,
				requestHash,
				now);
			if (existingResponse != null) {
				return existingResponse;
			}
		}

		getUser(userId);
		hazardReportImageUploadService.validateImageObjectKeys(userId, request.imageObjectKeys());
		hazardReportImageUploadService.validateThumbnailObjectKeys(userId, request.thumbnailObjectKeys());
		String address = resolveAddress(request.reportPoint());
		String idempotencyRequestHash = requestHash;
		HazardReportIdResponse response = writeTransaction.execute(status -> createHazardReportInTransaction(
			userId,
			request,
			normalizedIdempotencyKey,
			idempotencyRequestHash,
			address));
		return Objects.requireNonNull(response, "Hazard report write transaction returned null.");
	}

	private HazardReportIdResponse createHazardReportInTransaction(
		UUID userId,
		CreateHazardReportRequest request,
		String normalizedIdempotencyKey,
		String requestHash,
		String address) {
		LocalDateTime now = LocalDateTime.now(clock);
		User user = getUser(userId, normalizedIdempotencyKey != null);
		if (normalizedIdempotencyKey != null) {
			hazardReportRepository.clearExpiredIdempotencyMetadata(now);
			HazardReportIdResponse existingResponse = findExistingIdempotentResponse(
				userId,
				normalizedIdempotencyKey,
				request,
				requestHash,
				now);
			if (existingResponse != null) {
				return existingResponse;
			}
		}
		HazardReport hazardReport = HazardReport.create(
			user,
			request.reportType(),
			request.description(),
			address,
			geoPointConverter.toPoint(request.reportPoint()),
			request.imageObjectKeys(),
			request.thumbnailObjectKeys());
		hazardReport.applyIdempotency(
			normalizedIdempotencyKey,
			requestHash,
			now.plusHours(IDEMPOTENCY_RETENTION_HOURS));
		HazardReport savedHazardReport = hazardReportRepository.save(hazardReport);
		return new HazardReportIdResponse(savedHazardReport.getReportId());
	}

	@Transactional
	public int cleanupExpiredIdempotencyMetadata() {
		return hazardReportRepository.clearExpiredIdempotencyMetadata(LocalDateTime.now(clock));
	}

	@Transactional(readOnly = true)
	public HazardReportListResponse getMyHazardReports(UUID userId, Long cursor, int size) {
		PageRequest pageRequest = PageRequest.of(0, size, NEWEST_FIRST);
		Slice<HazardReport> hazardReports = cursor == null
			? hazardReportRepository.findAllByUser_UserId(userId, pageRequest)
			: hazardReportRepository.findAllByUser_UserIdAndReportIdLessThan(userId, cursor, pageRequest);
		return HazardReportListResponse.of(
			hazardReports.getContent(),
			size,
			hazardReports.hasNext(),
			getRepresentativeImageUrls(hazardReports.getContent()),
			geoPointConverter);
	}

	@Transactional(readOnly = true)
	public HazardReportDetailResponse getMyHazardReportDetail(UUID userId, Long reportId) {
		HazardReport hazardReport = getHazardReport(reportId);
		validateOwner(hazardReport, userId);
		return HazardReportDetailResponse.of(
			hazardReport,
			geoPointConverter,
			createImageReadUrls(hazardReport));
	}

	@Transactional(readOnly = true)
	public HazardMarkerListResponse getApprovedHazardMarkers(
		Double swLat,
		Double swLng,
		Double neLat,
		Double neLng) {
		validateMarkerBounds(swLat, swLng, neLat, neLng);
		List<HazardReport> reportsInBounds = hazardReportRepository.findApprovedWithinBounds(
			swLng,
			swLat,
			neLng,
			neLat,
			PageRequest.of(0, MAX_MARKER_COUNT));
		if (reportsInBounds.isEmpty()) {
			return new HazardMarkerListResponse(List.of());
		}

		Map<Long, HazardReport> reportsWithImagesById = hazardReportRepository.findAllByReportIdIn(
				reportsInBounds.stream()
					.map(HazardReport::getReportId)
					.toList())
			.stream()
			.collect(Collectors.toMap(HazardReport::getReportId, hazardReport -> hazardReport));

		List<HazardMarkerResponse> markers = reportsInBounds.stream()
			.map(hazardReport -> toHazardMarkerResponse(
				reportsWithImagesById.getOrDefault(hazardReport.getReportId(), hazardReport)))
			.toList();
		return new HazardMarkerListResponse(markers);
	}

	private Map<Long, String> getRepresentativeImageUrls(List<HazardReport> hazardReports) {
		List<Long> reportIds = hazardReports.stream()
			.map(HazardReport::getReportId)
			.toList();
		if (reportIds.isEmpty()) {
			return Map.of();
		}
		return hazardReportImageRepository
			.findAllByHazardReport_ReportIdInAndDisplayOrder(reportIds, REPRESENTATIVE_IMAGE_ORDER)
			.stream()
			.collect(Collectors.toMap(
				image -> image.getHazardReport().getReportId(),
				image -> hazardReportImageUploadService.createReadUrl(image.getImageObjectKey()),
				(existing, ignored) -> existing));
	}

	private List<String> createImageReadUrls(HazardReport hazardReport) {
		return hazardReport.getImages()
			.stream()
			.map(HazardReportImage::getImageObjectKey)
			.map(hazardReportImageUploadService::createReadUrl)
			.toList();
	}

	private HazardMarkerResponse toHazardMarkerResponse(HazardReport hazardReport) {
		Point reportPoint = hazardReport.getReportPoint();
		return new HazardMarkerResponse(
			hazardReport.getReportId(),
			hazardReport.getReportType(),
			reportPoint.getY(),
			reportPoint.getX(),
			hazardReport.getDescription(),
			createMarkerThumbnailReadUrls(hazardReport),
			createMarkerImageReadUrls(hazardReport));
	}

	private List<String> createMarkerThumbnailReadUrls(HazardReport hazardReport) {
		return hazardReport.getImages()
			.stream()
			.map(image -> toMarkerThumbnailReadUrl(hazardReport.getReportId(), image))
			.filter(Objects::nonNull)
			.toList();
	}

	private List<String> createMarkerImageReadUrls(HazardReport hazardReport) {
		return hazardReport.getImages()
			.stream()
			.map(image -> toMarkerImageReadUrl(hazardReport.getReportId(), image))
			.filter(Objects::nonNull)
			.toList();
	}

	private String toMarkerImageReadUrl(Long reportId, HazardReportImage image) {
		try {
			return hazardReportImageUploadService.createReadUrl(image.getImageObjectKey());
		} catch (RuntimeException exception) {
			log.warn("승인 제보 마커 이미지 URL 생성 실패. reportId={}, objectKey={}", reportId, image.getImageObjectKey(), exception);
			return null;
		}
	}

	private String toMarkerThumbnailReadUrl(Long reportId, HazardReportImage image) {
		String thumbnailObjectKey = image.getThumbnailObjectKey();
		if (thumbnailObjectKey == null || thumbnailObjectKey.isBlank()) {
			return toMarkerImageReadUrl(reportId, image);
		}
		try {
			return hazardReportImageUploadService.createReadUrl(thumbnailObjectKey);
		} catch (RuntimeException exception) {
			log.warn("승인 제보 마커 썸네일 URL 생성 실패. reportId={}, thumbnailObjectKey={}", reportId, thumbnailObjectKey, exception);
			return toMarkerImageReadUrl(reportId, image);
		}
	}

	private String resolveAddress(GeoPointRequest reportPoint) {
		if (reportPoint == null || reportPoint.lat() == null || reportPoint.lng() == null) {
			return null;
		}
		try {
			return kakaoLocalClient.reverseGeocode(reportPoint.lat(), reportPoint.lng())
				.map(KakaoAddressDocument::displayAddress)
				.orElse(null);
		} catch (RestClientException | IllegalArgumentException exception) {
			log.debug("제보 위치 주소 역지오코딩 실패. lat={}, lng={}", reportPoint.lat(), reportPoint.lng(), exception);
			return null;
		}
	}

	private HazardReportIdResponse findExistingIdempotentResponse(
		UUID userId,
		String idempotencyKey,
		CreateHazardReportRequest request,
		String requestHash,
		LocalDateTime now) {
		return hazardReportRepository.findByUser_UserIdAndIdempotencyKey(userId, idempotencyKey)
			.map(existingReport -> {
				if (!existingReport.hasActiveIdempotency(now)) {
					return null;
				}
				if (!HazardReportIdempotencyRequestHash.matchesStoredHash(existingReport.getIdempotencyRequestHash(), request)) {
					throw new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_IDEMPOTENCY_CONFLICT);
				}
				return new HazardReportIdResponse(existingReport.getReportId());
			})
			.orElse(null);
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return null;
		}
		String normalizedKey = idempotencyKey.trim();
		if (normalizedKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST,
				"Idempotency-Key는 255자 이하여야 합니다.");
		}
		return normalizedKey;
	}

	private void validateMarkerBounds(
		Double swLat,
		Double swLng,
		Double neLat,
		Double neLng) {
		if (swLat == null || swLng == null || neLat == null || neLng == null) {
			throw invalidMarkerBounds("bbox 좌표는 모두 필수입니다.");
		}
		if (swLat < -90 || swLat > 90 || neLat < -90 || neLat > 90) {
			throw invalidMarkerBounds("위도는 -90 이상 90 이하여야 합니다.");
		}
		if (swLng < -180 || swLng > 180 || neLng < -180 || neLng > 180) {
			throw invalidMarkerBounds("경도는 -180 이상 180 이하여야 합니다.");
		}
		if (swLat >= neLat || swLng >= neLng) {
			throw invalidMarkerBounds("bbox 좌표 순서가 올바르지 않습니다.");
		}
		double diagonalDistance = GeoDistanceCalculator.distanceMeter(swLat, swLng, neLat, neLng);
		if (diagonalDistance > MAX_MARKER_BBOX_DIAGONAL_METERS) {
			throw invalidMarkerBounds("bbox 대각선 거리는 2km 이하여야 합니다.");
		}
	}

	private HazardReportException invalidMarkerBounds(String message) {
		return new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST, message);
	}

	private User getUser(UUID userId) {
		return getUser(userId, false);
	}

	private User getUser(UUID userId, boolean lockForIdempotency) {
		Optional<User> user = lockForIdempotency
			? userRepository.findByIdForUpdate(userId)
			: userRepository.findById(userId);
		return user
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}

	private HazardReport getHazardReport(Long reportId) {
		return hazardReportRepository.findWithImagesByReportId(reportId)
			.orElseThrow(() -> new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_NOT_FOUND));
	}

	private void validateOwner(HazardReport hazardReport, UUID userId) {
		if (!hazardReport.isOwner(userId)) {
			throw new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_FORBIDDEN);
		}
	}
}
