import { type RefObject, useEffect, useRef, useState } from "react";
import type { AreaBoundaryFeature, BridgeFeature, BridgePayload, EditableSegmentType, EditAction, GeoPoint, ReferenceLayerKey, ReferencePointFeature, ReferencePointPayload, RoadAttributeFeature, RoadAttributePayload, SegmentFeature, SegmentFeatureType, SegmentPayload } from "../types";
import { attachKakaoWheelZoom, loadKakaoMap, type KakaoMap, type KakaoOverlay, type KakaoRoadview, type KakaoRoadviewClient } from "./kakaoLoader";
import { deletedEdgeIds, describeCrossWalkProjection, describeSideLineNodeSnap, draftEndpointNodeCandidates, draftSegmentFeatures, newNodeRef, resetPolygonDeleteSelection, roadNodeCandidates, segmentEndpointNodeCandidates, segmentsTouchingPolygon, snapToSegmentEndpointNode, type SnappedSegmentEndpoint, twoPointAddDraft, visibleSegmentFeatures } from "./draftSegments";
import { shouldShowRoadAttributeReference } from "./networkReferenceLayer";
import { roadAttributeStrokeColor, roadAttributeStrokeStyle, roadAttributeStrokeWeight } from "./roadAttributeStyle";
import { roadviewUnavailableMessage, shouldOpenRoadviewForMode } from "./roadviewMode";
import { pointLineDistanceM } from "./referenceMatching";

type Coord = [number, number];
type EditorMode = "idle" | "delete" | "add" | "roadview";
type AddType = EditableSegmentType;
interface ViewportBounds {
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
}

interface SegmentMapProps {
  payload?: SegmentPayload;
  bridgePayload?: BridgePayload;
  loading: boolean;
  error?: Error | null;
  draftEdits: EditAction[];
  onDraftEdit: (edit: EditAction) => void;
  selectedSegment: SegmentFeature | null;
  onSelectSegment: (feature: SegmentFeature) => void;
  referenceLayers?: Record<ReferenceLayerKey, boolean>;
  roadAttributePayload?: RoadAttributePayload;
  stairPayload?: ReferencePointPayload;
  audioSignalPayload?: ReferencePointPayload;
  brailleBlockPayload?: ReferencePointPayload;
  roadviewContainerRef: RefObject<HTMLDivElement | null>;
  onRoadviewChange: (state: RoadviewDockState) => void;
  editable?: boolean;
  routePointPickMode?: "start" | "end" | null;
  onRoutePointPick?: (point: GeoPoint) => void;
  routeLines?: {
    safe?: GeoPoint[];
    fast?: GeoPoint[];
  };
  routePoints?: {
    start?: GeoPoint | null;
    end?: GeoPoint | null;
  };
  focusMarker?: {
    point: GeoPoint;
    label?: string;
  } | null;
  preferredView?: {
    point: GeoPoint;
    level?: number;
  } | null;
  forceDetailedSegments?: boolean;
  toolbarMode?: "editor" | "roadSegmentLegend" | "segmentFeatureLegend" | "routeAttributeLegend";
  draftEditCount?: number;
  onUndoDraftEdit?: () => void;
  onClearDraftEdits?: () => void;
}

export interface RoadviewDockState {
  open: boolean;
  message: string;
  onClose?: () => void;
}

const ROADVIEW_DEFAULT_MESSAGE = "";
const DETAIL_SEGMENT_MAX_LEVEL = 4;
const FORCED_DETAIL_SEGMENT_MAX_COUNT = 1500;
const DETAIL_SEGMENT_RENDER_BATCH_SIZE = 250;
const DETAIL_SEGMENT_VIEWPORT_PADDING_RATIO = 0.35;
const segmentFeatureTypes: SegmentFeatureType[] = ["CROSSWALK", "AUDIO_SIGNAL", "BRAILLE_BLOCK", "STAIRS"];
const segmentFeatureLabels: Record<SegmentFeatureType, string> = {
  CROSSWALK: "횡단보도",
  AUDIO_SIGNAL: "음향신호기",
  BRAILLE_BLOCK: "점자블록",
  STAIRS: "계단",
};
const segmentFeatureColors: Record<SegmentFeatureType, string> = {
  CROSSWALK: "#2563eb",
  AUDIO_SIGNAL: "#0f766e",
  BRAILLE_BLOCK: "#7c3aed",
  STAIRS: "#7c2d12",
};
type RouteAttributeLayer = "slope" | "width" | "surface" | "walkAccess" | "crosswalk" | "signal" | "audioSignal" | "brailleBlock" | "stairs";
type RouteLineLayer = "safe" | "fast";
const routeAttributeLayerTypes: RouteAttributeLayer[] = ["slope", "width", "surface", "walkAccess", "crosswalk", "signal", "audioSignal", "brailleBlock", "stairs"];
const routeAttributeLegendLayerTypes: RouteAttributeLayer[] = routeAttributeLayerTypes.filter((layer) => layer !== "walkAccess");
const routeEventLayerTypes: RouteAttributeLayer[] = ["crosswalk", "signal", "audioSignal", "brailleBlock", "stairs"];
const routeAttributeLabels: Record<RouteAttributeLayer, string> = {
  slope: "경사도",
  width: "보도 폭",
  surface: "노면",
  walkAccess: "통행 불가",
  crosswalk: "횡단보도",
  signal: "신호등",
  audioSignal: "음향신호기",
  brailleBlock: "점자블록",
  stairs: "계단",
};
const routeAttributeColors: Record<RouteAttributeLayer, string> = {
  slope: "#dc2626",
  width: "#0284c7",
  surface: "#64748b",
  walkAccess: "#111827",
  crosswalk: "#2563eb",
  signal: "#ca8a04",
  audioSignal: "#0f766e",
  brailleBlock: "#7c3aed",
  stairs: "#7c2d12",
};
const routeLineColors: Record<RouteLineLayer, string> = {
  safe: "#dc2626",
  fast: "#2563eb",
};
interface SegmentStyleOverride {
  strokeColor?: string;
  strokeWeight?: number;
  strokeStyle?: string;
  opacity?: number;
  zIndex?: number;
}

