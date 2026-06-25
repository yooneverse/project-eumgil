package com.ssafy.e102.global.external.graphhopper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient.GraphHopperReloadResult;
import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient.GraphHopperReloadStatus;

class GraphHopperAdminClientTest {

	private RestTemplate restTemplate;
	private MockRestServiceServer server;
	private GraphHopperAdminClient client;

	@BeforeEach
	void setUp() {
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.createServer(restTemplate);
		client = new GraphHopperAdminClient(
			restTemplate,
			new GraphHopperProperties(
				"http://graphhopper-green.test",
				java.time.Duration.ofSeconds(5),
				java.time.Duration.ofSeconds(5),
				"graphhopper:active-slot",
				"graphhopper:previous-slot",
				"graphhopper:blue:url",
				"graphhopper:green:url",
				"http://graphhopper-blue.test",
				"http://graphhopper-green.test",
				"http://localhost:8990/healthcheck",
				"http://graphhopper-blue:8990/healthcheck",
				"http://graphhopper-green:8990/healthcheck"),
			() -> new GraphHopperEndpointSelection(
				"http://graphhopper-green.test",
				"http://graphhopper-blue.test",
				"green",
				"blue"));
	}

	@Test
	@DisplayName("GraphHopper override reload는 active와 previous slot에 모두 적용한다")
	void reloadRoutingOverridesAppliesToActiveAndPreviousSlots() {
		server.expect(requestTo("http://graphhopper-green.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://graphhopper-blue.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		GraphHopperReloadResult result = client.reloadRoutingOverrides();

		assertThat(result.status()).isEqualTo(GraphHopperReloadStatus.APPLIED);
		assertThat(result.message()).contains("green", "blue");
		server.verify();
	}

	@Test
	@DisplayName("GraphHopper override reload에서 일부 slot만 성공하면 경고 상태를 반환한다")
	void reloadRoutingOverridesReturnsAppliedWithWarningWhenPartialSuccess() {
		server.expect(requestTo("http://graphhopper-green.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://graphhopper-blue.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-blue.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());

		GraphHopperReloadResult result = client.reloadRoutingOverrides();

		assertThat(result.status()).isEqualTo(GraphHopperReloadStatus.APPLIED_WITH_WARNING);
		assertThat(result.message()).contains("green", "slot=blue");
		server.verify();
	}

	@Test
	@DisplayName("GraphHopper override reload는 active 실패 후 previous만 성공해도 FAILED를 반환한다")
	void reloadRoutingOverridesReturnsFailedWhenOnlyPreviousSucceeds() {
		server.expect(requestTo("http://graphhopper-green.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-green.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-blue.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		GraphHopperReloadResult result = client.reloadRoutingOverrides();

		assertThat(result.status()).isEqualTo(GraphHopperReloadStatus.FAILED);
		assertThat(result.message()).contains("slot=green");
		server.verify();
	}

	@Test
	@DisplayName("GraphHopper override reload 실패 시 동일 endpoint로 즉시 재시도하고 FAILED를 반환한다")
	void reloadRoutingOverridesRetriesAndReturnsFailed() {
		server.expect(requestTo("http://graphhopper-green.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-green.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-blue.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		server.expect(requestTo("http://graphhopper-blue.test/ieum/admin/overrides/reload"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());

		GraphHopperReloadResult result = client.reloadRoutingOverrides();

		assertThat(result.status()).isEqualTo(GraphHopperReloadStatus.FAILED);
		assertThat(result.message()).contains("slot=green", "slot=blue");
		server.verify();
	}
}
