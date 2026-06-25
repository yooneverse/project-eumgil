package com.ssafy.e102.domain.admin.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.e102.domain.route.type.SegmentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AdminRoadNetworkEditApplyRequest(
	@NotBlank
	String gu,
	@NotBlank
	String dong,
	@NotEmpty @Size(max = 1000)
	List<@Valid Edit> edits) {

	public record Edit(
		String action,
		SegmentType segmentType,
		LineGeometry geom,
		NodeRef fromNode,
		NodeRef toNode,
		Long edgeId,
		Long vertexId,
		String reason) {
	}

	public record LineGeometry(
		String type,
		List<List<Double>> coordinates) {
	}

	public record PointGeometry(
		String type,
		List<Double> coordinates) {
	}

	public record NodeRef(
		String mode,
		Long vertexId,
		String tempNodeId,
		String sourceNodeKey,
		PointGeometry geom,
		BigDecimal snapDistanceMeter) {
	}
}
