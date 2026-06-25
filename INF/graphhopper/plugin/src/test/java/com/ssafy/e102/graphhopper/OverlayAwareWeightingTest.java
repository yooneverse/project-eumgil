package com.ssafy.e102.graphhopper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.ssafy.e102.graphhopper.RoutingSegmentOverrideStore.RoutingSegmentOverrideSnapshot;
import com.ssafy.e102.graphhopper.ieum.IeumEncodedValues;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.WidthState;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.YesNoUnknown;

public final class OverlayAwareWeightingTest {

	private final IntEncodedValue dbEdgeId = new IntEncodedValueImpl(IeumEncodedValues.DB_EDGE_ID, 31, false);
	private final EnumEncodedValue<YesNoUnknown> walkAccess = new EnumEncodedValue<>(IeumEncodedValues.WALK_ACCESS, YesNoUnknown.class);
	private final EnumEncodedValue<YesNoUnknown> stairsState = new EnumEncodedValue<>(IeumEncodedValues.STAIRS_STATE, YesNoUnknown.class);
	private final EnumEncodedValue<WidthState> widthState = new EnumEncodedValue<>(IeumEncodedValues.WIDTH_STATE, WidthState.class);
	private final EnumEncodedValue<YesNoUnknown> brailleBlockState = new EnumEncodedValue<>(IeumEncodedValues.BRAILLE_BLOCK_STATE, YesNoUnknown.class);

	public static void main(String[] args) {
		OverlayAwareWeightingTest test = new OverlayAwareWeightingTest();
		test.wheelchairProfileOverridesWalkStairsAndWidthOnly();
		test.visualProfileOverridesAllCustomModelAccessibilityValues();
		test.pedestrianProfileOverridesSharedCustomModelAccessibilityValues();
		test.nullOverlayColumnsKeepBaseValues();
	}