export function SegmentMap({
  payload,
  bridgePayload,
  loading,
  error,
  draftEdits,
  onDraftEdit,
  selectedSegment,
  onSelectSegment,
  referenceLayers,
  roadAttributePayload,
  stairPayload,
  audioSignalPayload,
  brailleBlockPayload,
  roadviewContainerRef,
  onRoadviewChange,
  editable = true,
  routePointPickMode = null,
  onRoutePointPick,
  routeLines,
  routePoints,
  focusMarker = null,
  preferredView = null,
  forceDetailedSegments = false,
  toolbarMode = "editor",
  draftEditCount = 0,
  onUndoDraftEdit,
  onClearDraftEdits,
}: SegmentMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const detachWheelZoomRef = useRef<(() => void) | null>(null);
  const roadviewRef = useRef<KakaoRoadview | null>(null);
  const roadviewClientRef = useRef<KakaoRoadviewClient | null>(null);
  const roadviewMarkerRef = useRef<KakaoOverlay | null>(null);
  const roadviewArrowElRef = useRef<HTMLDivElement | null>(null);
  const overlaysRef = useRef<KakaoOverlay[]>([]);
  const tempOverlaysRef = useRef<KakaoOverlay[]>([]);
  const pendingEditOverlaysRef = useRef<KakaoOverlay[]>([]);
  const referenceOverlaysRef = useRef<KakaoOverlay[]>([]);
  const routeOverlaysRef = useRef<KakaoOverlay[]>([]);
  const routePointOverlaysRef = useRef<KakaoOverlay[]>([]);
  const focusMarkerOverlayRef = useRef<KakaoOverlay | null>(null);
  const segmentFeatureOverlaysRef = useRef<KakaoOverlay[]>([]);
  const selectedSegmentOverlayRef = useRef<KakaoOverlay | null>(null);
  const roadAttributeTooltipRef = useRef<KakaoOverlay | null>(null);
  const segmentOverlayByEdgeRef = useRef<Map<string, KakaoOverlay[]>>(new Map());
  const segmentRenderFrameRef = useRef<number | null>(null);
  const segmentFeatureRenderFrameRef = useRef<number | null>(null);
  const polygonShapeRef = useRef<KakaoOverlay | null>(null);
  const centeredPayloadKeyRef = useRef<string | null>(null);
  const draftEditsRef = useRef<EditAction[]>(draftEdits);
  const modeRef = useRef<EditorMode>("idle");
  const addTypeRef = useRef<AddType>("SIDE_LINE");
  const addPointsRef = useRef<Array<[number, number]>>([]);
  const addEndpointSnapsRef = useRef<SnappedSegmentEndpoint[]>([]);
  const polygonPointsRef = useRef<Coord[]>([]);
  const polygonDeleteActiveRef = useRef(false);
  const onDraftEditRef = useRef(onDraftEdit);
  const onSelectSegmentRef = useRef(onSelectSegment);
  const routePointPickModeRef = useRef(routePointPickMode);
  const onRoutePointPickRef = useRef(onRoutePointPick);
  const [mode, setModeState] = useState<EditorMode>("idle");
  const [addType, setAddTypeState] = useState<AddType>("SIDE_LINE");
  const [pendingAddCount, setPendingAddCount] = useState(0);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapReady, setMapReady] = useState(false);
  const [mapLevel, setMapLevel] = useState(6);
  const [viewportBounds, setViewportBounds] = useState<ViewportBounds | null>(null);
  const [detailOverlayRenderProgress, setDetailOverlayRenderProgress] = useState({ total: 0, rendered: 0, active: false });
  const [snapMessage, setSnapMessage] = useState<string | null>(null);
  const [polygonDeleteActive, setPolygonDeleteActive] = useState(false);
  const [polygonPointCount, setPolygonPointCount] = useState(0);
  const [roadSegmentLayers, setRoadSegmentLayers] = useState({
    sideLine: true,
    crossWalk: true,
    transitionConnector: true,
  });
  const [showBridgeGuides, setShowBridgeGuides] = useState(true);
  const [segmentFeatureLayers, setSegmentFeatureLayers] = useState<Record<SegmentFeatureType, boolean>>({
    CROSSWALK: true,
    AUDIO_SIGNAL: true,
    BRAILLE_BLOCK: true,
    STAIRS: true,
  });
  const [routeAttributeLayers, setRouteAttributeLayers] = useState<Record<RouteAttributeLayer, boolean>>({
    slope: true,
    width: true,
    surface: true,
    walkAccess: true,
    crosswalk: true,
    signal: true,
    audioSignal: true,
    brailleBlock: true,
    stairs: true,
  });
  const visibleSegmentCount = payload?.summary?.visibleSegmentCount ?? payload?.segments.features.length ?? 0;
  const forceDetailedSegmentsBlocked = forceDetailedSegments && visibleSegmentCount > FORCED_DETAIL_SEGMENT_MAX_COUNT;
  const detailedSegmentsVisible = (forceDetailedSegments && !forceDetailedSegmentsBlocked) || mapLevel <= DETAIL_SEGMENT_MAX_LEVEL;

  useEffect(() => {
    onDraftEditRef.current = onDraftEdit;
    onSelectSegmentRef.current = onSelectSegment;
    draftEditsRef.current = draftEdits;
    routePointPickModeRef.current = routePointPickMode;
    onRoutePointPickRef.current = onRoutePointPick;
  }, [draftEdits, onDraftEdit, onRoutePointPick, onSelectSegment, routePointPickMode]);

  useEffect(() => {
    if (!editable && (modeRef.current === "add" || modeRef.current === "delete")) {
      setMode("idle");
    }
  }, [editable]);

  useEffect(() => {
    let disposed = false;

    loadKakaoMap()
      .then(() => {
        if (disposed || !containerRef.current || mapRef.current || !window.kakao?.maps) return;
        const center = new window.kakao.maps.LatLng(35.1796, 129.0756);
        mapRef.current = new window.kakao.maps.Map(containerRef.current, {
          center,
          level: 6,
        });
        setMapLevel(mapRef.current.getLevel?.() ?? 6);
        updateViewportBounds();
        detachWheelZoomRef.current?.();
        detachWheelZoomRef.current = attachKakaoWheelZoom(containerRef.current, () => mapRef.current, setMapLevel);
        roadviewClientRef.current = window.kakao.maps.RoadviewClient ? new window.kakao.maps.RoadviewClient() : null;
        setMapReady(true);
        window.kakao.maps.event.addListener(mapRef.current, "click", (event: unknown) => {
          const latLng = (event as { latLng?: { getLng: () => number; getLat: () => number } }).latLng;
          if (!latLng) return;
          const coord: [number, number] = [latLng.getLng(), latLng.getLat()];
          handleMapCoordinate(coord, latLng);
        });
        window.kakao.maps.event.addListener(mapRef.current, "zoom_changed", () => {
          setMapLevel(mapRef.current?.getLevel?.() ?? 6);
          window.setTimeout(updateViewportBounds, 0);
        });
        window.kakao.maps.event.addListener(mapRef.current, "idle", updateViewportBounds);
      })
      .catch((reason: Error) => setMapError(reason.message));

    return () => {
      disposed = true;
      cancelScheduledFrame(segmentRenderFrameRef);
      cancelScheduledFrame(segmentFeatureRenderFrameRef);
      detachWheelZoomRef.current?.();
      detachWheelZoomRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !window.kakao?.maps) return;

    cancelScheduledFrame(segmentRenderFrameRef);
    overlaysRef.current.forEach((overlay) => overlay.setMap(null));
    overlaysRef.current = [];
    segmentOverlayByEdgeRef.current.clear();

    const useHitArea = toolbarMode === "editor" || toolbarMode === "routeAttributeLegend";
    const canRenderDetails = detailedSegmentsVisible;
    const allSegmentFeatures = visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current);
    overlaysRef.current.push(...createAreaBoundaryOverlay(payload?.areaBoundary, mapRef.current));
    const segmentFeatures = canRenderDetails
      ? detailSegmentRenderScope(allSegmentFeatures.filter(shouldShowRoadSegmentLayer), viewportBounds).features
      : [];
    setDetailOverlayRenderProgress({
      total: segmentFeatures.length,
      rendered: segmentFeatures.length && canRenderDetails ? 0 : 0,
      active: canRenderDetails && segmentFeatures.length > DETAIL_SEGMENT_RENDER_BATCH_SIZE,
    });
    scheduleOverlayRenderBatches(
      segmentFeatures,
      segmentRenderFrameRef,
      (feature) => {
        const routeAttributeStyleOverride = toolbarMode === "routeAttributeLegend"
          ? routeAttributeSegmentStyle(feature, routeAttributeLayers)
          : undefined;
        const segmentOverlays = createSegmentOverlay(feature, mapRef.current!, (coord, latLng) => {
          if (routePointPickModeRef.current) {
            handleMapCoordinate(coord, latLng);
            return;
          }
          if (toolbarMode !== "editor") {
            onSelectSegmentRef.current(feature);
            drawSelectedSegment(feature);
            return;
          }
          if (isCoordinatePickMode()) {
            preventMapClickPropagation();
            handleMapCoordinate(coord, latLng);
            return;
          }
          if (modeRef.current !== "delete") {
            handleMapCoordinate(coord, latLng);
            return;
          }
          onDraftEditRef.current({
            action: "delete_segment",
            edgeId: feature.properties.edgeId,
            segmentType: feature.properties.segmentType,
            reason: "ADMIN_click_delete",
          } as EditAction);
        }, { hitArea: useHitArea, style: routeAttributeStyleOverride });
        if (segmentOverlays) {
          overlaysRef.current.push(...segmentOverlays);
          segmentOverlayByEdgeRef.current.set(String(feature.properties.edgeId), segmentOverlays);
        }
      },
      (renderedCount) => {
        setDetailOverlayRenderProgress({
          total: segmentFeatures.length,
          rendered: renderedCount,
          active: renderedCount < segmentFeatures.length,
        });
      },
      syncDeletedSegmentOverlays,
    );

    const bridgeFeatures = showBridgeGuides ? bridgePayload?.bridges.features ?? [] : [];
    bridgeFeatures.forEach((feature) => {
      const bridge = createBridgeOverlay(feature, mapRef.current!);
      if (bridge) overlaysRef.current.push(...bridge);
    });

    centerMapForPayloadOnce(allSegmentFeatures, bridgeFeatures);
    if (canRenderDetails) {
      renderPendingEditOverlays();
    } else {
      clearPendingEditOverlays();
    }
    renderReferenceOverlays();
    renderSegmentFeatureOverlays();
  }, [bridgePayload, detailedSegmentsVisible, focusMarker, mapReady, mode, payload, polygonDeleteActive, preferredView, roadSegmentLayers, routeAttributeLayers, showBridgeGuides, toolbarMode, viewportBounds]);

  useEffect(() => {
    if (detailedSegmentsVisible) {
      renderPendingEditOverlays();
    } else {
      clearPendingEditOverlays();
    }
    syncDeletedSegmentOverlays();
  }, [draftEdits, detailedSegmentsVisible]);

  useEffect(() => {
    renderReferenceOverlays();
  }, [detailedSegmentsVisible, referenceLayers, roadAttributePayload, stairPayload, audioSignalPayload, brailleBlockPayload]);

  useEffect(() => {
    renderRouteOverlays();
  }, [routeLines, mapReady]);

  useEffect(() => {
    renderRoutePointOverlays();
  }, [routePoints, mapReady]);

  useEffect(() => {
    renderFocusMarkerOverlay();
  }, [focusMarker, mapReady]);

  useEffect(() => {
    renderSegmentFeatureOverlays();
  }, [detailedSegmentsVisible, draftEdits, mapReady, payload, routeAttributeLayers, segmentFeatureLayers, toolbarMode, viewportBounds]);

  useEffect(() => {
    if (!selectedSegment || !detailedSegmentsVisible) {
      selectedSegmentOverlayRef.current?.setMap(null);
      selectedSegmentOverlayRef.current = null;
      return;
    }
    drawSelectedSegment(selectedSegment);
  }, [detailedSegmentsVisible, selectedSegment]);

  function setMode(nextMode: EditorMode) {
    if (!editable && (nextMode === "add" || nextMode === "delete")) {
      return;
    }
    modeRef.current = nextMode;
    setModeState(nextMode);
    if (nextMode !== "add") {
      addPointsRef.current = [];
      addEndpointSnapsRef.current = [];
      clearTempOverlays();
      setPendingAddCount(0);
    }
    if (nextMode !== "delete") setPolygonDeleteActiveState(false);
    if (nextMode === "roadview") {
      showRoadviewPanel(ROADVIEW_DEFAULT_MESSAGE);
    }
  }

  function setAddType(nextAddType: AddType) {
    addTypeRef.current = nextAddType;
    setAddTypeState(nextAddType);
    addPointsRef.current = [];
    addEndpointSnapsRef.current = [];
    clearTempOverlays();
    setPendingAddCount(0);
    setSnapMessage(null);
  }

  function shouldRenderDetailedSegments() {
    return detailedSegmentsVisible;
  }

  function handleMapCoordinate(coord: Coord, latLng: unknown) {
    if (routePointPickModeRef.current) {
      onRoutePointPickRef.current?.({ lat: coord[1], lng: coord[0] });
      return;
    }

    if (toolbarMode !== "editor") {
      selectNearestSegment(coord, 35, false);
      return;
    }

    if (!editable && (modeRef.current === "add" || modeRef.current === "delete")) {
      return;
    }

    if (shouldOpenRoadviewForMode(modeRef.current)) {
      showRoadviewAt(latLng);
      return;
    }

    if (modeRef.current === "add") {
      const endpoint = snapAddEndpoint(coord);
      addEndpointSnapsRef.current = [...addEndpointSnapsRef.current, endpoint];
      addPointsRef.current = addEndpointSnapsRef.current.map((item) => item.coord);
      redrawAddPreview();
      setPendingAddCount(addPointsRef.current.length);
      const result = twoPointAddDraft(addTypeRef.current, addPointsRef.current, addEndpointSnapsRef.current);
      if (result.rejectedReason) {
        addPointsRef.current = result.remainingPoints;
        addEndpointSnapsRef.current = [];
        clearTempOverlays();
        setPendingAddCount(result.remainingPoints.length);
        setSnapMessage(result.rejectedReason);
        return;
      }
      if (!result.edit) return;
      onDraftEditRef.current(result.edit);
      addPointsRef.current = result.remainingPoints;
      addEndpointSnapsRef.current = [];
      clearTempOverlays();
      setPendingAddCount(result.remainingPoints.length);
      return;
    }

    if (modeRef.current === "delete" && polygonDeleteActiveRef.current) {
      if (polygonPointsRef.current.length >= 5) return;
      polygonPointsRef.current = [...polygonPointsRef.current, coord];
      redrawPolygon();
    }
  }

  function isCoordinatePickMode() {
    return modeRef.current === "add" || (modeRef.current === "delete" && polygonDeleteActiveRef.current);
  }

  function preventMapClickPropagation() {
    const kakaoEvent = window.kakao?.maps?.event as { preventMap?: () => void } | undefined;
    kakaoEvent?.preventMap?.();
  }

  function clearTempOverlays() {
    tempOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    tempOverlaysRef.current = [];
    polygonShapeRef.current?.setMap(null);
    polygonShapeRef.current = null;
  }

  function drawPoint(coord: Coord, color: string, radius: number, onClick?: (coord: Coord, latLng: unknown) => void): KakaoOverlay | null {
    if (!window.kakao?.maps || !mapRef.current) return null;
    const circle = new window.kakao.maps.Circle({
      map: mapRef.current,
      center: new window.kakao.maps.LatLng(coord[1], coord[0]),
      radius,
      strokeWeight: 2,
      strokeColor: "#ffffff",
      strokeOpacity: 1,
      fillColor: color,
      fillOpacity: 0.95,
      clickable: Boolean(onClick),
    });
    if (onClick) {
      window.kakao.maps.event.addListener(circle, "click", (event: unknown) => {
        preventMapClickPropagation();
        const latLng = (event as { latLng?: unknown }).latLng ?? kakaoLatLngAtCoord(coord);
        onClick(coord, latLng);
      });
    }
    return circle;
  }

  function cancelScheduledFrame(frameRef: RefObject<number | null>) {
    if (frameRef.current === null) return;
    window.cancelAnimationFrame(frameRef.current);
    frameRef.current = null;
  }

  function scheduleOverlayRenderBatches<T>(
    items: T[],
    frameRef: RefObject<number | null>,
    renderItem: (item: T) => void,
    onProgress?: (renderedCount: number) => void,
    onComplete?: () => void,
  ) {
    cancelScheduledFrame(frameRef);
    if (!items.length) {
      onProgress?.(0);
      onComplete?.();
      return;
    }

    let renderedCount = 0;
    const renderBatch = () => {
      const batchEnd = Math.min(renderedCount + DETAIL_SEGMENT_RENDER_BATCH_SIZE, items.length);
      while (renderedCount < batchEnd) {
        renderItem(items[renderedCount]!);
        renderedCount += 1;
      }
      onProgress?.(renderedCount);
      if (renderedCount >= items.length) {
        frameRef.current = null;
        onComplete?.();
        return;
      }
      frameRef.current = window.requestAnimationFrame(renderBatch);
    };

    renderBatch();
  }

  function renderPendingEditOverlays() {
    if (!window.kakao?.maps || !mapRef.current) return;
    clearPendingEditOverlays();

    draftSegmentFeatures(draftEditsRef.current).forEach((feature) => {
      const segmentOverlays = createSegmentOverlay(feature, mapRef.current!, (coord, latLng) => {
        if (!isCoordinatePickMode()) return;
        preventMapClickPropagation();
        handleMapCoordinate(coord, latLng);
      }, { draft: true, hitArea: false });
      if (segmentOverlays) pendingEditOverlaysRef.current.push(...segmentOverlays);
      feature.geometry.coordinates.forEach((coord) => {
        const point = drawPoint(coord, "#ef4444", 2, (pointCoord, latLng) => {
          if (!isCoordinatePickMode()) return;
          handleMapCoordinate(pointCoord, latLng);
        });
        if (point) pendingEditOverlaysRef.current.push(point);
      });
    });
  }

  function renderReferenceOverlays() {
    if (!window.kakao?.maps || !mapRef.current) return;
    referenceOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    referenceOverlaysRef.current = [];
    hideRoadAttributeTooltip();
    if (!shouldRenderDetailedSegments()) return;

    if (referenceLayers?.roadAttributes) {
      (roadAttributePayload?.roadAttributes.features ?? []).filter(shouldShowRoadAttributeReference).forEach((feature) => {
        const overlays = createRoadAttributeReferenceOverlay(feature, mapRef.current!, {
          onClick: (coord) => selectNearestSegment(coord, 50),
          onMouseOver: (coord) => showRoadAttributeTooltip(feature, coord),
          onMouseOut: hideRoadAttributeTooltip,
        }, toolbarMode !== "editor");
        if (overlays) referenceOverlaysRef.current.push(...overlays);
      });
    }

    const pointLayers: Array<[boolean | undefined, ReferencePointFeature[], string]> = [
      [referenceLayers?.stairs, stairPayload?.points.features ?? [], "#7c2d12"],
      [referenceLayers?.audioSignals, audioSignalPayload?.points.features ?? [], "#0f766e"],
      [referenceLayers?.brailleBlocks, brailleBlockPayload?.points.features ?? [], "#7c3aed"],
    ];
    pointLayers.forEach(([enabled, features, color]) => {
      if (!enabled) return;
      features.forEach((feature) => {
        const overlay = createReferencePointOverlay(feature, mapRef.current!, color);
        if (overlay) referenceOverlaysRef.current.push(overlay);
      });
    });
  }

  function shouldShowRoadSegmentLayer(feature: SegmentFeature) {
    if (toolbarMode !== "roadSegmentLegend" && toolbarMode !== "routeAttributeLegend") {
      return true;
    }
    const segmentType = feature.properties.segmentType;
    if (segmentType === "CROSS_WALK") {
      return roadSegmentLayers.crossWalk;
    }
    if (segmentType === "TRANSITION_CONNECTOR") {
      if (toolbarMode === "routeAttributeLegend") {
        return false;
      }
      return roadSegmentLayers.transitionConnector;
    }
    return roadSegmentLayers.sideLine;
  }

  function renderRouteOverlays() {
    if (!window.kakao?.maps || !mapRef.current) return;
    routeOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    routeOverlaysRef.current = [];
    const safeLine = createRoutePolyline(routeLines?.safe ?? [], routeLineColors.safe, {
      strokeWeight: 9,
      strokeOpacity: 0.72,
      zIndex: 32,
    });
    const fastLine = createRoutePolyline(routeLines?.fast ?? [], routeLineColors.fast, {
      strokeWeight: 5,
      strokeOpacity: 0.92,
      zIndex: 34,
    });
    if (safeLine) {
      safeLine.setMap(mapRef.current);
      routeOverlaysRef.current.push(safeLine);
    }
    if (fastLine) {
      fastLine.setMap(mapRef.current);
      routeOverlaysRef.current.push(fastLine);
    }
  }

  function renderRoutePointOverlays() {
    if (!window.kakao?.maps || !mapRef.current) return;
    const map = mapRef.current;
    routePointOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    routePointOverlaysRef.current = [];
    const start = routePoints?.start ? createRoutePointOverlay(routePoints.start, "출발", "start", map) : null;
    const end = routePoints?.end ? createRoutePointOverlay(routePoints.end, "도착", "end", map) : null;
    if (start) routePointOverlaysRef.current.push(start);
    if (end) routePointOverlaysRef.current.push(end);
  }

  function renderFocusMarkerOverlay() {
    if (!window.kakao?.maps || !mapRef.current) return;
    focusMarkerOverlayRef.current?.setMap(null);
    focusMarkerOverlayRef.current = null;
    if (!focusMarker) return;
    focusMarkerOverlayRef.current = createFocusMarkerOverlay(focusMarker.point, focusMarker.label ?? "신고 지점", mapRef.current);
  }

  function renderSegmentFeatureOverlays() {
    if (!window.kakao?.maps || !mapRef.current) return;
    const map = mapRef.current;
    cancelScheduledFrame(segmentFeatureRenderFrameRef);
    segmentFeatureOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    segmentFeatureOverlaysRef.current = [];
    if (!shouldRenderDetailedSegments()) return;
    if (toolbarMode === "routeAttributeLegend") {
      const activeLayers = routeEventLayerTypes.filter((layer) => routeAttributeLayers[layer]);
      if (!activeLayers.length) return;
      const segmentFeatures = detailSegmentRenderScope(
        visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current).filter(shouldShowRoadSegmentLayer),
        viewportBounds,
      ).features;
      scheduleOverlayRenderBatches(segmentFeatures, segmentFeatureRenderFrameRef, (feature) => {
        const overlay = createRouteAttributeEventOverlay(feature, activeLayers);
        if (!overlay) return;
        overlay.setMap(map);
        segmentFeatureOverlaysRef.current.push(overlay);
      });
      return;
    }
    if (toolbarMode !== "segmentFeatureLegend") return;
    const activeTypes = new Set(segmentFeatureTypes.filter((featureType) => segmentFeatureLayers[featureType]));
    if (!activeTypes.size) return;
    const segmentFeatures = detailSegmentRenderScope(
      visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current),
      viewportBounds,
    ).features;
    scheduleOverlayRenderBatches(segmentFeatures, segmentFeatureRenderFrameRef, (feature) => {
      const overlays = createSegmentFeatureOverlays(feature, activeTypes);
      overlays.forEach((overlay) => {
        overlay.setMap(map);
        segmentFeatureOverlaysRef.current.push(overlay);
      });
    });
  }

  function clearPendingEditOverlays() {
    pendingEditOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    pendingEditOverlaysRef.current = [];
  }

  function centerMapForPayloadOnce(segmentFeatures: SegmentFeature[], bridgeFeatures: BridgeFeature[]) {
    const nextKey = mapCenterKey(payload, bridgePayload, segmentFeatures, bridgeFeatures, focusMarker, preferredView);
    if (!nextKey || centeredPayloadKeyRef.current === nextKey) {
      return;
    }
    centeredPayloadKeyRef.current = nextKey;
    centerMapForPayload(segmentFeatures, bridgeFeatures);
  }

  function centerMapForPayload(segmentFeatures: SegmentFeature[], bridgeFeatures: BridgeFeature[]) {
    if (!window.kakao?.maps || !mapRef.current) return;
    if (preferredView) {
      if (typeof preferredView.level === "number" && mapRef.current.setLevel) {
        mapRef.current.setLevel(preferredView.level);
      }
      mapRef.current.setCenter(new window.kakao.maps.LatLng(preferredView.point.lat, preferredView.point.lng));
      window.setTimeout(updateViewportBounds, 0);
      return;
    }

    const bbox = payload?.bbox;
    if (bbox && window.kakao.maps.LatLngBounds && mapRef.current.setBounds) {
      const bounds = new window.kakao.maps.LatLngBounds();
      bounds.extend(new window.kakao.maps.LatLng(bbox[1], bbox[0]));
      bounds.extend(new window.kakao.maps.LatLng(bbox[3], bbox[2]));
      mapRef.current.setBounds(bounds);
      window.setTimeout(updateViewportBounds, 0);
      return;
    }
    const firstCoord = segmentFeatures[0]?.geometry.coordinates[0] ?? bridgeFeatures[0]?.geometry.coordinates[0];
    if (firstCoord) {
      mapRef.current.setCenter(new window.kakao.maps.LatLng(firstCoord[1], firstCoord[0]));
      window.setTimeout(updateViewportBounds, 0);
      return;
    }
    if (focusMarker) {
      mapRef.current.setCenter(new window.kakao.maps.LatLng(focusMarker.point.lat, focusMarker.point.lng));
      window.setTimeout(updateViewportBounds, 0);
    }
  }

  function updateViewportBounds() {
    const nextBounds = readViewportBounds(mapRef.current);
    if (!nextBounds) return;
    setViewportBounds((currentBounds) => areViewportBoundsEqual(currentBounds, nextBounds) ? currentBounds : nextBounds);
  }

  function hideRoadAttributeTooltip() {
    roadAttributeTooltipRef.current?.setMap(null);
    roadAttributeTooltipRef.current = null;
  }

  function showRoadAttributeTooltip(feature: RoadAttributeFeature, position: unknown) {
    if (!window.kakao?.maps || !mapRef.current) return;
    hideRoadAttributeTooltip();
    const content = document.createElement("div");
    content.className = "attribute-tooltip";
    content.innerHTML = `
      <strong>${escapeHtml(feature.properties.sourceId || feature.properties.handoffEdgeId)}</strong>
      <span>${escapeHtml(feature.properties.slopeLevelLabel || feature.properties.slopeLevel || "-")} · ${escapeHtml(feature.properties.widthLevelLabel || "-")} · ${escapeHtml(feature.properties.pavementQualityLabel || feature.properties.surfaceType || "-")}</span>
    `;
    roadAttributeTooltipRef.current = new window.kakao.maps.CustomOverlay({
      map: mapRef.current,
      position,
      content,
      xAnchor: 0.5,
      yAnchor: 1.2,
      zIndex: 40,
    });
    window.setTimeout(() => {
      content.parentElement?.style.setProperty("pointer-events", "none");
    }, 0);
  }

  function selectNearestSegment(coord: Coord, maxDistanceM: number, openRoadview = true) {
    if (!shouldRenderDetailedSegments()) return;
    const candidates = visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current);
    const nearest = candidates
      .map((feature) => ({ feature, distanceM: pointLineDistanceM(coord, feature.geometry.coordinates) }))
      .filter((item) => item.distanceM <= maxDistanceM)
      .sort((left, right) => left.distanceM - right.distanceM)[0];
    if (!nearest) return;
    onSelectSegmentRef.current(nearest.feature);
    drawSelectedSegment(nearest.feature);
    if (openRoadview) showRoadviewAt(kakaoLatLngAtMidpoint(nearest.feature.geometry.coordinates));
  }

  function drawSelectedSegment(feature: SegmentFeature) {
    if (!window.kakao?.maps || !mapRef.current) return;
    selectedSegmentOverlayRef.current?.setMap(null);
    selectedSegmentOverlayRef.current = new window.kakao.maps.Polyline({
      map: mapRef.current,
      path: feature.geometry.coordinates.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng)),
      strokeColor: "#f97316",
      strokeWeight: 10,
      strokeOpacity: 0.95,
      strokeStyle: "solid",
      zIndex: 80,
    });
  }

  function syncDeletedSegmentOverlays() {
    const map = mapRef.current;
    if (!map) return;
    const deleted = deletedEdgeIds(draftEditsRef.current);
    segmentOverlayByEdgeRef.current.forEach((overlays, edgeId) => {
      overlays.forEach((overlay) => overlay.setMap(deleted.has(edgeId) ? null : map));
    });
  }

  function redrawAddPreview() {
    clearTempOverlays();
    addPointsRef.current.forEach((coord, index) => {
      const endpoint = addEndpointSnapsRef.current[index];
      const point = drawPoint(coord, endpoint?.snapped ? "#22c55e" : "#ef4444", endpoint?.snapped ? 2.8 : 2);
      if (point) tempOverlaysRef.current.push(point);
    });
    if (addPointsRef.current.length >= 2) {
      const line = createPolyline(addPointsRef.current, addTypeRef.current, { opacity: 1 });
      if (line) tempOverlaysRef.current.push(line);
    }
  }

  function setPolygonDeleteActiveState(active: boolean) {
    polygonDeleteActiveRef.current = active;
    setPolygonDeleteActive(active);
    polygonPointsRef.current = [];
    setPolygonPointCount(0);
    clearTempOverlays();
  }

  function resetPolygonDeletePoints() {
    const reset = resetPolygonDeleteSelection(polygonDeleteActiveRef.current);
    polygonDeleteActiveRef.current = reset.active;
    setPolygonDeleteActive(reset.active);
    polygonPointsRef.current = reset.points;
    setPolygonPointCount(reset.pointCount);
    clearTempOverlays();
  }

  function redrawPolygon() {
    if (!window.kakao?.maps || !mapRef.current) return;
    clearTempOverlays();
    polygonPointsRef.current.forEach((coord) => {
      const point = drawPoint(coord, "#ef4444", 4);
      if (point) tempOverlaysRef.current.push(point);
    });
    if (polygonPointsRef.current.length >= 2) {
      polygonShapeRef.current = new window.kakao.maps.Polygon({
        map: mapRef.current,
        path: polygonPointsRef.current.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng)),
        strokeColor: "#ef4444",
        strokeWeight: 2,
        strokeOpacity: 0.9,
        fillColor: "#fecaca",
        fillOpacity: 0.2,
      });
    }
    setPolygonPointCount(polygonPointsRef.current.length);
  }

  function deletePolygon() {
    if (polygonPointsRef.current.length < 3) return;
    const candidates = visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current);
    const matches = segmentsTouchingPolygon(candidates, polygonPointsRef.current);
    matches.forEach((feature) => {
      onDraftEditRef.current({
        action: "delete_segment",
        edgeId: feature.properties.edgeId,
        reason: "manual_polygon_delete",
      });
    });
    resetPolygonDeletePoints();
  }

  function closeRoadviewPanel() {
    onRoadviewChange({ open: false, message: ROADVIEW_DEFAULT_MESSAGE });
    roadviewMarkerRef.current?.setMap(null);
    roadviewMarkerRef.current = null;
    roadviewArrowElRef.current = null;
    if (modeRef.current === "roadview") setMode("idle");
  }

  function showRoadviewPanel(message: string) {
    onRoadviewChange({ open: true, message, onClose: closeRoadviewPanel });
  }

  function setRoadviewPanelMessage(message: string) {
    onRoadviewChange({ open: true, message, onClose: closeRoadviewPanel });
  }

  function updateRoadviewMarkerDirection() {
    if (!roadviewRef.current?.getViewpoint || !roadviewArrowElRef.current) return;
    const viewpoint = roadviewRef.current.getViewpoint();
    const pan = Number(viewpoint?.pan || 0);
    roadviewArrowElRef.current.style.transform = `translate(-13px, -13px) rotate(${pan}deg)`;
  }

  function createRoadviewMarker(latLng: unknown) {
    if (!window.kakao?.maps || !mapRef.current) return;
    roadviewMarkerRef.current?.setMap(null);
    const marker = document.createElement("div");
    marker.className = "roadview-arrow-marker";
    const inner = document.createElement("div");
    inner.className = "roadview-arrow-inner";
    marker.appendChild(inner);
    roadviewArrowElRef.current = marker;
    roadviewMarkerRef.current = new window.kakao.maps.CustomOverlay({
      map: mapRef.current,
      position: latLng,
      content: marker,
      xAnchor: 0.5,
      yAnchor: 0.5,
      zIndex: 8,
    });
    window.setTimeout(() => {
      marker.parentElement?.style.setProperty("pointer-events", "none");
    }, 0);
  }

  function showRoadviewAt(latLng: unknown) {
    if (!window.kakao?.maps) return;
    showRoadviewPanel(ROADVIEW_DEFAULT_MESSAGE);
    const unavailable = roadviewUnavailableMessage(Boolean(roadviewClientRef.current), Boolean(window.kakao.maps.Roadview));
    if (unavailable) {
      setRoadviewPanelMessage(unavailable);
      return;
    }
    if (!roadviewRef.current && roadviewContainerRef.current && window.kakao.maps.Roadview) {
      roadviewRef.current = new window.kakao.maps.Roadview(roadviewContainerRef.current);
      window.kakao.maps.event.addListener(roadviewRef.current, "viewpoint_changed", updateRoadviewMarkerDirection);
      window.kakao.maps.event.addListener(roadviewRef.current, "pano_changed", updateRoadviewMarkerDirection);
    }
    if (!roadviewRef.current || !roadviewClientRef.current) {
      setRoadviewPanelMessage("Kakao Roadview is unavailable.");
      return;
    }
    setRoadviewPanelMessage("Searching nearby Roadview...");
    createRoadviewMarker(latLng);
    roadviewClientRef.current.getNearestPanoId(latLng, 80, (panoId) => {
      if (!panoId) {
        setRoadviewPanelMessage("No Kakao Roadview was found near this point.");
        return;
      }
      setRoadviewPanelMessage("");
      roadviewRef.current?.setPanoId(panoId, latLng);
      window.setTimeout(() => {
        roadviewRef.current?.relayout?.();
        updateRoadviewMarkerDirection();
      }, 0);
    });
  }

  function kakaoLatLngAtMidpoint(coords: Coord[]): unknown {
    const coord = coords[Math.floor(coords.length / 2)] ?? coords[0];
    return new window.kakao!.maps.LatLng(coord[1], coord[0]);
  }

  function snapAddEndpoint(coord: Coord): SnappedSegmentEndpoint {
    if (addTypeRef.current === "CROSS_WALK") {
      const snappedCoord = snapCrossWalkEndpoint(coord);
      return { coord: snappedCoord, snapped: false, nodeRef: newNodeRef(snappedCoord) };
    }
    const explicitNodeCandidates = roadNodeCandidates(payload?.roadNodes?.features ?? []);
    const draftNodeCandidates = draftEndpointNodeCandidates(draftEditsRef.current);
    const candidates = explicitNodeCandidates.length
      ? [...explicitNodeCandidates, ...draftNodeCandidates]
      : [
          ...segmentEndpointNodeCandidates(visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current)),
          ...draftNodeCandidates,
        ];
    const endpoint = snapToSegmentEndpointNode(coord, candidates);
    if (!endpoint.snapped) {
      setSnapMessage(null);
      return endpoint;
    }
    setSnapMessage(describeSideLineNodeSnap(String(endpoint.nodeId), endpoint.distanceMeter ?? 0));
    return endpoint;
  }

  function snapCrossWalkEndpoint(coord: Coord): Coord {
    const candidates = visibleSegmentFeatures(payload?.segments.features ?? [], draftEditsRef.current)
      .filter((feature) => {
        const segmentType = feature.properties.segmentType;
        return segmentType === "SIDE_LINE";
      });
    const nearest = nearestPointOnSegments(coord, candidates);
    if (!nearest || nearest.distanceM > 1.5) {
      setSnapMessage(null);
      return coord;
    }
    setSnapMessage(describeCrossWalkProjection(nearest.distanceM));
    return nearest.coord;
  }

  const visibleFeaturesForLegend = visibleSegmentFeatures(payload?.segments.features ?? [], draftEdits);
  const detailVisibleFeatures = detailedSegmentsVisible
    ? visibleFeaturesForLegend.filter(shouldShowRoadSegmentLayer)
    : [];
  const detailRenderStatus = detailSegmentRenderScope(detailVisibleFeatures, viewportBounds);
  const segmentFeatureCounts = countSegmentFeatureTypes(visibleFeaturesForLegend);
  const routeAttributeCounts = countRouteAttributeLayers(visibleFeaturesForLegend);
  const roadSegmentCounts = countRoadSegmentLayers(visibleFeaturesForLegend);
  const bridgeCandidateCount = bridgePayload?.summary?.visibleBridgeCandidateCount ?? bridgePayload?.bridges.features.length ?? 0;

  return (
    <section className="map-shell">
      <div ref={containerRef} className="map-canvas" />
      {toolbarMode === "editor" ? (
        <div className="map-toolbar editor-toolbar">
          <button type="button" className={`toolbar-button ${mode === "delete" ? "selected-tool" : ""}`} onClick={() => setMode("delete")} disabled={!editable}>Delete</button>
          <button type="button" className={`toolbar-button ${mode === "add" ? "selected-tool" : ""}`} onClick={() => setMode("add")} disabled={!editable}>Add</button>
          <select className="toolbar-select" value={addType} onChange={(event) => setAddType(event.target.value as AddType)} disabled={mode !== "add" || !editable}>
            <option value="SIDE_LINE">SIDE_LINE</option>
            <option value="CROSS_WALK">CROSS_WALK</option>
          </select>
          {mode === "delete" && (
            <>
              <button type="button" className={`toolbar-button danger-outline ${polygonDeleteActive ? "selected-tool" : ""}`} onClick={() => setPolygonDeleteActiveState(!polygonDeleteActive)} disabled={!editable}>영역 선택</button>
              <button type="button" className="toolbar-button danger-soft" onClick={deletePolygon} disabled={polygonPointCount < 3 || !editable}>영역 삭제</button>
              {polygonDeleteActive && <span className="toolbar-hint">3~5개 점 선택</span>}
            </>
          )}
          <button type="button" className={`toolbar-button ${mode === "roadview" ? "selected-tool" : ""}`} onClick={() => setMode("roadview")}>Roadview</button>
          <button type="button" className={`toolbar-button guide-button ${showBridgeGuides ? "selected-tool" : ""}`} onClick={() => setShowBridgeGuides((visible) => !visible)}>
            연결 {bridgeCandidateCount}
          </button>
          <span className="draft-count-badge">변경 {draftEditCount}건</span>
          <button type="button" className="toolbar-button" onClick={onUndoDraftEdit} disabled={!draftEditCount || !onUndoDraftEdit}>Undo</button>
          <button type="button" className="toolbar-button" onClick={onClearDraftEdits} disabled={!draftEditCount || !onClearDraftEdits}>Clear</button>
        </div>
      ) : toolbarMode === "roadSegmentLegend" ? (
        <div className="map-toolbar attribute-legend">
          <LegendItem
            color="#c9342f"
            label="SIDE_LINE"
            active={roadSegmentLayers.sideLine}
            onClick={() => setRoadSegmentLayers((layers) => ({ ...layers, sideLine: !layers.sideLine }))}
          />
          <LegendItem
            color="#2563eb"
            label="CROSS_WALK"
            active={roadSegmentLayers.crossWalk}
            onClick={() => setRoadSegmentLayers((layers) => ({ ...layers, crossWalk: !layers.crossWalk }))}
          />
          <LegendItem
            color="#64748b"
            label="TRANSITION_CONNECTOR"
            active={roadSegmentLayers.transitionConnector}
            onClick={() => setRoadSegmentLayers((layers) => ({ ...layers, transitionConnector: !layers.transitionConnector }))}
          />
        </div>
      ) : toolbarMode === "routeAttributeLegend" ? (
        <div className="map-toolbar attribute-legend route-attribute-legend">
          <LegendItem
            color="#c9342f"
            label={`SIDE_LINE ${roadSegmentCounts.sideLine}`}
            active={roadSegmentLayers.sideLine}
            onClick={() => setRoadSegmentLayers((layers) => ({ ...layers, sideLine: !layers.sideLine }))}
          />
          <LegendItem
            color="#2563eb"
            label={`CROSS_WALK ${roadSegmentCounts.crossWalk}`}
            active={roadSegmentLayers.crossWalk}
            onClick={() => setRoadSegmentLayers((layers) => ({ ...layers, crossWalk: !layers.crossWalk }))}
          />
          {routeAttributeLegendLayerTypes.map((layer) => (
            <LegendItem
              key={layer}
              color={routeAttributeColors[layer]}
              label={`${routeAttributeLabels[layer]} ${routeAttributeCounts.get(layer) ?? 0}`}
              active={routeAttributeLayers[layer]}
              onClick={() => setRouteAttributeLayers((layers) => ({ ...layers, [layer]: !layers[layer] }))}
            />
          ))}
        </div>
      ) : (
        <div className="map-toolbar attribute-legend">
          {segmentFeatureTypes.map((featureType) => (
            <LegendItem
              key={featureType}
              color={segmentFeatureColors[featureType]}
              label={`${segmentFeatureLabels[featureType]} ${segmentFeatureCounts.get(featureType) ?? 0}`}
              active={segmentFeatureLayers[featureType]}
              onClick={() => setSegmentFeatureLayers((layers) => ({ ...layers, [featureType]: !layers[featureType] }))}
            />
          ))}
        </div>
      )}
      <div className="map-status">
        {loading
          ? "loading..."
          : error
            ? `payload 오류: ${error.message}`
            : mapError
              ? `지도 오류: ${mapError}`
              : !detailedSegmentsVisible
                ? forceDetailedSegmentsBlocked
                  ? `세그먼트 ${visibleSegmentCount}건이 선택되어 상세 렌더링을 제한했습니다. 제보 주변 범위를 더 좁히거나 확대해서 확인해 주세요.`
                  : `확대하면 보행 네트워크 segment가 표시됩니다. 현재 level ${mapLevel}, 표시 기준 ${DETAIL_SEGMENT_MAX_LEVEL} 이하`
                : detailOverlayRenderProgress.active
                  ? `${detailOverlayRenderProgress.rendered}/${detailOverlayRenderProgress.total} segments rendering`
                : detailRenderStatus.capped
                  ? `${detailRenderStatus.features.length}/${detailRenderStatus.scopedCount} segments 렌더링 · 중요 속성과 화면 중심 우선 표시 중`
                : detailRenderStatus.scopedCount < visibleSegmentCount
                  ? `${detailRenderStatus.features.length}/${visibleSegmentCount} segments 렌더링 · 화면 보호를 위해 일부만 표시 중 · 현재 화면 ${detailRenderStatus.scopedCount}건`
                : `${visibleSegmentCount} segments · ${bridgePayload?.summary?.visibleBridgeCandidateCount ?? bridgePayload?.bridges.features.length ?? 0} bridges · ${mode}${pendingAddCount ? ` · add ${pendingAddCount}` : ""}${polygonDeleteActive ? ` · 영역 ${polygonPointCount}/5점` : ""}${snapMessage ? ` · ${snapMessage}` : ""}`}
      </div>
    </section>
  );
}

