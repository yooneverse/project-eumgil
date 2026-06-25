package com.ssafy.e102.global.external.odsay;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

@Component
public class OdsayClient {

	private static final Logger log = LoggerFactory.getLogger(OdsayClient.class);

	private static final int MAX_INTERNAL_CANDIDATES = 10;
	private static final int SEARCH_TYPE_ALL = 0;

	private final RestTemplate restTemplate;
	private final OdsayProperties properties;

	public OdsayClient(RestTemplateBuilder builder, OdsayProperties properties) {
		this.restTemplate = builder
			.connectTimeout(properties.connectTimeout())
			.readTimeout(properties.readTimeout())
			.build();
		this.properties = properties;
	}

	public OdsayTransitSearchResult searchPubTransPath(GeoPointRequest startPoint, GeoPointRequest endPoint) {
		if (!StringUtils.hasText(properties.apiKey())) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "ODsay API key가 설정되지 않았습니다.");
		}
		try {
			JsonNode body = restTemplate.exchange(
				RequestEntity
					.method(HttpMethod.GET, searchPubTransPathUri(startPoint, endPoint))
					.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.build(),
				JsonNode.class)
				.getBody();
			return parseSearchResult(body);
		} catch (HttpStatusCodeException exception) {
			throw externalFailure("searchPubTransPathT", exception);
		} catch (ResourceAccessException exception) {
			throw new RouteException(timeoutOrFailure(exception), timeoutOrFailure(exception).getMessage(), exception);
		} catch (RestClientException exception) {
			log.warn(
				"external route call failed provider={} operation={} status={} message={}",
				"odsay",
				"searchPubTransPathT",
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
				exception.getMessage(),
				exception);
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
		}
	}

	public List<OdsayLaneGeometry> loadLane(String mapObj) {
		if (!StringUtils.hasText(mapObj)) {
			return List.of();
		}
		if (!StringUtils.hasText(properties.apiKey())) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "ODsay API key가 설정되지 않았습니다.");
		}
		try {
			JsonNode body = restTemplate.exchange(
				RequestEntity
					.method(HttpMethod.GET, loadLaneUri(mapObj))
					.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.build(),
				JsonNode.class)
				.getBody();
			return parseLaneGeometries(body);
		} catch (HttpStatusCodeException exception) {
			throw externalFailure("loadLane", exception);
		} catch (ResourceAccessException exception) {
			throw new RouteException(timeoutOrFailure(exception), timeoutOrFailure(exception).getMessage(), exception);
		} catch (RestClientException exception) {
			log.warn(
				"external route call failed provider={} operation={} status={} message={}",
				"odsay",
				"loadLane",
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
				exception.getMessage(),
				exception);
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
		}
	}

	private URI searchPubTransPathUri(GeoPointRequest startPoint, GeoPointRequest endPoint) {
		return UriComponentsBuilder
			.fromUriString(properties.baseUrl())
			.path("/searchPubTransPathT")
			.queryParam("SX", startPoint.lng())
			.queryParam("SY", startPoint.lat())
			.queryParam("EX", endPoint.lng())
			.queryParam("EY", endPoint.lat())
			.queryParam("SearchType", SEARCH_TYPE_ALL)
			.queryParam("apiKey", encodedApiKey())
			.build(true)
			.toUri();
	}

	private URI loadLaneUri(String mapObj) {
		return UriComponentsBuilder
			.fromUriString(properties.baseUrl())
			.path("/loadLane")
			.queryParam("mapObject", "0:0@" + mapObj)
			.queryParam("apiKey", encodedApiKey())
			.build(true)
			.toUri();
	}

	private String encodedApiKey() {
		return URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8);
	}

	private OdsayTransitSearchResult parseSearchResult(JsonNode body) {
		rejectOdsayError(body, "searchPubTransPathT");
		JsonNode pathNodes = body == null ? null : body.path("result").path("path");
		if (pathNodes == null || !pathNodes.isArray() || pathNodes.isEmpty()) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		List<OdsayTransitPath> paths = new ArrayList<>();
		for (JsonNode pathNode : pathNodes) {
			if (paths.size() >= MAX_INTERNAL_CANDIDATES) {
				break;
			}
			paths.add(parsePath(pathNode));
		}
		return new OdsayTransitSearchResult(List.copyOf(paths));
	}

	private OdsayTransitPath parsePath(JsonNode pathNode) {
		JsonNode info = pathNode.path("info");
		List<OdsayTransitLeg> legs = new ArrayList<>();
		for (JsonNode legNode : pathNode.path("subPath")) {
			TransportMode type = toTransportMode(legNode.path("trafficType").asInt());
			legs.add(parseLeg(type, legNode));
		}
		return new OdsayTransitPath(
			decimal(info, "totalDistance"),
			info.path("totalTime").asInt(),
			info.path("totalWalk").asInt(),
			info.path("busTransitCount").asInt(),
			info.path("subwayTransitCount").asInt(),
			text(info, "mapObj"),
			List.copyOf(legs),
			snapshot(
				"mapObj", text(info, "mapObj"),
				"pathType", pathNode.path("pathType").asInt(),
				"info", info));
	}

	private List<OdsayLaneGeometry> parseLaneGeometries(JsonNode body) {
		rejectOdsayError(body, "loadLane");
		JsonNode laneNodes = body == null ? null : body.path("result").path("lane");
		if (laneNodes == null || !laneNodes.isArray()) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		List<OdsayLaneGeometry> geometries = new ArrayList<>();
		for (JsonNode laneNode : laneNodes) {
			TransportMode type = laneClassToTransportMode(laneNode.path("class").asInt());
			String geometry = toLineString(laneNode.path("section"));
			if (geometry != null) {
				geometries.add(new OdsayLaneGeometry(type, geometry));
			}
		}
		return List.copyOf(geometries);
	}

	private OdsayTransitLeg parseLeg(TransportMode type, JsonNode legNode) {
		return new OdsayTransitLeg(
			type,
			decimal(legNode, "distance"),
			legNode.path("sectionTime").asInt(),
			text(legNode, "startName"),
			decimal(legNode, "startY"),
			decimal(legNode, "startX"),
			text(legNode, "startID"),
			text(legNode, "startLocalStationID"),
			text(legNode, "startArsID"),
			decimal(legNode, "startExitY"),
			decimal(legNode, "startExitX"),
			text(legNode, "endName"),
			decimal(legNode, "endY"),
			decimal(legNode, "endX"),
			text(legNode, "endID"),
			text(legNode, "endLocalStationID"),
			text(legNode, "endArsID"),
			decimal(legNode, "endExitY"),
			decimal(legNode, "endExitX"),
			integer(legNode, "wayCode"),
			text(legNode, "way"),
			parseLanes(legNode.path("lane")),
			parsePassStops(legNode.path("passStopList").path("stations")),
			snapshot(
				"trafficType", legNode.path("trafficType").asInt(),
				"startID", text(legNode, "startID"),
				"endID", text(legNode, "endID"),
				"startLocalStationID", text(legNode, "startLocalStationID"),
				"endLocalStationID", text(legNode, "endLocalStationID"),
				"startArsID", text(legNode, "startArsID"),
				"endArsID", text(legNode, "endArsID"),
				"wayCode", integer(legNode, "wayCode"),
				"way", text(legNode, "way")));
	}

	private List<OdsayTransitLane> parseLanes(JsonNode laneNodes) {
		if (!laneNodes.isArray()) {
			return List.of();
		}
		List<OdsayTransitLane> lanes = new ArrayList<>();
		for (JsonNode laneNode : laneNodes) {
			lanes.add(new OdsayTransitLane(
				text(laneNode, "busNo"),
				text(laneNode, "busLocalBlID"),
				text(laneNode, "subwayCode"),
				text(laneNode, "name")));
		}
		return List.copyOf(lanes);
	}

	private List<OdsayPassStop> parsePassStops(JsonNode stationNodes) {
		if (!stationNodes.isArray()) {
			return List.of();
		}
		List<OdsayPassStop> stops = new ArrayList<>();
		for (JsonNode stationNode : stationNodes) {
			stops.add(new OdsayPassStop(
				text(stationNode, "stationID"),
				text(stationNode, "stationName"),
				text(stationNode, "localStationID"),
				text(stationNode, "arsID"),
				decimal(stationNode, "y"),
				decimal(stationNode, "x")));
		}
		return List.copyOf(stops);
	}

	private TransportMode toTransportMode(int trafficType) {
		return switch (trafficType) {
			case 1 -> TransportMode.SUBWAY;
			case 2 -> TransportMode.BUS;
			case 3 -> TransportMode.WALK;
			default ->
				throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "알 수 없는 ODsay trafficType입니다.");
		};
	}

	private TransportMode laneClassToTransportMode(int laneClass) {
		return switch (laneClass) {
			case 1 -> TransportMode.BUS;
			case 2 -> TransportMode.SUBWAY;
			default ->
				throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "알 수 없는 ODsay lane class입니다.");
		};
	}

	private String toLineString(JsonNode sectionNodes) {
		if (!sectionNodes.isArray()) {
			return null;
		}
		List<String> coordinates = new ArrayList<>();
		for (JsonNode sectionNode : sectionNodes) {
			for (JsonNode graphPos : sectionNode.path("graphPos")) {
				BigDecimal lng = decimal(graphPos, "x");
				BigDecimal lat = decimal(graphPos, "y");
				if (lng != null && lat != null) {
					String coordinate = lng + " " + lat;
					if (coordinates.isEmpty() || !coordinates.get(coordinates.size() - 1).equals(coordinate)) {
						coordinates.add(coordinate);
					}
				}
			}
		}
		if (coordinates.size() < 2) {
			return null;
		}
		return "LINESTRING(" + String.join(", ", coordinates) + ")";
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.asText();
		return StringUtils.hasText(text) ? text : null;
	}

	private BigDecimal decimal(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
			return null;
		}
		return new BigDecimal(value.asText());
	}

	private Integer integer(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
			return null;
		}
		return value.asInt();
	}

	private RouteException externalFailure(String operation, HttpStatusCodeException exception) {
		log.warn(
			"external route call failed provider={} operation={} status={} body={}",
			"odsay",
			operation,
			exception.getStatusCode(),
			exception.getResponseBodyAsString(),
			exception);
		return new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
			RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
	}

	private void rejectOdsayError(JsonNode body, String operation) {
		JsonNode error = body == null ? null : body.path("error");
		if (error == null || error.isMissingNode() || error.isNull()) {
			return;
		}
		log.warn(
			"external route call failed provider={} operation={} status={} error={}",
			"odsay",
			operation,
			RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
			error);
		throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED);
	}

	private RouteErrorCode timeoutOrFailure(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof SocketTimeoutException) {
				return RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT;
			}
			current = current.getCause();
		}
		return RouteErrorCode.EXTERNAL_ROUTE_API_FAILED;
	}

	private Map<String, Object> snapshot(Object... values) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		for (int index = 0; index < values.length; index += 2) {
			snapshot.put((String)values[index], values[index + 1]);
		}
		return snapshot;
	}
}
