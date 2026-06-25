package com.ssafy.e102.domain.admin.entity;

import org.locationtech.jts.geom.Geometry;

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
@Table(name = "admin_areas", indexes = {
	@Index(name = "idx_admin_areas_gu_dong", columnList = "gu,dong")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminArea {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "area_id", nullable = false, updatable = false)
	private Long areaId;

	@Column(name = "gu", nullable = false, length = 50)
	private String gu;

	@Column(name = "dong", nullable = false, length = 50)
	private String dong;

	@Column(name = "geom", nullable = false, columnDefinition = "geometry(Geometry, 4326)")
	private Geometry geom;
}
