package com.ssafy.e102.domain.route.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "odsay_load_lane", uniqueConstraints = {
	@UniqueConstraint(name = "uk_odsay_load_lane_map_obj", columnNames = {"map_obj"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OdsayLoadLane {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "odsay_load_lane_id", nullable = false, updatable = false)
	private Long odsayLoadLaneId;

	@Column(name = "map_obj", nullable = false, length = 255)
	private String mapObj;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "lane_geometries", nullable = false, columnDefinition = "jsonb")
	private JsonNode laneGeometries;

	public static OdsayLoadLane create(String mapObj, JsonNode laneGeometries) {
		OdsayLoadLane odsayLoadLane = new OdsayLoadLane();
		odsayLoadLane.mapObj = mapObj;
		odsayLoadLane.laneGeometries = laneGeometries;
		return odsayLoadLane;
	}

	public void replaceLaneGeometries(JsonNode laneGeometries) {
		this.laneGeometries = laneGeometries;
	}
}
