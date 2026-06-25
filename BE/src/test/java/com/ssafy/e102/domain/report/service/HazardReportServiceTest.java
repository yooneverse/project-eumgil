package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientException;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportRequest;
import com.ssafy.e102.domain.report.dto.response.HazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportIdResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.HazardMarkerListResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.external.kakao.KakaoAddressDocument;
import com.ssafy.e102.global.external.kakao.KakaoLocalClient;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class HazardReportServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-05-14T00:00:00Z"),
		ZoneOffset.UTC);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 14, 0, 0);

	@Mock
	private HazardReportRepository hazardReportRepository;

	@Mock
	private HazardReportImageRepository hazardReportImageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private HazardReportImageUploadService hazardReportImageUploadService;

	@Mock
	private KakaoLocalClient kakaoLocalClient;

	private HazardReportService hazardReportService;
	private GeoPointConverter geoPointConverter;
	private AtomicBoolean inWriteTransaction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		inWriteTransaction = new AtomicBoolean(false);
		geoPointConverter = new GeoPointConverter();
		hazardReportService = new HazardReportService(
			hazardReportRepository,
			hazardReportImageRepository,
			userRepository,
			geoPointConverter,
			hazardReportImageUploadService,
			kakaoLocalClient,
			FIXED_CLOCK,
			testTransactionOperations());
	}

	@Test
	@DisplayName("도로 상태 제보 등록은 현재 사용자와 위치를 저장한다")
	void createHazardReport() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		CreateHazardReportRequest request = defaultCreateRequest();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenAnswer(invocation -> {
				assertThat(inWriteTransaction.get()).isFalse();
				return Optional.of(new KakaoAddressDocument(
					"부산 부산진구 범전동 200",
					"부산 부산진구 시민공원로 73",
					null,
					"부산",
					"부산진구",
					"범전동"));
			});
		when(hazardReportRepository.save(any(HazardReport.class))).thenAnswer(invocation -> {
			assertThat(inWriteTransaction.get()).isTrue();
			HazardReport hazardReport = invocation.getArgument(0);
			assertThat(hazardReport.getAddress()).isEqualTo("부산 부산진구 시민공원로 73");
			ReflectionTestUtils.setField(hazardReport, "reportId", 1L);
			return hazardReport;
		});

		HazardReportIdResponse response = hazardReportService.createHazardReport(
			userId,
			request);

		assertThat(response.reportId()).isEqualTo(1L);
		verify(hazardReportImageUploadService).validateImageObjectKeys(
			userId,
			List.of("hazard-reports/user-1/20260514/image-1.jpg"));
		verify(hazardReportRepository).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("Idempotency-Key가 있으면 요청 해시와 24시간 만료 시각을 함께 저장한다")
	void createHazardReportWithIdempotencyKey() {
		UUID userId = UUID.randomUUID();
		CreateHazardReportRequest request = defaultCreateRequest();
		User user = user(userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
		when(hazardReportRepository.findByUser_UserIdAndIdempotencyKey(userId, "outbox-report-1"))
			.thenReturn(Optional.empty());
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576)).thenReturn(Optional.empty());
		when(hazardReportRepository.save(any(HazardReport.class))).thenAnswer(invocation -> {
			HazardReport hazardReport = invocation.getArgument(0);
			assertThat(hazardReport.getIdempotencyKey()).isEqualTo("outbox-report-1");
			assertThat(hazardReport.getIdempotencyRequestHash()).isEqualTo(idempotencyRequestHash(request));
			assertThat(hazardReport.getIdempotencyExpiresAt()).isEqualTo(NOW.plusHours(24));
			ReflectionTestUtils.setField(hazardReport, "reportId", 1L);
			return hazardReport;
		});

		HazardReportIdResponse response = hazardReportService.createHazardReport(
			userId,
			request,
			" outbox-report-1 ");

		assertThat(response.reportId()).isEqualTo(1L);
		verify(userRepository).findByIdForUpdate(userId);
		verify(hazardReportRepository).clearExpiredIdempotencyMetadata(NOW);
		verify(hazardReportRepository).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("동일 Idempotency-Key와 동일 요청은 기존 제보 ID를 반환한다")
	void createHazardReportReturnsExistingReportForSameIdempotencyRequest() {
		UUID userId = UUID.randomUUID();
		CreateHazardReportRequest request = defaultCreateRequest();
		User user = user(userId);
		HazardReport existingReport = hazardReport(user, 7L, List.of("hazard-reports/user-1/20260514/image-1.jpg"));
		ReflectionTestUtils.setField(existingReport, "idempotencyKey", "outbox-report-1");
		ReflectionTestUtils.setField(existingReport, "idempotencyRequestHash", idempotencyRequestHash(request));
		ReflectionTestUtils.setField(existingReport, "idempotencyExpiresAt", NOW.plusHours(1));
		when(hazardReportRepository.findByUser_UserIdAndIdempotencyKey(userId, "outbox-report-1"))
			.thenReturn(Optional.of(existingReport));

		HazardReportIdResponse response = hazardReportService.createHazardReport(
			userId,
			request,
			"outbox-report-1");

		assertThat(response.reportId()).isEqualTo(7L);
		verify(userRepository, never()).findByIdForUpdate(userId);
		verify(hazardReportRepository, never()).clearExpiredIdempotencyMetadata(NOW);
		verify(hazardReportImageUploadService, never()).validateImageObjectKeys(any(), any());
		verify(kakaoLocalClient, never()).reverseGeocode(anyDouble(), anyDouble());
		verify(hazardReportRepository, never()).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("legacy idempotency hash로 저장된 기존 제보도 동일 요청 재시도를 허용한다")
	void createHazardReportReturnsExistingReportForLegacyIdempotencyRequest() {
		UUID userId = UUID.randomUUID();
		CreateHazardReportRequest request = defaultCreateRequest();
		User user = user(userId);
		HazardReport existingReport = hazardReport(user, 9L, List.of("hazard-reports/user-1/20260514/image-1.jpg"));
		ReflectionTestUtils.setField(existingReport, "idempotencyKey", "outbox-report-legacy");
		ReflectionTestUtils.setField(existingReport, "idempotencyRequestHash", legacyIdempotencyRequestHash(request));
		ReflectionTestUtils.setField(existingReport, "idempotencyExpiresAt", NOW.plusHours(1));
		when(hazardReportRepository.findByUser_UserIdAndIdempotencyKey(userId, "outbox-report-legacy"))
			.thenReturn(Optional.of(existingReport));

		HazardReportIdResponse response = hazardReportService.createHazardReport(
			userId,
			request,
			"outbox-report-legacy");

		assertThat(response.reportId()).isEqualTo(9L);
		verify(userRepository, never()).findByIdForUpdate(userId);
		verify(hazardReportRepository, never()).clearExpiredIdempotencyMetadata(NOW);
		verify(hazardReportImageUploadService, never()).validateImageObjectKeys(any(), any());
		verify(kakaoLocalClient, never()).reverseGeocode(anyDouble(), anyDouble());
		verify(hazardReportRepository, never()).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("동일 Idempotency-Key와 다른 요청은 충돌로 거부한다")
	void rejectDifferentRequestWithSameIdempotencyKey() {
		UUID userId = UUID.randomUUID();
		CreateHazardReportRequest request = defaultCreateRequest();
		User user = user(userId);
		HazardReport existingReport = hazardReport(user, 7L, List.of("hazard-reports/user-1/20260514/image-1.jpg"));
		ReflectionTestUtils.setField(existingReport, "idempotencyKey", "outbox-report-1");
		ReflectionTestUtils.setField(existingReport, "idempotencyRequestHash", "different-request-hash");
		ReflectionTestUtils.setField(existingReport, "idempotencyExpiresAt", NOW.plusHours(1));
		when(hazardReportRepository.findByUser_UserIdAndIdempotencyKey(userId, "outbox-report-1"))
			.thenReturn(Optional.of(existingReport));

		assertThatThrownBy(() -> hazardReportService.createHazardReport(userId, request, "outbox-report-1"))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_IDEMPOTENCY_CONFLICT);

		verify(userRepository, never()).findByIdForUpdate(userId);
		verify(hazardReportRepository, never()).clearExpiredIdempotencyMetadata(NOW);
		verify(hazardReportImageUploadService, never()).validateImageObjectKeys(any(), any());
		verify(hazardReportRepository, never()).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("Idempotency-Key가 255자를 넘으면 제보 등록을 거부한다")
	void rejectTooLongIdempotencyKey() {
		UUID userId = UUID.randomUUID();

		assertThatThrownBy(() -> hazardReportService.createHazardReport(
			userId,
			defaultCreateRequest(),
			"a".repeat(256)))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST);

		verify(userRepository, never()).findById(any());
		verify(userRepository, never()).findByIdForUpdate(any());
		verify(hazardReportRepository, never()).clearExpiredIdempotencyMetadata(any());
		verify(hazardReportRepository, never()).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("만료된 멱등성 메타데이터를 현재 시각 기준으로 정리한다")
	void cleanupExpiredIdempotencyMetadata() {
		when(hazardReportRepository.clearExpiredIdempotencyMetadata(NOW)).thenReturn(2);

		int cleanedCount = hazardReportService.cleanupExpiredIdempotencyMetadata();

		assertThat(cleanedCount).isEqualTo(2);
		verify(hazardReportRepository, times(1)).clearExpiredIdempotencyMetadata(NOW);
	}

	@Test
	@DisplayName("주소 역지오코딩이 실패해도 제보는 주소 없이 저장한다")
	void createHazardReportWithNullAddressWhenReverseGeocodeFails() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenThrow(new RestClientException("timeout"));
		when(hazardReportRepository.save(any(HazardReport.class))).thenAnswer(invocation -> {
			HazardReport hazardReport = invocation.getArgument(0);
			assertThat(hazardReport.getAddress()).isNull();
			ReflectionTestUtils.setField(hazardReport, "reportId", 1L);
			return hazardReport;
		});

		HazardReportIdResponse response = hazardReportService.createHazardReport(
			userId,
			new CreateHazardReportRequest(
				ReportType.SIDEWALK_MISSING,
				"보행 가능한 인도가 없습니다.",
				new GeoPointRequest(35.1686, 129.0576),
				List.of(),
				List.of()));

		assertThat(response.reportId()).isEqualTo(1L);
		verify(hazardReportRepository).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("제보 이미지 object key가 허용되지 않으면 도로 상태 제보를 저장하지 않는다")
	void rejectCreateWithInvalidImageObjectKey() {
		UUID userId = UUID.randomUUID();
		List<String> imageObjectKeys = List.of("https://attacker.example.com/image.jpg");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
		doThrow(new HazardReportException(
			HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL,
			"제보 이미지 object key는 업로드 API로 발급된 값이어야 합니다."))
			.when(hazardReportImageUploadService)
			.validateImageObjectKeys(userId, imageObjectKeys);

		assertThatThrownBy(() -> hazardReportService.createHazardReport(
			userId,
			new CreateHazardReportRequest(
				ReportType.SIDEWALK_MISSING,
				"보행 가능한 인도가 없습니다.",
				new GeoPointRequest(35.1686, 129.0576),
				imageObjectKeys,
				List.of())))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL);

		verify(hazardReportRepository, never()).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("사용자가 없으면 도로 상태 제보를 등록하지 않는다")
	void rejectCreateWithoutUser() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> hazardReportService.createHazardReport(
			userId,
			new CreateHazardReportRequest(
				ReportType.SIDEWALK_MISSING,
				null,
				new GeoPointRequest(35.1686, 129.0576),
				null,
				null)))
			.hasMessage("사용자를 찾을 수 없습니다.");

		verify(hazardReportRepository, never()).save(any(HazardReport.class));
	}

	@Test
	@DisplayName("내 제보 목록은 최신 저장순 cursor 기반으로 대표 이미지를 함께 조회한다")
	void getMyHazardReports() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = hazardReport(
			user(userId),
			1L,
			"부산 부산진구 시민공원로 73",
			"가".repeat(81),
			List.of("hazard-reports/user-1/20260514/image-1.jpg"));
		PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "reportId"));
		when(hazardReportRepository.findAllByUser_UserId(userId, pageable))
			.thenReturn(new SliceImpl<>(List.of(hazardReport), pageable, false));
		when(hazardReportImageRepository.findAllByHazardReport_ReportIdInAndDisplayOrder(List.of(1L), (short)0))
			.thenReturn(List.of(hazardReport.getImages().get(0)));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-1.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-1");

		HazardReportListResponse response = hazardReportService.getMyHazardReports(userId, null, 10);

		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).reportId()).isEqualTo(1L);
		assertThat(response.content().get(0).reportType()).isEqualTo(ReportType.SIDEWALK_MISSING);
		assertThat(response.content().get(0).status()).isEqualTo(ReportStatus.PENDING);
		assertThat(response.content().get(0).address()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.content().get(0).description())
			.isEqualTo("가".repeat(80) + "...");
		assertThat(response.content().get(0).representativeImageUrl())
			.isEqualTo("https://storage.example.com/read?key=image-1");
		assertThat(response.nextCursor()).isNull();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	@DisplayName("내 제보 목록은 마지막 제보 ID 이후 cursor로 조회한다")
	void getMyHazardReportsWithCursor() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = hazardReport(user(userId), 3L, List.of());
		PageRequest pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "reportId"));
		when(hazardReportRepository.findAllByUser_UserIdAndReportIdLessThan(userId, 10L, pageable))
			.thenReturn(new SliceImpl<>(List.of(hazardReport), pageable, true));

		HazardReportListResponse response = hazardReportService.getMyHazardReports(userId, 10L, 2);

		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).representativeImageUrl()).isNull();
		assertThat(response.nextCursor()).isEqualTo(3L);
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	@DisplayName("내 제보 상세는 전체 이미지를 조회용 presigned URL로 반환한다")
	void getMyHazardReportDetail() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = hazardReport(user(userId), 1L, List.of(
			"hazard-reports/user-1/20260514/image-1.jpg",
			"hazard-reports/user-1/20260514/image-2.jpg"));
		when(hazardReportRepository.findWithImagesByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-1.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-1");
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-2.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-2");

		HazardReportDetailResponse response = hazardReportService.getMyHazardReportDetail(userId, 1L);

		assertThat(response.reportId()).isEqualTo(1L);
		assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
		assertThat(response.imageUrls()).containsExactly(
			"https://storage.example.com/read?key=image-1",
			"https://storage.example.com/read?key=image-2");
	}

	@Test
	@DisplayName("다른 사용자의 제보 상세 조회는 거부한다")
	void rejectOtherUserHazardReportDetail() {
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 1L, List.of());
		when(hazardReportRepository.findWithImagesByReportId(1L)).thenReturn(Optional.of(hazardReport));

		assertThatThrownBy(() -> hazardReportService.getMyHazardReportDetail(UUID.randomUUID(), 1L))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_FORBIDDEN);
	}

	@Test
	@DisplayName("존재하지 않는 제보 상세 조회는 거부한다")
	void rejectMissingHazardReportDetail() {
		when(hazardReportRepository.findWithImagesByReportId(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> hazardReportService.getMyHazardReportDetail(UUID.randomUUID(), 1L))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_NOT_FOUND);
	}

	@Test
	@DisplayName("APPROVED ?듭씤 ?쒕낫留?bbox ?덈뿉??留ㅼ빱??諛섑솚?쒕떎")
	void getApprovedHazardMarkers() {
		HazardReport approvedWithImages = hazardReport(
			user(UUID.randomUUID()),
			12L,
			ReportType.RAMP,
			ReportStatus.APPROVED,
			35.1,
			129.1,
			List.of(
				"hazard-reports/user-1/20260514/image-1.jpg",
				"hazard-reports/user-1/20260514/image-2.jpg",
				"hazard-reports/user-1/20260514/image-3.jpg"));
		HazardReport approvedWithoutImages = hazardReport(
			user(UUID.randomUUID()),
			13L,
			ReportType.RAMP,
			ReportStatus.APPROVED,
			35.1005,
			129.1005,
			List.of());
		PageRequest pageRequest = PageRequest.of(0, 100);
		when(hazardReportRepository.findApprovedWithinBounds(129.095, 35.095, 129.105, 35.105, pageRequest))
			.thenReturn(List.of(approvedWithImages, approvedWithoutImages));
		when(hazardReportRepository.findAllByReportIdIn(List.of(12L, 13L)))
			.thenReturn(List.of(approvedWithImages, approvedWithoutImages));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-1.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-1");
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-2.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-2");
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-3.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-3");

		HazardMarkerListResponse response = hazardReportService.getApprovedHazardMarkers(35.095, 129.095, 35.105, 129.105);

		assertThat(response.markers()).hasSize(2);
		assertThat(response.markers().get(0).reportId()).isEqualTo(12L);
		assertThat(response.markers().get(0).reportType()).isEqualTo(ReportType.RAMP);
		assertThat(response.markers().get(0).lat()).isEqualTo(35.1);
		assertThat(response.markers().get(0).lng()).isEqualTo(129.1);
		assertThat(response.markers().get(0).description()).isEqualTo(approvedWithImages.getDescription());
		assertThat(response.markers().get(0).imageUrls())
			.containsExactly(
				"https://storage.example.com/read?key=image-1",
				"https://storage.example.com/read?key=image-2",
				"https://storage.example.com/read?key=image-3");
		assertThat(response.markers().get(1).imageUrls()).isEmpty();
	}

	@Test
	@DisplayName("marker image URL generation failure skips only the broken image")
	void getApprovedHazardMarkersSkipsBrokenImageUrl() {
		HazardReport approvedWithImages = hazardReport(
			user(UUID.randomUUID()),
			12L,
			ReportType.RAMP,
			ReportStatus.APPROVED,
			35.1,
			129.1,
			List.of(
				"hazard-reports/user-1/20260514/image-1.jpg",
				"hazard-reports/user-1/20260514/image-2.jpg",
				"hazard-reports/user-1/20260514/image-3.jpg"));
		PageRequest pageRequest = PageRequest.of(0, 100);
		when(hazardReportRepository.findApprovedWithinBounds(129.095, 35.095, 129.105, 35.105, pageRequest))
			.thenReturn(List.of(approvedWithImages));
		when(hazardReportRepository.findAllByReportIdIn(List.of(12L)))
			.thenReturn(List.of(approvedWithImages));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-1.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-1");
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-2.jpg"))
			.thenThrow(new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-3.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-3");

		HazardMarkerListResponse response = hazardReportService.getApprovedHazardMarkers(35.095, 129.095, 35.105, 129.105);

		assertThat(response.markers()).hasSize(1);
		assertThat(response.markers().get(0).imageUrls())
			.containsExactly(
				"https://storage.example.com/read?key=image-1",
				"https://storage.example.com/read?key=image-3");
	}

	@Test
	@DisplayName("marker remains visible when every image URL generation fails")
	void getApprovedHazardMarkersKeepsMarkerWhenAllImageUrlsFail() {
		HazardReport approvedWithImages = hazardReport(
			user(UUID.randomUUID()),
			12L,
			ReportType.RAMP,
			ReportStatus.APPROVED,
			35.1,
			129.1,
			List.of(
				"hazard-reports/user-1/20260514/image-1.jpg",
				"hazard-reports/user-1/20260514/image-2.jpg"));
		PageRequest pageRequest = PageRequest.of(0, 100);
		when(hazardReportRepository.findApprovedWithinBounds(129.095, 35.095, 129.105, 35.105, pageRequest))
			.thenReturn(List.of(approvedWithImages));
		when(hazardReportRepository.findAllByReportIdIn(List.of(12L)))
			.thenReturn(List.of(approvedWithImages));
		when(hazardReportImageUploadService.createReadUrl(any()))
			.thenThrow(new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL));

		HazardMarkerListResponse response = hazardReportService.getApprovedHazardMarkers(35.095, 129.095, 35.105, 129.105);

		assertThat(response.markers()).hasSize(1);
		assertThat(response.markers().get(0).reportId()).isEqualTo(12L);
		assertThat(response.markers().get(0).imageUrls()).isEmpty();
	}

	@Test
	@DisplayName("bbox 媛믪씠 ?뺤긽 踰붿쐞瑜?踰쀬낵?섎㈃ INVALID_HAZARD_REPORT_REQUEST瑜?諛쒖깮?쒕떎")
	void rejectInvalidApprovedHazardMarkerBounds() {
		assertThatThrownBy(() -> hazardReportService.getApprovedHazardMarkers(35.2, 129.0, 35.0, 129.2))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST);

		assertThatThrownBy(() -> hazardReportService.getApprovedHazardMarkers(35.0, 129.0, 35.04, 129.04))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST);

		verify(hazardReportRepository, never()).findApprovedWithinBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
	}

	private TransactionOperations testTransactionOperations() {
		return new TransactionOperations() {

			@Override
			public <T> T execute(TransactionCallback<T> action) {
				assertThat(inWriteTransaction.compareAndSet(false, true)).isTrue();
				try {
					return action.doInTransaction(new SimpleTransactionStatus());
				} finally {
					inWriteTransaction.set(false);
				}
			}
		};
	}

	private HazardReport hazardReport(User user, Long reportId, List<String> imageObjectKeys) {
		return hazardReport(user, reportId, "부산 부산진구 시민공원로 73", "보행 가능한 인도가 없습니다.", imageObjectKeys);
	}

	private HazardReport hazardReport(
		User user,
		Long reportId,
		String address,
		String description,
		List<String> imageObjectKeys) {
		HazardReport hazardReport = HazardReport.create(
			user,
			ReportType.SIDEWALK_MISSING,
			description,
			address,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			imageObjectKeys);
		ReflectionTestUtils.setField(hazardReport, "reportId", reportId);
		ReflectionTestUtils.setField(hazardReport, "createdAt", LocalDateTime.of(2026, 4, 28, 17, 0));
		return hazardReport;
	}

	private HazardReport hazardReport(
		User user,
		Long reportId,
		ReportType reportType,
		ReportStatus status,
		double lat,
		double lng,
		List<String> imageObjectKeys) {
		HazardReport hazardReport = HazardReport.create(
			user,
			reportType,
			"蹂댄뻾 媛?ν븳 ?몃룄媛 ?놁뒿?덈떎.",
			"遺??遺?곗쭊援??쒕?怨듭썝濡?73",
			geoPointConverter.toPoint(new GeoPointRequest(lat, lng)),
			imageObjectKeys);
		ReflectionTestUtils.setField(hazardReport, "reportId", reportId);
		ReflectionTestUtils.setField(hazardReport, "status", status);
		ReflectionTestUtils.setField(hazardReport, "createdAt", LocalDateTime.of(2026, 4, 28, 17, 0));
		return hazardReport;
	}

	private CreateHazardReportRequest defaultCreateRequest() {
		return new CreateHazardReportRequest(
			ReportType.SIDEWALK_MISSING,
			"보행 가능한 인도가 없습니다.",
			new GeoPointRequest(35.1686, 129.0576),
			List.of("hazard-reports/user-1/20260514/image-1.jpg"),
			List.of("hazard-reports/user-1/20260514/image-1-thumb.jpg"));
	}

	private String idempotencyRequestHash(CreateHazardReportRequest request) {
		return HazardReportIdempotencyRequestHash.from(request);
	}

	private String legacyIdempotencyRequestHash(CreateHazardReportRequest request) {
		StringBuilder canonical = new StringBuilder();
		appendPart(canonical, request.reportType().name());
		appendPart(canonical, request.description().trim());
		appendPart(canonical, request.reportPoint().lat().toString());
		appendPart(canonical, request.reportPoint().lng().toString());
		canonical.append(request.imageObjectKeys().size()).append('|');
		for (String imageObjectKey : request.imageObjectKeys()) {
			appendPart(canonical, imageObjectKey);
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private void appendPart(StringBuilder canonical, String value) {
		canonical.append(value.length()).append(':').append(value).append('|');
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