function LegendItem({
  color,
  label,
  active,
  onClick,
}: {
  color: string;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button type="button" className={`legend-item legend-toggle ${active ? "active" : ""}`} onClick={onClick}>
      <span style={{ backgroundColor: color }} />
      {label}
    </button>
  );
}

export function detailSegmentRenderScope(features: SegmentFeature[], viewportBounds: ViewportBounds | null) {
  const expandedBounds = viewportBounds ? expandViewportBounds(viewportBounds, DETAIL_SEGMENT_VIEWPORT_PADDING_RATIO) : null;
  const scopedFeatures = expandedBounds
    ? features.filter((feature) => segmentIntersectsBounds(feature, expandedBounds))
    : features;
  const orderedFeatures = orderDetailSegmentFeatures(scopedFeatures, viewportBounds);
  return {
    features: orderedFeatures,
    scopedCount: scopedFeatures.length,
    capped: false,
  };
}

function orderDetailSegmentFeatures(features: SegmentFeature[], viewportBounds: ViewportBounds | null) {
  const center = viewportBounds ? viewportBoundsCenter(viewportBounds) : null;
  return [...features].sort((left, right) => {
    const priorityDelta = detailSegmentPriority(left) - detailSegmentPriority(right);
    if (priorityDelta !== 0) return priorityDelta;
    if (center) {
      const distanceDelta = segmentCenterDistanceSquared(left, center) - segmentCenterDistanceSquared(right, center);
      if (distanceDelta !== 0) return distanceDelta;
    }
    return String(left.properties.edgeId).localeCompare(String(right.properties.edgeId), undefined, { numeric: true });
  });
}

