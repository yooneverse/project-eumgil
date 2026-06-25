package com.ssafy.e102.domain.route.entity;

import org.locationtech.jts.geom.Point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "subway_station_elevators", indexes = {
	@Index(name = "idx_sse_odsay_station_id", columnList = "odsay_station_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubwayStationElevator {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "subway_station_elevator_id", nullable = false, updatable = false)
	private Long subwayStationElevatorId;

	@Column(name = "odsay_station_id", nullable = false, length = 30)
	private String odsayStationId;

	@Column(name = "station_name", nullable = false, length = 100)
	private String stationName;

	@Column(name = "line_name", nullable = false, length = 50)
	private String lineName;

	@Column(name = "point", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point point;

	public static SubwayStationElevator create(String odsayStationId, String stationName, String lineName,
		Point point) {
		SubwayStationElevator elevator = new SubwayStationElevator();
		elevator.odsayStationId = odsayStationId;
		elevator.stationName = stationName;
		elevator.lineName = lineName;
		elevator.point = point;
		return elevator;
	}
}
