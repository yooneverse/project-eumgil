package com.ssafy.e102.graphhopper;

import java.util.Map;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.ssafy.e102.graphhopper.RoutingSegmentOverrideStore.RoutingSegmentOverrideSnapshot;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.WidthState;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.YesNoUnknown;

final class OverlayAwareWeighting implements Weighting {

	private final Weighting delegate;
	private final IntEncodedValue dbEdgeIdEncodedValue;
	private final EnumEncodedValue<YesNoUnknown> walkAccessEncodedValue;
	private final EnumEncodedValue<YesNoUnknown> stairsStateEncodedValue;
	private final EnumEncodedValue<WidthState> widthStateEncodedValue;
	private final EnumEncodedValue<YesNoUnknown> brailleBlockStateEncodedValue;
	private final RoutingSegmentOverrideStore routingSegmentOverrideStore;
	private final OverlayProfilePolicy profilePolicy;

	OverlayAwareWeighting(
		Weighting delegate,
		IntEncodedValue dbEdgeIdEncodedValue,
		EnumEncodedValue<YesNoUnknown> walkAccessEncodedValue,
		EnumEncodedValue<YesNoUnknown> stairsStateEncodedValue,
		EnumEncodedValue<WidthState> widthStateEncodedValue,
		EnumEncodedValue<YesNoUnknown> brailleBlockStateEncodedValue,
		RoutingSegmentOverrideStore routingSegmentOverrideStore,
		String profileName) {
		this.delegate = delegate;
		this.dbEdgeIdEncodedValue = dbEdgeIdEncodedValue;
		this.walkAccessEncodedValue = walkAccessEncodedValue;
		this.stairsStateEncodedValue = stairsStateEncodedValue;
		this.widthStateEncodedValue = widthStateEncodedValue;
		this.brailleBlockStateEncodedValue = brailleBlockStateEncodedValue;
		this.routingSegmentOverrideStore = routingSegmentOverrideStore;
		this.profilePolicy = OverlayProfilePolicy.fromProfileName(profileName);
	}

	@Override
	public double calcMinWeightPerDistance() {
		return delegate.calcMinWeightPerDistance();
	}

