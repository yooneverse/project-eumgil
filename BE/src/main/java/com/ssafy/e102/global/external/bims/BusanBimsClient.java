package com.ssafy.e102.global.external.bims;

import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;

@Component
public class BusanBimsClient {

	private static final Logger log = LoggerFactory.getLogger(BusanBimsClient.class);

	private final RestTemplate restTemplate;
	private final BusanBimsProperties properties;

	public BusanBimsClient(RestTemplateBuilder builder, BusanBimsProperties properties) {
		this.restTemplate = builder
			.connectTimeout(properties.connectTimeout())
			.readTimeout(properties.readTimeout())
			.build();
		this.properties = properties;
	}

	public BusanBimsArrival findArrival(String stopId, String lineId, String routeNo) {
		if (!StringUtils.hasText(stopId)) {
			return new BusanBimsArrival(null, lineId, routeNo, null, null);
		}
		String resolvedLineId = StringUtils.hasText(lineId) ? lineId : findLineId(stopId, routeNo);
		if (!StringUtils.hasText(resolvedLineId)) {
			return new BusanBimsArrival(stopId, null, routeNo, null, null);
		}
		List<Element> items = requestItems("busStopArrByBstopidLineid", stopId, resolvedLineId);
		Element item = items.isEmpty() ? null : items.get(0);
		ArrivalSlot arrivalSlot = arrivalSlot(item);
		return new BusanBimsArrival(
			stopId,
			resolvedLineId,
			text(item, "lineno", routeNo),
			arrivalSlot.remainingMinute(),
			arrivalSlot.lowFloor(),
			arrivalSlot.vehicleNo(),
			arrivalSlot.remainingStopCount());
	}

	public List<BusanBimsArrival> findArrivalsByStopId(String stopId) {
		if (!StringUtils.hasText(stopId)) {
			return List.of();
		}
		return requestItems("stopArrByBstopid", stopId, null)
			.stream()
			.map(item -> {
				ArrivalSlot arrivalSlot = arrivalSlot(item);
				return new BusanBimsArrival(
					stopId,
					text(item, "lineid", null),
					text(item, "lineno", null),
					arrivalSlot.remainingMinute(),
					arrivalSlot.lowFloor(),
					arrivalSlot.vehicleNo(),
					arrivalSlot.remainingStopCount());
			})
			.filter(arrival -> StringUtils.hasText(arrival.routeNo()))
			.sorted(Comparator.comparing(BusanBimsArrival::remainingMinute,
				Comparator.nullsLast(Integer::compareTo)))
			.toList();
	}

