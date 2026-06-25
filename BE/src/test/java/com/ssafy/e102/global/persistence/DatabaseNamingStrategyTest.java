package com.ssafy.e102.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StreamUtils;

import com.ssafy.e102.domain.admin.entity.AdminArea;
import com.ssafy.e102.domain.admin.entity.AdminAreaAssignment;
import com.ssafy.e102.domain.bookmark.entity.FavoriteRoute;
import com.ssafy.e102.domain.place.entity.Bookmark;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.entity.HazardReportImage;
import com.ssafy.e102.domain.route.entity.BusStop;
import com.ssafy.e102.domain.route.entity.OdsayLoadLane;
import com.ssafy.e102.domain.route.entity.RoadNode;
import com.ssafy.e102.domain.route.entity.RoadSegment;
import com.ssafy.e102.domain.route.entity.RoutingSegmentOverride;
import com.ssafy.e102.domain.route.entity.RouteRating;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.entity.SegmentFeature;
import com.ssafy.e102.domain.route.entity.SourceFeature;
import com.ssafy.e102.domain.route.entity.SubwayStation;
import com.ssafy.e102.domain.route.entity.SubwayStationAccessibilityFeature;
import com.ssafy.e102.domain.route.entity.SubwayStationElevator;
import com.ssafy.e102.domain.route.entity.SubwayTimetable;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

class DatabaseNamingStrategyTest {

	private static final List<Class<?>> ENTITY_TYPES = List.of(
		User.class,
		FavoriteRoute.class,
		Bookmark.class,
		Place.class,
		PlaceAccessibilityFeature.class,
		HazardReport.class,
		HazardReportImage.class,
		BusStop.class,
		OdsayLoadLane.class,
		RoadNode.class,
		RoadSegment.class,
		RoutingSegmentOverride.class,
		AdminArea.class,
		AdminAreaAssignment.class,
		SegmentFeature.class,
		SourceFeature.class,
		RouteRating.class,
		RouteSession.class,
		SubwayStation.class,
		SubwayStationAccessibilityFeature.class,
		SubwayStationElevator.class,
		SubwayTimetable.class);

