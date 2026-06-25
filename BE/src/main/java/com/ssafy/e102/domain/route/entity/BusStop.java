package com.ssafy.e102.domain.route.entity;

import java.time.LocalDateTime;

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
@Table(name = "bus_stops", indexes = {
	@Index(name = "idx_bus_stops_active", columnList = "active"),
	@Index(name = "idx_bus_stops_stop_name", columnList = "stop_name"),
	@Index(name = "idx_bus_stops_ars_no", columnList = "ars_no")
}, uniqueConstraints = {
	@UniqueConstraint(name = "uk_bus_stops_bstop_id", columnNames = "bstop_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusStop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "bus_stop_id", nullable = false, updatable = false)
	private Long busStopId;

	@Column(name = "bstop_id", nullable = false, length = 30)
	private String bstopId;

	@Column(name = "stop_name", nullable = false, length = 100)
	private String stopName;

	@Column(name = "ars_no", length = 20)
	private String arsNo;

	@Column(name = "stop_type", length = 30)
	private String stopType;

	@Column(name = "point", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point point;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;

	public static BusStop create(
		String bstopId,
		String stopName,
		String arsNo,
		String stopType,
		Point point,
		LocalDateTime syncedAt) {
		BusStop busStop = new BusStop();
		busStop.bstopId = bstopId;
		busStop.update(stopName, arsNo, stopType, point, syncedAt);
		return busStop;
	}

	public void update(String stopName, String arsNo, String stopType, Point point, LocalDateTime syncedAt) {
		this.stopName = stopName;
		this.arsNo = arsNo;
		this.stopType = stopType;
		this.point = point;
		this.active = true;
		this.syncedAt = syncedAt;
	}

	public void deactivate(LocalDateTime syncedAt) {
		this.active = false;
		this.syncedAt = syncedAt;
	}
}
