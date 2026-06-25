package com.ssafy.e102.global.external.graphhopper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GraphHopperAdminClient {

	private static final Logger log = LoggerFactory.getLogger(GraphHopperAdminClient.class);
	private static final int MAX_SINGLE_ENDPOINT_ATTEMPTS = 2;
	private static final int MAX_RELOAD_ENDPOINTS = 2;
	private static final Duration RELOAD_STALE_LOCK_BUFFER = Duration.ofSeconds(30);

	private final RestTemplate restTemplate;
	private final GraphHopperEndpointProvider endpointProvider;
	private final Duration staleLockRecoveryThreshold;

	@Autowired
	public GraphHopperAdminClient(
		RestTemplateBuilder builder,
		GraphHopperProperties properties,
		GraphHopperEndpointProvider endpointProvider) {
		this(
			builder.connectTimeout(properties.connectTimeout()).readTimeout(properties.readTimeout()).build(),
			properties,
			endpointProvider);
	}

	GraphHopperAdminClient(RestTemplate restTemplate, GraphHopperProperties properties) {
		this(restTemplate, properties, () -> GraphHopperEndpointSelection.fallback(properties.baseUrl()));
	}

	GraphHopperAdminClient(
		RestTemplate restTemplate,
		GraphHopperProperties properties,
		GraphHopperEndpointProvider endpointProvider) {
		this.restTemplate = restTemplate;
		this.staleLockRecoveryThreshold = properties.connectTimeout()
			.plus(properties.readTimeout())
			.multipliedBy(MAX_SINGLE_ENDPOINT_ATTEMPTS * MAX_RELOAD_ENDPOINTS)
			.plus(RELOAD_STALE_LOCK_BUFFER);
		this.endpointProvider = endpointProvider;
	}

	public Duration staleLockRecoveryThreshold() {
		return staleLockRecoveryThreshold;
	}

	public GraphHopperReloadResult reloadRoutingOverrides() {
		GraphHopperEndpointSelection endpointSelection = endpointProvider.selectEndpoint();
		List<EndpointTarget> endpointTargets = resolveTargets(endpointSelection);
		EndpointReloadResult activeReloadResult = null;
		List<String> reloadedSlots = new ArrayList<>();
		List<String> failureMessages = new ArrayList<>();

		for (int index = 0; index < endpointTargets.size(); index++) {
			EndpointTarget endpointTarget = endpointTargets.get(index);
			EndpointReloadResult reloadResult = reloadEndpointWithRetry(endpointTarget);
			if (index == 0) {
				activeReloadResult = reloadResult;
			}
			if (reloadResult.success()) {
				reloadedSlots.add(endpointTarget.slot());
				continue;
			}
			failureMessages.add(reloadResult.message());
		}

		if (failureMessages.isEmpty()) {
			return new GraphHopperReloadResult(
				GraphHopperReloadStatus.APPLIED,
				"Reloaded GraphHopper override slot(s): " + String.join(", ", reloadedSlots));
		}
		if (activeReloadResult != null && activeReloadResult.success()) {
			return new GraphHopperReloadResult(
				GraphHopperReloadStatus.APPLIED_WITH_WARNING,
				"Reloaded GraphHopper override slot(s): "
					+ String.join(", ", reloadedSlots)
					+ " | failed slot(s): "
					+ String.join(" | ", failureMessages));
		}
		return new GraphHopperReloadResult(
			GraphHopperReloadStatus.FAILED,
			"GraphHopper override reload failed: " + String.join(" | ", failureMessages));
	}

	private EndpointReloadResult reloadEndpointWithRetry(EndpointTarget endpointTarget) {
		RuntimeException lastException = null;
		for (int attempt = 1; attempt <= MAX_SINGLE_ENDPOINT_ATTEMPTS; attempt++) {
			try {
				reloadRoutingOverridesOnce(endpointTarget.baseUrl());
				return EndpointReloadResult.succeeded();
			} catch (HttpStatusCodeException | ResourceAccessException exception) {
				lastException = exception;
				log.warn(
					"graphhopper override reload failed slot={} attempt={} message={}",
					endpointTarget.slot(),
					attempt,
					exception.getMessage(),
					exception);
			} catch (RestClientException exception) {
				lastException = exception;
				log.warn(
					"graphhopper override reload failed slot={} attempt={} message={}",
					endpointTarget.slot(),
					attempt,
					exception.getMessage(),
					exception);
			}
		}
		return EndpointReloadResult.failed(
			"slot=" + endpointTarget.slot() + " message=" + describeFailure(lastException));
	}

	private void reloadRoutingOverridesOnce(String baseUrl) {
		restTemplate.exchange(
			RequestEntity
				.method(HttpMethod.POST, reloadRoutingOverridesUri(baseUrl))
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of()),
			Void.class);
	}

	private java.net.URI reloadRoutingOverridesUri(String baseUrl) {
		return UriComponentsBuilder
			.fromUriString(baseUrl)
			.path("/ieum/admin/overrides/reload")
			.build()
			.toUri();
	}

	private List<EndpointTarget> resolveTargets(GraphHopperEndpointSelection endpointSelection) {
		Map<String, EndpointTarget> targetsByBaseUrl = new LinkedHashMap<>();
		targetsByBaseUrl.put(
			endpointSelection.activeBaseUrl(),
			new EndpointTarget(endpointSelection.activeBaseUrl(), endpointSelection.activeSlot()));
		if (endpointSelection.hasPrevious()) {
			targetsByBaseUrl.putIfAbsent(
				endpointSelection.previousBaseUrl(),
				new EndpointTarget(endpointSelection.previousBaseUrl(), endpointSelection.previousSlot()));
		}
		return List.copyOf(targetsByBaseUrl.values());
	}

	private String describeFailure(RuntimeException exception) {
		if (exception == null) {
			return "unknown";
		}
		if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
			return httpStatusCodeException.getStatusCode() + " " + httpStatusCodeException.getResponseBodyAsString();
		}
		if (exception instanceof ResourceAccessException resourceAccessException) {
			return hasTimeoutCause(resourceAccessException) ? "timeout" : resourceAccessException.getMessage();
		}
		return exception.getMessage();
	}

	private boolean hasTimeoutCause(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof SocketTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	public record GraphHopperReloadResult(GraphHopperReloadStatus status, String message) {
	}

	public enum GraphHopperReloadStatus {
		SKIPPED,
		APPLIED,
		APPLIED_WITH_WARNING,
		FAILED
	}

	private record EndpointTarget(String baseUrl, String slot) {
	}

	private record EndpointReloadResult(boolean success, String message) {
		private static EndpointReloadResult succeeded() {
			return new EndpointReloadResult(true, null);
		}

		private static EndpointReloadResult failed(String message) {
			return new EndpointReloadResult(false, message);
		}
	}
}
