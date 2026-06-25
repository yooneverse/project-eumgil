package com.ssafy.e102.global.external.graphhopper;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

/**
 * Backend route service에서 GraphHopper runtime의 `/route` API로 나가는 단일 통로다.
 *
 * <p>호출 흐름은 route service -> GraphHopperRouteClient -> GraphHopper `/route` -> 정제된
 * {@link GraphHopperRoutePath} 반환 순서다. HTTP 실패와 timeout은 route 도메인 에러 코드로 변환한다.
 */
@Component
public class GraphHopperRouteClient {

	private static final Logger log = LoggerFactory.getLogger(GraphHopperRouteClient.class);
	private static final double MAX_SNAP_DISTANCE_METER = 10.0;

	private static final List<String> WALK_PATH_DETAILS = List.of(
		"edge_id",
		"walk_access",
		"segment_type",
		"signal_state",
		"audio_signal_state",
		"avg_slope_percent",
		"width_state",
		"surface_state",
		"stairs_state");

	private final RestTemplate restTemplate;
	private final GraphHopperEndpointProvider endpointProvider;
	private final ObjectMapper objectMapper;

	@Autowired
	public GraphHopperRouteClient(RestTemplateBuilder builder, GraphHopperProperties properties,
		ObjectMapper objectMapper, GraphHopperEndpointProvider endpointProvider) {
		this(builder
			.connectTimeout(properties.connectTimeout())
			.readTimeout(properties.readTimeout())
			.build(), endpointProvider, objectMapper);
	}

	GraphHopperRouteClient(RestTemplate restTemplate, GraphHopperProperties properties) {
		this(restTemplate, () -> GraphHopperEndpointSelection.fallback(properties.baseUrl()), new ObjectMapper());
	}

	GraphHopperRouteClient(
		RestTemplate restTemplate,
		GraphHopperEndpointProvider endpointProvider,
		ObjectMapper objectMapper) {
		this.restTemplate = restTemplate;
		this.endpointProvider = endpointProvider;
		this.objectMapper = objectMapper;
	}

	public GraphHopperRoutePath route(GraphHopperRouteRequest request) {
		return executeWithEndpointFallback("route", baseUrl -> routeOnce(baseUrl, request));
	}

	public GraphHopperRoutePath routeWithCustomModel(GraphHopperRouteRequest request, JsonNode customModel) {
		return executeWithEndpointFallback(
			"route-custom-model",
			baseUrl -> routeWithCustomModelOnce(baseUrl, request, customModel));
	}

	private GraphHopperRoutePath executeWithEndpointFallback(
		String operation,
		GraphHopperEndpointOperation endpointOperation) {
		GraphHopperEndpointSelection endpoint = endpointProvider.selectEndpoint();
		try {
			return endpointOperation.execute(endpoint.activeBaseUrl());
		} catch (HttpStatusCodeException exception) {
			if (shouldRetryPrevious(exception, endpoint)) {
				return retryPrevious(operation, endpointOperation, endpoint, exception);
			}
			RouteErrorCode errorCode = graphHopperHttpErrorCode(exception);
			log.warn(
				"external route call failed provider={} operation={} status={} body={}",
				"graphhopper",
				operation,
				exception.getStatusCode(),
				exception.getResponseBodyAsString(),
				exception);
			throw new RouteException(
				errorCode,
				errorCode.getMessage(),
				exception);
		} catch (ResourceAccessException exception) {
			if (endpoint.hasPrevious()) {
				return retryPrevious(operation, endpointOperation, endpoint, exception);
			}
			RouteErrorCode errorCode = hasTimeoutCause(exception)
				? RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT
				: RouteErrorCode.EXTERNAL_ROUTE_API_FAILED;
			log.warn(
				"external route call failed provider={} operation={} status={} message={}",
				"graphhopper",
				operation,
				errorCode.getStatus(),
				exception.getMessage(),
				exception);
			throw new RouteException(errorCode, errorCode.getMessage(), exception);
		} catch (RestClientException exception) {
			log.warn(
				"external route call failed provider={} operation={} status={} message={}",
				"graphhopper",
				operation,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
				exception.getMessage(),
				exception);
			throw new RouteException(
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(),
				exception);
		}
	}

	private GraphHopperRoutePath retryPrevious(
		String operation,
		GraphHopperEndpointOperation endpointOperation,
		GraphHopperEndpointSelection endpoint,
		RuntimeException activeException) {
		log.warn(
			"graphhopper active endpoint failed. retrying previous endpoint operation={} activeSlot={} previousSlot={} message={}",
			operation,
			endpoint.activeSlot(),
			endpoint.previousSlot(),
			activeException.getMessage());
		try {
			return endpointOperation.execute(endpoint.previousBaseUrl());
		} catch (HttpStatusCodeException exception) {
			RouteErrorCode errorCode = graphHopperHttpErrorCode(exception);
			log.warn(
				"external route call failed provider={} operation={} status={} body={}",
				"graphhopper",
				operation,
				exception.getStatusCode(),
				exception.getResponseBodyAsString(),
				exception);
			throw new RouteException(errorCode, errorCode.getMessage(), exception);
		} catch (ResourceAccessException exception) {
			RouteErrorCode errorCode = hasTimeoutCause(exception)
				? RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT
				: RouteErrorCode.EXTERNAL_ROUTE_API_FAILED;
			log.warn(
				"external route call failed provider={} operation={} status={} message={}",
				"graphhopper",
				operation,
				errorCode.getStatus(),
				exception.getMessage(),
				exception);
			throw new RouteException(errorCode, errorCode.getMessage(), exception);
		} catch (RestClientException exception) {
			log.warn(
				"external route call failed provider={} operation={} status={} message={}",
				"graphhopper",
				operation,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
				exception.getMessage(),
				exception);
			throw new RouteException(
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(),
				exception);
		}
	}