function viewportBoundsCenter(bounds: ViewportBounds): Coord {
  return [
    (bounds.minLng + bounds.maxLng) / 2,
    (bounds.minLat + bounds.maxLat) / 2,
  ];
}

function segmentCenterDistanceSquared(feature: SegmentFeature, center: Coord) {
  const [lng, lat] = midpointCoord(feature.geometry.coordinates);
  const lngScale = Math.max(Math.cos((center[1] * Math.PI) / 180), 0.2);
  const lngDelta = (lng - center[0]) * lngScale;
  const latDelta = lat - center[1];
  return lngDelta * lngDelta + latDelta * latDelta;
}

function detailSegmentPriority(feature: SegmentFeature) {
  const properties = feature.properties;
  if (isYesState(properties.stairsState) || properties.featureTypes?.includes("STAIRS") === true) return 0;
  if (isNoState(properties.walkAccess)) return 1;
  const slope = numberOrNull(properties.avgSlopePercent);
  if (slope !== null && Math.abs(slope) >= 8) return 2;
  if (isNarrowWidth(properties.widthState, properties.widthMeter)) return 3;
  if (isUnpavedSurface(properties.surfaceState)) return 4;
  if (isYesState(properties.audioSignalState) || properties.featureTypes?.includes("AUDIO_SIGNAL") === true) return 5;
  if (isYesState(properties.brailleBlockState) || properties.featureTypes?.includes("BRAILLE_BLOCK") === true) return 6;
  if (isYesState(properties.signalState)) return 7;
  if (properties.segmentType === "CROSS_WALK" || properties.featureTypes?.includes("CROSSWALK") === true) return 8;
  return 9;
}

