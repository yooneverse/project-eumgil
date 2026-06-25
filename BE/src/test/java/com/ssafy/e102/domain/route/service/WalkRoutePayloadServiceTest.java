package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceDirection;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventType;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceFeature;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.global.external.graphhopper.GraphHopperCoordinate;
import com.ssafy.e102.global.external.graphhopper.GraphHopperPathDetail;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;

class WalkRoutePayloadServiceTest {

	private final WalkRoutePayloadService service = new WalkRoutePayloadService(new RouteTurnInstructionService());

	@Test
	void mapsGraphHopperPathDetailsToGuidanceEvents() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("950.456"),
			960_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.9360"), new BigDecimal("35.1200")),
				new GraphHopperCoordinate(new BigDecimal("128.9361"), new BigDecimal("35.1201")),
				new GraphHopperCoordinate(new BigDecimal("128.9362"), new BigDecimal("35.1202"))),
			Map.of(
				"segment_type", List.of(new GraphHopperPathDetail(0, 1, "CROSS_WALK")),
				"signal_state", List.of(new GraphHopperPathDetail(0, 1, "YES")),
				"avg_slope_percent", List.of(new GraphHopperPathDetail(0, 2, "6.25")),
				"width_state", List.of(new GraphHopperPathDetail(0, 2, "NARROW")),
				"surface_state", List.of(new GraphHopperPathDetail(1, 2, "UNPAVED"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.routeId()).isEqualTo("rs_walk_test_safe");
		assertThat(route.transportMode()).isEqualTo(TransportMode.WALK);
		assertThat(route.routeOption()).isEqualTo(RouteOption.SAFE);
		assertThat(route.distanceMeter()).isEqualByComparingTo("950.46");
		assertThat(route.durationSecond()).isEqualTo(960);
		assertThat(route.estimatedTimeMinute()).isEqualTo(16);
		assertThat(route.badges())
			.containsExactly(
				RouteBadge.NARROW_SIDEWALK,
				RouteBadge.UNPAVED,
				RouteBadge.MIDDLE_SLOPE,
				RouteBadge.CROSSWALK);
		assertThat(route.legs()).hasSize(1);
		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly(
				RouteGuidanceEventType.NARROW_SIDEWALK,
				RouteGuidanceEventType.UNPAVED,
				RouteGuidanceEventType.STRAIGHT);
		assertThat(route.legs().get(0).guidanceEvents().get(0).distanceFromLegStartMeter())
			.isEqualByComparingTo("0.00");
		assertThat(route.legs().get(0).guidanceEvents().get(0).geometry())
			.isEqualTo("POINT(128.9360 35.1200)");
	}

	@Test
	void convertsTurnManeuverToGuidanceEvent() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("200.00"),
			120_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("0.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("1.0"))),
			Map.of("width_state", List.of(new GraphHopperPathDetail(0, 2, "ADEQUATE_150"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly((RouteGuidanceEventType)null);
		RouteGuidanceEventResponse turnEvent = route.legs().get(0).guidanceEvents().get(0);
		assertThat(turnEvent.direction()).isEqualTo(RouteGuidanceDirection.TURN_LEFT);
		assertThat(turnEvent.distanceFromLegStartMeter()).isGreaterThan(BigDecimal.ZERO);
		assertThat(turnEvent.geometry()).isEqualTo("POINT(1.0 0.0)");
	}

	@Test
	void mergesTurnDirectionIntoAccessibilityEventAtSameDistance() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("200.00"),
			120_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("0.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("1.0"))),
			Map.of("width_state", List.of(new GraphHopperPathDetail(1, 2, "NARROW"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents()).hasSize(1);
		RouteGuidanceEventResponse event = route.legs().get(0).guidanceEvents().get(0);
		assertThat(event.type()).isEqualTo(RouteGuidanceEventType.NARROW_SIDEWALK);
		assertThat(event.direction()).isEqualTo(RouteGuidanceDirection.TURN_LEFT);
		assertThat(event.geometry()).isEqualTo("POINT(1.0 0.0)");
	}

	@Test
	void suppressesTurnEventsWithinTenMetersBeforeOrAfterCrosswalkAndAddsContinueStraight() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("200.00"),
			120_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("0.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("1.0"))),
			Map.of(
				"segment_type", List.of(new GraphHopperPathDetail(0, 1, "CROSS_WALK")),
				"signal_state", List.of(new GraphHopperPathDetail(0, 1, "YES"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly(RouteGuidanceEventType.CROSSWALK, RouteGuidanceEventType.STRAIGHT);
		RouteGuidanceEventResponse crosswalk = route.legs().get(0).guidanceEvents().get(0);
		assertThat(crosswalk.features()).containsExactly(RouteGuidanceFeature.SIGNAL);
		assertThat(crosswalk.direction()).isNull();
		RouteGuidanceEventResponse straight = route.legs().get(0).guidanceEvents().get(1);
		assertThat(straight.direction()).isEqualTo(RouteGuidanceDirection.STRAIGHT);
		assertThat(straight.geometry()).isEqualTo("POINT(1.0 0.0)");
	}

	@Test
	void suppressesShortZigzagTurnGuidanceEvents() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("101.00"),
			101_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("0.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("-0.01")),
				new GraphHopperCoordinate(new BigDecimal("2.0"), new BigDecimal("-0.01"))),
			Map.of());

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents()).isEmpty();
	}

	@Test
	void allocatesGuidanceEventDistanceAndDurationByHaversineGeometryLength() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("300.00"),
			90_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0020"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0020"), new BigDecimal("35.0010"))),
			Map.of("width_state", List.of(new GraphHopperPathDetail(2, 3, "NARROW"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		RouteGuidanceEventResponse event = route.legs().get(0).guidanceEvents()
			.stream()
			.filter(guidanceEvent -> guidanceEvent.type() == RouteGuidanceEventType.NARROW_SIDEWALK)
			.findFirst()
			.orElseThrow();
		assertThat(event.distanceFromLegStartMeter()).isGreaterThan(BigDecimal.ZERO)
			.isLessThan(new BigDecimal("300.00"));
		assertThat(event.durationFromLegStartSecond()).isBetween(1, 89);
		assertThat(event.geometry()).isEqualTo("POINT(128.0020 35.0000)");
	}

	@Test
	void choosesGuidanceEventByPriorityAtSameCoordinate() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("100.00"),
			60_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000"))),
			Map.of(
				"segment_type", List.of(new GraphHopperPathDetail(0, 1, "CROSS_WALK")),
				"stairs_state", List.of(new GraphHopperPathDetail(0, 1, "YES")),
				"width_state", List.of(new GraphHopperPathDetail(0, 1, "NARROW")),
				"surface_state", List.of(new GraphHopperPathDetail(0, 1, "UNPAVED")),
				"avg_slope_percent", List.of(new GraphHopperPathDetail(0, 1, "6.25"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents()).hasSize(1);
		assertThat(route.legs().get(0).guidanceEvents().get(0).type()).isEqualTo(RouteGuidanceEventType.STAIR);
		assertThat(route.legs().get(0).guidanceEvents().get(0).distanceFromLegStartMeter())
			.isEqualByComparingTo("0.00");
	}

	@Test
	void attachesCrosswalkSignalAndAudioSignalAsFeatures() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("100.00"),
			60_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0020"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0030"), new BigDecimal("35.0000"))),
			Map.of(
				"segment_type", List.of(
					new GraphHopperPathDetail(0, 1, "CROSS_WALK"),
					new GraphHopperPathDetail(1, 2, "CROSS_WALK"),
					new GraphHopperPathDetail(2, 3, "CROSS_WALK")),
				"signal_state", List.of(
					new GraphHopperPathDetail(1, 2, "YES"),
					new GraphHopperPathDetail(2, 3, "YES")),
				"audio_signal_state", List.of(new GraphHopperPathDetail(2, 3, "YES"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly(
				RouteGuidanceEventType.CROSSWALK,
				RouteGuidanceEventType.CROSSWALK,
				RouteGuidanceEventType.CROSSWALK);
		assertThat(route.legs().get(0).guidanceEvents().get(0).features()).isEmpty();
		assertThat(route.legs().get(0).guidanceEvents().get(1).features())
			.containsExactly(RouteGuidanceFeature.SIGNAL);
		assertThat(route.legs().get(0).guidanceEvents().get(2).features())
			.containsExactly(RouteGuidanceFeature.SIGNAL, RouteGuidanceFeature.AUDIO_SIGNAL);
		assertThat(route.legs().get(0).guidanceEvents().get(1).distanceFromLegStartMeter())
			.isGreaterThan(BigDecimal.ZERO);
		assertThat(route.legs().get(0).guidanceEvents().get(2).distanceFromLegStartMeter())
			.isGreaterThan(route.legs().get(0).guidanceEvents().get(1).distanceFromLegStartMeter());
	}

	@Test
	void keepsAudioSignalFeatureEvenWithoutSignalState() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("100.00"),
			60_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000"))),
			Map.of(
				"segment_type", List.of(new GraphHopperPathDetail(0, 1, "CROSS_WALK")),
				"audio_signal_state", List.of(new GraphHopperPathDetail(0, 1, "YES"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents()).hasSize(1);
		assertThat(route.legs().get(0).guidanceEvents().get(0).type()).isEqualTo(RouteGuidanceEventType.CROSSWALK);
		assertThat(route.legs().get(0).guidanceEvents().get(0).features())
			.containsExactly(RouteGuidanceFeature.AUDIO_SIGNAL);
	}

	@Test
	void summarizesStartingLowSlopeOnlyAsRouteBadge() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("100.00"),
			60_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000"))),
			Map.of("avg_slope_percent", List.of(new GraphHopperPathDetail(0, 1, "2.50"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.badges()).containsExactly(RouteBadge.LOW_SLOPE);
		assertThat(route.legs().get(0).guidanceEvents()).isEmpty();
	}

	@Test
	void deduplicatesContinuousAccessibilityStateEvents() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("500.00"),
			300_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0020"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0030"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0040"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0050"), new BigDecimal("35.0000"))),
			Map.of("avg_slope_percent", List.of(
				new GraphHopperPathDetail(0, 1, "2.50"),
				new GraphHopperPathDetail(1, 2, "2.60"),
				new GraphHopperPathDetail(2, 3, "6.25"),
				new GraphHopperPathDetail(3, 4, "6.30"),
				new GraphHopperPathDetail(4, 5, "2.50"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.badges()).containsExactly(RouteBadge.MIDDLE_SLOPE, RouteBadge.LOW_SLOPE);
		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly(RouteGuidanceEventType.MIDDLE_SLOPE, RouteGuidanceEventType.LOW_SLOPE);
		assertThat(route.legs().get(0).guidanceEvents().get(0).distanceFromLegStartMeter())
			.isGreaterThan(BigDecimal.ZERO);
		assertThat(route.legs().get(0).guidanceEvents().get(1).distanceFromLegStartMeter())
			.isGreaterThan(route.legs().get(0).guidanceEvents().get(0).distanceFromLegStartMeter());
	}

	@Test
	void keepsTurnEventSeparateFromContinuousAccessibilityStateDedupe() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("200.00"),
			120_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("0.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("0.0")),
				new GraphHopperCoordinate(new BigDecimal("1.0"), new BigDecimal("1.0"))),
			Map.of("width_state", List.of(
				new GraphHopperPathDetail(0, 1, "NARROW"),
				new GraphHopperPathDetail(1, 2, "NARROW"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly(RouteGuidanceEventType.NARROW_SIDEWALK, null);
		assertThat(route.legs().get(0).guidanceEvents().get(1).direction())
			.isEqualTo(RouteGuidanceDirection.TURN_LEFT);
	}

	@Test
	void classifiesSlopeByWalkRouteProfileThresholds() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("100.00"),
			60_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000"))),
			Map.of("avg_slope_percent", List.of(new GraphHopperPathDetail(0, 1, "4.00"))));

		RouteSummaryResponse pedestrianRoute = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));
		RouteSummaryResponse visualRoute = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.VISUAL_SAFE, path));

		assertThat(pedestrianRoute.badges()).containsExactly(RouteBadge.LOW_SLOPE);
		assertThat(visualRoute.badges()).containsExactly(RouteBadge.MIDDLE_SLOPE);
	}

	@Test
	void includesLowAndMiddleSlopeBadgesWhenBothSlopeTypesExist() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("100.00"),
			60_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0020"), new BigDecimal("35.0000"))),
			Map.of("avg_slope_percent", List.of(
				new GraphHopperPathDetail(0, 1, "6.25"),
				new GraphHopperPathDetail(1, 2, "2.50"))));

		RouteSummaryResponse route = service.toRouteSummary(
			"rs_walk_test",
			new WalkRouteCandidate(RouteOption.SAFE, WalkRouteProfile.PEDESTRIAN_SAFE, path));

		assertThat(route.badges()).containsExactly(RouteBadge.MIDDLE_SLOPE, RouteBadge.LOW_SLOPE);
		assertThat(route.legs().get(0).guidanceEvents())
			.extracting(RouteGuidanceEventResponse::type)
			.containsExactly(RouteGuidanceEventType.MIDDLE_SLOPE, RouteGuidanceEventType.LOW_SLOPE);
	}

	@Test
	void appendsDestinationGuidanceEventAtWalkLegEnd() {
		GraphHopperRoutePath path = new GraphHopperRoutePath(
			new BigDecimal("30.00"),
			30_000,
			List.of(
				new GraphHopperCoordinate(new BigDecimal("128.0000"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0010"), new BigDecimal("35.0000")),
				new GraphHopperCoordinate(new BigDecimal("128.0020"), new BigDecimal("35.0000"))),
			Map.of());

		var leg = service.toWalkLeg(
			1,
			RouteLegRole.WALK_TO_TRANSIT,
			"walk to bus stop",
			path,
			RouteGuidanceEventType.BUS_STOP);

		assertThat(leg.guidanceEvents()).hasSize(1);
		assertThat(leg.guidanceEvents().get(0).type()).isEqualTo(RouteGuidanceEventType.BUS_STOP);
		assertThat(leg.guidanceEvents().get(0).distanceFromLegStartMeter()).isEqualByComparingTo("30.00");
		assertThat(leg.guidanceEvents().get(0).durationFromLegStartSecond()).isEqualTo(30);
		assertThat(leg.guidanceEvents().get(0).geometry()).isEqualTo("POINT(128.0020 35.0000)");
	}
}
