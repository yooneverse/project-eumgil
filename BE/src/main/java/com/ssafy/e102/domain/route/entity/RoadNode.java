package com.ssafy.e102.domain.route.entity;

import org.locationtech.jts.geom.Point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "road_nodes", uniqueConstraints = {
	@UniqueConstraint(name = "uk_road_nodes_source_node_key", columnNames = "source_node_key")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadNode {

	@Id
	@Column(name = "vertex_id", nullable = false, updatable = false)
	private Long vertexId;

	@Column(name = "source_node_key", nullable = false, length = 100)
	private String sourceNodeKey;

	@Column(name = "point", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point point;

	public static RoadNode create(Long vertexId, String sourceNodeKey, Point point) {
		RoadNode roadNode = new RoadNode();
		roadNode.vertexId = vertexId;
		roadNode.sourceNodeKey = sourceNodeKey;
		roadNode.point = point;
		return roadNode;
	}
}