function readViewportBounds(map: KakaoMap | null): ViewportBounds | null {
  const bounds = map?.getBounds?.() as {
    getSouthWest?: () => { getLng?: () => number; getLat?: () => number };
    getNorthEast?: () => { getLng?: () => number; getLat?: () => number };
  } | null | undefined;
  const southWest = bounds?.getSouthWest?.();
  const northEast = bounds?.getNorthEast?.();
  const minLng = southWest?.getLng?.();
  const minLat = southWest?.getLat?.();
  const maxLng = northEast?.getLng?.();
  const maxLat = northEast?.getLat?.();
  if (
    typeof minLng !== "number"
    || typeof minLat !== "number"
    || typeof maxLng !== "number"
    || typeof maxLat !== "number"
    || !Number.isFinite(minLng)
    || !Number.isFinite(minLat)
    || !Number.isFinite(maxLng)
    || !Number.isFinite(maxLat)
  ) {
    return null;
  }
  return { minLng, minLat, maxLng, maxLat };
}

function areViewportBoundsEqual(left: ViewportBounds | null, right: ViewportBounds) {
  return left !== null
    && Math.abs(left.minLng - right.minLng) < 0.000001
    && Math.abs(left.minLat - right.minLat) < 0.000001
    && Math.abs(left.maxLng - right.maxLng) < 0.000001
    && Math.abs(left.maxLat - right.maxLat) < 0.000001;
}

