package com.ssafy.e102.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class KakaoLocalClientTest {

	private MockRestServiceServer server;
	private KakaoLocalClient client;

	@BeforeEach
	void setUp() {
		client = new KakaoLocalClient(new RestTemplateBuilder(), new KakaoLocalProperties(
			"https://dapi.kakao.com",
			"test-rest-api-key"));
		RestTemplate restTemplate = (RestTemplate)ReflectionTestUtils.getField(client, "restTemplate");
		server = MockRestServiceServer.bindTo(restTemplate).build();
	}

	@Test
	@DisplayName("카카오 키워드 검색 API는 좌표 기반 거리 정렬 파라미터를 전달한다")
	void searchKeywordCallsKakaoKeywordSearchWithDistanceSort() {
		server.expect(requestTo("https://dapi.kakao.com/v2/local/search/keyword.json"
			+ "?query=%EC%82%BC%EC%84%B1%EC%A0%84%EA%B8%B0&page=1&size=15&y=35.1&x=128.9&sort=distance"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-rest-api-key"))
			.andRespond(withSuccess("""
				{
					"meta": {
						"pageable_count": 1,
						"is_end": true
					},
					"documents": [
						{
							"id": "1",
							"place_name": "삼성전기 부산사업장",
							"road_address_name": "부산 강서구 녹산산업중로 333",
							"address_name": "부산 강서구 송정동 1600",
							"category_name": "회사",
							"phone": "",
							"x": "128.9",
							"y": "35.1",
							"distance": "4072"
						}
					]
				}
				""", MediaType.APPLICATION_JSON));

		KakaoPlaceSearchResult result = client.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기",
			35.1,
			128.9,
			null,
			1,
			15,
			"distance"));

		assertThat(result.documents())
			.extracting(KakaoPlaceDocument::placeName)
			.containsExactly("삼성전기 부산사업장");
		assertThat(result.documents().get(0).distanceMeter()).isEqualTo(4072);
		server.verify();
	}

	@Test
	@DisplayName("카카오 키워드 검색 API는 rect 제한 검색 파라미터를 전달한다")
	void searchKeywordCallsKakaoKeywordSearchWithRect() {
		server.expect(requestTo("https://dapi.kakao.com/v2/local/search/keyword.json"
			+ "?query=%EC%82%BC%EC%84%B1%EC%A0%84%EA%B8%B0&page=1&size=15&rect=128.75,34.85,129.35,35.40&sort=accuracy"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-rest-api-key"))
			.andRespond(withSuccess("""
				{
					"meta": {
						"pageable_count": 1,
						"is_end": true
					},
					"documents": [
						{
							"id": "1",
							"place_name": "삼성전기 부산사업장",
							"road_address_name": "부산 강서구 녹산산업중로 333",
							"address_name": "부산 강서구 송정동 1600",
							"category_name": "회사",
							"phone": "",
							"x": "128.9",
							"y": "35.1",
							"distance": ""
						}
					]
				}
				""", MediaType.APPLICATION_JSON));

		KakaoPlaceSearchResult result = client.searchKeyword(new KakaoPlaceSearchRequest(
			"삼성전기",
			null,
			null,
			null,
			"128.75,34.85,129.35,35.40",
			1,
			15,
			"accuracy"));

		assertThat(result.documents())
			.extracting(KakaoPlaceDocument::placeName)
			.containsExactly("삼성전기 부산사업장");
		server.verify();
	}

	@Test
	@DisplayName("카카오 좌표 주소 변환 API를 경도 x, 위도 y로 호출하고 도로명 주소를 우선 반환한다")
	void reverseGeocodeCallsKakaoCoordToAddress() {
		server.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2address.json"
			+ "?x=129.0576&y=35.1686&input_coord=WGS84"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-rest-api-key"))
			.andRespond(withSuccess("""
				{
				  "meta": {
				    "total_count": 1
				  },
				  "documents": [
				    {
				      "road_address": {
				        "address_name": "부산 부산진구 시민공원로 73",
				        "building_name": "부산시민공원",
				        "region_1depth_name": "부산",
				        "region_2depth_name": "부산진구",
				        "region_3depth_name": "범전동"
				      },
				      "address": {
				        "address_name": "부산 부산진구 범전동 200",
				        "region_1depth_name": "부산",
				        "region_2depth_name": "부산진구",
				        "region_3depth_name": "범전동"
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		Optional<KakaoAddressDocument> result = client.reverseGeocode(35.1686, 129.0576);

		assertThat(result).contains(new KakaoAddressDocument(
			"부산 부산진구 범전동 200",
			"부산 부산진구 시민공원로 73",
			"부산시민공원",
			"부산",
			"부산진구",
			"범전동"));
		server.verify();
	}

	@Test
	@DisplayName("카카오 좌표 주소 변환 결과에 도로명 주소가 없으면 지번 주소를 표시 주소로 사용한다")
	void reverseGeocodeFallbacksToAddress() {
		server.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2address.json"
			+ "?x=129.0576&y=35.1686&input_coord=WGS84"))
			.andRespond(withSuccess("""
				{
				  "meta": {
				    "total_count": 1
				  },
				  "documents": [
				    {
				      "road_address": null,
				      "address": {
				        "address_name": "부산 부산진구 범전동 200",
				        "region_1depth_name": "부산",
				        "region_2depth_name": "부산진구",
				        "region_3depth_name": "범전동"
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		KakaoAddressDocument result = client.reverseGeocode(35.1686, 129.0576).orElseThrow();

		assertThat(result.displayAddress()).isEqualTo("부산 부산진구 범전동 200");
		assertThat(result.roadAddress()).isNull();
	}

	@Test
	@DisplayName("카카오 카테고리 검색 API를 category_group_code와 좌표로 호출한다")
	void searchCategoryCallsKakaoCategorySearch() {
		server.expect(requestTo("https://dapi.kakao.com/v2/local/search/category.json"
			+ "?category_group_code=SW8&x=128.984611&y=35.162166&page=1&size=5&radius=300"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-rest-api-key"))
			.andRespond(withSuccess("""
				{
				  "meta": {
				    "pageable_count": 1,
				    "is_end": true
				  },
				  "documents": [
				    {
				      "id": "21160880",
				      "place_name": "사상역 부산2호선",
				      "road_address_name": "부산 사상구 사상로 지하 203",
				      "address_name": "부산 사상구 괘법동 529-1",
				      "category_name": "교통,수송 > 지하철,전철 > 부산2호선",
				      "phone": "051-678-6191",
				      "x": "128.984611",
				      "y": "35.162166",
				      "distance": "17"
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		KakaoPlaceSearchResult result = client.searchCategory("SW8", 35.162166, 128.984611, 300, 1, 5);

		assertThat(result.totalElements()).isEqualTo(1);
		assertThat(result.isEnd()).isTrue();
		assertThat(result.documents())
			.extracting(KakaoPlaceDocument::placeName)
			.isEqualTo(List.of("사상역 부산2호선"));
		assertThat(result.documents().get(0).phone()).isEqualTo("051-678-6191");
		server.verify();
	}
}
