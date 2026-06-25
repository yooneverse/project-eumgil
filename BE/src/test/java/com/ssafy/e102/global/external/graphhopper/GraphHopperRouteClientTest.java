package com.ssafy.e102.global.external.graphhopper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class GraphHopperRouteClientTest {

	private RestTemplate restTemplate;
	private MockRestServiceServer server;
	private GraphHopperRouteClient client;

	@BeforeEach
	void setUp() {
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.createServer(restTemplate);
		client = new GraphHopperRouteClient(restTemplate, properties());
	}

	@Test
	@DisplayName("active GraphHopper 5xx 실패 시 previous slot으로 1회 재시도한다")
	void routeRetriesPreviousEndpointWhenActiveReturnsServerError() {
		client = new GraphHopperRouteClient(
			restTemplate,
			() -> new GraphHopperEndpointSelection(
				"http://graphhopper-green.test",
				"http://graphhopper-blue.test",
				"green",
				"blue"),
			new ObjectMapper());

		String query = "/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state";
		server.expect(requestTo("http://graphhopper-green.test" + query))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-blue.test" + query))
			.andRespond(withSuccess("""
				{
				  "paths": [
				    {
				      "distance": 120.0,
				      "time": 90000,
				      "points": {
				        "type": "LineString",
				        "coordinates": [[128.936,35.12],[128.8823,35.1315]]
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		GraphHopperRoutePath path = client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE));

		assertThat(path.distanceMeter()).isEqualByComparingTo(BigDecimal.valueOf(120.0));
		server.verify();
	}

	@Test
	@DisplayName("active GraphHopper 설정 오류 4xx는 no-route가 아니면 previous slot으로 재시도한다")
	void routeRetriesPreviousEndpointWhenActiveReturnsNonNoRouteClientError() {
		client = new GraphHopperRouteClient(
			restTemplate,
			() -> new GraphHopperEndpointSelection(
				"http://graphhopper-green.test",
				"http://graphhopper-blue.test",
				"green",
				"blue"),
			new ObjectMapper());

		String query = "/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state";
		server.expect(requestTo("http://graphhopper-green.test" + query))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{
					  "message": "Cannot find profile pedestrian_safe",
					  "hints": [
					    {
					      "message": "Cannot find profile pedestrian_safe",
					      "details": "java.lang.IllegalArgumentException"
					    }
					  ]
					}
					"""));
		server.expect(requestTo("http://graphhopper-blue.test" + query))
			.andRespond(withSuccess("""
				{
				  "paths": [
				    {
				      "distance": 125.0,
				      "time": 93000,
				      "points": {
				        "type": "LineString",
				        "coordinates": [[128.936,35.12],[128.8823,35.1315]]
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		GraphHopperRoutePath path = client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE));

		assertThat(path.distanceMeter()).isEqualByComparingTo(BigDecimal.valueOf(125.0));
		server.verify();
	}

	@Test
	@DisplayName("GraphHopper route API를 profile과 좌표 query로 호출하고 첫 path를 반환한다")
	void routeCallsGraphHopperAndParsesFirstPath() {
		server.expect(requestTo("http://graphhopper.test/route?profile=visual_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("profile", "visual_safe"))
			.andExpect(queryParam("points_encoded", "false"))
			.andRespond(withSuccess("""
				{
				  "paths": [
				    {
				      "distance": 950.5,
				      "time": 960000,
				      "points": {
				        "type": "LineString",
				        "coordinates": [[128.936,35.12],[128.8823,35.1315]]
				      },
				      "details": {
				        "segment_type": [[0,1,"CROSS_WALK"]],
				        "avg_slope_percent": [[0,1,6.5]]
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		GraphHopperRoutePath path = client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.VISUAL_SAFE));

		assertThat(path.distanceMeter()).isEqualByComparingTo(BigDecimal.valueOf(950.5));
		assertThat(path.timeMs()).isEqualTo(960000);
		assertThat(path.coordinates()).hasSize(2);
		assertThat(path.coordinates().get(0).lng()).isEqualByComparingTo("128.936");
		assertThat(path.details().get("segment_type").get(0).value()).isEqualTo("CROSS_WALK");
		assertThat(path.details().get("avg_slope_percent").get(0).value()).isEqualTo("6.5");
		server.verify();
	}

	@Test
	@DisplayName("GraphHopper 응답에 path가 없으면 RT4040으로 매핑한다")
	void routeMapsEmptyPathsToRouteNotFound() {
		server.expect(requestTo("http://graphhopper.test/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andRespond(withSuccess("{\"paths\":[]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND);
	}

	@Test
	@DisplayName("초기 경로 검색에서는 GraphHopper가 멀리 snap한 경로도 반환한다")
	void routeAllowsFarSnappedWaypoint() {
		server.expect(requestTo("http://graphhopper.test/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andRespond(withSuccess("""
				{
				  "paths": [
				    {
				      "distance": 950.5,
				      "time": 960000,
				      "points": {
				        "type": "LineString",
				        "coordinates": [[128.936,35.12],[128.8823,35.1315]]
				      },
				      "snapped_waypoints": {
				        "type": "LineString",
				        "coordinates": [[128.9365,35.1205],[128.8823,35.1315]]
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		GraphHopperRoutePath path = client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE,
			false));

		assertThat(path.distanceMeter()).isEqualByComparingTo(BigDecimal.valueOf(950.5));
		assertThat(path.coordinates()).hasSize(2);
	}

	@Test
	@DisplayName("GraphHopper path detail에 walk_access=NO가 포함돼도 path가 있으면 경로를 반환한다")
	void routeSucceedsEvenWhenWalkAccessDetailContainsNo() {
		server.expect(requestTo("http://graphhopper.test/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andRespond(withSuccess("""
				{
				  "paths": [
				    {
				      "distance": 950.5,
				      "time": 960000,
				      "points": {
				        "type": "LineString",
				        "coordinates": [[128.936,35.12],[128.8823,35.1315]]
				      },
				      "details": {
				        "walk_access": [[0,1,"NO"]]
				      }
				    }
				  ]
				}
				""", MediaType.APPLICATION_JSON));

		GraphHopperRoutePath path = client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE));

		assertThat(path.distanceMeter()).isEqualByComparingTo(BigDecimal.valueOf(950.5));
		assertThat(path.details().get("walk_access")).hasSize(1);
		assertThat(path.details().get("walk_access").get(0).value()).isEqualTo("NO");
	}

	@Test
	@DisplayName("GraphHopper HTTP 실패는 EX5020으로 매핑한다")
	void routeMapsHttpFailureToExternalRouteApiFailed() {
		server.expect(requestTo("http://graphhopper.test/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED);
	}

	@Test
	@DisplayName("GraphHopper ConnectionNotFoundException은 RT4040으로 매핑한다")
	void routeMapsConnectionNotFoundToRouteNotFound() {
		server.expect(requestTo("http://graphhopper.test/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{
					  "message": "Connection between locations not found",
					  "hints": [
					    {
					      "message": "Connection between locations not found",
					      "details": "com.graphhopper.util.exceptions.ConnectionNotFoundException"
					    }
					  ]
					}
					"""));

		assertThatThrownBy(() -> client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND);
	}

	@Test
	@DisplayName("GraphHopper profile/parameter 오류는 EX5020으로 유지한다")
	void routeKeepsBadRequestWithoutNoRouteHintAsExternalRouteApiFailed() {
		server.expect(requestTo("http://graphhopper.test/route?profile=pedestrian_safe&point=35.12,128.936&"
			+ "point=35.1315,128.8823&points_encoded=false&locale=ko-KR&details=edge_id&details=walk_access&"
			+ "details=segment_type&details=signal_state&details=audio_signal_state&details=avg_slope_percent&details=width_state&details=surface_state&details=stairs_state"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{
					  "message": "Cannot find profile pedestrian_safe",
					  "hints": [
					    {
					      "message": "Cannot find profile pedestrian_safe",
					      "details": "java.lang.IllegalArgumentException"
					    }
					  ]
					}
					"""));

		assertThatThrownBy(() -> client.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED);
	}

	@Test
	@DisplayName("GraphHopper timeout은 EX5040으로 매핑한다")
	void routeMapsTimeoutToExternalRouteApiTimeout() {
		GraphHopperRouteClient timeoutClient = new GraphHopperRouteClient(new TimeoutRestTemplate(), properties());

		assertThatThrownBy(() -> timeoutClient.route(new GraphHopperRouteRequest(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			WalkRouteProfile.PEDESTRIAN_SAFE)))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT);
	}

	private GraphHopperProperties properties() {
		return new GraphHopperProperties(
			"http://graphhopper.test",
			Duration.ofSeconds(5),
			Duration.ofSeconds(5),
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null);
	}

	private static class TimeoutRestTemplate extends RestTemplate {

		@Override
		public <T> org.springframework.http.ResponseEntity<T> exchange(
			RequestEntity<?> entity,
			Class<T> responseType) {
			throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
		}
	}
}