	public BusanBimsBusStopPage findBusStops(int pageNo, int numOfRows) {
		if (pageNo < 1 || numOfRows < 1) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "BIMS busStopList page 요청값이 올바르지 않습니다.");
		}
		return requestBusStopPage(pageNo, numOfRows);
	}

	private String findLineId(String stopId, String routeNo) {
		if (!StringUtils.hasText(routeNo)) {
			return null;
		}
		return requestItems("stopArrByBstopid", stopId, null)
			.stream()
			.filter(item -> routeNo.equals(text(item, "lineno", null)))
			.findFirst()
			.map(item -> text(item, "lineid", null))
			.orElse(null);
	}

	private List<Element> requestItems(String endpoint, String stopId, String lineId) {
		if (!StringUtils.hasText(properties.serviceKey())) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "BIMS service key가 설정되지 않았습니다.");
		}
		try {
			UriComponentsBuilder uriBuilder = UriComponentsBuilder
				.fromUriString(properties.baseUrl())
				.path("/" + endpoint)
				.queryParam("serviceKey", encodedServiceKey())
				.queryParam("bstopid", stopId)
				.queryParam("pageNo", 1)
				.queryParam("numOfRows", 20);
			if (StringUtils.hasText(lineId)) {
				uriBuilder.queryParam("lineid", lineId);
			}
			String body = restTemplate.exchange(
				RequestEntity
					.method(HttpMethod.GET, uriBuilder.build(true).toUri())
					.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
					.build(),
				String.class)
				.getBody();
			return parseItems(body);
		} catch (HttpStatusCodeException exception) {
			throw externalFailure(endpoint, exception);
		} catch (ResourceAccessException exception) {
			RouteErrorCode errorCode = timeoutOrFailure(exception);
			log.warn(
				"external route call failed provider={} operation={} status={} stopId={} lineId={} message={}",
				"bims",
				endpoint,
				errorCode.getStatus(),
				stopId,
				lineId,
				exception.getMessage(),
				exception);
			throw new RouteException(errorCode, errorCode.getMessage(), exception);
		} catch (RestClientException exception) {
			log.warn(
				"external route call failed provider={} operation={} status={} stopId={} lineId={} message={}",
				"bims",
				endpoint,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
				stopId,
				lineId,
				exception.getMessage(),
				exception);
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
		}
	}

	private BusanBimsBusStopPage requestBusStopPage(int pageNo, int numOfRows) {
		String endpoint = "busStopList";
		if (!StringUtils.hasText(properties.serviceKey())) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED, "BIMS service key가 설정되지 않았습니다.");
		}
		try {
			UriComponentsBuilder uriBuilder = UriComponentsBuilder
				.fromUriString(properties.baseUrl())
				.path("/" + endpoint)
				.queryParam("serviceKey", encodedServiceKey())
				.queryParam("pageNo", pageNo)
				.queryParam("numOfRows", numOfRows);
			String body = restTemplate.exchange(
				RequestEntity
					.method(HttpMethod.GET, uriBuilder.build(true).toUri())
					.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
					.build(),
				String.class)
				.getBody();
			return parseBusStopPage(body, pageNo, numOfRows);
		} catch (HttpStatusCodeException exception) {
			throw externalFailure(endpoint, exception);
		} catch (ResourceAccessException exception) {
			RouteErrorCode errorCode = timeoutOrFailure(exception);
			log.warn(
				"external route call failed provider={} operation={} status={} pageNo={} numOfRows={} message={}",
				"bims",
				endpoint,
				errorCode.getStatus(),
				pageNo,
				numOfRows,
				exception.getMessage(),
				exception);
			throw new RouteException(errorCode, errorCode.getMessage(), exception);
		} catch (RestClientException exception) {
			log.warn(
				"external route call failed provider={} operation={} status={} pageNo={} numOfRows={} message={}",
				"bims",
				endpoint,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus(),
				pageNo,
				numOfRows,
				exception.getMessage(),
				exception);
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
		}
	}

	private String encodedServiceKey() {
		String serviceKey = properties.serviceKey();
		if (serviceKey.contains("%")) {
			return serviceKey;
		}
		return URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
	}

	private List<Element> parseItems(String body) {
		if (!StringUtils.hasText(body)) {
			return List.of();
		}
		try {
			Document document = parseDocument(body);
			NodeList nodes = document.getElementsByTagName("item");
			List<Element> items = new ArrayList<>();
			for (int index = 0; index < nodes.getLength(); index++) {
				items.add((Element)nodes.item(index));
			}
			return List.copyOf(items);
		} catch (Exception exception) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
		}
	}

	private BusanBimsBusStopPage parseBusStopPage(String body, int pageNo, int numOfRows) {
		if (!StringUtils.hasText(body)) {
			return new BusanBimsBusStopPage(List.of(), 0, pageNo, numOfRows);
		}
		try {
			Document document = parseDocument(body);
			NodeList nodes = document.getElementsByTagName("item");
			List<BusanBimsBusStop> busStops = new ArrayList<>();
			for (int index = 0; index < nodes.getLength(); index++) {
				Element item = (Element)nodes.item(index);
				busStops.add(new BusanBimsBusStop(
					text(item, "bstopid", null),
					text(item, "bstopnm", null),
					text(item, "arsno", null),
					doubleValue(item, "gpsx"),
					doubleValue(item, "gpsy"),
					text(item, "stoptype", null)));
			}
			return new BusanBimsBusStopPage(
				List.copyOf(busStops),
				documentInteger(document, "totalCount"),
				pageNo,
				numOfRows);
		} catch (Exception exception) {
			throw new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
				RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
		}
	}

	private Document parseDocument(String body) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
	}

	private ArrivalSlot arrivalSlot(Element item) {
		List<ArrivalSlot> slots = Arrays.asList(arrivalSlot(item, 1), arrivalSlot(item, 2))
			.stream()
			.filter(ArrivalSlot::hasArrival)
			.toList();
		if (slots.isEmpty()) {
			return ArrivalSlot.empty();
		}
		return slots.stream()
			.filter(slot -> Boolean.TRUE.equals(slot.lowFloor()))
			.min(Comparator.comparing(ArrivalSlot::remainingMinuteOrMax))
			.orElseGet(() -> slots.stream()
				.min(Comparator.comparing(ArrivalSlot::remainingMinuteOrMax))
				.orElse(ArrivalSlot.empty()));
	}

	private ArrivalSlot arrivalSlot(Element item, int index) {
		return new ArrivalSlot(
			integer(item, "min" + index),
			booleanValue(item, "lowplate" + index),
			firstText(item, List.of("carno" + index, "carNo" + index, "car" + index), null),
			integer(item, "station" + index));
	}

	private Integer integer(Element item, String tagName) {
		String value = text(item, tagName, null);
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private Double doubleValue(Element item, String tagName) {
		String value = text(item, tagName, null);
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private int documentInteger(Document document, String tagName) {
		NodeList nodes = document.getElementsByTagName(tagName);
		if (nodes.getLength() == 0) {
			return 0;
		}
		try {
			return Integer.parseInt(nodes.item(0).getTextContent().trim());
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	private Boolean booleanValue(Element item, String tagName) {
		String value = text(item, tagName, null);
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return switch (value.trim().toUpperCase()) {
			case "1", "Y", "YES", "TRUE" -> Boolean.TRUE;
			case "0", "N", "NO", "FALSE" -> Boolean.FALSE;
			default -> null;
		};
	}

	private String text(Element item, String tagName, String defaultValue) {
		if (item == null) {
			return defaultValue;
		}
		NodeList nodes = item.getElementsByTagName(tagName);
		if (nodes.getLength() == 0) {
			return defaultValue;
		}
		String value = nodes.item(0).getTextContent();
		return StringUtils.hasText(value) ? value.trim() : defaultValue;
	}

	private String firstText(Element item, List<String> tagNames, String defaultValue) {
		for (String tagName : tagNames) {
			String value = text(item, tagName, null);
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return defaultValue;
	}

	private record ArrivalSlot(
		Integer remainingMinute,
		Boolean lowFloor,
		String vehicleNo,
		Integer remainingStopCount) {

		static ArrivalSlot empty() {
			return new ArrivalSlot(null, null, null, null);
		}

		boolean hasArrival() {
			return remainingMinute != null || lowFloor != null || StringUtils.hasText(vehicleNo);
		}

		int remainingMinuteOrMax() {
			return remainingMinute == null ? Integer.MAX_VALUE : remainingMinute;
		}
	}

	private RouteException externalFailure(String operation, HttpStatusCodeException exception) {
		log.warn(
			"external route call failed provider={} operation={} status={} body={}",
			"bims",
			operation,
			exception.getStatusCode(),
			exception.getResponseBodyAsString(),
			exception);
		return new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED,
			RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getMessage(), exception);
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
}
