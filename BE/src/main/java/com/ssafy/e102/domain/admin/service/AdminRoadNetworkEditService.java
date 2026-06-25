package com.ssafy.e102.domain.admin.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.dto.request.AdminRoadNetworkEditApplyRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkEditApplyResponse;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.route.type.SegmentType;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;

@Service
public class AdminRoadNetworkEditService {

	private static final double SNAP_DISTANCE_METER = 1.0;
	private static final double SIDE_LINE_PROJECTION_DISTANCE_METER = 1.0;
	private static final double CROSS_WALK_PROJECTION_DISTANCE_METER = 1.5;
	private static final double SEGMENT_ENDPOINT_VALIDATION_TOLERANCE_METER = 0.01;
	private static final double INTERSECTION_SPLIT_MIN_GAP_METER = 0.01;
	private static final double SOURCE_FEATURE_BBOX_EXPAND_DEGREE = 0.0005;
	private static final int SRID = 4326;

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private final AdminService adminService;
	private final AdminAuditLogService adminAuditLogService;

	public AdminRoadNetworkEditService(
		JdbcTemplate jdbcTemplate,
		AdminService adminService,
		AdminAuditLogService adminAuditLogService) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		this.adminService = adminService;
		this.adminAuditLogService = adminAuditLogService;
	}

	@Transactional
	public AdminRoadNetworkEditApplyResponse apply(UUID userId, AdminRoadNetworkEditApplyRequest request) {
		List<AdminRoadNetworkEditApplyRequest.Edit> edits = request.edits();
		validateEditableRequest(userId, request);
		Set<Long> deleteEdgeIds = new LinkedHashSet<>();
		Set<Long> deleteNodeIds = new LinkedHashSet<>();

		for (AdminRoadNetworkEditApplyRequest.Edit edit : edits) {
			if ("delete_segment".equals(edit.action())) {
				deleteEdgeIds.add(requirePositiveId(edit.edgeId(), "삭제할 segment ID는 필수입니다."));
				continue;
			}
			if ("delete_node".equals(edit.action())) {
				deleteNodeIds.add(requirePositiveId(edit.vertexId(), "삭제할 node ID는 필수입니다."));
			}
		}
		validateExistingEdgeIds(deleteEdgeIds);
		validateExistingNodeIds(deleteNodeIds);

		if (!deleteNodeIds.isEmpty()) {
			deleteEdgeIds.addAll(findIncidentEdgeIds(deleteNodeIds));
		}

		Set<Long> orphanCleanupCandidateNodeIds = new LinkedHashSet<>(deleteNodeIds);
		orphanCleanupCandidateNodeIds.addAll(findEndpointNodeIds(deleteEdgeIds));
		List<Long> deletedEdgeIds = deleteSegments(deleteEdgeIds);
		validateDeletedSegments(deleteEdgeIds, deletedEdgeIds);
		ApplyCounters counters = new ApplyCounters();
		List<Long> addedEdgeIds = new ArrayList<>();
		List<Long> createdNodeIds = new ArrayList<>();
		List<Long> snappedNodeIds = new ArrayList<>();
		List<AddSegmentInput> addSegmentInputs = new ArrayList<>();

		for (AdminRoadNetworkEditApplyRequest.Edit edit : edits) {
			if (!"add_segment".equals(edit.action())) {
				continue;
			}
			LineInput lineInput = requireLineInput(edit);
			SegmentType segmentType = segmentTypeOrDefault(edit.segmentType());
			Long requestedFromNodeId = existingNodeRefId(edit.fromNode());
			Long requestedToNodeId = existingNodeRefId(edit.toNode());
			if (segmentType == SegmentType.CROSS_WALK) {
				lineInput = projectCrossWalkLineInput(lineInput);
				requestedFromNodeId = null;
				requestedToNodeId = null;
			} else if (segmentType == SegmentType.SIDE_LINE) {
				lineInput = projectSideLineInput(lineInput, requestedFromNodeId, requestedToNodeId);
			}
			addSegmentInputs.add(new AddSegmentInput(
				addSegmentInputs.size() + 1,
				segmentType,
				lineInput,
				requestedFromNodeId,
				requestedToNodeId));
		}
		addSegments(addSegmentInputs, counters, addedEdgeIds, createdNodeIds, snappedNodeIds,
			orphanCleanupCandidateNodeIds);

		int removedOrphanNodes = removeOrphanNodes(orphanCleanupCandidateNodeIds);
		AdminRoadNetworkEditApplyResponse response = new AdminRoadNetworkEditApplyResponse(
			addedEdgeIds.size(),
			counters.skippedSegments,
			deletedEdgeIds.size(),
			counters.createdNodes,
			counters.snappedNodes,
			removedOrphanNodes,
			counters.createdSegmentFeatures,
			counters.updatedSegmentAttributes,
			addedEdgeIds,
			deletedEdgeIds,
			createdNodeIds,
			snappedNodeIds);
		adminAuditLogService.record(
			userId,
			"ROAD_NETWORK_EDIT_APPLY",
			"ROAD_NETWORK",
			request.gu() + "/" + request.dong(),
			request.gu(),
			request.dong(),
			"보행 네트워크 편집 반영 add=" + response.addedSegments()
				+ ", skip=" + response.skippedSegments()
				+ ", delete=" + response.deletedSegments(),
			request,
			response);
		return response;
	}

	public void validateEditableRequest(UUID userId, AdminRoadNetworkEditApplyRequest request) {
		adminService.requireCanEditArea(userId, request.gu(), request.dong(), AdminAreaAssignmentType.ROAD_NETWORK);
		validateActions(request.edits());
	}

	private void validateActions(List<AdminRoadNetworkEditApplyRequest.Edit> edits) {
		for (AdminRoadNetworkEditApplyRequest.Edit edit : edits) {
			String action = edit.action();
			if (!"add_segment".equals(action) && !"delete_segment".equals(action) && !"delete_node".equals(action)) {
				throw invalidRequest("지원하지 않는 편집 action입니다.");
			}
		}
	}

	private void validateExistingEdgeIds(Set<Long> edgeIds) {
		if (edgeIds.isEmpty()) {
			return;
		}
		Long existingCount = namedParameterJdbcTemplate.queryForObject(
			"""
				select count(*)
				from road_segments
				where edge_id in (:edgeIds)
				""",
			Map.of("edgeIds", edgeIds),
			Long.class);
		if (existingCount == null || existingCount != edgeIds.size()) {
			throw invalidRequest("삭제할 segment를 찾을 수 없습니다.");
		}
	}

	private void validateExistingNodeIds(Set<Long> nodeIds) {
		if (nodeIds.isEmpty()) {
			return;
		}
		Long existingCount = namedParameterJdbcTemplate.queryForObject(
			"""
				select count(*)
				from road_nodes
				where vertex_id in (:nodeIds)
				""",
			Map.of("nodeIds", nodeIds),
			Long.class);
		if (existingCount == null || existingCount != nodeIds.size()) {
			throw invalidRequest("삭제할 node를 찾을 수 없습니다.");
		}
	}

	private List<Long> findIncidentEdgeIds(Set<Long> deleteNodeIds) {
		return namedParameterJdbcTemplate.queryForList(
			"""
				select edge_id
				from road_segments
				where from_node_id in (:nodeIds)
					or to_node_id in (:nodeIds)
				order by edge_id
				""",
			Map.of("nodeIds", deleteNodeIds),
			Long.class);
	}

	private List<Long> findEndpointNodeIds(Set<Long> edgeIds) {
		if (edgeIds.isEmpty()) {
			return List.of();
		}
		return namedParameterJdbcTemplate.queryForList(
			"""
				select distinct node_id
				from (
					select from_node_id as node_id
					from road_segments
					where edge_id in (:edgeIds)
					union
					select to_node_id as node_id
					from road_segments
					where edge_id in (:edgeIds)
				) endpoint_nodes
				order by node_id
				""",
			Map.of("edgeIds", edgeIds),
			Long.class);
	}

	private List<Long> deleteSegments(Set<Long> edgeIds) {
		if (edgeIds.isEmpty()) {
			return List.of();
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource("edgeIds", edgeIds);
		namedParameterJdbcTemplate.update(
			"delete from segment_features where edge_id in (:edgeIds)",
			parameters);
		return namedParameterJdbcTemplate.queryForList(
			"""
				delete from road_segments
				where edge_id in (:edgeIds)
				returning edge_id
				""",
			parameters,
			Long.class);
	}

	private void validateDeletedSegments(Set<Long> requestedEdgeIds, List<Long> deletedEdgeIds) {
		if (requestedEdgeIds.size() != deletedEdgeIds.size()) {
			throw invalidRequest("삭제할 segment를 찾을 수 없습니다.");
		}
	}

	private void addSegments(
		List<AddSegmentInput> inputs,
		ApplyCounters counters,
		List<Long> addedEdgeIds,
		List<Long> createdNodeIds,
		List<Long> snappedNodeIds,
		Set<Long> orphanCleanupCandidateNodeIds) {
		if (inputs.isEmpty()) {
			return;
		}
		validateExistingAddNodeRefs(inputs);
		createAddSegmentTempTables();
		jdbcTemplate.batchUpdate(
			"""
				insert into admin_edit_add_segments (
					edit_seq,
					segment_type,
					from_lng,
					from_lat,
					to_lng,
					to_lat,
					requested_from_node_id,
					requested_to_node_id,
					line_wkt
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
			inputs.stream()
				.map(input -> new Object[] {
					input.editSeq(),
					input.segmentType().name(),
					input.lineInput().first().lng(),
					input.lineInput().first().lat(),
					input.lineInput().last().lng(),
					input.lineInput().last().lat(),
					input.requestedFromNodeId(),
					input.requestedToNodeId(),
					input.lineInput().toWkt()
				})
				.toList());
		resolveAddSegmentNodes();
		splitExistingSegmentsForProjectedAddEndpoints();
		createdNodeIds.addAll(queryLongs("select vertex_id from admin_edit_created_points order by vertex_id"));
		snappedNodeIds
			.addAll(queryLongs("select vertex_id from admin_edit_snapped_points order by edit_seq, endpoint"));
		orphanCleanupCandidateNodeIds.addAll(createdNodeIds);
		orphanCleanupCandidateNodeIds.addAll(snappedNodeIds);
		counters.createdNodes += createdNodeIds.size();
		counters.snappedNodes += snappedNodeIds.size();
		insertBulkRoadSegments();
		validateNewSegmentEndpointAlignment();
		addedEdgeIds.addAll(queryLongs("select edge_id from admin_edit_new_segments order by edge_id"));
		int skippedSegments = countSkippedAddSegments();
		counters.skippedSegments += skippedSegments;
		validateAddedSegments(inputs, countAppliedAddSegmentEdits(), skippedSegments);
		counters.createdSegmentFeatures += insertSegmentFeaturesForSplitSegments();
		counters.createdSegmentFeatures += insertSegmentFeaturesForNewSegments();
		counters.updatedSegmentAttributes += updateSegmentAttributesForNewSegments();
	}

	private void validateAddedSegments(List<AddSegmentInput> inputs, int appliedEditCount, int skippedSegments) {
		if (inputs.size() != appliedEditCount + skippedSegments) {
			throw invalidRequest("추가할 segment의 양 끝점이 같은 node로 연결되어 추가할 수 없습니다.");
		}
	}

	private void validateExistingAddNodeRefs(List<AddSegmentInput> inputs) {
		Set<Long> requestedNodeIds = new LinkedHashSet<>();
		inputs.forEach(input -> {
			if (input.requestedFromNodeId() != null) {
				requestedNodeIds.add(input.requestedFromNodeId());
			}
			if (input.requestedToNodeId() != null) {
				requestedNodeIds.add(input.requestedToNodeId());
			}
		});
		if (requestedNodeIds.isEmpty()) {
			return;
		}
		Long existingCount = namedParameterJdbcTemplate.queryForObject(
			"""
				select count(*)
				from road_nodes
				where vertex_id in (:nodeIds)
				""",
			Map.of("nodeIds", requestedNodeIds),
			Long.class);
		if (existingCount == null || existingCount != requestedNodeIds.size()) {
			throw invalidRequest("추가할 segment의 endpoint node를 찾을 수 없습니다.");
		}
	}

	private void createAddSegmentTempTables() {
		jdbcTemplate.execute("drop table if exists admin_edit_add_segments");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_add_segments (
					edit_seq integer primary key,
					segment_type varchar(30) not null,
					from_lng double precision not null,
					from_lat double precision not null,
					to_lng double precision not null,
					to_lat double precision not null,
					requested_from_node_id bigint,
					requested_to_node_id bigint,
					line_wkt text not null
				) on commit drop
				""");
	}

	private void resolveAddSegmentNodes() {
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_points on commit drop as
				select edit_seq, 'FROM'::varchar(4) as endpoint, from_lng as lng, from_lat as lat,
					requested_from_node_id as requested_vertex_id
				from admin_edit_add_segments
				union all
				select edit_seq, 'TO'::varchar(4) as endpoint, to_lng as lng, to_lat as lat,
					requested_to_node_id as requested_vertex_id
				from admin_edit_add_segments
				""");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_snapped_points on commit drop as
				with requested_points as (
					select
						p.edit_seq,
						p.endpoint,
						p.requested_vertex_id as vertex_id,
						p.lng,
						p.lat
					from admin_edit_points p
					where p.requested_vertex_id is not null
				),
				fallback_snapped_points as (
					select distinct on (p.edit_seq, p.endpoint)
						p.edit_seq,
						p.endpoint,
						rn.vertex_id,
						p.lng,
						p.lat
					from admin_edit_points p
					join road_nodes rn
						on ST_DWithin(
							rn."point"::geography,
							ST_SetSRID(ST_MakePoint(p.lng, p.lat), 4326)::geography,
							%s
						)
					where p.requested_vertex_id is null
					order by p.edit_seq,
						p.endpoint,
						ST_Distance(
							rn."point"::geography,
							ST_SetSRID(ST_MakePoint(p.lng, p.lat), 4326)::geography
						),
						rn.vertex_id
				)
				select
					edit_seq,
					endpoint,
					vertex_id,
					lng,
					lat
				from requested_points
				union all
				select
					edit_seq,
					endpoint,
					vertex_id,
					lng,
					lat
				from fallback_snapped_points
				""".formatted(SNAP_DISTANCE_METER));
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_unresolved_points on commit drop as
				select
					p.edit_seq,
					p.endpoint,
					p.lng,
					p.lat,
					ST_SetSRID(ST_MakePoint(p.lng, p.lat), 4326) as geom
				from admin_edit_points p
				where not exists (
					select 1
					from admin_edit_snapped_points sp
					where sp.edit_seq = p.edit_seq
						and sp.endpoint = p.endpoint
				)
				""");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_created_point_assignments on commit drop as
				with clustered as (
					select
						edit_seq,
						endpoint,
						lng,
						lat,
						ST_ClusterDBSCAN(
							ST_Transform(geom, 5179),
							eps := %s,
							minpoints := 1
						) over () as cluster_id
					from admin_edit_unresolved_points
				),
				representatives as (
					select distinct on (cluster_id)
						cluster_id,
						lng,
						lat
					from clustered
					order by cluster_id, edit_seq, endpoint
				),
				created_points as (
					select
						cluster_id,
						nextval('road_nodes_vertex_id_seq') as vertex_id,
						lng,
						lat
					from representatives
				)
				select
					c.edit_seq,
					c.endpoint,
					cp.vertex_id,
					cp.lng,
					cp.lat
				from clustered c
				join created_points cp
					on cp.cluster_id = c.cluster_id
				""".formatted(SNAP_DISTANCE_METER));
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_created_points on commit drop as
				select distinct vertex_id, lng, lat
				from admin_edit_created_point_assignments
				""");
		jdbcTemplate.update(
			"""
				insert into road_nodes (vertex_id, source_node_key, "point")
				select
					vertex_id,
					'editor:manual:' || vertex_id,
					ST_SetSRID(ST_MakePoint(lng, lat), 4326)
				from admin_edit_created_points
				""");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_resolved_points on commit drop as
				select edit_seq, endpoint, vertex_id, lng, lat
				from admin_edit_snapped_points
				union all
				select edit_seq, endpoint, vertex_id, lng, lat
				from admin_edit_created_point_assignments
				""");
	}

	private void splitExistingSegmentsForProjectedAddEndpoints() {
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_new_segment_split_points (
					edit_seq integer not null,
					vertex_id bigint not null,
					split_geom geometry(Point, 4326) not null,
					split_fraction double precision not null,
					primary key (edit_seq, vertex_id)
				) on commit drop
				""");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_intersection_split_points on commit drop as
				with synchronized_add_segments as (
					select
						s.edit_seq,
						s.segment_type,
						ST_SetPoint(
							ST_SetPoint(ST_GeomFromText(s.line_wkt, 4326), 0, from_node."point"),
							ST_NPoints(ST_GeomFromText(s.line_wkt, 4326)) - 1,
							to_node."point"
						) as synced_geom
					from admin_edit_add_segments s
					join admin_edit_resolved_points from_point
						on from_point.edit_seq = s.edit_seq
						and from_point.endpoint = 'FROM'
					join admin_edit_resolved_points to_point
						on to_point.edit_seq = s.edit_seq
						and to_point.endpoint = 'TO'
					join road_nodes from_node
						on from_node.vertex_id = from_point.vertex_id
					join road_nodes to_node
						on to_node.vertex_id = to_point.vertex_id
					where s.segment_type = 'SIDE_LINE'
						and from_point.vertex_id <> to_point.vertex_id
				),
				raw_existing_intersections as (
					select
						s.edit_seq,
						rs.edge_id,
						intersection_dump.geom as split_geom,
						ST_LineLocatePoint(s.synced_geom, intersection_dump.geom) as add_split_fraction,
						ST_LineLocatePoint(rs.geom, intersection_dump.geom) as edge_split_fraction,
						s.synced_geom as add_geom,
						rs.geom as edge_geom
					from synchronized_add_segments s
					join road_segments rs
						on rs.segment_type = 'SIDE_LINE'
						and ST_Intersects(s.synced_geom, rs.geom)
					cross join lateral ST_Dump(ST_Intersection(s.synced_geom, rs.geom)) as intersection_dump
					where GeometryType(intersection_dump.geom) = 'POINT'
				),
				filtered_existing_intersections as (
					select *
					from raw_existing_intersections
					where ST_Length(ST_LineSubstring(add_geom, 0, add_split_fraction)::geography) > %s
						and ST_Length(ST_LineSubstring(add_geom, add_split_fraction, 1)::geography) > %s
						and ST_Length(ST_LineSubstring(edge_geom, 0, edge_split_fraction)::geography) > %s
						and ST_Length(ST_LineSubstring(edge_geom, edge_split_fraction, 1)::geography) > %s
				),
				raw_add_intersections as (
					select
						left_add.edit_seq as left_edit_seq,
						right_add.edit_seq as right_edit_seq,
						intersection_dump.geom as split_geom,
						ST_LineLocatePoint(left_add.synced_geom, intersection_dump.geom) as left_split_fraction,
						ST_LineLocatePoint(right_add.synced_geom, intersection_dump.geom) as right_split_fraction,
						left_add.synced_geom as left_geom,
						right_add.synced_geom as right_geom
					from synchronized_add_segments left_add
					join synchronized_add_segments right_add
						on left_add.edit_seq < right_add.edit_seq
						and ST_Intersects(left_add.synced_geom, right_add.synced_geom)
					cross join lateral ST_Dump(ST_Intersection(left_add.synced_geom, right_add.synced_geom)) as intersection_dump
					where GeometryType(intersection_dump.geom) = 'POINT'
				),
				filtered_add_intersections as (
					select *
					from raw_add_intersections
					where ST_Length(ST_LineSubstring(left_geom, 0, left_split_fraction)::geography) > %s
						and ST_Length(ST_LineSubstring(left_geom, left_split_fraction, 1)::geography) > %s
						and ST_Length(ST_LineSubstring(right_geom, 0, right_split_fraction)::geography) > %s
						and ST_Length(ST_LineSubstring(right_geom, right_split_fraction, 1)::geography) > %s
				),
				intersection_node_candidates as (
					select distinct on (point_key)
						point_key,
						split_geom
					from (
						select
							round(ST_X(split_geom)::numeric, 8) || ':'
								|| round(ST_Y(split_geom)::numeric, 8) as point_key,
							split_geom
						from filtered_existing_intersections
						union all
						select
							round(ST_X(split_geom)::numeric, 8) || ':'
								|| round(ST_Y(split_geom)::numeric, 8) as point_key,
							split_geom
						from filtered_add_intersections
					) candidates
					order by point_key
				),
				intersection_nodes as (
					select
						point_key,
						nextval('road_nodes_vertex_id_seq') as vertex_id,
						split_geom
					from intersection_node_candidates
				),
				existing_intersection_rows as (
					select distinct on (f.edge_id, round(f.edge_split_fraction::numeric, 6))
						f.edit_seq,
						f.edge_id,
						n.vertex_id,
						n.split_geom,
						f.add_split_fraction,
						f.edge_split_fraction
					from filtered_existing_intersections f
					join intersection_nodes n
						on n.point_key = round(ST_X(f.split_geom)::numeric, 8) || ':'
							|| round(ST_Y(f.split_geom)::numeric, 8)
					order by f.edge_id, round(f.edge_split_fraction::numeric, 6), f.edit_seq
				),
				add_intersection_rows as (
					select
						f.left_edit_seq as edit_seq,
						null::bigint as edge_id,
						n.vertex_id,
						n.split_geom,
						f.left_split_fraction as add_split_fraction,
						null::double precision as edge_split_fraction
					from filtered_add_intersections f
					join intersection_nodes n
						on n.point_key = round(ST_X(f.split_geom)::numeric, 8) || ':'
							|| round(ST_Y(f.split_geom)::numeric, 8)
					union all
					select
						f.right_edit_seq as edit_seq,
						null::bigint as edge_id,
						n.vertex_id,
						n.split_geom,
						f.right_split_fraction as add_split_fraction,
						null::double precision as edge_split_fraction
					from filtered_add_intersections f
					join intersection_nodes n
						on n.point_key = round(ST_X(f.split_geom)::numeric, 8) || ':'
							|| round(ST_Y(f.split_geom)::numeric, 8)
				)
				select edit_seq, edge_id, vertex_id, split_geom, add_split_fraction, edge_split_fraction
				from existing_intersection_rows
				union all
				select edit_seq, edge_id, vertex_id, split_geom, add_split_fraction, edge_split_fraction
				from add_intersection_rows
				"""
				.formatted(
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER,
					INTERSECTION_SPLIT_MIN_GAP_METER));
		jdbcTemplate.update(
			"""
				insert into admin_edit_created_points (vertex_id, lng, lat)
				select distinct on (vertex_id)
					vertex_id,
					ST_X(split_geom),
					ST_Y(split_geom)
				from admin_edit_intersection_split_points
				order by vertex_id
				""");
		jdbcTemplate.update(
			"""
				insert into road_nodes (vertex_id, source_node_key, "point")
				select distinct on (vertex_id)
					vertex_id,
					'editor:intersection:' || vertex_id,
					split_geom
				from admin_edit_intersection_split_points
				order by vertex_id
				""");
		jdbcTemplate.execute(
			"""
				insert into admin_edit_new_segment_split_points (edit_seq, vertex_id, split_geom, split_fraction)
				select distinct on (edit_seq, vertex_id)
					edit_seq,
					vertex_id,
					split_geom,
					add_split_fraction
				from admin_edit_intersection_split_points
				order by edit_seq, vertex_id, add_split_fraction
				""");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_add_split_points on commit drop as
				with projected_created_points as (
					select
						rp.edit_seq,
						rp.endpoint,
						rp.vertex_id,
						ST_SetSRID(ST_MakePoint(rp.lng, rp.lat), 4326) as point_geom,
						case
							when s.segment_type = 'CROSS_WALK' then %s
							else %s
						end as projection_distance_meter
					from admin_edit_resolved_points rp
					join admin_edit_created_points cp
						on cp.vertex_id = rp.vertex_id
					join admin_edit_add_segments s
						on s.edit_seq = rp.edit_seq
					where s.segment_type in ('CROSS_WALK', 'SIDE_LINE')
				),
				nearest_segments as (
					select distinct on (cp.edit_seq, cp.endpoint)
						cp.edit_seq,
						cp.endpoint,
						cp.vertex_id,
						rs.edge_id,
						ST_ClosestPoint(rs.geom, cp.point_geom) as split_geom,
						ST_LineLocatePoint(rs.geom, ST_ClosestPoint(rs.geom, cp.point_geom)) as split_fraction,
						ST_Distance(rs.geom::geography, cp.point_geom::geography) as distance_meter
					from projected_created_points cp
					join road_segments rs
						on rs.segment_type = 'SIDE_LINE'
						and ST_DWithin(
							rs.geom::geography,
							cp.point_geom::geography,
							cp.projection_distance_meter
						)
					order by cp.edit_seq, cp.endpoint, distance_meter, rs.edge_id
				),
				filtered as (
					select ns.*
					from nearest_segments ns
					join road_segments rs
						on rs.edge_id = ns.edge_id
					join projected_created_points cp
						on cp.edit_seq = ns.edit_seq
						and cp.endpoint = ns.endpoint
					where ST_Length(ST_LineSubstring(rs.geom, 0, ns.split_fraction)::geography) > cp.projection_distance_meter
						and ST_Length(ST_LineSubstring(rs.geom, ns.split_fraction, 1)::geography) > cp.projection_distance_meter
				),
				projected_endpoint_split_points as (
					select distinct on (edge_id, round(split_fraction::numeric, 6))
						edge_id,
						vertex_id,
						split_geom,
						split_fraction
					from filtered
					order by edge_id, round(split_fraction::numeric, 6), distance_meter, vertex_id
				),
				intersection_split_points as (
					select
						edge_id,
						vertex_id,
						split_geom,
						edge_split_fraction as split_fraction
					from admin_edit_intersection_split_points
					where edge_id is not null
				),
				combined_split_points as (
					select edge_id, vertex_id, split_geom, split_fraction
					from projected_endpoint_split_points
					union all
					select edge_id, vertex_id, split_geom, split_fraction
					from intersection_split_points
				)
				select distinct on (edge_id, round(split_fraction::numeric, 6))
					edge_id,
					vertex_id,
					split_geom,
					split_fraction
				from combined_split_points
				order by edge_id, round(split_fraction::numeric, 6), vertex_id
					"""
				.formatted(
					CROSS_WALK_PROJECTION_DISTANCE_METER,
					SIDE_LINE_PROJECTION_DISTANCE_METER));
		Long splitPointCount = jdbcTemplate.queryForObject(
			"select count(*) from admin_edit_add_split_points",
			Long.class);
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_split_segments (
					edge_id bigint primary key,
					old_edge_id bigint not null,
					from_node_id bigint not null,
					to_node_id bigint not null,
					geom geometry(LineString, 4326) not null,
					walk_access varchar(30) not null,
					avg_slope_percent numeric(6, 2),
					width_meter numeric(6, 2),
					braille_block_state varchar(30) not null,
					audio_signal_state varchar(30) not null,
					width_state varchar(30) not null,
					surface_state varchar(30) not null,
					stairs_state varchar(30) not null,
					signal_state varchar(30) not null,
					segment_type varchar(30) not null
				) on commit drop
				""");
		if (splitPointCount == null || splitPointCount == 0) {
			return;
		}
		jdbcTemplate.execute(
			"""
				insert into admin_edit_split_segments (
					edge_id,
					old_edge_id,
					from_node_id,
					to_node_id,
					geom,
					walk_access,
					avg_slope_percent,
					width_meter,
					braille_block_state,
					audio_signal_state,
					width_state,
					surface_state,
					stairs_state,
					signal_state,
					segment_type
				)
				with target_edges as (
					select rs.*
					from road_segments rs
					where exists (
						select 1
						from admin_edit_add_split_points sp
						where sp.edge_id = rs.edge_id
					)
				),
				split_vertices as (
					select
						edge_id,
						0.0::double precision as fraction,
						from_node_id as vertex_id
					from target_edges
					union all
					select edge_id, split_fraction, vertex_id
					from admin_edit_add_split_points
					union all
					select
						edge_id,
						1.0::double precision as fraction,
						to_node_id as vertex_id
					from target_edges
				),
				ordered_vertices as (
					select
						edge_id,
						fraction,
						vertex_id,
						lead(fraction) over (partition by edge_id order by fraction, vertex_id) as next_fraction,
						lead(vertex_id) over (partition by edge_id order by fraction, vertex_id) as next_vertex_id
					from split_vertices
				)
				select
					nextval('road_segments_edge_id_seq') as edge_id,
					te.edge_id as old_edge_id,
					ov.vertex_id as from_node_id,
					ov.next_vertex_id as to_node_id,
					ST_LineSubstring(te.geom, ov.fraction, ov.next_fraction) as geom,
					te.walk_access,
					te.avg_slope_percent,
					te.width_meter,
					te.braille_block_state,
					te.audio_signal_state,
					te.width_state,
					te.surface_state,
					te.stairs_state,
					te.signal_state,
					te.segment_type
				from ordered_vertices ov
				join target_edges te
					on te.edge_id = ov.edge_id
				where ov.next_fraction is not null
					and ov.vertex_id <> ov.next_vertex_id
					and ST_Length(ST_LineSubstring(te.geom, ov.fraction, ov.next_fraction)::geography) > 0.01
				""");
		jdbcTemplate.update(
			"""
				insert into road_segments (
					edge_id,
					from_node_id,
					to_node_id,
					geom,
					length_meter,
					walk_access,
					avg_slope_percent,
					width_meter,
					braille_block_state,
					audio_signal_state,
					width_state,
					surface_state,
					stairs_state,
					signal_state,
					segment_type
				)
				select
					edge_id,
					from_node_id,
					to_node_id,
					geom,
					round(ST_Length(geom::geography)::numeric, 2),
					walk_access,
					avg_slope_percent,
					width_meter,
					braille_block_state,
					audio_signal_state,
					width_state,
					surface_state,
					stairs_state,
					signal_state,
					segment_type
				from admin_edit_split_segments
				""");
		jdbcTemplate.update(
			"""
				delete from segment_features sf
				where exists (
					select 1
					from admin_edit_split_segments ss
					where ss.old_edge_id = sf.edge_id
				)
				""");
		jdbcTemplate.update(
			"""
				delete from road_segments rs
				where exists (
					select 1
					from admin_edit_split_segments ss
					where ss.old_edge_id = rs.edge_id
				)
				""");
	}

	private int insertSegmentFeaturesForSplitSegments() {
		return jdbcTemplate.update(
			"""
				with source_candidates as (
					select
						ss.edge_id,
						sf.feature_type,
						sf.geom,
						sf.state,
						sf.value_number,
						ST_Distance(ST_Transform(sf.geom, 5179), ST_Transform(ss.geom, 5179)) as distance_meter
					from admin_edit_split_segments ss
					join source_features sf
						on sf.feature_type in ('CROSSWALK', 'AUDIO_SIGNAL', 'BRAILLE_BLOCK', 'STAIRS')
						and sf.geom && ST_Expand(ss.geom, %s)
						and ST_DWithin(
							ST_Transform(sf.geom, 5179),
							ST_Transform(ss.geom, 5179),
							source_match_threshold_meter(sf.source_file)
						)
				),
				deduped as (
					select *
					from (
						select
							edge_id,
							feature_type,
							geom,
							state,
							value_number,
							row_number() over (
								partition by edge_id, feature_type, coalesce(state, '')
								order by distance_meter asc
							) as row_no
						from source_candidates
					) ranked
					where row_no = 1
				)
				insert into segment_features (feature_id, edge_id, feature_type, geom, state, value_number)
				select
					nextval('segment_features_feature_id_seq'),
					edge_id,
					feature_type,
					geom,
					state,
					value_number
				from deduped
				""".formatted(SOURCE_FEATURE_BBOX_EXPAND_DEGREE));
	}

	private void insertBulkRoadSegments() {
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_new_segments (
					edge_id bigint primary key,
					edit_seq integer not null,
					from_node_id bigint not null,
					to_node_id bigint not null,
					"geom" geometry(LineString, 4326) not null
				) on commit drop
				""");
		jdbcTemplate.execute(
			"""
				create temp table admin_edit_skipped_add_segments (
					edit_seq integer primary key,
					reason varchar(80) not null
				) on commit drop
				""");
		jdbcTemplate.execute(
			"""
				with all_resolved_segments as (
					select
						s.edit_seq,
						s.segment_type,
						ST_GeomFromText(s.line_wkt, 4326) as raw_geom,
						from_point.vertex_id as from_node_id,
						to_point.vertex_id as to_node_id
					from admin_edit_add_segments s
					join admin_edit_resolved_points from_point
						on from_point.edit_seq = s.edit_seq
						and from_point.endpoint = 'FROM'
					join admin_edit_resolved_points to_point
						on to_point.edit_seq = s.edit_seq
						and to_point.endpoint = 'TO'
				),
				skipped as (
					insert into admin_edit_skipped_add_segments (edit_seq, reason)
					select edit_seq, 'SAME_NODE_ENDPOINT'
					from all_resolved_segments
					where from_node_id = to_node_id
					returning edit_seq
				),
				resolved_segments as (
					select *
					from all_resolved_segments
					where from_node_id <> to_node_id
				),
				synchronized_segments as (
					select
						rs.edit_seq,
						rs.segment_type,
						rs.from_node_id,
						rs.to_node_id,
						ST_SetPoint(
							ST_SetPoint(raw_geom, 0, from_node."point"),
							ST_NPoints(raw_geom) - 1,
							to_node."point"
						) as synced_geom
					from resolved_segments rs
					join road_nodes from_node
						on from_node.vertex_id = rs.from_node_id
					join road_nodes to_node
						on to_node.vertex_id = rs.to_node_id
				),
				split_vertices as (
					select
						edit_seq,
						0.0::double precision as fraction,
						from_node_id as vertex_id
					from synchronized_segments
					union all
					select
						edit_seq,
						split_fraction as fraction,
						vertex_id
					from admin_edit_new_segment_split_points
					union all
					select
						edit_seq,
						1.0::double precision as fraction,
						to_node_id as vertex_id
					from synchronized_segments
				),
				ordered_vertices as (
					select
						edit_seq,
						fraction,
						vertex_id,
						lead(fraction) over (partition by edit_seq order by fraction, vertex_id) as next_fraction,
						lead(vertex_id) over (partition by edit_seq order by fraction, vertex_id) as next_vertex_id
					from split_vertices
				),
				segment_pieces as (
					select
						ss.edit_seq,
						ss.segment_type,
						ov.vertex_id as from_node_id,
						ov.next_vertex_id as to_node_id,
						ST_LineSubstring(ss.synced_geom, ov.fraction, ov.next_fraction) as geom
					from ordered_vertices ov
					join synchronized_segments ss
						on ss.edit_seq = ov.edit_seq
					where ov.next_fraction is not null
						and ov.vertex_id <> ov.next_vertex_id
						and ST_Length(ST_LineSubstring(ss.synced_geom, ov.fraction, ov.next_fraction)::geography) > 0.01
				),
				insert_candidates as (
					select
						nextval('road_segments_edge_id_seq') as edge_id,
						edit_seq,
						from_node_id,
						to_node_id,
						geom,
						segment_type
					from segment_pieces
				),
				inserted as (
					insert into road_segments (
						edge_id,
						from_node_id,
						to_node_id,
						"geom",
						length_meter,
						walk_access,
						braille_block_state,
						audio_signal_state,
						width_state,
						surface_state,
						stairs_state,
						signal_state,
						segment_type
					)
					select
						edge_id,
						from_node_id,
						to_node_id,
						geom,
						round(ST_Length(geom::geography)::numeric, 2),
						'UNKNOWN',
						'UNKNOWN',
						'UNKNOWN',
						'UNKNOWN',
						'UNKNOWN',
						'UNKNOWN',
						'UNKNOWN',
						segment_type
					from insert_candidates
					returning edge_id
				)
				insert into admin_edit_new_segments (edge_id, edit_seq, from_node_id, to_node_id, "geom")
				select ic.edge_id, ic.edit_seq, ic.from_node_id, ic.to_node_id, ic.geom
				from inserted
				join insert_candidates ic
					on ic.edge_id = inserted.edge_id
				""");
	}

	private void validateNewSegmentEndpointAlignment() {
		Long mismatchCount = jdbcTemplate.queryForObject(
			"""
				select count(*)
				from admin_edit_new_segments ns
				join road_nodes from_node
					on from_node.vertex_id = ns.from_node_id
				join road_nodes to_node
					on to_node.vertex_id = ns.to_node_id
				where not ST_DWithin(
					ST_StartPoint(ns.geom)::geography,
					from_node."point"::geography,
					%s
				)
				or not ST_DWithin(
					ST_EndPoint(ns.geom)::geography,
					to_node."point"::geography,
					%s
				)
				""".formatted(
				SEGMENT_ENDPOINT_VALIDATION_TOLERANCE_METER,
				SEGMENT_ENDPOINT_VALIDATION_TOLERANCE_METER),
			Long.class);
		if (mismatchCount != null && mismatchCount > 0) {
			throw internalError("추가한 segment endpoint와 road node 좌표가 일치하지 않습니다.");
		}
	}

	private int countSkippedAddSegments() {
		Long skippedCount = jdbcTemplate.queryForObject(
			"select count(*) from admin_edit_skipped_add_segments",
			Long.class);
		return skippedCount == null ? 0 : skippedCount.intValue();
	}

	private int countAppliedAddSegmentEdits() {
		Long appliedEditCount = jdbcTemplate.queryForObject(
			"select count(distinct edit_seq) from admin_edit_new_segments",
			Long.class);
		return appliedEditCount == null ? 0 : appliedEditCount.intValue();
	}

	private int insertSegmentFeaturesForNewSegments() {
		return jdbcTemplate.update(
			"""
				with source_candidates as (
					select
						ns.edge_id,
						sf.feature_type,
						sf.geom,
						sf.state,
						sf.value_number,
						ST_Distance(ST_Transform(sf.geom, 5179), ST_Transform(ns.geom, 5179)) as distance_meter
					from admin_edit_new_segments ns
					join source_features sf
						on sf.feature_type in ('CROSSWALK', 'AUDIO_SIGNAL', 'BRAILLE_BLOCK', 'STAIRS')
						and sf.geom && ST_Expand(ns.geom, %s)
						and ST_DWithin(
							ST_Transform(sf.geom, 5179),
							ST_Transform(ns.geom, 5179),
							source_match_threshold_meter(sf.source_file)
						)
				),
				deduped as (
					select *
					from (
						select
							edge_id,
							feature_type,
							geom,
							state,
							value_number,
							row_number() over (
								partition by edge_id, feature_type, coalesce(state, '')
								order by distance_meter asc
							) as row_no
						from source_candidates
					) ranked
					where row_no = 1
				)
				insert into segment_features (feature_id, edge_id, feature_type, geom, state, value_number)
				select
					nextval('segment_features_feature_id_seq'),
					edge_id,
					feature_type,
					geom,
					state,
					value_number
				from deduped
				""".formatted(SOURCE_FEATURE_BBOX_EXPAND_DEGREE));
	}

	private int updateSegmentAttributesForNewSegments() {
		return jdbcTemplate.update(
			"""
				with source_candidates as (
					select
						ns.edge_id,
						sf.feature_type,
						sf.state,
						sf.value_number
					from admin_edit_new_segments ns
					join source_features sf
						on sf.geom && ST_Expand(ns.geom, %s)
						and ST_DWithin(
							ST_Transform(sf.geom, 5179),
							ST_Transform(ns.geom, 5179),
							source_match_threshold_meter(sf.source_file)
						)
				),
				updates as (
					select
						edge_id,
						count(*) as match_count,
						bool_or(feature_type = 'WALK_ACCESS' and state = 'YES') as walk_access_yes,
						bool_or(feature_type = 'CROSSWALK' and state = 'YES') as crosswalk_yes,
						bool_or(feature_type = 'SIGNAL' and state = 'YES') as signal_yes,
						bool_or(feature_type = 'AUDIO_SIGNAL' and state = 'YES') as audio_signal_yes,
						bool_or(feature_type = 'STAIRS' and state = 'YES') as stairs_yes,
						case
							when bool_or(feature_type = 'BRAILLE_BLOCK' and state = 'YES') then 'YES'
							when bool_or(feature_type = 'BRAILLE_BLOCK' and state = 'NO') then 'NO'
							else null
						end as braille_block_state,
						avg(value_number) filter (where feature_type = 'SLOPE' and value_number is not null) as avg_slope_percent,
						percentile_cont(0.5) within group (order by value_number)
							filter (where feature_type = 'WIDTH' and value_number is not null) as width_meter,
						bool_or(feature_type = 'SURFACE' and state = 'UNPAVED') as has_unpaved,
						bool_or(feature_type = 'SURFACE' and state = 'PAVED') as has_paved
					from source_candidates
					group by edge_id
				)
				update road_segments rs
				set
					walk_access = case
						when updates.walk_access_yes and rs.walk_access <> 'NO' then 'YES'
						else rs.walk_access
					end,
					avg_slope_percent = coalesce(round(updates.avg_slope_percent::numeric, 2), rs.avg_slope_percent),
					width_meter = coalesce(round(updates.width_meter::numeric, 2), rs.width_meter),
					braille_block_state = coalesce(updates.braille_block_state, rs.braille_block_state),
					audio_signal_state = case
						when updates.audio_signal_yes then 'YES'
						else rs.audio_signal_state
					end,
					width_state = case
						when updates.width_meter is null then rs.width_state
						when updates.width_meter >= 1.50 then 'ADEQUATE_150'
						when updates.width_meter >= 1.20 then 'ADEQUATE_120'
						else 'NARROW'
					end,
					surface_state = case
						when updates.has_unpaved then 'UNPAVED'
						when updates.has_paved then 'PAVED'
						else rs.surface_state
					end,
					stairs_state = case
						when updates.stairs_yes then 'YES'
						else rs.stairs_state
					end,
					signal_state = case
						when updates.signal_yes then 'YES'
						else rs.signal_state
					end,
					segment_type = case
						when updates.crosswalk_yes then 'CROSS_WALK'
						else rs.segment_type
					end
				from updates
				where rs.edge_id = updates.edge_id
					and updates.match_count > 0
				"""
				.formatted(SOURCE_FEATURE_BBOX_EXPAND_DEGREE));
	}

	private int removeOrphanNodes(Set<Long> candidateNodeIds) {
		if (candidateNodeIds.isEmpty()) {
			return 0;
		}
		return namedParameterJdbcTemplate.update(
			"""
				delete from road_nodes rn
				where rn.vertex_id in (:nodeIds)
					and not exists (
					select 1
					from road_segments rs
					where rs.from_node_id = rn.vertex_id
						or rs.to_node_id = rn.vertex_id
				)
				""",
			Map.of("nodeIds", candidateNodeIds));
	}

	private LineInput requireLineInput(AdminRoadNetworkEditApplyRequest.Edit edit) {
		AdminRoadNetworkEditApplyRequest.LineGeometry geom = edit.geom();
		if (geom == null || !"LineString".equals(geom.type()) || geom.coordinates() == null
			|| geom.coordinates().size() < 2) {
			throw invalidRequest("추가할 segment geometry가 올바르지 않습니다.");
		}
		List<CoordinateInput> coordinates = geom.coordinates()
			.stream()
			.map(this::toCoordinateInput)
			.toList();
		return new LineInput(coordinates);
	}

	private CoordinateInput toCoordinateInput(List<Double> coordinate) {
		if (coordinate == null || coordinate.size() < 2) {
			throw invalidRequest("좌표는 [lng, lat] 형식이어야 합니다.");
		}
		Double lng = coordinate.get(0);
		Double lat = coordinate.get(1);
		if (lng == null || lat == null || !Double.isFinite(lng) || !Double.isFinite(lat)
			|| lng < -180 || lng > 180 || lat < -90 || lat > 90) {
			throw invalidRequest("좌표 범위가 올바르지 않습니다.");
		}
		return new CoordinateInput(lng, lat);
	}

	private Long requirePositiveId(Long id, String message) {
		if (id == null || id <= 0) {
			throw invalidRequest(message);
		}
		return id;
	}

	private SegmentType segmentTypeOrDefault(SegmentType segmentType) {
		return segmentType == null ? SegmentType.SIDE_LINE : segmentType;
	}

	private Long existingNodeRefId(AdminRoadNetworkEditApplyRequest.NodeRef nodeRef) {
		if (nodeRef == null || !"existing".equals(nodeRef.mode())) {
			return null;
		}
		return requirePositiveId(nodeRef.vertexId(), "추가할 segment의 endpoint node ID가 올바르지 않습니다.");
	}

	private LineInput projectCrossWalkLineInput(LineInput lineInput) {
		CoordinateInput first = projectCrossWalkEndpoint(lineInput.first());
		CoordinateInput last = projectCrossWalkEndpoint(lineInput.last());
		if (first.equals(lineInput.first()) && last.equals(lineInput.last())) {
			return lineInput;
		}
		return lineInput.withEndpoints(first, last);
	}

	private LineInput projectSideLineInput(
		LineInput lineInput,
		Long requestedFromNodeId,
		Long requestedToNodeId) {
		CoordinateInput first = requestedFromNodeId == null
			? projectSideLineEndpoint(lineInput.first())
			: lineInput.first();
		CoordinateInput last = requestedToNodeId == null
			? projectSideLineEndpoint(lineInput.last())
			: lineInput.last();
		if (first.equals(lineInput.first()) && last.equals(lineInput.last())) {
			return lineInput;
		}
		return lineInput.withEndpoints(first, last);
	}

	private CoordinateInput projectCrossWalkEndpoint(CoordinateInput coordinate) {
		return projectEndpointToSideLine(coordinate, CROSS_WALK_PROJECTION_DISTANCE_METER);
	}

	private CoordinateInput projectSideLineEndpoint(CoordinateInput coordinate) {
		return projectEndpointToSideLine(coordinate, SIDE_LINE_PROJECTION_DISTANCE_METER);
	}

	private CoordinateInput projectEndpointToSideLine(CoordinateInput coordinate, double thresholdMeter) {
		if (hasNearbyRoadNode(coordinate)) {
			return coordinate;
		}
		List<CoordinateInput> projected = namedParameterJdbcTemplate.query(
			"""
				with input_point as (
					select ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) as geom
				),
				candidates as (
					select
						ST_ClosestPoint(rs.geom, input_point.geom) as projected_geom,
						ST_Distance(rs.geom::geography, input_point.geom::geography) as distance_meter
					from road_segments rs
					cross join input_point
					where rs.segment_type = 'SIDE_LINE'
						and ST_DWithin(
							rs.geom::geography,
							input_point.geom::geography,
							:thresholdMeter
						)
					order by distance_meter, rs.edge_id
					limit 1
				)
				select ST_X(projected_geom) as lng, ST_Y(projected_geom) as lat
				from candidates
				""",
			new MapSqlParameterSource()
				.addValue("lng", coordinate.lng())
				.addValue("lat", coordinate.lat())
				.addValue("thresholdMeter", thresholdMeter),
			(resultSet, rowNumber) -> new CoordinateInput(
				resultSet.getDouble("lng"),
				resultSet.getDouble("lat")));
		return projected.isEmpty() ? coordinate : projected.get(0);
	}

	private boolean hasNearbyRoadNode(CoordinateInput coordinate) {
		Boolean exists = namedParameterJdbcTemplate.queryForObject(
			"""
				select exists (
					select 1
					from road_nodes rn
					where ST_DWithin(
						rn."point"::geography,
						ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
						:thresholdMeter
					)
				)
				""",
			new MapSqlParameterSource()
				.addValue("lng", coordinate.lng())
				.addValue("lat", coordinate.lat())
				.addValue("thresholdMeter", SNAP_DISTANCE_METER),
			Boolean.class);
		return Boolean.TRUE.equals(exists);
	}

	private List<Long> queryLongs(String sql) {
		return jdbcTemplate.queryForList(sql, Long.class)
			.stream()
			.filter(Objects::nonNull)
			.toList();
	}

	private BusinessException invalidRequest(String message) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
	}

	private BusinessException internalError(String message) {
		return new BusinessException(CommonErrorCode.INTERNAL_ERROR, message);
	}

	private record CoordinateInput(
		double lng,
		double lat) {
	}

	private record AddSegmentInput(
		int editSeq,
		SegmentType segmentType,
		LineInput lineInput,
		Long requestedFromNodeId,
		Long requestedToNodeId) {
	}

	private record LineInput(
		List<CoordinateInput> coordinates) {

		CoordinateInput first() {
			return coordinates.get(0);
		}

		CoordinateInput last() {
			return coordinates.get(coordinates.size() - 1);
		}

		String toWkt() {
			return "LINESTRING(" + coordinates.stream()
				.map(coordinate -> coordinate.lng() + " " + coordinate.lat())
				.reduce((left, right) -> left + ", " + right)
				.orElseThrow() + ")";
		}

		LineInput withEndpoints(CoordinateInput first, CoordinateInput last) {
			List<CoordinateInput> nextCoordinates = new ArrayList<>(coordinates);
			nextCoordinates.set(0, first);
			nextCoordinates.set(nextCoordinates.size() - 1, last);
			return new LineInput(List.copyOf(nextCoordinates));
		}
	}

	private static class ApplyCounters {
		private int createdNodes;
		private int snappedNodes;
		private int skippedSegments;
		private int createdSegmentFeatures;
		private int updatedSegmentAttributes;
	}
}
