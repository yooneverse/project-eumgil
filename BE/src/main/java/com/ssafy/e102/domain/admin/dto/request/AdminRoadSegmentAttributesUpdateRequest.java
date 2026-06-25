package com.ssafy.e102.domain.admin.dto.request;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

public final class AdminRoadSegmentAttributesUpdateRequest {

	private static final String WALK_ACCESS_FIELD = "walkAccess";
	private static final String BRAILLE_BLOCK_STATE_FIELD = "brailleBlockState";
	private static final String AUDIO_SIGNAL_STATE_FIELD = "audioSignalState";
	private static final String WIDTH_STATE_FIELD = "widthState";
	private static final String SURFACE_STATE_FIELD = "surfaceState";
	private static final String STAIRS_STATE_FIELD = "stairsState";
	private static final String SIGNAL_STATE_FIELD = "signalState";
	private static final String APPLY_ROUTING_IMMEDIATELY_FIELD = "applyRoutingImmediately";

	private final AccessibilityState walkAccess;
	private final AccessibilityState brailleBlockState;
	private final AccessibilityState audioSignalState;
	private final WidthState widthState;
	private final SurfaceState surfaceState;
	private final AccessibilityState stairsState;
	private final AccessibilityState signalState;
	private final Boolean applyRoutingImmediately;
	private final Set<String> requestedFields;

	public AdminRoadSegmentAttributesUpdateRequest(
		AccessibilityState walkAccess,
		AccessibilityState brailleBlockState,
		AccessibilityState audioSignalState,
		WidthState widthState,
		SurfaceState surfaceState,
		AccessibilityState stairsState,
		AccessibilityState signalState,
		Boolean applyRoutingImmediately) {
		this(
			walkAccess,
			brailleBlockState,
			audioSignalState,
			widthState,
			surfaceState,
			stairsState,
			signalState,
			applyRoutingImmediately,
			requestedNonNullFields(
				walkAccess,
				brailleBlockState,
				audioSignalState,
				widthState,
				surfaceState,
				stairsState,
				signalState,
				applyRoutingImmediately));
	}

	private AdminRoadSegmentAttributesUpdateRequest(
		AccessibilityState walkAccess,
		AccessibilityState brailleBlockState,
		AccessibilityState audioSignalState,
		WidthState widthState,
		SurfaceState surfaceState,
		AccessibilityState stairsState,
		AccessibilityState signalState,
		Boolean applyRoutingImmediately,
		Set<String> requestedFields) {
		this.walkAccess = walkAccess;
		this.brailleBlockState = brailleBlockState;
		this.audioSignalState = audioSignalState;
		this.widthState = widthState;
		this.surfaceState = surfaceState;
		this.stairsState = stairsState;
		this.signalState = signalState;
		this.applyRoutingImmediately = applyRoutingImmediately;
		this.requestedFields = Set.copyOf(requestedFields);
	}

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static AdminRoadSegmentAttributesUpdateRequest fromJson(JsonNode node) {
		Set<String> requestedFields = new HashSet<>();
		return new AdminRoadSegmentAttributesUpdateRequest(
			parseEnum(node, WALK_ACCESS_FIELD, AccessibilityState.class, requestedFields),
			parseEnum(node, BRAILLE_BLOCK_STATE_FIELD, AccessibilityState.class, requestedFields),
			parseEnum(node, AUDIO_SIGNAL_STATE_FIELD, AccessibilityState.class, requestedFields),
			parseEnum(node, WIDTH_STATE_FIELD, WidthState.class, requestedFields),
			parseEnum(node, SURFACE_STATE_FIELD, SurfaceState.class, requestedFields),
			parseEnum(node, STAIRS_STATE_FIELD, AccessibilityState.class, requestedFields),
			parseEnum(node, SIGNAL_STATE_FIELD, AccessibilityState.class, requestedFields),
			parseBoolean(node, APPLY_ROUTING_IMMEDIATELY_FIELD, requestedFields),
			requestedFields);
	}

	public AccessibilityState walkAccess() {
		return walkAccess;
	}

	public AccessibilityState brailleBlockState() {
		return brailleBlockState;
	}

	public AccessibilityState audioSignalState() {
		return audioSignalState;
	}

	public WidthState widthState() {
		return widthState;
	}

	public SurfaceState surfaceState() {
		return surfaceState;
	}

	public AccessibilityState stairsState() {
		return stairsState;
	}

	public AccessibilityState signalState() {
		return signalState;
	}

	public Boolean applyRoutingImmediately() {
		return applyRoutingImmediately;
	}

	public boolean hasWalkAccessField() {
		return requestedFields.contains(WALK_ACCESS_FIELD);
	}

	public boolean hasBrailleBlockStateField() {
		return requestedFields.contains(BRAILLE_BLOCK_STATE_FIELD);
	}

	public boolean hasWidthStateField() {
		return requestedFields.contains(WIDTH_STATE_FIELD);
	}

	public boolean hasStairsStateField() {
		return requestedFields.contains(STAIRS_STATE_FIELD);
	}

	public boolean hasRoutingOverlayTargetField() {
		return hasWalkAccessField()
			|| hasBrailleBlockStateField()
			|| hasWidthStateField()
			|| hasStairsStateField();
	}

	private static Set<String> requestedNonNullFields(
		AccessibilityState walkAccess,
		AccessibilityState brailleBlockState,
		AccessibilityState audioSignalState,
		WidthState widthState,
		SurfaceState surfaceState,
		AccessibilityState stairsState,
		AccessibilityState signalState,
		Boolean applyRoutingImmediately) {
		Set<String> fields = new HashSet<>();
		addIfNonNull(fields, WALK_ACCESS_FIELD, walkAccess);
		addIfNonNull(fields, BRAILLE_BLOCK_STATE_FIELD, brailleBlockState);
		addIfNonNull(fields, AUDIO_SIGNAL_STATE_FIELD, audioSignalState);
		addIfNonNull(fields, WIDTH_STATE_FIELD, widthState);
		addIfNonNull(fields, SURFACE_STATE_FIELD, surfaceState);
		addIfNonNull(fields, STAIRS_STATE_FIELD, stairsState);
		addIfNonNull(fields, SIGNAL_STATE_FIELD, signalState);
		addIfNonNull(fields, APPLY_ROUTING_IMMEDIATELY_FIELD, applyRoutingImmediately);
		return fields;
	}

	private static void addIfNonNull(Set<String> fields, String fieldName, Object value) {
		if (value != null) {
			fields.add(fieldName);
		}
	}

	private static <T extends Enum<T>> T parseEnum(
		JsonNode node,
		String fieldName,
		Class<T> enumType,
		Set<String> requestedFields) {
		if (node == null || !node.has(fieldName)) {
			return null;
		}
		requestedFields.add(fieldName);
		JsonNode value = node.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		return Enum.valueOf(enumType, value.asText());
	}

	private static Boolean parseBoolean(JsonNode node, String fieldName, Set<String> requestedFields) {
		if (node == null || !node.has(fieldName)) {
			return null;
		}
		requestedFields.add(fieldName);
		JsonNode value = node.get(fieldName);
		return value == null || value.isNull() ? null : value.asBoolean();
	}
}