function expandViewportBounds(bounds: ViewportBounds, ratio: number): ViewportBounds {
  const lngPadding = Math.max((bounds.maxLng - bounds.minLng) * ratio, 0.0008);
  const latPadding = Math.max((bounds.maxLat - bounds.minLat) * ratio, 0.0008);
  return {
    minLng: bounds.minLng - lngPadding,
    minLat: bounds.minLat - latPadding,
    maxLng: bounds.maxLng + lngPadding,
    maxLat: bounds.maxLat + latPadding,
  };
}

function segmentIntersectsBounds(feature: SegmentFeature, bounds: ViewportBounds) {
  const coordinates = feature.geometry.coordinates;
  if (!coordinates.length) return false;
  let minLng = Number.POSITIVE_INFINITY;
  let minLat = Number.POSITIVE_INFINITY;
  let maxLng = Number.NEGATIVE_INFINITY;
  let maxLat = Number.NEGATIVE_INFINITY;
  coordinates.forEach(([lng, lat]) => {
    minLng = Math.min(minLng, lng);
    minLat = Math.min(minLat, lat);
    maxLng = Math.max(maxLng, lng);
    maxLat = Math.max(maxLat, lat);
  });
  return maxLng >= bounds.minLng
    && minLng <= bounds.maxLng
    && maxLat >= bounds.minLat
    && minLat <= bounds.maxLat;
}

function mapCenterKey(
  payload: SegmentPayload | undefined,
  bridgePayload: BridgePayload | undefined,
  segmentFeatures: SegmentFeature[],
  bridgeFeatures: BridgeFeature[],
  focusMarker?: { point: GeoPoint } | null,
  preferredView?: { point: GeoPoint; level?: number } | null,
) {
  if (preferredView) {
    return `${preferredView.point.lng},${preferredView.point.lat},${preferredView.level ?? ""}`;
  }
  const bbox = payload?.bbox ?? bridgePayload?.bbox;
  if (bbox) return bbox.join(",");
  const firstCoord = segmentFeatures[0]?.geometry.coordinates[0] ?? bridgeFeatures[0]?.geometry.coordinates[0];
  if (firstCoord) return firstCoord.join(",");
  return focusMarker ? `${focusMarker.point.lng},${focusMarker.point.lat}` : null;
}

function createRoutePolyline(
  points: GeoPoint[],
  color: string,
  options: {
    strokeWeight: number;
    strokeOpacity: number;
    zIndex: number;
  },
): KakaoOverlay | null {
  if (!window.kakao?.maps || points.length < 2) return null;
  return new window.kakao.maps.Polyline({
    path: points.map((point) => new window.kakao!.maps.LatLng(point.lat, point.lng)),
    strokeWeight: options.strokeWeight,
    strokeColor: color,
    strokeOpacity: options.strokeOpacity,
    strokeStyle: "solid",
    zIndex: options.zIndex,
  });
}

