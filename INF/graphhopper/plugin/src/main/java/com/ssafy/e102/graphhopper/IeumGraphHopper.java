package com.ssafy.e102.graphhopper;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.WeightingFactory;

final class IeumGraphHopper extends GraphHopper {

	private final RoutingSegmentOverrideStore routingSegmentOverrideStore;

	IeumGraphHopper(RoutingSegmentOverrideStore routingSegmentOverrideStore) {
		this.routingSegmentOverrideStore = routingSegmentOverrideStore;
	}

	@Override
	protected WeightingFactory createWeightingFactory() {
		return new OverlayAwareWeightingFactory(getBaseGraph(), getEncodingManager(), routingSegmentOverrideStore);
	}
}
