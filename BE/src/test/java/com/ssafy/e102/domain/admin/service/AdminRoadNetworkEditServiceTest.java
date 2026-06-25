package com.ssafy.e102.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminRoadNetworkEditServiceTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private AdminService adminService;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	private AdminRoadNetworkEditService adminRoadNetworkEditService;

	@BeforeEach
	void setUp() {
		adminRoadNetworkEditService = new AdminRoadNetworkEditService(
			jdbcTemplate,
			adminService,
			adminAuditLogService);
	}

	@Test
	@DisplayName("새 add endpoint들은 1m 반경 cluster 단위로 같은 node를 생성한다")
	void resolveAddSegmentNodesClustersNearbyCreatedEndpoints() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

		ReflectionTestUtils.invokeMethod(adminRoadNetworkEditService, "resolveAddSegmentNodes");

		verify(jdbcTemplate, times(6)).execute(sqlCaptor.capture());
		assertThat(sqlCaptor.getAllValues().get(3))
			.contains("admin_edit_created_point_assignments")
			.contains("ST_ClusterDBSCAN")
			.contains("eps := 1.0")
			.contains("admin_edit_unresolved_points");
	}

	@Test
	@DisplayName("bulk insert SQL은 resolved node 좌표로 저장 geometry endpoint를 다시 맞춘다")
	void insertBulkRoadSegmentsSynchronizesResolvedNodeCoordinatesIntoGeometry() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

		ReflectionTestUtils.invokeMethod(adminRoadNetworkEditService, "insertBulkRoadSegments");

		verify(jdbcTemplate, times(3)).execute(sqlCaptor.capture());
		List<String> sqlStatements = sqlCaptor.getAllValues();
		String insertSql = sqlStatements.get(2);

		assertThat(insertSql)
			.contains("join road_nodes from_node")
			.contains("join road_nodes to_node")
			.contains("ST_SetPoint")
			.contains("ST_NPoints(raw_geom) - 1")
			.contains("segment_pieces")
			.contains("admin_edit_new_segment_split_points");
	}

	@Test
	@DisplayName("bulk insert SQL은 최종 양끝 node가 같아진 add edit만 skip 처리한다")
	void insertBulkRoadSegmentsSkipsOnlySameNodeAdds() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

		ReflectionTestUtils.invokeMethod(adminRoadNetworkEditService, "insertBulkRoadSegments");

		verify(jdbcTemplate, times(3)).execute(sqlCaptor.capture());
		List<String> sqlStatements = sqlCaptor.getAllValues();

		assertThat(sqlStatements.get(1))
			.contains("admin_edit_skipped_add_segments");
		assertThat(sqlStatements.get(2))
			.contains("all_resolved_segments")
			.contains("where from_node_id = to_node_id")
			.contains("where from_node_id <> to_node_id");
	}

	@Test
	@DisplayName("add endpoint split SQL은 CROSS_WALK와 SIDE_LINE 모두 기존 SIDE_LINE 중간 보정을 적용한다")
	void splitExistingSegmentsForProjectedAddEndpointsSupportsSideLine() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

		ReflectionTestUtils.invokeMethod(adminRoadNetworkEditService, "splitExistingSegmentsForProjectedAddEndpoints");

		verify(jdbcTemplate, times(5)).execute(sqlCaptor.capture());
		assertThat(String.join("\n", sqlCaptor.getAllValues()))
			.contains("admin_edit_add_split_points")
			.contains("s.segment_type in ('CROSS_WALK', 'SIDE_LINE')")
			.contains("projection_distance_meter")
			.contains("ST_ClosestPoint")
			.contains("ST_LineLocatePoint");
	}

	@Test
	@DisplayName("add 교차점 split SQL은 신규 SIDE_LINE과 기존 SIDE_LINE의 내부 교차점 node를 만든다")
	void splitExistingSegmentsForProjectedAddEndpointsCreatesIntersectionNodes() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

		ReflectionTestUtils.invokeMethod(adminRoadNetworkEditService, "splitExistingSegmentsForProjectedAddEndpoints");

		verify(jdbcTemplate, times(5)).execute(sqlCaptor.capture());
		assertThat(String.join("\n", sqlCaptor.getAllValues()))
			.contains("admin_edit_intersection_split_points")
			.contains("ST_Intersection")
			.contains("GeometryType(intersection_dump.geom) = 'POINT'")
			.contains("admin_edit_new_segment_split_points")
			.contains("raw_add_intersections")
			.contains("left_add.edit_seq < right_add.edit_seq")
			.contains("edge_split_fraction");
	}

	@Test
	@DisplayName("새 segment endpoint 검증 SQL은 저장 geometry와 node point 일치를 다시 확인한다")
	void validateNewSegmentEndpointAlignmentChecksStoredGeometryAgainstNodePoints() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

		ReflectionTestUtils.invokeMethod(adminRoadNetworkEditService, "validateNewSegmentEndpointAlignment");

		verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class));
		assertThat(sqlCaptor.getValue())
			.contains("admin_edit_new_segments")
			.contains("ST_StartPoint")
			.contains("ST_EndPoint")
			.contains("ST_DWithin");
	}
}
