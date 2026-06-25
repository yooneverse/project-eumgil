package com.ssafy.e102.domain.route.entity;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "route_sessions", indexes = {
	@Index(name = "idx_route_sessions_user_route_updated", columnList = "user_id, route_id, updated_at"),
	@Index(name = "idx_route_sessions_route_updated", columnList = "route_id, updated_at")
}, uniqueConstraints = {
	@UniqueConstraint(name = "uk_route_sessions_user_active_route", columnNames = {"user_id", "active_route_key"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteSession extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID sessionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 120)
	private String routeId;

	@Column(length = 120)
	private String activeRouteKey;

	@Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point startPoint;

	@Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point endPoint;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode routeSnapshotJson;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RouteSessionStatus status = RouteSessionStatus.ACTIVE;

	public static RouteSession create(
		User user,
		String routeId,
		Point startPoint,
		Point endPoint,
		JsonNode routeSnapshotJson) {
		RouteSession routeSession = new RouteSession();
		routeSession.user = user;
		routeSession.routeId = routeId;
		routeSession.activeRouteKey = routeId;
		routeSession.startPoint = startPoint;
		routeSession.endPoint = endPoint;
		routeSession.routeSnapshotJson = routeSnapshotJson;
		routeSession.status = RouteSessionStatus.ACTIVE;
		return routeSession;
	}

	public void complete() {
		this.status = RouteSessionStatus.COMPLETED;
		this.activeRouteKey = null;
	}

	public boolean ensureActiveRouteKey() {
		if (status != RouteSessionStatus.ACTIVE || routeId.equals(activeRouteKey)) {
			return false;
		}
		this.activeRouteKey = routeId;
		return true;
	}
}
