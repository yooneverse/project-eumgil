package com.ssafy.e102.domain.place.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.place.dto.request.PlaceClickDetailRequest;
import com.ssafy.e102.domain.place.dto.response.PlaceClickDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceDetailResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceListResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceMarkerResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceReverseGeocodeResponse;
import com.ssafy.e102.domain.place.dto.response.PlaceSearchResponse;
import com.ssafy.e102.domain.place.service.PlaceService;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceClickType;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.domain.place.type.PlaceMarkerKind;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class PlaceControllerTest {

	@Mock
	private PlaceService placeService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new PlaceController(placeService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("장소 검색은 query parameter를 서비스에 전달한다")
	void searchPlaces() throws Exception {
		when(placeService.searchPlaces(
			"부산시민공원",
			"35.1686",
			"129.0576",
			"1000",
			"next-cursor",
			"distance",
			"10"))
			.thenReturn(new PlaceSearchResponse(List.of(), null, 10, 0, false));

		mockMvc.perform(get("/places/search")
			.param("keyword", "부산시민공원")
			.param("lat", "35.1686")
			.param("lng", "129.0576")
			.param("radius", "1000")
			.param("cursor", "next-cursor")
			.param("sort", "distance")
			.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("좌표 주소 변환은 query parameter를 서비스에 전달한다")
	void reverseGeocode() throws Exception {
		when(placeService.reverseGeocode("35.1686", "129.0576"))
			.thenReturn(new PlaceReverseGeocodeResponse(
				"부산 부산진구 시민공원로 73",
				"부산 부산진구 시민공원로 73",
				"부산 부산진구 범전동 200",
				"부산",
				"부산진구",
				"범전동"));

		mockMvc.perform(get("/places/reverse-geocode")
			.param("lat", "35.1686")
			.param("lng", "129.0576"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.displayAddress").value("부산 부산진구 시민공원로 73"))
			.andExpect(jsonPath("$.data.address").value("부산 부산진구 범전동 200"));

		verify(placeService).reverseGeocode("35.1686", "129.0576");
	}

	@Test
	@DisplayName("주변 시설 조회는 현재 사용자와 필터를 서비스에 전달한다")
	void getPlaces() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(placeService.getPlaces(
			userId,
			"35.1686",
			"129.0576",
			"500",
			"TOURIST_SPOT",
			"accessibleToilet"))
			.thenReturn(new PlaceListResponse(List.of(new PlaceMarkerResponse(
				10L,
				"부산시민공원",
				PlaceCategory.TOURIST_SPOT,
				"부산광역시",
				new GeoPointResponse(35.1686, 129.0576),
				List.of(),
				true,
				PlaceMarkerKind.DEFAULT))));

		mockMvc.perform(get("/places")
			.principal(authentication)
			.param("lat", "35.1686")
			.param("lng", "129.0576")
			.param("radius", "500")
			.param("category", "TOURIST_SPOT")
			.param("featureType", "accessibleToilet"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.places[0].placeId").value(10))
			.andExpect(jsonPath("$.data.places[0].isBookmarked").value(true))
			.andExpect(jsonPath("$.data.places[0].markerKind").value("DEFAULT"));

		verify(placeService).getPlaces(userId, "35.1686", "129.0576", "500", "TOURIST_SPOT", "accessibleToilet");
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("시설 상세 조회는 현재 사용자와 placeId를 서비스에 전달한다")
	void getPlace() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(placeService.getPlace(userId, "10"))
			.thenReturn(new PlaceDetailResponse(
				10L,
				"부산시민공원",
				PlaceCategory.TOURIST_SPOT,
				"부산광역시",
				new GeoPointResponse(35.1686, 129.0576),
				"123456789",
				List.of(),
				false));

		mockMvc.perform(get("/places/10")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.placeId").value(10))
			.andExpect(jsonPath("$.data.isBookmarked").value(false));

		verify(placeService).getPlace(userId, "10");
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("지도 클릭 상세 조회는 현재 사용자와 request body를 서비스에 전달한다")
	void getPlaceDetail() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		PlaceClickDetailRequest request = new PlaceClickDetailRequest(
			35.1686,
			129.0576,
			PlaceClickType.POI,
			"KAKAO",
			"123456789",
			"부산시민공원");
		when(placeService.getPlaceDetail(userId, request))
			.thenReturn(new PlaceClickDetailResponse(
				"tgt_9d13f0b44d68abcd",
				PlaceDetailType.EXTERNAL_POI,
				null,
				"KAKAO",
				"123456789",
				"부산시민공원",
				null,
				"여행 > 관광,명소 > 공원",
				"051-123-4567",
				"부산광역시 부산진구 시민공원로 73",
				new GeoPointResponse(35.1686, 129.0576),
				List.of(),
				false));

		mockMvc.perform(post("/places/detail")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "lat": 35.1686,
				  "lng": 129.0576,
				  "clickType": "POI",
				  "provider": "KAKAO",
				  "providerPlaceId": "123456789",
				  "nameHint": "부산시민공원"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.bookmarkTargetId").value("tgt_9d13f0b44d68abcd"))
			.andExpect(jsonPath("$.data.detailType").value("EXTERNAL_POI"))
			.andExpect(jsonPath("$.data.name").value("부산시민공원"))
			.andExpect(jsonPath("$.data.phone").value("051-123-4567"));

		verify(placeService).getPlaceDetail(userId, request);
		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
