package com.ssafy.e102.global.security.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.e102.E102Application;
import com.ssafy.e102.domain.admin.dto.response.AdminMeResponse;
import com.ssafy.e102.domain.admin.service.AdminService;
import com.ssafy.e102.domain.auth.dto.response.SocialLoginResponse;
import com.ssafy.e102.domain.auth.dto.response.TokenResponse;
import com.ssafy.e102.domain.auth.service.AuthService;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.bookmark.service.FavoriteRouteService;
import com.ssafy.e102.domain.bookmark.service.PlaceBookmarkService;
import com.ssafy.e102.domain.report.service.AdminHazardReportService;
import com.ssafy.e102.domain.report.service.HazardReportService;
import com.ssafy.e102.domain.route.service.RerouteService;
import com.ssafy.e102.domain.route.service.RouteRatingService;
import com.ssafy.e102.domain.route.service.RouteSelectService;
import com.ssafy.e102.domain.route.service.RouteSessionCommandService;
import com.ssafy.e102.domain.route.service.TransitRefreshService;
import com.ssafy.e102.domain.route.service.TransitRouteSearchService;
import com.ssafy.e102.domain.route.service.WalkRouteSearchService;
import com.ssafy.e102.domain.user.dto.response.UserMeResponse;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.service.UserService;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.domain.user.type.UserRole;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

@SpringBootTest(classes = E102Application.class, properties = {
	"cors.allowed-origins=http://localhost:3001"
})
@AutoConfigureMockMvc
class SecurityConfigTest {

	private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private AuthTokenStore authTokenStore;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private FavoriteRouteService favoriteRouteService;

	@MockitoBean
	private AdminService adminService;

	@MockitoBean
	private PlaceBookmarkService placeBookmarkService;

	@MockitoBean
	private HazardReportService hazardReportService;

	@MockitoBean
	private AdminHazardReportService adminHazardReportService;

	@MockitoBean
	private WalkRouteSearchService walkRouteSearchService;

	@MockitoBean
	private TransitRouteSearchService transitRouteSearchService;

	@MockitoBean
	private RerouteService rerouteService;

	@MockitoBean
	private RouteSelectService routeSelectService;

	@MockitoBean
	private RouteSessionCommandService routeSessionCommandService;

	@MockitoBean
	private TransitRefreshService transitRefreshService;

	@MockitoBean
	private RouteRatingService routeRatingService;

	@Test
	@DisplayName("소셜 로그인은 인증 없이 접근할 수 있다")
	void socialLoginIsPublic() throws Exception {
		when(authService.socialLogin(any()))
			.thenReturn(SocialLoginResponse.existingUser(
				"access-token",
				"refresh-token",
				UUID.randomUUID(),
				PrimaryUserType.LOW_VISION,
				null));

		mockMvc.perform(post("/auth/social-login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"socialProvider\":\"KAKAO\",\"socialAccessToken\":\"kakao-access-token\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"));
	}

	@Test
	@DisplayName("토큰 재발급은 인증 없이 접근할 수 있다")
	void reissueIsPublic() throws Exception {
		when(authService.reissue(any())).thenReturn(new TokenResponse("new-access-token", "new-refresh-token"));

		mockMvc.perform(post("/auth/reissue")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"refresh-token\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"));
	}

	@Test
	@DisplayName("actuator health endpoint는 인증 없이 접근할 수 있다")
	void actuatorHealthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").exists());
	}

	@Test
	@DisplayName("actuator prometheus endpoint는 인증 없이 접근할 수 있다")
	void actuatorPrometheusIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
	}

	@Test
	@DisplayName("내 정보 조회는 인증이 필요하다")
	void usersMeRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("로그아웃은 인증이 필요하다")
	void logoutRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/auth/logout"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("경로 북마크 API는 인증이 필요하다")
	void favoriteRoutesRequireAuthentication() throws Exception {
		mockMvc.perform(get("/favorite-routes"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("장소 북마크 API는 인증이 필요하다")
	void placeBookmarksRequireAuthentication() throws Exception {
		mockMvc.perform(get("/bookmarks"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("음성 분석 API는 인증이 필요하다")
	void voiceAnalyzeRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/voice/analyze")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "text": "부산역 어디야",
				  "mode": "LOW_VISION"
				}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("도보 경로 검색 API는 인증이 필요하다")
	void walkRouteSearchRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/routes/search/walk")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "startPoint": {"lat": 35.12, "lng": 128.936},
				  "endPoint": {"lat": 35.1315, "lng": 128.8823}
				}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("대중교통 도착정보 갱신 API는 인증이 필요하다")
	void transitRefreshRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/routes/rt_selected_001/transit-refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "legSequence": 2
				}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("경로 평가 API는 인증이 필요하다")
	void routeRatingsRequireAuthentication() throws Exception {
		mockMvc.perform(post("/route-ratings")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "sessionId": "00000000-0000-0000-0000-000000000099",
				  "score": 5
				}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("도로 상태 제보 API는 인증이 필요하다")
	void hazardReportsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/hazard-reports/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("관리자 API는 인증이 필요하다")
	void adminRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/admin/hazard-reports"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4010"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	@Test
	@DisplayName("관리자 웹 origin의 CORS preflight를 허용한다")
	void adminWebCorsPreflightIsAllowed() throws Exception {
		mockMvc.perform(options("/admin/me")
			.header(HttpHeaders.ORIGIN, "http://localhost:3001")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3001"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	@DisplayName("관리자 API는 ADMIN 권한이 필요하다")
	void adminRequiresAdminRole() throws Exception {
		String accessToken = jwtTokenProvider.createAccessToken(UUID.randomUUID());

		mockMvc.perform(get("/admin/hazard-reports")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("A4030"));
	}

	@Test
	@DisplayName("ADMIN role 사용자는 관리자 API에 접근할 수 있다")
	void adminRoleUserCanAccessAdminApi() throws Exception {
		String accessToken = jwtTokenProvider.createAccessToken(ADMIN_USER_ID);
		when(userRepository.existsByUserIdAndRole(ADMIN_USER_ID, UserRole.ADMIN)).thenReturn(true);
		when(adminService.getMe(ADMIN_USER_ID))
			.thenReturn(new AdminMeResponse(ADMIN_USER_ID, "ADMIN", List.of("HAZARD_REPORT_READ")));

		mockMvc.perform(get("/admin/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(ADMIN_USER_ID.toString()))
			.andExpect(jsonPath("$.data.role").value("ADMIN"));
	}

	@Test
	@DisplayName("유효한 액세스 토큰은 인증 주체를 만든다")
	void validAccessTokenCreatesPrincipal() throws Exception {
		UUID userId = UUID.randomUUID();
		String accessToken = jwtTokenProvider.createAccessToken(userId);
		when(userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN)).thenReturn(false);
		when(userService.getMe(userId))
			.thenReturn(new UserMeResponse(userId, SocialProvider.KAKAO, PrimaryUserType.LOW_VISION, null));

		mockMvc.perform(get("/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(userId.toString()));
	}

	@Test
	@DisplayName("블랙리스트에 있는 액세스 토큰은 인증하지 않는다")
	void blacklistedAccessTokenIsRejected() throws Exception {
		UUID userId = UUID.randomUUID();
		String accessToken = jwtTokenProvider.createAccessToken(userId);
		when(authTokenStore.containsAccessToken(accessToken)).thenReturn(true);

		mockMvc.perform(get("/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value("A4010"));
	}
}