	void wheelchairProfileOverridesWalkStairsAndWidthOnly() {
		CapturingWeighting delegate = new CapturingWeighting(walkAccess, stairsState, widthState, brailleBlockState);
		OverlayAwareWeighting weighting = weighting(
			"wheelchair_manual_safe",
			delegate,
			new RoutingSegmentOverrideSnapshot(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.YES));
		EdgeIteratorState baseEdge = edge(
			Map.of(
				IeumEncodedValues.WALK_ACCESS, YesNoUnknown.NO,
				IeumEncodedValues.STAIRS_STATE, YesNoUnknown.YES,
				IeumEncodedValues.WIDTH_STATE, WidthState.NARROW,
				IeumEncodedValues.BRAILLE_BLOCK_STATE, YesNoUnknown.NO));

		assertEquals(7.0, weighting.calcEdgeWeight(baseEdge, false), "delegate weight must be returned");
		assertEquals(70L, weighting.calcEdgeMillis(baseEdge, true), "delegate millis must be returned");

		delegate.assertWeightSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.NO);
		delegate.assertMillisSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.NO);
		delegate.assertReverseSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.NO);
	}

	void visualProfileOverridesAllCustomModelAccessibilityValues() {
		CapturingWeighting delegate = new CapturingWeighting(walkAccess, stairsState, widthState, brailleBlockState);
		OverlayAwareWeighting weighting = weighting(
			"visual_safe",
			delegate,
			new RoutingSegmentOverrideSnapshot(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.YES));
		EdgeIteratorState baseEdge = edge(
			Map.of(
				IeumEncodedValues.WALK_ACCESS, YesNoUnknown.NO,
				IeumEncodedValues.STAIRS_STATE, YesNoUnknown.YES,
				IeumEncodedValues.WIDTH_STATE, WidthState.NARROW,
				IeumEncodedValues.BRAILLE_BLOCK_STATE, YesNoUnknown.NO));

		weighting.calcEdgeWeight(baseEdge, false);
		weighting.calcEdgeMillis(baseEdge, true);

		delegate.assertWeightSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.YES);
		delegate.assertMillisSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.YES);
		delegate.assertReverseSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.YES);
	}

	void pedestrianProfileOverridesSharedCustomModelAccessibilityValues() {
		CapturingWeighting delegate = new CapturingWeighting(walkAccess, stairsState, widthState, brailleBlockState);
		OverlayAwareWeighting weighting = weighting(
			"pedestrian_safe",
			delegate,
			new RoutingSegmentOverrideSnapshot(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.YES));
		EdgeIteratorState baseEdge = edge(
			Map.of(
				IeumEncodedValues.WALK_ACCESS, YesNoUnknown.NO,
				IeumEncodedValues.STAIRS_STATE, YesNoUnknown.YES,
				IeumEncodedValues.WIDTH_STATE, WidthState.NARROW,
				IeumEncodedValues.BRAILLE_BLOCK_STATE, YesNoUnknown.NO));

		weighting.calcEdgeWeight(baseEdge, false);
		weighting.calcEdgeMillis(baseEdge, true);

		delegate.assertWeightSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.NO);
		delegate.assertMillisSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.NO);
		delegate.assertReverseSeen(YesNoUnknown.YES, YesNoUnknown.NO, WidthState.ADEQUATE_150, YesNoUnknown.NO);
	}

	void nullOverlayColumnsKeepBaseValues() {
		CapturingWeighting delegate = new CapturingWeighting(walkAccess, stairsState, widthState, brailleBlockState);
		OverlayAwareWeighting weighting = weighting(
			"wheelchair_auto_fast",
			delegate,
			new RoutingSegmentOverrideSnapshot(null, YesNoUnknown.NO, null, null));
		EdgeIteratorState baseEdge = edge(
			Map.of(
				IeumEncodedValues.WALK_ACCESS, YesNoUnknown.UNKNOWN,
				IeumEncodedValues.STAIRS_STATE, YesNoUnknown.YES,
				IeumEncodedValues.WIDTH_STATE, WidthState.ADEQUATE_120,
				IeumEncodedValues.BRAILLE_BLOCK_STATE, YesNoUnknown.NO));

		weighting.calcEdgeWeight(baseEdge, false);
		weighting.calcEdgeMillis(baseEdge, true);

		delegate.assertWeightSeen(YesNoUnknown.UNKNOWN, YesNoUnknown.NO, WidthState.ADEQUATE_120, YesNoUnknown.NO);
		delegate.assertMillisSeen(YesNoUnknown.UNKNOWN, YesNoUnknown.NO, WidthState.ADEQUATE_120, YesNoUnknown.NO);
		delegate.assertReverseSeen(YesNoUnknown.UNKNOWN, YesNoUnknown.NO, WidthState.ADEQUATE_120, YesNoUnknown.NO);
	}

	private OverlayAwareWeighting weighting(
		String profileName,
		CapturingWeighting delegate,
		RoutingSegmentOverrideSnapshot snapshot) {
		return new OverlayAwareWeighting(
			delegate,
			dbEdgeId,
			walkAccess,
			stairsState,
			widthState,
			brailleBlockState,
			new FixedStore(snapshot),
			profileName);
	}

	private EdgeIteratorState edge(Map<String, Enum<?>> enumValues) {
		InvocationHandler handler = (proxy, method, args) -> {
			String methodName = method.getName();
			if (("get".equals(methodName) || "getReverse".equals(methodName)) && args != null && args.length == 1) {
				Object encodedValue = args[0];
				if (encodedValue instanceof IntEncodedValue intEncodedValue
					&& IeumEncodedValues.DB_EDGE_ID.equals(intEncodedValue.getName())) {
					return 123;
				}
				if (encodedValue instanceof EnumEncodedValue<?> enumEncodedValue) {
					return enumValues.get(enumEncodedValue.getName());
				}
			}
			if (method.getReturnType().isAssignableFrom(EdgeIteratorState.class)) {
				return proxy;
			}
			return defaultValue(method.getReturnType());
		};
		return (EdgeIteratorState)Proxy.newProxyInstance(
			EdgeIteratorState.class.getClassLoader(),
			new Class<?>[] {EdgeIteratorState.class},
			handler);
	}

	private static Object defaultValue(Class<?> returnType) {
		if (returnType == boolean.class) {
			return false;
		}
		if (returnType == int.class) {
			return 0;
		}
		if (returnType == long.class) {
			return 0L;
		}
		if (returnType == double.class) {
			return 0.0;
		}
		if (returnType == String.class) {
			return "";
		}
		if (returnType == IntsRef.class) {
			return new IntsRef(1);
		}
		if (returnType == PointList.class) {
			return PointList.EMPTY;
		}
		if (returnType == Map.class) {
			return Map.<String, KVStorage.KValue>of();
		}
		return null;
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
		}
	}

	private static final class FixedStore extends RoutingSegmentOverrideStore {
		private final RoutingSegmentOverrideSnapshot snapshot;

		private FixedStore(RoutingSegmentOverrideSnapshot snapshot) {
			this.snapshot = snapshot;
		}

		@Override
		public RoutingSegmentOverrideSnapshot getOverride(long edgeId) {
			return snapshot;
		}
	}

	private static final class CapturingWeighting implements Weighting {
		private final EnumEncodedValue<YesNoUnknown> walkAccess;
		private final EnumEncodedValue<YesNoUnknown> stairsState;
		private final EnumEncodedValue<WidthState> widthState;
		private final EnumEncodedValue<YesNoUnknown> brailleBlockState;
		private Object[] weightSeen;
		private Object[] millisSeen;
		private Object[] reverseSeen;

		private CapturingWeighting(
			EnumEncodedValue<YesNoUnknown> walkAccess,
			EnumEncodedValue<YesNoUnknown> stairsState,
			EnumEncodedValue<WidthState> widthState,
			EnumEncodedValue<YesNoUnknown> brailleBlockState) {
			this.walkAccess = walkAccess;
			this.stairsState = stairsState;
			this.widthState = widthState;
			this.brailleBlockState = brailleBlockState;
		}

		@Override
		public double calcMinWeightPerDistance() {
			return 1.0;
		}

		@Override
		public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
			weightSeen = readForward(edgeState);
			return 7.0;
		}

		@Override
		public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
			millisSeen = readForward(edgeState);
			reverseSeen = readReverse(edgeState);
			return 70L;
		}

		@Override
		public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
			return 0;
		}

		@Override
		public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
			return 0;
		}

		@Override
		public boolean hasTurnCosts() {
			return false;
		}

		@Override
		public String getName() {
			return "capture";
		}

		private Object[] readForward(EdgeIteratorState edgeState) {
			return new Object[] {
				edgeState.get(walkAccess),
				edgeState.get(stairsState),
				edgeState.get(widthState),
				edgeState.get(brailleBlockState)
			};
		}

		private Object[] readReverse(EdgeIteratorState edgeState) {
			return new Object[] {
				edgeState.getReverse(walkAccess),
				edgeState.getReverse(stairsState),
				edgeState.getReverse(widthState),
				edgeState.getReverse(brailleBlockState)
			};
		}

		private void assertWeightSeen(Object walk, Object stairs, Object width, Object braille) {
			assertSeen(weightSeen, walk, stairs, width, braille, "weight edge");
		}

		private void assertMillisSeen(Object walk, Object stairs, Object width, Object braille) {
			assertSeen(millisSeen, walk, stairs, width, braille, "millis edge");
		}

		private void assertReverseSeen(Object walk, Object stairs, Object width, Object braille) {
			assertSeen(reverseSeen, walk, stairs, width, braille, "reverse edge");
		}

		private void assertSeen(Object[] seen, Object walk, Object stairs, Object width, Object braille, String label) {
			assertEquals(walk, seen[0], label + " walk_access");
			assertEquals(stairs, seen[1], label + " stairs_state");
			assertEquals(width, seen[2], label + " width_state");
			assertEquals(braille, seen[3], label + " braille_block_state");
		}
	}
}