function createRoutePointOverlay(point: GeoPoint, label: string, type: "start" | "end", map: KakaoMap): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;
  const marker = document.createElement("div");
  marker.className = `route-point-marker ${type}`;
  marker.textContent = label;
  return new window.kakao.maps.CustomOverlay({
    map,
    position: new window.kakao.maps.LatLng(point.lat, point.lng),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 1,
    zIndex: 36,
  });
}

function createFocusMarkerOverlay(point: GeoPoint, label: string, map: KakaoMap): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;
  const marker = document.createElement("div");
  marker.className = "route-focus-marker";
  marker.title = label;

  const core = document.createElement("span");
  core.className = "route-focus-marker__core";
  marker.appendChild(core);

  return new window.kakao.maps.CustomOverlay({
    map,
    position: new window.kakao.maps.LatLng(point.lat, point.lng),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 1,
    zIndex: 37,
  });
}

function createAreaBoundaryOverlay(feature: AreaBoundaryFeature | null | undefined, map: KakaoMap): KakaoOverlay[] {
  if (!window.kakao?.maps || !feature) return [];
  return areaBoundaryLineCoordinates(feature).map((coordinates) => new window.kakao!.maps.Polyline({
    map,
    path: coordinates.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng)),
    strokeWeight: 3,
    strokeColor: "#0f766e",
    strokeOpacity: 0.86,
    strokeStyle: "shortdash",
    clickable: false,
    zIndex: 6,
  }));
}

function areaBoundaryLineCoordinates(feature: AreaBoundaryFeature): Coord[][] {
  const coordinates = feature.geometry.coordinates;
  switch (feature.geometry.type) {
    case "LineString":
      return isLineStringCoordinates(coordinates) ? [coordinates] : [];
    case "MultiLineString":
      return isMultiLineStringCoordinates(coordinates) ? coordinates : [];
    case "Polygon":
      return isMultiLineStringCoordinates(coordinates) ? coordinates : [];
    case "MultiPolygon":
      return Array.isArray(coordinates)
        ? coordinates.flatMap((polygon) => isMultiLineStringCoordinates(polygon) ? polygon : [])
        : [];
    default:
      return [];
  }
}

function isLineStringCoordinates(value: unknown): value is Coord[] {
  return Array.isArray(value)
    && value.every((coord) =>
      Array.isArray(coord)
      && coord.length >= 2
      && typeof coord[0] === "number"
      && typeof coord[1] === "number");
}

function isMultiLineStringCoordinates(value: unknown): value is Coord[][] {
  return Array.isArray(value) && value.every(isLineStringCoordinates);
}

function createSegmentFeatureOverlays(feature: SegmentFeature, activeTypes: Set<SegmentFeatureType>): KakaoOverlay[] {
  if (!window.kakao?.maps) return [];
  const matchedTypes = segmentFeatureTypes.filter((featureType) =>
    activeTypes.has(featureType) && feature.properties.featureTypes?.includes(featureType));
  if (!matchedTypes.length) return [];

  const primaryType = primarySegmentFeatureType(matchedTypes);
  const badge = document.createElement("div");
  badge.className = "segment-feature-badge";
  badge.style.backgroundColor = segmentFeatureColors[primaryType];
  badge.title = matchedTypes.map((featureType) => segmentFeatureLabels[featureType]).join(" / ");
  badge.textContent = matchedTypes.length > 1 ? String(matchedTypes.length) : "";
  const badgeCoord = midpointCoord(feature.geometry.coordinates);

  return [new window.kakao.maps.CustomOverlay({
    position: new window.kakao.maps.LatLng(badgeCoord[1], badgeCoord[0]),
    content: badge,
    xAnchor: 0.5,
    yAnchor: 0.5,
    zIndex: 27,
  })];
}

function primarySegmentFeatureType(featureTypes: SegmentFeatureType[]) {
  const priority: SegmentFeatureType[] = ["STAIRS", "BRAILLE_BLOCK", "AUDIO_SIGNAL", "CROSSWALK"];
  return priority.find((featureType) => featureTypes.includes(featureType)) ?? featureTypes[0];
}

function midpointCoord(coords: Coord[]): Coord {
  return coords[Math.floor(coords.length / 2)] ?? coords[0] ?? [0, 0];
}

function createRouteAttributeEventOverlay(feature: SegmentFeature, activeLayers: RouteAttributeLayer[]): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;
  const matchedLayers = activeLayers.filter((layer) => routeAttributeLayerMatches(feature, layer));
  if (!matchedLayers.length) return null;
  const marker = document.createElement("div");
  marker.className = "route-attribute-badge-stack";
  marker.title = matchedLayers.map((layer) => routeAttributeLabels[layer]).join(", ");
  matchedLayers.forEach((layer) => {
    const dot = document.createElement("span");
    dot.style.backgroundColor = routeAttributeColors[layer];
    marker.appendChild(dot);
  });
  const coord = midpointCoord(feature.geometry.coordinates);
  return new window.kakao.maps.CustomOverlay({
    position: new window.kakao.maps.LatLng(coord[1], coord[0]),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 0.5,
    zIndex: 28,
  });
}

function countSegmentFeatureTypes(features: SegmentFeature[]) {
  const counts = new Map<SegmentFeatureType, number>();
  features.forEach((feature) => {
    feature.properties.featureTypes?.forEach((featureType) => {
      counts.set(featureType, (counts.get(featureType) ?? 0) + 1);
    });
  });
  return counts;
}

function countRouteAttributeLayers(features: SegmentFeature[]) {
  const counts = new Map<RouteAttributeLayer, number>();
  features.forEach((feature) => {
    routeAttributeLayerTypes.forEach((layer) => {
      if (routeAttributeLayerMatches(feature, layer)) {
        counts.set(layer, (counts.get(layer) ?? 0) + 1);
      }
    });
  });
  return counts;
}

function countRoadSegmentLayers(features: SegmentFeature[]) {
  return features.reduce(
    (counts, feature) => {
      const segmentType = feature.properties.segmentType;
      if (segmentType === "CROSS_WALK") {
        counts.crossWalk += 1;
      } else if (segmentType === "TRANSITION_CONNECTOR") {
        counts.transitionConnector += 1;
      } else {
        counts.sideLine += 1;
      }
      return counts;
    },
    { sideLine: 0, crossWalk: 0, transitionConnector: 0 },
  );
}

function routeAttributeLayerMatches(feature: SegmentFeature, layer: RouteAttributeLayer) {
  const properties = feature.properties;
  switch (layer) {
    case "slope":
      return numberOrNull(properties.avgSlopePercent) !== null;
    case "width":
      return numberOrNull(properties.widthMeter) !== null || Boolean(properties.widthState);
    case "surface":
      return Boolean(properties.surfaceState);
    case "walkAccess":
      return isNoState(properties.walkAccess);
    case "crosswalk":
      return properties.segmentType === "CROSS_WALK" || properties.featureTypes?.includes("CROSSWALK") === true;
    case "signal":
      return isYesState(properties.signalState);
    case "audioSignal":
      return isYesState(properties.audioSignalState) || properties.featureTypes?.includes("AUDIO_SIGNAL") === true;
    case "brailleBlock":
      return isYesState(properties.brailleBlockState) || properties.featureTypes?.includes("BRAILLE_BLOCK") === true;
    case "stairs":
      return isYesState(properties.stairsState) || properties.featureTypes?.includes("STAIRS") === true;
    default:
      return false;
  }
}

function routeAttributeSegmentStyle(feature: SegmentFeature, layers: Record<RouteAttributeLayer, boolean>): SegmentStyleOverride {
  const properties = feature.properties;
  const style: SegmentStyleOverride = {};
  if (layers.walkAccess && isNoState(properties.walkAccess)) {
    style.strokeColor = "#111827";
    style.opacity = 0.95;
    style.zIndex = 24;
  } else if (layers.slope) {
    const slope = numberOrNull(properties.avgSlopePercent);
    if (slope !== null) {
      style.strokeColor = slopeColor(slope);
      style.opacity = 0.9;
      style.zIndex = 22;
    }
  }
  if (layers.width) {
    style.strokeWeight = widthStrokeWeight(properties.widthState, properties.widthMeter);
  }
  if (layers.surface) {
    style.strokeStyle = surfaceStrokeStyle(properties.surfaceState);
  }
  return style;
}

function isYesState(value: unknown) {
  return String(value ?? "").toUpperCase() === "YES";
}

function isNoState(value: unknown) {
  return String(value ?? "").toUpperCase() === "NO";
}

