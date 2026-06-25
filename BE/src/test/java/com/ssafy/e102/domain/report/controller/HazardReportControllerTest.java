package com.ssafy.e102.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportImageUploadUrlRequest;
import com.ssafy.e102.domain.report.dto.request.CreateHazardReportRequest;
import com.ssafy.e102.domain.report.dto.response.CreateHazardReportImageUploadUrlResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportIdResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.HazardMarkerListResponse;
import com.ssafy.e102.domain.report.dto.response.HazardMarkerResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportSummaryResponse;
import com.ssafy.e102.domain.report.service.HazardReportImageUploadService;
import com.ssafy.e102.domain.report.service.HazardReportService;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class HazardReportControllerTest {

	@Mock
	private HazardReportService hazardReportService;

	@Mock
	private HazardReportImageUploadService hazardReportImageUploadService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new HazardReportController(hazardReportService, hazardReportImageUploadService),
			new HazardMarkerController(hazardReportService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("제보 이미지 업로드 URL은 현재 사용자와 파일 메타데이터로 발급한다")
	void createPresignedUploadUrl() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportImageUploadService.createUploadUrl(
			eq(userId),
			any(CreateHazardReportImageUploadUrlRequest.class)))
			.thenReturn(new CreateHazardReportImageUploadUrlResponse(
				"https://storage.example.com/upload",
				"hazard-reports/%s/20260514/image.jpg".formatted(userId),
				Instant.parse("2026-05-14T01:10:00Z")));

		mockMvc.perform(post("/hazard-reports/images/presigned-upload")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fileName\":\"image.jpg\",\"contentType\":\"image/jpeg\",\"contentLength\":1024}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.uploadUrl").value("https://storage.example.com/upload"))
			.andExpect(jsonPath("$.data.imageUrl").doesNotExist())
			.andExpect(jsonPath("$.data.objectKey")
				.value("hazard-reports/%s/20260514/image.jpg".formatted(userId)))
			.andExpect(jsonPath("$.data.expiresAt").value("2026-05-14T01:10:00Z"));

		verify(hazardReportImageUploadService).createUploadUrl(
			eq(userId),
			any(CreateHazardReportImageUploadUrlRequest.class));
		SecurityContextHolder.clearContext();
	}

	@Test
	void createPresignedUploadUrls() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportImageUploadService.createUploadUrls(eq(userId), any()))
			.thenReturn(List.of(
				new CreateHazardReportImageUploadUrlResponse(
					"https://storage.example.com/upload/1",
					"hazard-reports/%s/20260514/image-1.jpg".formatted(userId),
					Instant.parse("2026-05-14T01:10:00Z")),
				new CreateHazardReportImageUploadUrlResponse(
					"https://storage.example.com/upload/2",
					"hazard-reports/%s/20260514/image-2.jpg".formatted(userId),
					Instant.parse("2026-05-14T01:10:00Z"))));

		mockMvc.perform(post("/hazard-reports/images/presigned-upload/batch")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"files\":[{\"fileName\":\"image-1.jpg\",\"contentType\":\"image/jpeg\",\"contentLength\":1024},{\"fileName\":\"image-2.jpg\",\"contentType\":\"image/jpeg\",\"contentLength\":2048}]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.uploads[0].uploadUrl").value("https://storage.example.com/upload/1"))
			.andExpect(jsonPath("$.data.uploads[1].objectKey").value("hazard-reports/%s/20260514/image-2.jpg".formatted(userId)));
	}

	@Test
	@DisplayName("도로 상태 제보 등록은 현재 사용자와 요청 본문으로 생성 응답을 반환한다")
	void createHazardReport() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportService.createHazardReport(
			eq(userId),
			any(CreateHazardReportRequest.class),
			eq("outbox-report-1")))
			.thenReturn(new HazardReportIdResponse(1L));

		mockMvc.perform(post("/hazard-reports")
			.principal(authentication)
			.header("Idempotency-Key", "outbox-report-1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reportType\":\"SIDEWALK_MISSING\",\"description\":\"보행 가능한 인도가 없습니다.\","
				+ "\"reportPoint\":{\"lat\":35.1686,\"lng\":129.0576},"
				+ "\"imageObjectKeys\":[\"hazard-reports/%s/20260514/image-1.jpg\"],"
				+ "\"thumbnailObjectKeys\":[\"hazard-reports/%s/20260514/image-1-thumb.jpg\"]}".formatted(userId, userId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.reportId").value(1));

		verify(hazardReportService).createHazardReport(
			eq(userId),
			any(CreateHazardReportRequest.class),
			eq("outbox-report-1"));
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Idempotency-Key가 없으면 null로 제보 등록 서비스에 전달한다")
	void createHazardReportWithoutIdempotencyKey() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportService.createHazardReport(
			eq(userId),
			any(CreateHazardReportRequest.class),
			isNull()))
			.thenReturn(new HazardReportIdResponse(1L));

		mockMvc.perform(post("/hazard-reports")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reportType\":\"SIDEWALK_MISSING\",\"description\":\"보행 가능한 인도가 없습니다.\","
				+ "\"reportPoint\":{\"lat\":35.1686,\"lng\":129.0576},"
				+ "\"imageObjectKeys\":[],"
				+ "\"thumbnailObjectKeys\":[]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.reportId").value(1));

		verify(hazardReportService).createHazardReport(
			eq(userId),
			any(CreateHazardReportRequest.class),
			isNull());
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("내 제보 목록은 현재 사용자의 cursor 목록을 반환한다")
	void getMyHazardReports() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportService.getMyHazardReports(userId, null, 10))
			.thenReturn(new HazardReportListResponse(
				List.of(new HazardReportSummaryResponse(
					1L,
					ReportType.SIDEWALK_MISSING,
					ReportStatus.PENDING,
					"부산 부산진구 시민공원로 73",
					"보행 가능한 인도가 없습니다.",
					new GeoPointResponse(35.1686, 129.0576),
					LocalDateTime.of(2026, 4, 28, 17, 0),
					"https://example.com/reports/1/image-1.jpg")),
				10,
				null,
				false));

		mockMvc.perform(get("/hazard-reports/me")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.content[0].reportId").value(1))
			.andExpect(jsonPath("$.data.content[0].reportType").value("SIDEWALK_MISSING"))
			.andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
			.andExpect(jsonPath("$.data.content[0].address").value("부산 부산진구 시민공원로 73"))
			.andExpect(jsonPath("$.data.content[0].description").value("보행 가능한 인도가 없습니다."))
			.andExpect(jsonPath("$.data.content[0].reportPoint.lat").value(35.1686))
			.andExpect(jsonPath("$.data.content[0].representativeImageUrl")
				.value("https://example.com/reports/1/image-1.jpg"))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.data.hasNext").value(false));

		verify(hazardReportService).getMyHazardReports(userId, null, 10);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("내 제보 목록은 cursor와 size를 전달한다")
	void getMyHazardReportsWithCursor() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportService.getMyHazardReports(userId, 10L, 2))
			.thenReturn(new HazardReportListResponse(
				List.of(new HazardReportSummaryResponse(
					3L,
					ReportType.RAMP,
					ReportStatus.APPROVED,
					null,
					null,
					new GeoPointResponse(35.1686, 129.0576),
					LocalDateTime.of(2026, 4, 28, 17, 0),
					null)),
				2,
				3L,
				true));

		mockMvc.perform(get("/hazard-reports/me")
			.param("cursor", "10")
			.param("size", "2")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].reportId").value(3))
			.andExpect(jsonPath("$.data.nextCursor").value(3))
			.andExpect(jsonPath("$.data.hasNext").value(true));

		verify(hazardReportService).getMyHazardReports(userId, 10L, 2);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("내 제보 상세는 조회용 presigned 이미지 URL을 반환한다")
	void getMyHazardReportDetail() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportService.getMyHazardReportDetail(userId, 1L))
			.thenReturn(new HazardReportDetailResponse(
				1L,
				ReportType.SIDEWALK_MISSING,
				ReportStatus.PENDING,
				"보행 가능한 인도가 없습니다.",
				new GeoPointResponse(35.1686, 129.0576),
				LocalDateTime.of(2026, 4, 28, 17, 0),
				List.of(
					"https://example.com/reports/1/image-1.jpg",
					"https://example.com/reports/1/image-2.jpg")));

		mockMvc.perform(get("/hazard-reports/me/1")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.reportId").value(1))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.imageUrls[0]").value("https://example.com/reports/1/image-1.jpg"))
			.andExpect(jsonPath("$.data.imageUrls[1]").value("https://example.com/reports/1/image-2.jpg"));

		verify(hazardReportService).getMyHazardReportDetail(userId, 1L);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("吏?꾨룄 bbox ?덈뿉??듭씤 ?쒕낫 留ㅼ빱 紐⑸줉??諛섑솚?쒕떎")
	void getApprovedHazardMarkers() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportService.getApprovedHazardMarkers(35.095, 129.095, 35.105, 129.105))
			.thenReturn(new HazardMarkerListResponse(
				List.of(
					new HazardMarkerResponse(
						12L,
						ReportType.RAMP,
						35.1,
						129.1,
						"보행로에 임시 장애물이 있습니다.",
						List.of("https://example.com/reports/12/image-1-thumb.jpg"),
						List.of("https://example.com/reports/12/image-1.jpg")))));

		mockMvc.perform(get("/hazard/markers/")
			.principal(authentication)
			.param("swLat", "35.095")
			.param("swLng", "129.095")
			.param("neLat", "35.105")
			.param("neLng", "129.105"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.markers[0].reportId").value(12))
			.andExpect(jsonPath("$.data.markers[0].reportType").value("RAMP"))
			.andExpect(jsonPath("$.data.markers[0].lat").value(35.1))
			.andExpect(jsonPath("$.data.markers[0].lng").value(129.1))
			.andExpect(jsonPath("$.data.markers[0].description").value("보행로에 임시 장애물이 있습니다."))
			.andExpect(jsonPath("$.data.markers[0].thumbnailUrls[0]").value("https://example.com/reports/12/image-1-thumb.jpg"))
			.andExpect(jsonPath("$.data.markers[0].imageUrls[0]").value("https://example.com/reports/12/image-1.jpg"));

		verify(hazardReportService).getApprovedHazardMarkers(35.095, 129.095, 35.105, 129.105);
		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
