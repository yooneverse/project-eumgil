package com.ssafy.e102.domain.route.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.dto.response.LowFloorBusReservationResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceDirection;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventType;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceFeature;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.TransitLaneOptionResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteWarningCode;
import com.ssafy.e102.domain.route.type.TransportMode;

class RouteDtoJsonTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("walk route response serializes guidanceEvents API contract")
	void walkRouteSearchResponseSerializesApiContract() throws Exception {
		WalkRouteSearchResponse response = new WalkRouteSearchResponse(
			"rs_walk_20260506_abc123",
			List.of(new RouteSummaryResponse(
				"walk_rt_safe_001",
				TransportMode.WALK,
				RouteOption.SAFE,
				"safe route",
				BigDecimal.valueOf(950),
				960,
				16,
				List.of(RouteBadge.LOW_SLOPE, RouteBadge.CROSSWALK),
				"LINESTRING(128.9360 35.1200, 128.8823 35.1315)",
				List.of(new RouteLegResponse(
					1,
					TransportMode.WALK,
					RouteLegRole.WALK_ONLY,
					"walk to destination",
					BigDecimal.valueOf(950),
					960,
					16,
					"LINESTRING(128.9360 35.1200, 128.8823 35.1315)",
					List.of(new RouteGuidanceEventResponse(
						1,
						RouteGuidanceEventType.CROSSWALK,
						null,
						List.of(RouteGuidanceFeature.SIGNAL, RouteGuidanceFeature.AUDIO_SIGNAL),
						BigDecimal.valueOf(12),
						35,
						"POINT(128.9360 35.1200)")))))));

		JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(root.get("searchId").asText()).isEqualTo("rs_walk_20260506_abc123");
		JsonNode route = root.get("routes").get(0);
		assertThat(route.get("routeId").asText()).isEqualTo("walk_rt_safe_001");
		assertThat(route.get("transportMode").asText()).isEqualTo("WALK");
		assertThat(route.get("routeOption").asText()).isEqualTo("SAFE");
		assertThat(route.get("routeOptions").get(0).asText()).isEqualTo("SAFE");
		assertThat(route.has("transferCount")).isFalse();
		assertThat(route.get("badges").get(0).asText()).isEqualTo("LOW_SLOPE");
		assertThat(route.has("warnings")).isFalse();
		JsonNode leg = route.get("legs").get(0);
		assertThat(leg.get("type").asText()).isEqualTo("WALK");
		assertThat(leg.get("role").asText()).isEqualTo("WALK_ONLY");
		assertThat(leg.has("routeNo")).isFalse();
		assertThat(leg.has("laneOptions")).isFalse();
		assertThat(leg.has("boardingStop")).isFalse();
		assertThat(leg.has("arrivingStop")).isFalse();
		assertThat(leg.has("alightingStop")).isFalse();
		assertThat(leg.has("remainingMinute")).isFalse();
		assertThat(leg.has("headsign")).isFalse();
		assertThat(leg.has("isLowFloor")).isFalse();
		assertThat(leg.has("badges")).isFalse();
		assertThat(leg.has("steps")).isFalse();
		JsonNode guidanceEvent = leg.get("guidanceEvents").get(0);
		assertThat(guidanceEvent.get("sequence").asInt()).isEqualTo(1);
		assertThat(guidanceEvent.get("type").asText()).isEqualTo("CROSSWALK");
		assertThat(guidanceEvent.get("features").get(0).asText()).isEqualTo("SIGNAL");
		assertThat(guidanceEvent.get("features").get(1).asText()).isEqualTo("AUDIO_SIGNAL");
		assertThat(guidanceEvent.has("direction")).isFalse();
		assertThat(guidanceEvent.get("distanceFromLegStartMeter").decimalValue()).isEqualByComparingTo("12");
		assertThat(guidanceEvent.get("durationFromLegStartSecond").asInt()).isEqualTo(35);
		assertThat(guidanceEvent.get("distanceFromRouteStartMeter").decimalValue()).isEqualByComparingTo("12");
		assertThat(guidanceEvent.get("durationFromRouteStartSecond").asInt()).isEqualTo(35);
		assertThat(guidanceEvent.get("geometry").asText()).isEqualTo("POINT(128.9360 35.1200)");
	}

	@Test
	@DisplayName("subway leg는 시간표 기반 remainingMinute와 headsign을 직렬화한다")
	void subwayLegSerializesArrivalAndHeadsignContract() throws Exception {
		RouteLegResponse response = new RouteLegResponse(
			2,
			TransportMode.SUBWAY,
			RouteLegRole.TRANSIT,
			"부산 2호선에 탑승하세요.",
			BigDecimal.valueOf(4100),
			780,
			13,
			"LINESTRING(129.061 35.161, 129.066 35.166)",
			List.of(),
			"부산 2호선",
			List.of(),
			null,
			null,
			4,
			"장산행",
			null,
			List.of(RouteBadge.ELEVATOR));

		JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(root.get("type").asText()).isEqualTo("SUBWAY");
		assertThat(root.get("routeNo").asText()).isEqualTo("부산 2호선");
		assertThat(root.get("remainingMinute").asInt()).isEqualTo(4);
		assertThat(root.get("headsign").asText()).isEqualTo("장산행");
		assertThat(root.has("laneOptions")).isFalse();
		assertThat(root.has("isLowFloor")).isFalse();
		assertThat(root.has("badges")).isFalse();
	}

	@Test
	@DisplayName("guidance event direction은 type과 별도 필드로 직렬화한다")
	void routeGuidanceDirectionSerializesSeparatelyFromType() throws Exception {
		RouteGuidanceEventResponse response = new RouteGuidanceEventResponse(
			1,
			RouteGuidanceEventType.CROSSWALK,
			RouteGuidanceDirection.TURN_RIGHT,
			List.of(RouteGuidanceFeature.SIGNAL),
			BigDecimal.valueOf(159),
			120,
			"POINT(128.872855 35.082394)");

		JsonNode guidanceEvent = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(guidanceEvent.get("type").asText()).isEqualTo("CROSSWALK");
		assertThat(guidanceEvent.get("direction").asText()).isEqualTo("TURN_RIGHT");
		assertThat(guidanceEvent.get("features").get(0).asText()).isEqualTo("SIGNAL");
	}

	@Test
	@DisplayName("direction-only guidance event는 type 없이 직렬화한다")
	void directionOnlyGuidanceEventOmitsType() throws Exception {
		RouteGuidanceEventResponse response = new RouteGuidanceEventResponse(
			1,
			null,
			RouteGuidanceDirection.TURN_RIGHT,
			BigDecimal.valueOf(159),
			120,
			"POINT(128.872855 35.082394)");

		JsonNode guidanceEvent = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(guidanceEvent.has("type")).isFalse();
		assertThat(guidanceEvent.get("direction").asText()).isEqualTo("TURN_RIGHT");
	}

	@Test
	@DisplayName("transit route warning code는 문자열 배열로 직렬화한다")
	void transitRouteWarningsSerializeAsCodeArray() throws Exception {
		WalkRouteSearchResponse response = new WalkRouteSearchResponse(
			"rs_transit_20260506_abc123",
			List.of(new RouteSummaryResponse(
				"pt_rt_001",
				TransportMode.PUBLIC_TRANSIT,
				RouteOption.RECOMMENDED,
				List.of(RouteOption.RECOMMENDED),
				"transit route",
				BigDecimal.valueOf(1200),
				601,
				10,
				List.of(),
				List.of(RouteWarningCode.LOW_FLOOR_BUS_UNAVAILABLE),
				"LINESTRING(128.9360 35.1200, 128.8823 35.1315)",
				List.of())));

		JsonNode route = objectMapper.readTree(objectMapper.writeValueAsString(response)).get("routes").get(0);

		assertThat(route.get("warnings")).hasSize(1);
		assertThat(route.get("warnings").get(0).asText()).isEqualTo("LOW_FLOOR_BUS_UNAVAILABLE");
	}

	@Test
	@DisplayName("transit lane option은 저상버스 예약 계약을 중첩 객체로 직렬화한다")
	void transitLaneOptionSerializesLowFloorReservationContract() throws Exception {
		TransitLaneOptionResponse response = new TransitLaneOptionResponse(
			"7000",
			14,
			840,
			14,
			true,
			new LowFloorBusReservationResponse(
				"부산역",
				"70001",
				"7000",
				"1618",
				14,
				2));

		JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

		JsonNode reservation = root.get("lowFloorReservation");
		assertThat(reservation.get("stopName").asText()).isEqualTo("부산역");
		assertThat(reservation.get("arsNo").asText()).isEqualTo("70001");
		assertThat(reservation.get("routeNo").asText()).isEqualTo("7000");
		assertThat(reservation.get("vehicleNo").asText()).isEqualTo("1618");
		assertThat(reservation.get("remainingMinute").asInt()).isEqualTo(14);
		assertThat(reservation.get("remainingStopCount").asInt()).isEqualTo(2);
	}

	@Test
	@DisplayName("guidance event enum exposes API contract values")
	void routeGuidanceEventTypeExposesContractValues() {
		assertThat(RouteGuidanceEventType.values())
			.extracting(Enum::name)
			.contains(
				"CROSSWALK",
				"STRAIGHT",
				"STAIR",
				"NARROW_SIDEWALK",
				"UNPAVED",
				"LOW_SLOPE",
				"MIDDLE_SLOPE",
				"BUS_STOP",
				"SUBWAY_ELEVATOR",
				"ARRIVING_POINT",
				"DESTINATION")
			.doesNotContain("NONE", "CURB", "ELEVATOR", "ALIGHTING_POINT", "CROSSWALK_SIGNAL",
				"CROSSWALK_AUDIO");
	}
}