function numberOrNull(value: unknown) {
  if (value === null || value === undefined || value === "") return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function isNarrowWidth(widthState: unknown, widthMeter: unknown) {
  const state = String(widthState ?? "").toUpperCase();
  if (state.includes("NARROW")) return true;
  const meter = numberOrNull(widthMeter);
  return meter !== null && meter < 1.2;
}

function isUnpavedSurface(surfaceState: unknown) {
  const state = String(surfaceState ?? "").toUpperCase();
  return state.includes("UNPAVED") || state.includes("BAD");
}

function slopeColor(slopePercent: number) {
  const absoluteSlope = Math.abs(slopePercent);
  if (absoluteSlope >= 20) return "#7f1d1d";
  if (absoluteSlope >= 12) return "#dc2626";
  if (absoluteSlope >= 8) return "#f97316";
  if (absoluteSlope >= 4) return "#f59e0b";
  return "#16a34a";
}

function widthStrokeWeight(widthState: unknown, widthMeter: unknown) {
  const state = String(widthState ?? "").toUpperCase();
  if (state.includes("ADEQUATE_150")) return 8;
  if (state.includes("ADEQUATE_120")) return 6;
  if (state.includes("NARROW")) return 3;
  const meter = numberOrNull(widthMeter);
  if (meter !== null) {
    if (meter >= 1.5) return 8;
    if (meter >= 1.2) return 6;
    return 3;
  }
  return 4;
}

function surfaceStrokeStyle(surfaceState: unknown) {
  const state = String(surfaceState ?? "").toUpperCase();
  if (state.includes("UNPAVED") || state.includes("BAD")) return "shortdash";
  if (state.includes("UNKNOWN")) return "shortdot";
  return "solid";
}

function createBridgeOverlay(feature: BridgeFeature, map: KakaoMap): KakaoOverlay[] | null {
  if (!window.kakao?.maps) return null;

  const path = feature.geometry.coordinates.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng));
  const line = new window.kakao.maps.Polyline({
    path,
    strokeWeight: feature.properties.priority === "AUTO" ? 4 : 3,
    strokeColor: "#60a5fa",
    strokeOpacity: 0.92,
    strokeStyle: "shortdash",
  });
  line.setMap(map);

  const markerPoint = feature.properties.markerPoint ?? feature.geometry.coordinates[0];
  const marker = new window.kakao.maps.Circle({
    map,
    center: new window.kakao.maps.LatLng(markerPoint[1], markerPoint[0]),
    radius: 4 / 3,
    strokeWeight: 2,
    strokeColor: "#ffffff",
    strokeOpacity: 1,
    fillColor: "#2563eb",
    fillOpacity: 0.95,
  });

  return [line, marker];
}

function createRoadAttributeReferenceOverlay(
  feature: RoadAttributeFeature,
  map: KakaoMap,
  handlers: {
    onClick: (coord: Coord) => void;
    onMouseOver: (position: unknown) => void;
    onMouseOut: () => void;
  },
  interactive: boolean,
): KakaoOverlay[] | null {
  if (!window.kakao?.maps) return null;
  const path = feature.geometry.coordinates.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng));
  const visibleLine = new window.kakao.maps.Polyline({
    map,
    path,
    strokeWeight: Math.max(3, roadAttributeStrokeWeight(feature.properties.widthLevel) + 2),
    strokeColor: roadAttributeStrokeColor(feature.properties.slopeLevel),
    strokeOpacity: 0.22,
    strokeStyle: roadAttributeStrokeStyle(feature.properties.surfaceType),
    clickable: false,
    zIndex: 1,
  });

  if (!interactive) return [visibleLine];

  const hitLine = new window.kakao.maps.Polyline({
    map,
    path,
    strokeWeight: Math.max(14, roadAttributeStrokeWeight(feature.properties.widthLevel) + 10),
    strokeColor: roadAttributeStrokeColor(feature.properties.slopeLevel),
    strokeOpacity: 0.01,
    strokeStyle: "solid",
    clickable: true,
    zIndex: 19,
  });

  window.kakao.maps.event.addListener(hitLine, "click", (event: unknown) => {
    const latLng = (event as { latLng?: { getLng: () => number; getLat: () => number } }).latLng;
    if (!latLng) return;
    handlers.onClick([latLng.getLng(), latLng.getLat()]);
  });
  window.kakao.maps.event.addListener(hitLine, "mouseover", (event: unknown) => {
    const position = (event as { latLng?: unknown }).latLng ?? path[Math.floor(path.length / 2)];
    handlers.onMouseOver(position);
  });
  window.kakao.maps.event.addListener(hitLine, "mouseout", handlers.onMouseOut);
  return [visibleLine, hitLine];
}

function createReferencePointOverlay(feature: ReferencePointFeature, map: KakaoMap, color: string): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;
  const marker = document.createElement("span");
  marker.className = "reference-point-marker";
  marker.style.backgroundColor = color;
  marker.title = `${feature.properties.label} ${feature.properties.sourceId}`;
  return new window.kakao.maps.CustomOverlay({
    map,
    position: new window.kakao.maps.LatLng(feature.geometry.coordinates[1], feature.geometry.coordinates[0]),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 0.5,
    zIndex: 12,
  });
}

function createSegmentOverlay(
  feature: SegmentFeature,
  map: KakaoMap,
  onClick: (coord: Coord, latLng: unknown) => void,
  options: { draft?: boolean; hitArea?: boolean; style?: SegmentStyleOverride } = {},
): KakaoOverlay[] | null {
  const line = createPolyline(feature.geometry.coordinates, feature.properties.segmentType ?? "SIDE_LINE", {
    draft: options.draft,
    opacity: options.draft ? 0.98 : undefined,
    ...options.style,
  });
  if (!line || !window.kakao?.maps) return null;
  const overlays = [line];
  const handleClick = (event: unknown) => {
    const latLng = (event as { latLng?: { getLng: () => number; getLat: () => number } }).latLng ?? kakaoLatLngAtCoord(feature.geometry.coordinates[Math.floor(feature.geometry.coordinates.length / 2)] ?? feature.geometry.coordinates[0]);
    onClick([latLng.getLng(), latLng.getLat()], latLng);
  };
  window.kakao.maps.event.addListener(line, "click", handleClick);

  if (options.hitArea !== false) {
    const path = feature.geometry.coordinates.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng));
    const hitLine = new window.kakao.maps.Polyline({
      path,
      strokeWeight: feature.properties.segmentType === "CROSS_WALK" ? 18 : 20,
      strokeColor: "#111827",
      strokeOpacity: 0.01,
      strokeStyle: "solid",
      clickable: true,
      zIndex: 21,
    });
    window.kakao.maps.event.addListener(hitLine, "click", handleClick);
    hitLine.setMap(map);
    overlays.push(hitLine);
  }

  line.setMap(map);
  return overlays;
}

function kakaoLatLngAtCoord(coord: Coord): { getLng: () => number; getLat: () => number } {
  const latLng = new window.kakao!.maps.LatLng(coord[1], coord[0]);
  return latLng as { getLng: () => number; getLat: () => number };
}

function createPolyline(
  coordinates: Coord[],
  segmentType: string,
  options: { draft?: boolean; opacity?: number } & SegmentStyleOverride = {},
): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;

  const path = coordinates.map(([lng, lat]) => new window.kakao!.maps.LatLng(lat, lng));
  const isCrossWalk = segmentType === "CROSS_WALK";
  const strokeColor = options.strokeColor ?? (isCrossWalk ? "#2563eb" : segmentType === "TRANSITION_CONNECTOR" ? "#64748b" : "#c9342f");
  const strokeWeight = options.strokeWeight ?? (isCrossWalk ? 5 : 4);
  const opacity = options.opacity ?? (segmentType === "TRANSITION_CONNECTOR" ? 0.45 : 0.88);
  const zIndex = options.zIndex ?? (options.draft ? 18 : segmentType === "TRANSITION_CONNECTOR" ? 8 : isCrossWalk ? 16 : 14);

  return new window.kakao.maps.Polyline({
    path,
    strokeWeight,
    strokeColor,
    strokeOpacity: opacity,
    strokeStyle: options.strokeStyle ?? (options.draft ? "shortdash" : "solid"),
    clickable: true,
    zIndex,
  });
}

function nearestPointOnSegments(point: Coord, segments: SegmentFeature[]): { coord: Coord; distanceM: number } | null {
  let nearest: { coord: Coord; distanceM: number } | null = null;
  segments.forEach((feature) => {
    const coordinates = feature.geometry.coordinates;
    coordinates.slice(1).forEach((coord, index) => {
      const projected = nearestPointOnLineSegment(point, coordinates[index], coord);
      if (!nearest || projected.distanceM < nearest.distanceM) {
        nearest = projected;
      }
    });
  });
  return nearest;
}

function nearestPointOnLineSegment(point: Coord, start: Coord, end: Coord): { coord: Coord; distanceM: number } {
  const originLat = point[1];
  const pointMeters = lngLatToLocalMeters(point, originLat);
  const startMeters = lngLatToLocalMeters(start, originLat);
  const endMeters = lngLatToLocalMeters(end, originLat);
  const dx = endMeters.x - startMeters.x;
  const dy = endMeters.y - startMeters.y;
  const lengthSquared = dx * dx + dy * dy;
  const t = lengthSquared === 0
    ? 0
    : Math.max(0, Math.min(1, ((pointMeters.x - startMeters.x) * dx + (pointMeters.y - startMeters.y) * dy) / lengthSquared));
  const projectedMeters = {
    x: startMeters.x + dx * t,
    y: startMeters.y + dy * t,
  };
  return {
    coord: localMetersToLngLat(projectedMeters, originLat),
    distanceM: Math.hypot(pointMeters.x - projectedMeters.x, pointMeters.y - projectedMeters.y),
  };
}

function lngLatToLocalMeters([lng, lat]: Coord, originLat: number) {
  const metersPerDegreeLat = 111_320;
  const metersPerDegreeLng = 111_320 * Math.cos((originLat * Math.PI) / 180);
  return {
    x: lng * metersPerDegreeLng,
    y: lat * metersPerDegreeLat,
  };
}

function localMetersToLngLat(point: { x: number; y: number }, originLat: number): Coord {
  const metersPerDegreeLat = 111_320;
  const metersPerDegreeLng = 111_320 * Math.cos((originLat * Math.PI) / 180);
  return [point.x / metersPerDegreeLng, point.y / metersPerDegreeLat];
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