	@Override
	public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
		return delegate.calcEdgeWeight(effectiveEdge(edgeState), reverse);
	}

	@Override
	public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
		return delegate.calcEdgeMillis(effectiveEdge(edgeState), reverse);
	}

	@Override
	public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
		return delegate.calcTurnWeight(inEdge, viaNode, outEdge);
	}

	@Override
	public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
		return delegate.calcTurnMillis(inEdge, viaNode, outEdge);
	}

	@Override
	public boolean hasTurnCosts() {
		return delegate.hasTurnCosts();
	}

	@Override
	public String getName() {
		return delegate.getName();
	}

	private EdgeIteratorState effectiveEdge(EdgeIteratorState edgeState) {
		int dbEdgeId = edgeState.get(dbEdgeIdEncodedValue);
		RoutingSegmentOverrideSnapshot snapshot = routingSegmentOverrideStore.getOverride(dbEdgeId);
		if (snapshot == null || profilePolicy == OverlayProfilePolicy.NONE) {
			return edgeState;
		}
		return new EffectiveOverlayEdgeIteratorState(edgeState, snapshot);
	}

	private enum OverlayProfilePolicy {
		SHARED_ACCESSIBILITY,
		VISUAL,
		NONE;

		private static OverlayProfilePolicy fromProfileName(String profileName) {
			if (profileName != null && profileName.startsWith("visual_")) {
				return VISUAL;
			}
			if (profileName != null
				&& (profileName.startsWith("wheelchair_") || profileName.startsWith("pedestrian_"))) {
				return SHARED_ACCESSIBILITY;
			}
			return NONE;
		}
	}

	private final class EffectiveOverlayEdgeIteratorState implements EdgeIteratorState {
		private final EdgeIteratorState delegateEdge;
		private final RoutingSegmentOverrideSnapshot snapshot;

		private EffectiveOverlayEdgeIteratorState(
			EdgeIteratorState delegateEdge,
			RoutingSegmentOverrideSnapshot snapshot) {
			this.delegateEdge = delegateEdge;
			this.snapshot = snapshot;
		}

		@Override
		public int getEdge() {
			return delegateEdge.getEdge();
		}

		@Override
		public int getEdgeKey() {
			return delegateEdge.getEdgeKey();
		}

		@Override
		public int getReverseEdgeKey() {
			return delegateEdge.getReverseEdgeKey();
		}

		@Override
		public int getBaseNode() {
			return delegateEdge.getBaseNode();
		}

		@Override
		public int getAdjNode() {
			return delegateEdge.getAdjNode();
		}

		@Override
		public PointList fetchWayGeometry(FetchMode fetchMode) {
			return delegateEdge.fetchWayGeometry(fetchMode);
		}

		@Override
		public EdgeIteratorState setWayGeometry(PointList pointList) {
			return delegateEdge.setWayGeometry(pointList);
		}

		@Override
		public double getDistance() {
			return delegateEdge.getDistance();
		}

		@Override
		public EdgeIteratorState setDistance(double distance) {
			return delegateEdge.setDistance(distance);
		}

		@Override
		public IntsRef getFlags() {
			return delegateEdge.getFlags();
		}

		@Override
		public EdgeIteratorState setFlags(IntsRef intsRef) {
			return delegateEdge.setFlags(intsRef);
		}

		@Override
		public boolean get(BooleanEncodedValue encodedValue) {
			return delegateEdge.get(encodedValue);
		}

		@Override
		public EdgeIteratorState set(BooleanEncodedValue encodedValue, boolean value) {
			return delegateEdge.set(encodedValue, value);
		}

		@Override
		public boolean getReverse(BooleanEncodedValue encodedValue) {
			return delegateEdge.getReverse(encodedValue);
		}

		@Override
		public EdgeIteratorState setReverse(BooleanEncodedValue encodedValue, boolean value) {
			return delegateEdge.setReverse(encodedValue, value);
		}

		@Override
		public EdgeIteratorState set(BooleanEncodedValue encodedValue, boolean forwardValue, boolean reverseValue) {
			return delegateEdge.set(encodedValue, forwardValue, reverseValue);
		}

		@Override
		public int get(IntEncodedValue encodedValue) {
			return delegateEdge.get(encodedValue);
		}

		@Override
		public EdgeIteratorState set(IntEncodedValue encodedValue, int value) {
			return delegateEdge.set(encodedValue, value);
		}

		@Override
		public int getReverse(IntEncodedValue encodedValue) {
			return delegateEdge.getReverse(encodedValue);
		}

		@Override
		public EdgeIteratorState setReverse(IntEncodedValue encodedValue, int value) {
			return delegateEdge.setReverse(encodedValue, value);
		}

		@Override
		public EdgeIteratorState set(IntEncodedValue encodedValue, int forwardValue, int reverseValue) {
			return delegateEdge.set(encodedValue, forwardValue, reverseValue);
		}

		@Override
		public double get(DecimalEncodedValue encodedValue) {
			return delegateEdge.get(encodedValue);
		}

		@Override
		public EdgeIteratorState set(DecimalEncodedValue encodedValue, double value) {
			return delegateEdge.set(encodedValue, value);
		}

		@Override
		public double getReverse(DecimalEncodedValue encodedValue) {
			return delegateEdge.getReverse(encodedValue);
		}

		@Override
		public EdgeIteratorState setReverse(DecimalEncodedValue encodedValue, double value) {
			return delegateEdge.setReverse(encodedValue, value);
		}

		@Override
		public EdgeIteratorState set(DecimalEncodedValue encodedValue, double forwardValue, double reverseValue) {
			return delegateEdge.set(encodedValue, forwardValue, reverseValue);
		}

		@Override
		public <T extends Enum<?>> T get(EnumEncodedValue<T> encodedValue) {
			T override = effectiveEnumValue(encodedValue);
			return override == null ? delegateEdge.get(encodedValue) : override;
		}

		@Override
		public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> encodedValue, T value) {
			return delegateEdge.set(encodedValue, value);
		}

		@Override
		public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> encodedValue) {
			T override = effectiveEnumValue(encodedValue);
			return override == null ? delegateEdge.getReverse(encodedValue) : override;
		}

		@Override
		public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> encodedValue, T value) {
			return delegateEdge.setReverse(encodedValue, value);
		}

		@Override
		public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> encodedValue, T forwardValue, T reverseValue) {
			return delegateEdge.set(encodedValue, forwardValue, reverseValue);
		}

		@Override
		public String get(StringEncodedValue encodedValue) {
			return delegateEdge.get(encodedValue);
		}

		@Override
		public EdgeIteratorState set(StringEncodedValue encodedValue, String value) {
			return delegateEdge.set(encodedValue, value);
		}

		@Override
		public String getReverse(StringEncodedValue encodedValue) {
			return delegateEdge.getReverse(encodedValue);
		}

		@Override
		public EdgeIteratorState setReverse(StringEncodedValue encodedValue, String value) {
			return delegateEdge.setReverse(encodedValue, value);
		}

		@Override
		public EdgeIteratorState set(StringEncodedValue encodedValue, String forwardValue, String reverseValue) {
			return delegateEdge.set(encodedValue, forwardValue, reverseValue);
		}

		@Override
		public String getName() {
			return delegateEdge.getName();
		}

		@Override
		public EdgeIteratorState setKeyValues(Map<String, KVStorage.KValue> keyValues) {
			return delegateEdge.setKeyValues(keyValues);
		}

		@Override
		public Map<String, KVStorage.KValue> getKeyValues() {
			return delegateEdge.getKeyValues();
		}

		@Override
		public Object getValue(String key) {
			return delegateEdge.getValue(key);
		}

		@Override
		public EdgeIteratorState detach(boolean reverse) {
			EdgeIteratorState detached = delegateEdge.detach(reverse);
			return new EffectiveOverlayEdgeIteratorState(detached, snapshot);
		}

		@Override
		public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState edgeIteratorState) {
			return delegateEdge.copyPropertiesFrom(edgeIteratorState);
		}

		@SuppressWarnings("unchecked")
		private <T extends Enum<?>> T effectiveEnumValue(EnumEncodedValue<T> encodedValue) {
			String encodedValueName = encodedValue.getName();
			if (profilePolicy == OverlayProfilePolicy.SHARED_ACCESSIBILITY || profilePolicy == OverlayProfilePolicy.VISUAL) {
				if (walkAccessEncodedValue.getName().equals(encodedValueName) && snapshot.walkAccess() != null) {
					return (T)snapshot.walkAccess();
				}
				if (stairsStateEncodedValue.getName().equals(encodedValueName) && snapshot.stairsState() != null) {
					return (T)snapshot.stairsState();
				}
				if (widthStateEncodedValue.getName().equals(encodedValueName) && snapshot.widthState() != null) {
					return (T)snapshot.widthState();
				}
			}
			if (profilePolicy == OverlayProfilePolicy.VISUAL
				&& brailleBlockStateEncodedValue.getName().equals(encodedValueName)
				&& snapshot.brailleBlockState() != null) {
				return (T)snapshot.brailleBlockState();
			}
			return null;
		}
	}
}