	@Test
	@DisplayName("Hibernate 물리 컬럼 네이밍은 snake_case 전략을 사용한다")
	void applicationUsesSnakeCasePhysicalNamingStrategy() throws IOException {
		ClassPathResource resource = new ClassPathResource("application.yml");
		String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

		assertThat(content)
			.contains("org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
			.doesNotContain("org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
	}

	@Test
	@DisplayName("현재 JPA 엔티티는 모두 naming 회귀 테스트 대상에 포함한다")
	void allCurrentEntitiesAreCoveredByNamingRegressionTest() {
		assertThat(ENTITY_TYPES)
			.containsExactlyInAnyOrderElementsOf(scanEntityTypes());
	}

	@Test
	@DisplayName("엔티티 테이블명은 명시적인 snake_case를 사용한다")
	void entityTableNamesUseExplicitSnakeCase() {
		for (Class<?> entity : ENTITY_TYPES) {
			Table table = entity.getAnnotation(Table.class);
			assertThat(table)
				.as("%s must declare @Table", entity.getSimpleName())
				.isNotNull();
			assertThat(table.name())
				.as("%s @Table name", entity.getSimpleName())
				.matches("[a-z0-9_]+");
		}
	}

	@Test
	@DisplayName("사용자 관련 엔티티의 물리 컬럼명은 snake_case다")
	void userAndFavoriteRouteColumnsUseSnakeCase() {
		assertThat(physicalColumnName(User.class, "userId")).isEqualTo("user_id");
		assertThat(physicalColumnName(User.class, "socialProvider")).isEqualTo("social_provider");
		assertThat(physicalColumnName(User.class, "socialProviderUserId")).isEqualTo("social_provider_user_id");
		assertThat(physicalColumnName(User.class, "selectedPrimaryUserType"))
			.isEqualTo("selected_primary_user_type");
		assertThat(physicalColumnName(User.class, "selectedMobilitySubtype"))
			.isEqualTo("selected_mobility_subtype");

		Table userTable = User.class.getAnnotation(Table.class);
		assertThat(userTable.uniqueConstraints())
			.extracting(UniqueConstraint::name)
			.contains("uk_users_social_provider_user_id");
		assertThat(uniqueColumnNames(User.class))
			.contains("social_provider", "social_provider_user_id");

		assertThat(physicalColumnName(FavoriteRoute.class, "favRouteId")).isEqualTo("fav_route_id");
		assertThat(physicalColumnName(FavoriteRoute.class, "routeName")).isEqualTo("route_name");
		assertThat(physicalColumnName(FavoriteRoute.class, "startLabel")).isEqualTo("start_label");
		assertThat(physicalColumnName(FavoriteRoute.class, "endLabel")).isEqualTo("end_label");
		assertThat(physicalColumnName(FavoriteRoute.class, "startPoint")).isEqualTo("start_point");
		assertThat(physicalColumnName(FavoriteRoute.class, "endPoint")).isEqualTo("end_point");
		assertThat(physicalColumnName(FavoriteRoute.class, "transportMode")).isEqualTo("transport_mode");
		assertThat(physicalColumnName(FavoriteRoute.class, "routeOption")).isEqualTo("route_option");
		assertThat(physicalColumnName(FavoriteRoute.class, "routeSnapshotJson"))
			.isEqualTo("route_snapshot_json");
		assertThat(joinColumnName(FavoriteRoute.class, "user")).isEqualTo("user_id");
	}

	@Test
	@DisplayName("장소 관련 엔티티의 물리 컬럼명은 snake_case다")
	void placeColumnsUseSnakeCase() {
		assertThat(physicalColumnName(Bookmark.class, "bookmarkId")).isEqualTo("bookmark_id");
		assertThat(joinColumnName(Bookmark.class, "user")).isEqualTo("user_id");
		assertThat(joinColumnName(Bookmark.class, "place")).isEqualTo("place_id");
		assertThat(uniqueColumnNames(Bookmark.class)).contains("user_id", "place_id");

		assertThat(physicalColumnName(Place.class, "placeId")).isEqualTo("place_id");
		assertThat(physicalColumnName(Place.class, "name")).isEqualTo("name");
		assertThat(physicalColumnName(Place.class, "category")).isEqualTo("category");
		assertThat(physicalColumnName(Place.class, "address")).isEqualTo("address");
		assertThat(physicalColumnName(Place.class, "point")).isEqualTo("point");
		assertThat(physicalColumnName(Place.class, "providerPlaceId")).isEqualTo("provider_place_id");
		assertThat(uniqueColumnNames(Place.class)).contains("provider_place_id");

		assertThat(physicalColumnName(PlaceAccessibilityFeature.class, "id")).isEqualTo("id");
		assertThat(joinColumnName(PlaceAccessibilityFeature.class, "place")).isEqualTo("place_id");
		assertThat(physicalColumnName(PlaceAccessibilityFeature.class, "featureType"))
			.isEqualTo("feature_type");
		assertThat(physicalColumnName(PlaceAccessibilityFeature.class, "isAvailable"))
			.isEqualTo("is_available");
		assertThat(uniqueColumnNames(PlaceAccessibilityFeature.class)).contains("place_id", "feature_type");
	}

	@Test
	@DisplayName("제보 관련 엔티티의 물리 컬럼명은 snake_case다")
	void hazardReportColumnsUseSnakeCase() {
		assertThat(physicalColumnName(HazardReport.class, "reportId")).isEqualTo("report_id");
		assertThat(joinColumnName(HazardReport.class, "user")).isEqualTo("user_id");
		assertThat(physicalColumnName(HazardReport.class, "reportType")).isEqualTo("report_type");
		assertThat(physicalColumnName(HazardReport.class, "description")).isEqualTo("description");
		assertThat(physicalColumnName(HazardReport.class, "address")).isEqualTo("address");
		assertThat(physicalColumnName(HazardReport.class, "idempotencyKey")).isEqualTo("idempotency_key");
		assertThat(physicalColumnName(HazardReport.class, "idempotencyRequestHash"))
			.isEqualTo("idempotency_request_hash");
		assertThat(physicalColumnName(HazardReport.class, "idempotencyExpiresAt"))
			.isEqualTo("idempotency_expires_at");
		assertThat(physicalColumnName(HazardReport.class, "reportPoint")).isEqualTo("report_point");
		assertThat(physicalColumnName(HazardReport.class, "status")).isEqualTo("status");
		assertThat(uniqueColumnNames(HazardReport.class)).contains("user_id", "idempotency_key");

		assertThat(physicalColumnName(HazardReportImage.class, "reportImgId")).isEqualTo("report_img_id");
		assertThat(physicalColumnName(HazardReportImage.class, "imageObjectKey")).isEqualTo("image_url");
		assertThat(physicalColumnName(HazardReportImage.class, "displayOrder")).isEqualTo("display_order");
		assertThat(joinColumnName(HazardReportImage.class, "hazardReport")).isEqualTo("report_id");
		assertThat(uniqueColumnNames(HazardReportImage.class)).contains("report_id", "display_order");
	}

	@Test
	@DisplayName("보행 네트워크 엔티티의 물리 컬럼명은 snake_case다")
	void roadNetworkColumnsUseSnakeCase() {
		assertThat(physicalColumnName(RoadNode.class, "vertexId")).isEqualTo("vertex_id");
		assertThat(physicalColumnName(RoadNode.class, "sourceNodeKey")).isEqualTo("source_node_key");

		assertThat(uniqueColumnNames(RoadNode.class))
			.contains("source_node_key");

		assertThat(physicalColumnName(RoadSegment.class, "edgeId")).isEqualTo("edge_id");
		assertThat(physicalColumnName(RoadSegment.class, "fromNodeId")).isEqualTo("from_node_id");
		assertThat(physicalColumnName(RoadSegment.class, "toNodeId")).isEqualTo("to_node_id");
		assertThat(physicalColumnName(RoadSegment.class, "lengthMeter")).isEqualTo("length_meter");
		assertThat(physicalColumnName(RoadSegment.class, "walkAccess")).isEqualTo("walk_access");
		assertThat(physicalColumnName(RoadSegment.class, "avgSlopePercent"))
			.isEqualTo("avg_slope_percent");
		assertThat(physicalColumnName(RoadSegment.class, "widthMeter")).isEqualTo("width_meter");
		assertThat(physicalColumnName(RoadSegment.class, "brailleBlockState"))
			.isEqualTo("braille_block_state");
		assertThat(physicalColumnName(RoadSegment.class, "audioSignalState"))
			.isEqualTo("audio_signal_state");
		assertThat(physicalColumnName(RoadSegment.class, "widthState")).isEqualTo("width_state");
		assertThat(physicalColumnName(RoadSegment.class, "surfaceState")).isEqualTo("surface_state");
		assertThat(physicalColumnName(RoadSegment.class, "stairsState")).isEqualTo("stairs_state");
		assertThat(physicalColumnName(RoadSegment.class, "signalState")).isEqualTo("signal_state");
		assertThat(physicalColumnName(RoadSegment.class, "segmentType")).isEqualTo("segment_type");

		assertThat(physicalColumnName(RoutingSegmentOverride.class, "edgeId")).isEqualTo("edge_id");
		assertThat(physicalColumnName(RoutingSegmentOverride.class, "walkAccess")).isEqualTo("walk_access");
		assertThat(physicalColumnName(RoutingSegmentOverride.class, "stairsState")).isEqualTo("stairs_state");
		assertThat(physicalColumnName(RoutingSegmentOverride.class, "widthState")).isEqualTo("width_state");
		assertThat(physicalColumnName(RoutingSegmentOverride.class, "brailleBlockState"))
			.isEqualTo("braille_block_state");

		assertThat(physicalColumnName(AdminArea.class, "areaId")).isEqualTo("area_id");
		assertThat(physicalColumnName(AdminArea.class, "gu")).isEqualTo("gu");
		assertThat(physicalColumnName(AdminArea.class, "dong")).isEqualTo("dong");
		assertThat(physicalColumnName(AdminArea.class, "geom")).isEqualTo("geom");

		assertThat(physicalColumnName(AdminAreaAssignment.class, "assignmentId")).isEqualTo("assignment_id");
		assertThat(physicalColumnName(AdminAreaAssignment.class, "gu")).isEqualTo("gu");
		assertThat(physicalColumnName(AdminAreaAssignment.class, "dong")).isEqualTo("dong");
		assertThat(physicalColumnName(AdminAreaAssignment.class, "assignmentType")).isEqualTo("assignment_type");
		assertThat(joinColumnName(AdminAreaAssignment.class, "assignee")).isEqualTo("assignee_user_id");
		assertThat(physicalColumnName(AdminAreaAssignment.class, "status")).isEqualTo("status");
		assertThat(uniqueColumnNames(AdminAreaAssignment.class)).contains("gu", "dong", "assignment_type");

		assertThat(physicalColumnName(SegmentFeature.class, "featureId")).isEqualTo("feature_id");
		assertThat(physicalColumnName(SegmentFeature.class, "edgeId")).isEqualTo("edge_id");
		assertThat(physicalColumnName(SegmentFeature.class, "featureType")).isEqualTo("feature_type");
		assertThat(physicalColumnName(SegmentFeature.class, "geom")).isEqualTo("geom");
		assertThat(physicalColumnName(SegmentFeature.class, "state")).isEqualTo("state");
		assertThat(physicalColumnName(SegmentFeature.class, "valueNumber")).isEqualTo("value_number");

		assertThat(physicalColumnName(SourceFeature.class, "sourceFeatureId")).isEqualTo("source_feature_id");
		assertThat(physicalColumnName(SourceFeature.class, "featureType")).isEqualTo("feature_type");
		assertThat(physicalColumnName(SourceFeature.class, "geom")).isEqualTo("geom");
		assertThat(physicalColumnName(SourceFeature.class, "state")).isEqualTo("state");
		assertThat(physicalColumnName(SourceFeature.class, "valueNumber")).isEqualTo("value_number");
		assertThat(physicalColumnName(SourceFeature.class, "sourceFile")).isEqualTo("source_file");
	}

	@Test
	@DisplayName("경로 세션과 평가 엔티티의 물리 컬럼명은 snake_case다")
	void routeSessionAndRatingColumnsUseSnakeCase() {
		assertThat(physicalColumnName(RouteSession.class, "sessionId")).isEqualTo("session_id");
		assertThat(joinColumnName(RouteSession.class, "user")).isEqualTo("user_id");
		assertThat(physicalColumnName(RouteSession.class, "routeId")).isEqualTo("route_id");
		assertThat(physicalColumnName(RouteSession.class, "activeRouteKey")).isEqualTo("active_route_key");
		assertThat(physicalColumnName(RouteSession.class, "startPoint")).isEqualTo("start_point");
		assertThat(physicalColumnName(RouteSession.class, "endPoint")).isEqualTo("end_point");
		assertThat(physicalColumnName(RouteSession.class, "routeSnapshotJson"))
			.isEqualTo("route_snapshot_json");
		assertThat(physicalColumnName(RouteSession.class, "status")).isEqualTo("status");
		assertThat(uniqueColumnNames(RouteSession.class)).contains("user_id", "active_route_key");

		assertThat(physicalColumnName(OdsayLoadLane.class, "odsayLoadLaneId")).isEqualTo("odsay_load_lane_id");
		assertThat(physicalColumnName(OdsayLoadLane.class, "mapObj")).isEqualTo("map_obj");
		assertThat(physicalColumnName(OdsayLoadLane.class, "laneGeometries")).isEqualTo("lane_geometries");
		assertThat(uniqueColumnNames(OdsayLoadLane.class)).contains("map_obj");

		assertThat(physicalColumnName(RouteRating.class, "ratingId")).isEqualTo("rating_id");
		assertThat(joinColumnName(RouteRating.class, "user")).isEqualTo("user_id");
		assertThat(joinColumnName(RouteRating.class, "routeSession")).isEqualTo("session_id");
		assertThat(physicalColumnName(RouteRating.class, "routeId")).isEqualTo("route_id");
		assertThat(physicalColumnName(RouteRating.class, "score")).isEqualTo("score");
		assertThat(physicalColumnName(RouteRating.class, "routeContextJson"))
			.isEqualTo("route_context_json");
		assertThat(uniqueColumnNames(RouteRating.class)).contains("session_id");
	}

	@Test
	@DisplayName("지하철 시간표 엔티티의 물리 컬럼명은 snake_case다")
	void subwayScheduleColumnsUseSnakeCase() {
		assertThat(physicalColumnName(SubwayStation.class, "subwayStationId"))
			.isEqualTo("subway_station_id");
		assertThat(physicalColumnName(SubwayStation.class, "odsayStationId"))
			.isEqualTo("odsay_station_id");
		assertThat(physicalColumnName(SubwayStation.class, "stationName")).isEqualTo("station_name");
		assertThat(physicalColumnName(SubwayStation.class, "lineName")).isEqualTo("line_name");
		assertThat(physicalColumnName(SubwayStation.class, "point")).isEqualTo("point");
		assertThat(uniqueColumnNames(SubwayStation.class)).contains("odsay_station_id");

		assertThat(physicalColumnName(SubwayStationAccessibilityFeature.class, "id")).isEqualTo("id");
		assertThat(joinColumnName(SubwayStationAccessibilityFeature.class, "subwayStation"))
			.isEqualTo("subway_station_id");
		assertThat(physicalColumnName(SubwayStationAccessibilityFeature.class, "featureType"))
			.isEqualTo("feature_type");
		assertThat(physicalColumnName(SubwayStationAccessibilityFeature.class, "isAvailable"))
			.isEqualTo("is_available");
		assertThat(uniqueColumnNames(SubwayStationAccessibilityFeature.class))
			.contains("subway_station_id", "feature_type");

		assertThat(physicalColumnName(SubwayTimetable.class, "subwayTimetableId"))
			.isEqualTo("subway_timetable_id");
		assertThat(physicalColumnName(SubwayTimetable.class, "odsayStationId"))
			.isEqualTo("odsay_station_id");
		assertThat(physicalColumnName(SubwayTimetable.class, "serviceDayType"))
			.isEqualTo("service_day_type");
		assertThat(physicalColumnName(SubwayTimetable.class, "wayCode")).isEqualTo("way_code");
		assertThat(physicalColumnName(SubwayTimetable.class, "departureTimeText"))
			.isEqualTo("departure_time_text");
		assertThat(physicalColumnName(SubwayTimetable.class, "departureSecondOfDay"))
			.isEqualTo("departure_second_of_day");
		assertThat(physicalColumnName(SubwayTimetable.class, "endStationName"))
			.isEqualTo("end_station_name");
		assertThat(uniqueColumnNames(SubwayTimetable.class))
			.contains(
				"odsay_station_id",
				"service_day_type",
				"way_code",
				"departure_second_of_day",
				"end_station_name");
	}

	@Test
	@DisplayName("감사 컬럼은 snake_case로 고정한다")
	void baseEntityAuditColumnsUseSnakeCase() {
		assertThat(physicalColumnName(BaseEntity.class, "createdAt")).isEqualTo("created_at");
		assertThat(physicalColumnName(BaseEntity.class, "updatedAt")).isEqualTo("updated_at");
	}

	@Test
	@DisplayName("명시한 컬럼명과 조인 컬럼명에는 camelCase를 남기지 않는다")
	void explicitColumnNamesDoNotUseCamelCase() {
		List<Class<?>> entities = List.of(
			BaseEntity.class,
			User.class,
			FavoriteRoute.class,
			Bookmark.class,
			Place.class,
			PlaceAccessibilityFeature.class,
			HazardReport.class,
			HazardReportImage.class,
			BusStop.class,
			OdsayLoadLane.class,
			RoadNode.class,
			RoadSegment.class,
			RoutingSegmentOverride.class,
			AdminArea.class,
			AdminAreaAssignment.class,
			SegmentFeature.class,
			SourceFeature.class,
			RouteRating.class,
			RouteSession.class,
			SubwayStation.class,
			SubwayStationAccessibilityFeature.class,
			SubwayTimetable.class);

		for (Class<?> entity : entities) {
			for (Field field : entity.getDeclaredFields()) {
				Column column = field.getAnnotation(Column.class);
				if (column != null && !column.name().isBlank()) {
					assertThat(column.name())
						.as("%s.%s @Column name", entity.getSimpleName(), field.getName())
						.isEqualTo(column.name().toLowerCase(Locale.ROOT));
				}
				JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
				if (joinColumn != null && !joinColumn.name().isBlank()) {
					assertThat(joinColumn.name())
						.as("%s.%s @JoinColumn name", entity.getSimpleName(), field.getName())
						.isEqualTo(joinColumn.name().toLowerCase(Locale.ROOT));
				}
			}
		}
	}

	@Test
	@DisplayName("연관관계 FK 컬럼은 명시적인 snake_case @JoinColumn을 사용한다")
	void associationJoinColumnsAreExplicitSnakeCase() {
		for (Class<?> entity : ENTITY_TYPES) {
			for (Field field : entity.getDeclaredFields()) {
				if (field.getAnnotation(ManyToOne.class) == null && field.getAnnotation(OneToOne.class) == null) {
					continue;
				}

				JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
				assertThat(joinColumn)
					.as("%s.%s must declare @JoinColumn", entity.getSimpleName(), field.getName())
					.isNotNull();
				assertThat(joinColumn.name())
					.as("%s.%s @JoinColumn name", entity.getSimpleName(), field.getName())
					.matches("[a-z0-9_]+");
			}
		}
	}

	@Test
	@DisplayName("native SQL은 naming strategy를 타지 않으므로 snake_case 컬럼명을 직접 사용한다")
	void nativeSqlUsesSnakeCaseColumnNames() throws IOException {
		String placeRepository = Files.readString(
			Path.of("src/main/java/com/ssafy/e102/domain/place/repository/PlaceRepository.java"));

		assertThat(placeRepository)
			.contains("p.place_id")
			.contains("ef.place_id")
			.contains("ef.is_available")
			.contains("ef.feature_type")
			.doesNotContain("p.placeId")
			.doesNotContain("ef.placeId")
			.doesNotContain("ef.isAvailable")
			.doesNotContain("ef.featureType");
	}

	private String physicalColumnName(Class<?> type, String fieldName) {
		Field field = findField(type, fieldName);
		Column column = field.getAnnotation(Column.class);
		if (column != null && !column.name().isBlank()) {
			return column.name();
		}
		return camelToSnake(fieldName);
	}

	private String joinColumnName(Class<?> type, String fieldName) {
		Field field = findField(type, fieldName);
		JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
		assertThat(joinColumn)
			.as("%s.%s must declare @JoinColumn", type.getSimpleName(), fieldName)
			.isNotNull();
		return joinColumn.name();
	}

	private List<String> uniqueColumnNames(Class<?> type) {
		Table table = type.getAnnotation(Table.class);
		assertThat(table)
			.as("%s must declare @Table", type.getSimpleName())
			.isNotNull();
		return Arrays.stream(table.uniqueConstraints())
			.flatMap(uniqueConstraint -> Arrays.stream(uniqueConstraint.columnNames()))
			.toList();
	}

	private Field findField(Class<?> type, String fieldName) {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(fieldName);
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new IllegalArgumentException("Field not found: " + type.getName() + "." + fieldName);
	}

	private Set<Class<?>> scanEntityTypes() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
		return scanner.findCandidateComponents("com.ssafy.e102")
			.stream()
			.map(BeanDefinition::getBeanClassName)
			.map(this::loadClass)
			.collect(Collectors.toSet());
	}

	private Class<?> loadClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException exception) {
			throw new IllegalStateException("Entity class not found: " + className, exception);
		}
	}

	private String camelToSnake(String value) {
		return value
			.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
			.toLowerCase(Locale.ROOT);
	}
}
