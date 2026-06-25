package com.ssafy.e102.global.external.graphhopper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GraphHopperActiveHealthCheckerTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
		.withBean(GraphHopperProperties.class, this::properties)
		.withBean(GraphHopperEndpointProvider.class,
			() -> () -> new GraphHopperEndpointSelection("http://graphhopper-blue.test:8989", null, "blue", null))
		.withBean(GraphHopperActiveHealthChecker.class);

	@Test
	@DisplayName("Spring context가 생성자 주입으로 health checker bean을 생성한다")
	void springContextCreatesHealthChecker() {
		contextRunner.run(context -> assertThat(context).hasSingleBean(GraphHopperActiveHealthChecker.class));
	}

	@Test
	@DisplayName("active slot GraphHopper healthcheck가 성공하면 UP을 반환한다")
	void activeSlotHealthcheckUp() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		GraphHopperActiveHealthChecker checker = new GraphHopperActiveHealthChecker(
			restTemplate,
			() -> new GraphHopperEndpointSelection(
				"http://graphhopper-green.test",
				"http://graphhopper-blue.test",
				"green",
				"blue"),
			properties());
		server.expect(requestTo("http://graphhopper-green.test:8990/healthcheck"))
			.andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));

		GraphHopperActiveHealthChecker.GraphHopperHealthStatus status = checker.check();

		assertThat(status.status()).isEqualTo("UP");
		assertThat(status.activeSlot()).isEqualTo("green");
		assertThat(status.previousSlot()).isEqualTo("blue");
		server.verify();
	}

	@Test
	@DisplayName("active slot GraphHopper healthcheck가 실패하면 DOWN을 반환한다")
	void activeSlotHealthcheckDown() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		GraphHopperActiveHealthChecker checker = new GraphHopperActiveHealthChecker(
			restTemplate,
			() -> new GraphHopperEndpointSelection(
				"http://graphhopper-blue.test",
				null,
				"blue",
				null),
			properties());
		server.expect(requestTo("http://graphhopper-blue.test:8990/healthcheck"))
			.andRespond(withServerError());

		GraphHopperActiveHealthChecker.GraphHopperHealthStatus status = checker.check();

		assertThat(status.status()).isEqualTo("DOWN");
		assertThat(status.activeSlot()).isEqualTo("blue");
		server.verify();
	}

	private GraphHopperProperties properties() {
		return new GraphHopperProperties(
			"http://fallback.test:8989",
			Duration.ofSeconds(5),
			Duration.ofSeconds(5),
			null,
			null,
			null,
			null,
			"http://graphhopper-blue.test:8989",
			"http://graphhopper-green.test:8989",
			"http://fallback.test:8990/healthcheck",
			"http://graphhopper-blue.test:8990/healthcheck",
			"http://graphhopper-green.test:8990/healthcheck");
	}
}
