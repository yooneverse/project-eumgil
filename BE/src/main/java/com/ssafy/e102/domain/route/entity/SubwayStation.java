package com.ssafy.e102.domain.route.entity;

import org.locationtech.jts.geom.Point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "subway_stations", indexes = {
	@Index(name = "idx_subway_stations_station_line", columnList = "station_name, line_name")
}, uniqueConstraints = {
	@UniqueConstraint(name = "uk_subway_stations_odsay_station_id", columnNames = "odsay_station_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubwayStation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "subway_station_id", nullable = false, updatable = false)
	private Long subwayStationId;

	@Column(name = "odsay_station_id", nullable = false, length = 30)
	private String odsayStationId;

	@Column(name = "station_name", nullable = false, length = 100)
	private String stationName;

	@Column(name = "line_name", nullable = false, length = 50)
	private String lineName;

	@Column(name = "point", columnDefinition = "geometry(Point, 4326)")
	private Point point;

	public static SubwayStation create(String odsayStationId, String stationName, String lineName, Point point) {
		SubwayStation station = new SubwayStation();
		station.odsayStationId = odsayStationId;
		station.stationName = stationName;
		station.lineName = lineName;
		station.point = point;
		return station;
	}
}
