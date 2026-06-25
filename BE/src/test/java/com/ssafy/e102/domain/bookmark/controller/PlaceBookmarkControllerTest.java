package com.ssafy.e102.domain.bookmark.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.ssafy.e102.domain.bookmark.dto.request.CreatePlaceBookmarkRequest;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkCreateResponse;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkItemResponse;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkListResponse;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkExceptionHandler;
import com.ssafy.e102.domain.bookmark.service.PlaceBookmarkService;
import com.ssafy.e102.domain.place.dto.response.PlaceAccessibilityFeatureResponse;
import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.global.exception.GlobalExceptionHandler;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class PlaceBookmarkControllerTest {

	@Mock
	private PlaceBookmarkService placeBookmarkService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new PlaceBookmarkController(placeBookmarkService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.setControllerAdvice(new PlaceBookmarkExceptionHandler(), new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("장소 북마크 목록 조회는 cursor와 size를 서비스에 전달한다")
	void getBookmarks() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(placeBookmarkService.getBookmarks(userId, 10L, 2))
			.thenReturn(new PlaceBookmarkListResponse(
				List.of(new PlaceBookmarkItemResponse(
					1L,
					"tgt_9d13f0b44d68abcd",
					PlaceDetailType.INTERNAL_PLACE,
					10L,
					"KAKAO",
					"123456789",
					"부산시민공원",
					PlaceCategory.TOURIST_SPOT,
					null,
					"부산광역시 부산진구 시민공원로 73",
					new GeoPointResponse(35.1686, 129.0576),
					List.of(new PlaceAccessibilityFeatureResponse(AccessibilityFeatureType.accessibleToilet, true)))),
				2,
				1L,
				true));

		mockMvc.perform(get("/bookmarks")
			.param("cursor", "10")
			.param("size", "2")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.content[0].bookmarkId").value(1))
			.andExpect(jsonPath("$.data.content[0].targetType").value("INTERNAL_PLACE"))
			.andExpect(jsonPath("$.data.content[0].accessibilityFeatures[0].featureType").value("accessibleToilet"))
			.andExpect(jsonPath("$.data.nextCursor").value(1))
			.andExpect(jsonPath("$.data.hasNext").value(true));

		verify(placeBookmarkService).getBookmarks(userId, 10L, 2);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("장소 북마크 저장은 생성 응답을 반환한다")
	void createBookmark() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(placeBookmarkService.createBookmark(eq(userId), any(CreatePlaceBookmarkRequest.class)))
			.thenReturn(
				new PlaceBookmarkCreateResponse(1L, "tgt_9d13f0b44d68abcd", PlaceDetailType.EXTERNAL_POI, null));

		mockMvc.perform(post("/bookmarks")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"provider": "KAKAO",
					"providerPlaceId": "123456789",
					"name": "부산시민공원",
					"providerCategory": "여행 > 관광,명소 > 공원",
					"address": "부산광역시 부산진구 시민공원로 73",
					"point": {
						"lat": 35.1686,
						"lng": 129.0576
					}
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("S2010"))
			.andExpect(jsonPath("$.data.bookmarkId").value(1))
			.andExpect(jsonPath("$.data.bookmarkTargetId").value("tgt_9d13f0b44d68abcd"));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("장소 북마크 저장 요청값 검증 실패는 BM4000을 반환한다")
	void createBookmarkValidationErrorReturnsBookmarkErrorCode() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(post("/bookmarks")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"provider": "KAKAO",
					"name": "부산시민공원",
					"point": {
						"lat": "wrong",
						"lng": 129.0576
					}
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("BM4000"));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("공통 장소 북마크 해제는 204 No Content를 반환한다")
	void deleteBookmarkByTarget() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(delete("/bookmarks/targets/tgt_9d13f0b44d68abcd")
			.principal(authentication))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(placeBookmarkService).deleteBookmarkByTarget(userId, "tgt_9d13f0b44d68abcd");
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("공통 장소 북마크 해제 요청값 검증 실패는 BM4001을 반환한다")
	void deleteBookmarkByTargetValidationErrorReturnsBookmarkDeleteErrorCode() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(delete("/bookmarks/targets/invalid-target-id")
			.principal(authentication))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("BM4001"));

		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("내부 장소 북마크 해제는 204 No Content를 반환한다")
	void deleteBookmarkByPlaceId() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(delete("/bookmarks/places/10")
			.principal(authentication))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(placeBookmarkService).deleteBookmarkByPlaceId(userId, 10L);
		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
