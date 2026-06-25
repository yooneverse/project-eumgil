package com.ssafy.e102.graphhopper;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PMap;
import com.ssafy.e102.graphhopper.ieum.IeumEncodedValues;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.WidthState;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.YesNoUnknown;

final class OverlayAwareWeightingFactory implements WeightingFactory {

	private final WeightingFactory delegateFactory;
	private final IntEncodedValue dbEdgeIdEncodedValue;
	private final EnumEncodedValue<YesNoUnknown> walkAccessEncodedValue;
	private final EnumEncodedValue<YesNoUnknown> stairsStateEncodedValue;
	private final EnumEncodedValue<WidthState> widthStateEncodedValue;
	private final EnumEncodedValue<YesNoUnknown> brailleBlockStateEncodedValue;
	private final RoutingSegmentOverrideStore routingSegmentOverrideStore;

	OverlayAwareWeightingFactory(
		BaseGraph baseGraph,
		EncodingManager encodingManager,
		RoutingSegmentOverrideStore routingSegmentOverrideStore) {
		this.delegateFactory = new DefaultWeightingFactory(baseGraph, encodingManager);
		this.dbEdgeIdEncodedValue = encodingManager.getIntEncodedValue(IeumEncodedValues.DB_EDGE_ID);
		this.walkAccessEncodedValue = encodingManager.getEnumEncodedValue(IeumEncodedValues.WALK_ACCESS, YesNoUnknown.class);
		this.stairsStateEncodedValue = encodingManager.getEnumEncodedValue(IeumEncodedValues.STAIRS_STATE, YesNoUnknown.class);
		this.widthStateEncodedValue = encodingManager.getEnumEncodedValue(IeumEncodedValues.WIDTH_STATE, WidthState.class);
		this.brailleBlockStateEncodedValue = encodingManager.getEnumEncodedValue(IeumEncodedValues.BRAILLE_BLOCK_STATE, YesNoUnknown.class);
		this.routingSegmentOverrideStore = routingSegmentOverrideStore;
	}

	@Override
	public Weighting createWeighting(Profile profile, PMap hints, boolean disableTurnCosts) {
		Weighting delegate = delegateFactory.createWeighting(profile, hints, disableTurnCosts);
		return new OverlayAwareWeighting(
			delegate,
			dbEdgeIdEncodedValue,
			walkAccessEncodedValue,
			stairsStateEncodedValue,
			widthStateEncodedValue,
			brailleBlockStateEncodedValue,
			routingSegmentOverrideStore,
			profile.getName());
	}
}
