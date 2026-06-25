package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

public record AdminRoadNetworkEditApplyResponse(
	int addedSegments,
	int skippedSegments,
	int deletedSegments,
	int createdNodes,
	int snappedNodes,
	int removedOrphanNodes,
	int createdSegmentFeatures,
	int updatedSegmentAttributes,
	List<Long> addedEdgeIds,
	List<Long> deletedEdgeIds,
	List<Long> createdNodeIds,
	List<Long> snappedNodeIds) {
}