	private boolean shouldRetryPrevious(HttpStatusCodeException exception, GraphHopperEndpointSelection endpoint) {
		if (!endpoint.hasPrevious() || isGraphHopperNoRoute(exception.getResponseBodyAsString())) {
			return false;
		}
		return exception.getStatusCode().is5xxServerError() || exception.getStatusCode().is4xxClientError();
	}

	private RouteErrorCode graphHopperHttpErrorCode(HttpStatusCodeException exception) {
		if (isGraphHopperNoRoute(exception.getResponseBodyAsString())) {
			return RouteErrorCode.ROUTE_NOT_FOUND;
		}
		return RouteErrorCode.EXTERNAL_ROUTE_API_FAILED;
	}

	private boolean isGraphHopperNoRoute(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return false;
		}
		String normalizedBody = responseBody.toLowerCase(Locale.ROOT);
		return normalizedBody.contains("connectionnotfoundexception")
			|| normalizedBody.contains("connection between locations not found");
	}

	private GraphHopperRoutePath routeOnce(String baseUrl, GraphHopperRouteRequest request) {
		// GraphHopper는 GET query 기반 API라 profile과 두 point를 URI에 직접 싣는다.
		GraphHopperRouteResponse response = restTemplate.exchange(
			RequestEntity
				.method(HttpMethod.GET, routeUri(baseUrl, request))
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build(),
			GraphHopperRouteResponse.class)
			.getBody();
		return extractFirstPath(request, response);
	}

	private GraphHopperRoutePath routeWithCustomModelOnce(
		String baseUrl,
		GraphHopperRouteRequest request,
		JsonNode customModel) {
		GraphHopperRouteResponse response = restTemplate.exchange(
			RequestEntity
				.post(routePostUri(baseUrl))
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.body(routePostBody(request, customModel)),
			GraphHopperRouteResponse.class)
			.getBody();
		return extractFirstPath(request, response);
	}

	private URI routeUri(String baseUrl, GraphHopperRouteRequest request) {
		return UriComponentsBuilder
			.fromUriString(baseUrl)
			.path("/route")
			.queryParam("profile", request.profile().getProfileName())
			.queryParam("point", point(request.startPoint()))
			.queryParam("point", point(request.endPoint()))
			.queryParam("points_encoded", "false")
			.queryParam("locale", "ko-KR")
			.queryParam("details", WALK_PATH_DETAILS.toArray())
			.build()
			.toUri();
	}

	private URI routePostUri(String baseUrl) {
		return UriComponentsBuilder
			.fromUriString(baseUrl)
			.path("/route")
			.build()
			.toUri();
	}

	private Map<String, Object> routePostBody(GraphHopperRouteRequest request, JsonNode customModel) {
		return Map.of(
			"profile", request.profile().getProfileName(),
			"points", List.of(
				List.of(request.startPoint().lng(), request.startPoint().lat()),
				List.of(request.endPoint().lng(), request.endPoint().lat())),
			"points_encoded", false,
			"locale", "ko-KR",
			"details", WALK_PATH_DETAILS,
			"custom_model", objectMapper.convertValue(customModel, Map.class));
	}

	private String point(GeoPointRequest point) {
		return point.lat() + "," + point.lng();
	}

	private GraphHopperRoutePath extractFirstPath(GraphHopperRouteRequest request, GraphHopperRouteResponse response) {
		if (response == null || response.paths() == null || response.paths().isEmpty()) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		GraphHopperPathResponse path = response.paths().get(0);
		if (request.enforceSnapDistanceLimit()) {
			validateSnapDistance(request, path);
		}
		List<GraphHopperCoordinate> coordinates = path.coordinates();
		if (coordinates.isEmpty()) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		// 이후 step/payload service는 GraphHopper 원본 JSON이 아니라 이 정제된 path만 사용한다.
		return new GraphHopperRoutePath(path.distance(), path.time(), coordinates, path.pathDetails());
	}

	private void validateSnapDistance(GraphHopperRouteRequest request, GraphHopperPathResponse path) {
		List<GraphHopperCoordinate> snappedCoordinates = path.snappedCoordinates();
		if (snappedCoordinates.size() < 2) {
			return;
		}
		if (snapDistanceMeter(request.startPoint(), snappedCoordinates.get(0)) > MAX_SNAP_DISTANCE_METER
			|| snapDistanceMeter(request.endPoint(),
				snappedCoordinates.get(snappedCoordinates.size() - 1)) > MAX_SNAP_DISTANCE_METER) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
	}

	private double snapDistanceMeter(GeoPointRequest requestedPoint, GraphHopperCoordinate snappedCoordinate) {
		return GeoDistanceCalculator.distanceMeter(
			requestedPoint,
			new GeoPointRequest(snappedCoordinate.lat().doubleValue(), snappedCoordinate.lng().doubleValue()));
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

	@FunctionalInterface
	private interface GraphHopperEndpointOperation {

		GraphHopperRoutePath execute(String baseUrl);
	}
}
