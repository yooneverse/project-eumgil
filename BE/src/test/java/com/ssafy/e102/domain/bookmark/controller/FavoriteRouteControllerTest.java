package com.ssafy.e102.domain.bookmark.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.bookmark.dto.request.CreateFavoriteRouteRequest;
import com.ssafy.e102.domain.bookmark.dto.request.UpdateFavoriteRouteRequest;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteDetailResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteIdResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteListResponse;
import com.ssafy.e102.domain.bookmark.dto.response.FavoriteRouteResponse;
import com.ssafy.e102.domain.bookmark.service.FavoriteRouteService;
import com.ssafy.e102.domain.bookmark.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class FavoriteRouteControllerTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Mock
	private FavoriteRouteService favoriteRouteService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new FavoriteRouteController(favoriteRouteService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("경로 북마크 목록 조회는 현재 사용자의 cursor 목록을 반환한다")
	void getFavoriteRoutes() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(favoriteRouteService.getFavoriteRoutes(userId, null, 10))
			.thenReturn(new FavoriteRouteListResponse(
				List.of(new FavoriteRouteResponse(
					1L,
					"부산시민공원-부산역",
					"부산시민공원",
					"부산역",
					new GeoPointResponse(35.1686, 129.0576),
					new GeoPointResponse(35.1152, 129.0422),
					TransportMode.WALK,
					RouteOption.SAFE)),
				10,
				null,
				false));

		mockMvc.perform(get("/favorite-routes")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.content[0].favRouteId").value(1))
			.andExpect(jsonPath("$.data.content[0].routeName").value("부산시민공원-부산역"))
			.andExpect(jsonPath("$.data.content[0].startPoint.lat").value(35.1686))
			.andExpect(jsonPath("$.data.content[0].startPoint.lng").value(129.0576))
			.andExpect(jsonPath("$.data.content[0].transportMode").value("WALK"))
			.andExpect(jsonPath("$.data.content[0].routeOption").value("SAFE"))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.data.hasNext").value(false));

		verify(favoriteRouteService).getFavoriteRoutes(userId, null, 10);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 북마크 목록 조회는 cursor와 size를 전달한다")
	void getFavoriteRoutesWithCursor() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(favoriteRouteService.getFavoriteRoutes(userId, 10L, 2))
			.thenReturn(new FavoriteRouteListResponse(
				List.of(new FavoriteRouteResponse(
					3L,
					"서면역-부산역",
					"서면역",
					"부산역",
					new GeoPointResponse(35.1577, 129.0592),
					new GeoPointResponse(35.1152, 129.0422),
					TransportMode.WALK,
					RouteOption.SHORTEST)),
				2,
				3L,
				true));

		mockMvc.perform(get("/favorite-routes")
			.param("cursor", "10")
			.param("size", "2")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.content[0].favRouteId").value(3))
			.andExpect(jsonPath("$.data.nextCursor").value(3))
			.andExpect(jsonPath("$.data.hasNext").value(true));

		verify(favoriteRouteService).getFavoriteRoutes(userId, 10L, 2);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 북마크 상세 조회는 저장된 route snapshot을 반환한다")
	void getFavoriteRouteDetail() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(favoriteRouteService.getFavoriteRouteDetail(userId, 1L))
			.thenReturn(new FavoriteRouteDetailResponse(
				1L,
				"부산시민공원-부산역",
				"부산시민공원",
				"부산역",
				new GeoPointResponse(35.1686, 129.0576),
				new GeoPointResponse(35.1152, 129.0422),
				TransportMode.WALK,
				RouteOption.SAFE,
				OBJECT_MAPPER.createObjectNode()
					.put("routeId", "walk_rt_safe_001")
					.put("transportMode", "WALK")
					.put("routeOption", "SAFE")));

		mockMvc.perform(get("/favorite-routes/1")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.favRouteId").value(1))
			.andExpect(jsonPath("$.data.route.routeId").value("walk_rt_safe_001"));

		verify(favoriteRouteService).getFavoriteRouteDetail(userId, 1L);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 북마크 저장은 routeId와 표시명을 받아 생성 응답을 반환한다")
	void createFavoriteRoute() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(favoriteRouteService.createFavoriteRoute(
			eq(userId),
			any(CreateFavoriteRouteRequest.class)))
			.thenReturn(new FavoriteRouteIdResponse(1L));

		mockMvc.perform(post("/favorite-routes")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"routeId\":\"walk_rt_safe_001\",\"startLabel\":\"부산시민공원\",\"endLabel\":\"부산역\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.favRouteId").value(1));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 북마크 수정은 수정된 경로 북마크 ID를 반환한다")
	void updateFavoriteRoute() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(favoriteRouteService.updateFavoriteRoute(
			eq(userId),
			eq(1L),
			any(UpdateFavoriteRouteRequest.class)))
			.thenReturn(new FavoriteRouteIdResponse(1L));

		mockMvc.perform(patch("/favorite-routes/1")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"startLabel\":\"서면역\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.favRouteId").value(1));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("경로 북마크 삭제는 성공 메시지를 반환한다")
	void deleteFavoriteRoute() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(delete("/favorite-routes/1")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.message").value("경로 북마크가 삭제되었습니다."));

		verify(favoriteRouteService).deleteFavoriteRoute(userId, 1L);
		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
