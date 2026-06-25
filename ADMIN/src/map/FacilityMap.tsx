import { type RefObject, useEffect, useRef, useState } from "react";
import type { AccessibilityFeatureType, AdminPlaceDetailResponse, AreaBoundaryFeature, FacilityFeature, FacilityPayload, PlaceCategory } from "../types";
import { attachKakaoWheelZoom, loadKakaoMap, type KakaoMap, type KakaoOverlay, type KakaoRoadview, type KakaoRoadviewClient } from "./kakaoLoader";
import { facilityCategoryColor, facilityCategoryLabel, facilityCategoryOrder } from "./facilityStyle";
import { roadviewUnavailableMessage } from "./roadviewMode";
import type { RoadviewDockState } from "./SegmentMap";

type Coord = [number, number];

interface FacilityMapProps {
  payload?: FacilityPayload;
  loading: boolean;
  error?: Error | null;
  selectedFeature: FacilityFeature | null;
  selectedDetail?: AdminPlaceDetailResponse;
  selectedCategories?: readonly PlaceCategory[];
  onSelectFeature: (feature: FacilityFeature) => void;
  onClearSelection?: () => void;
  roadviewContainerRef: RefObject<HTMLDivElement | null>;
  onRoadviewChange: (state: RoadviewDockState) => void;
  locationPickEnabled?: boolean;
  onPickLocation?: (point: { lat: number; lng: number }) => void;
}

const ROADVIEW_DEFAULT_MESSAGE = "";
const ROADVIEW_MAP_CLICK_MESSAGE = "";

export function FacilityMap({
  payload,
  loading,
  error,
  selectedFeature,
  selectedDetail,
  selectedCategories = facilityCategoryOrder,
  onSelectFeature,
  onClearSelection,
  roadviewContainerRef,
  onRoadviewChange,
  locationPickEnabled = false,
  onPickLocation,
}: FacilityMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const detachWheelZoomRef = useRef<(() => void) | null>(null);
  const roadviewRef = useRef<KakaoRoadview | null>(null);
  const roadviewClientRef = useRef<KakaoRoadviewClient | null>(null);
  const roadviewMarkerRef = useRef<KakaoOverlay | null>(null);
  const roadviewArrowElRef = useRef<HTMLDivElement | null>(null);
  const overlaysRef = useRef<KakaoOverlay[]>([]);
  const selectedOverlayRef = useRef<KakaoOverlay | null>(null);
  const tooltipRef = useRef<KakaoOverlay | null>(null);
  const onSelectFeatureRef = useRef(onSelectFeature);
  const locationPickEnabledRef = useRef(locationPickEnabled);
  const onPickLocationRef = useRef(onPickLocation);
  const centeredPayloadKeyRef = useRef<string | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapReady, setMapReady] = useState(false);

  useEffect(() => {
    onSelectFeatureRef.current = onSelectFeature;
    locationPickEnabledRef.current = locationPickEnabled;
    onPickLocationRef.current = onPickLocation;
  }, [locationPickEnabled, onPickLocation, onSelectFeature]);

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
        detachWheelZoomRef.current?.();
        detachWheelZoomRef.current = attachKakaoWheelZoom(containerRef.current, () => mapRef.current);
        roadviewClientRef.current = window.kakao.maps.RoadviewClient ? new window.kakao.maps.RoadviewClient() : null;
        setMapReady(true);
        window.kakao.maps.event.addListener(mapRef.current, "click", (event: unknown) => {
          const latLng = (event as { latLng?: { getLng: () => number; getLat: () => number } }).latLng;
          if (!latLng) return;
          if (locationPickEnabledRef.current) {
            onPickLocationRef.current?.({ lat: latLng.getLat(), lng: latLng.getLng() });
            return;
          }
          showRoadviewAt(latLng, ROADVIEW_MAP_CLICK_MESSAGE);
        });
      })
      .catch((reason: Error) => setMapError(reason.message));

    return () => {
      disposed = true;
      detachWheelZoomRef.current?.();
      detachWheelZoomRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !window.kakao?.maps) return;

    overlaysRef.current.forEach((overlay) => overlay.setMap(null));
    overlaysRef.current = [];
    selectedOverlayRef.current?.setMap(null);
    selectedOverlayRef.current = null;
    tooltipRef.current?.setMap(null);
    tooltipRef.current = null;

    overlaysRef.current.push(...createAreaBoundaryOverlay(payload?.areaBoundary, mapRef.current));
    const features = payload?.facilities.features ?? [];
    features.forEach((feature) => {
      const overlay = createFacilityOverlay(feature, mapRef.current!, {
        onClick: () => {
          onSelectFeatureRef.current(feature);
          drawSelectedFeature(feature);
          showRoadviewAt(kakaoLatLng(feature));
        },
        onMouseOver: () => showTooltip(feature),
        onMouseOut: hideTooltip,
      });
      if (overlay) overlaysRef.current.push(overlay);
    });
    if (selectedFeature && selectedCategories.includes(selectedFeature.properties.category)) {
      drawSelectedFeature(selectedFeature);
    }

    centerMapForPayloadOnce(features);
  }, [mapReady, payload, selectedCategories, selectedFeature]);

  useEffect(() => {
    if (!mapReady) return;
    if (!selectedFeature) {
      selectedOverlayRef.current?.setMap(null);
      selectedOverlayRef.current = null;
      return;
    }
    drawSelectedFeature(selectedFeature);
  }, [mapReady, selectedFeature]);

  useEffect(() => {
    if (!mapReady || !containerRef.current) return;

    let frame = 0;
    const relayout = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        relayoutMap();
      });
    };
    const timers = [0, 120, 360, 800].map((delay) => window.setTimeout(relayout, delay));

    const observer = new ResizeObserver(relayout);
    observer.observe(containerRef.current);
    window.addEventListener("resize", relayout);

    return () => {
      timers.forEach((timer) => window.clearTimeout(timer));
      observer.disconnect();
      window.removeEventListener("resize", relayout);
      window.cancelAnimationFrame(frame);
    };
  }, [mapReady]);

  function relayoutMap() {
    const map = mapRef.current;
    if (!map) return;
    const center = map.getCenter?.();
    map.relayout?.();
    if (center) {
      map.setCenter(center);
    }
  }

  function queueMapRelayout() {
    window.requestAnimationFrame(() => {
      relayoutMap();
      window.setTimeout(relayoutMap, 140);
    });
  }

  function hideTooltip() {
    tooltipRef.current?.setMap(null);
    tooltipRef.current = null;
  }

  function showTooltip(feature: FacilityFeature) {
    if (!window.kakao?.maps || !mapRef.current) return;
    hideTooltip();
    const content = document.createElement("div");
    content.className = "attribute-tooltip";
    content.innerHTML = `
      <strong>${escapeHtml(feature.properties.name || feature.properties.placeId)}</strong>
      <span>${escapeHtml(facilityCategoryLabel(feature.properties.category))} · ${escapeHtml(feature.properties.address || "-")}</span>
    `;
    tooltipRef.current = new window.kakao.maps.CustomOverlay({
      map: mapRef.current,
      position: kakaoLatLng(feature),
      content,
      xAnchor: 0.5,
      yAnchor: 1.35,
      zIndex: 30,
    });
  }

  function drawSelectedFeature(feature: FacilityFeature) {
    if (!window.kakao?.maps || !mapRef.current) return;
    selectedOverlayRef.current?.setMap(null);
    selectedOverlayRef.current = new window.kakao.maps.Circle({
      map: mapRef.current,
      center: kakaoLatLng(feature),
      radius: 18,
      strokeWeight: 3,
      strokeColor: "#111827",
      strokeOpacity: 0.95,
      fillColor: "#ffffff",
      fillOpacity: 0.25,
      zIndex: 20,
    });
  }

  function centerMapForPayloadOnce(features: FacilityFeature[]) {
    if (!window.kakao?.maps || !mapRef.current) return;
    const bbox = payload?.bbox;
    const firstCoord = features[0]?.geometry.coordinates;
    const nextKey = bbox ? bbox.join(",") : firstCoord?.join(",");
    if (!nextKey || centeredPayloadKeyRef.current === nextKey) {
      return;
    }
    centeredPayloadKeyRef.current = nextKey;
    if (bbox && window.kakao.maps.LatLngBounds && mapRef.current.setBounds) {
      const bounds = new window.kakao.maps.LatLngBounds();
      bounds.extend(new window.kakao.maps.LatLng(bbox[1], bbox[0]));
      bounds.extend(new window.kakao.maps.LatLng(bbox[3], bbox[2]));
      mapRef.current.setBounds(bounds);
      queueMapRelayout();
      return;
    }
    if (firstCoord) {
      mapRef.current.setCenter(new window.kakao.maps.LatLng(firstCoord[1], firstCoord[0]));
      queueMapRelayout();
    }
  }

  function closeRoadviewPanel() {
    onRoadviewChange({ open: false, message: ROADVIEW_DEFAULT_MESSAGE });
    roadviewMarkerRef.current?.setMap(null);
    roadviewMarkerRef.current = null;
    roadviewArrowElRef.current = null;
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
  }

  function showRoadviewAt(latLng: unknown, initialMessage = ROADVIEW_DEFAULT_MESSAGE) {
    if (!window.kakao?.maps) return;
    setRoadviewPanelMessage(initialMessage);
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

  function kakaoLatLng(feature: FacilityFeature): unknown {
    const [lng, lat] = feature.geometry.coordinates;
    return new window.kakao!.maps.LatLng(lat, lng);
  }

  const visibleCounts = payload?.summary?.visibleCategoryCounts ?? {};
  const visibleCategories = facilityCategoryOrder.filter((category) => (visibleCounts[category] ?? 0) > 0);
  const legendCategories = selectedCategories.length > 0 ? selectedCategories : visibleCategories;

  return (
    <section className="map-shell">
      <div ref={containerRef} className="map-canvas" />
      <div className="map-toolbar attribute-legend facility-map-legend">
        <strong>선택 카테고리 {selectedCategories.length}개</strong>
        {legendCategories.map((category) => (
          <LegendItem key={category} color={facilityCategoryColor(category)} label={`${facilityCategoryLabel(category)} ${visibleCounts[category] ?? 0}개`} />
        ))}
      </div>
      {selectedFeature && (
        <SelectedFacilityMapCard
          feature={selectedFeature}
          detail={selectedDetail}
          onClose={onClearSelection}
        />
      )}
      <div className="map-status">
        {loading
          ? "loading..."
          : error
            ? `편의시설 오류: ${error.message}`
            : mapError
              ? `지도 오류: ${mapError}`
              : `표시 시설 ${payload?.summary?.visibleFacilityCount ?? payload?.facilities.features.length ?? 0}개${selectedFeature ? ` · 선택 ${selectedFeature.properties.placeId}` : ""}`}
      </div>
    </section>
  );
}

function SelectedFacilityMapCard({
  feature,
  detail,
  onClose,
}: {
  feature: FacilityFeature;
  detail?: AdminPlaceDetailResponse;
  onClose?: () => void;
}) {
  const availableFeatures = detail?.accessibilityFeatures
    .filter((item) => item.isAvailable)
    .slice(0, 4) ?? [];

  return (
    <article className="facility-selected-map-card">
      <div className="facility-selected-map-card__thumb" aria-hidden="true">
        <span>{facilityCategoryLabel(feature.properties.category).slice(0, 1)}</span>
      </div>
      <div className="facility-selected-map-card__content">
        <div className="facility-selected-map-card__title-row">
          <strong>{detail?.name ?? (feature.properties.name || `place ${feature.properties.placeId}`)}</strong>
          <span style={{ color: facilityCategoryColor(detail?.category ?? feature.properties.category) }}>
            {facilityCategoryLabel(detail?.category ?? feature.properties.category)}
          </span>
        </div>
        <p>{detail?.address ?? (feature.properties.address || "-")}</p>
        <div className="facility-selected-feature-row">
          {availableFeatures.length > 0
            ? availableFeatures.map((item) => (
              <span key={item.featureType}>{facilityMapFeatureLabel(item.featureType)}</span>
            ))
            : <span>상세정보 수정 패널에서 접근성 정보를 확인하세요</span>}
        </div>
      </div>
      {onClose && (
        <button className="facility-selected-map-card__close" type="button" aria-label="선택 시설 닫기" onClick={onClose}>
          x
        </button>
      )}
    </article>
  );
}

function createFacilityOverlay(
  feature: FacilityFeature,
  map: KakaoMap,
  handlers: {
    onClick: () => void;
    onMouseOver: () => void;
    onMouseOut: () => void;
  },
): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;

  const marker = document.createElement("button");
  marker.type = "button";
  marker.className = "facility-marker";
  marker.style.backgroundColor = facilityCategoryColor(feature.properties.category);
  marker.title = feature.properties.name;
  marker.addEventListener("click", handlers.onClick);
  marker.addEventListener("mouseenter", handlers.onMouseOver);
  marker.addEventListener("mouseleave", handlers.onMouseOut);

  const overlay = new window.kakao.maps.CustomOverlay({
    map,
    position: new window.kakao.maps.LatLng(feature.geometry.coordinates[1], feature.geometry.coordinates[0]),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 0.5,
    zIndex: 10,
  });
  return overlay;
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

function LegendItem({ color, label, active = true, onClick }: { color: string; label: string; active?: boolean; onClick?: () => void }) {
  const content = (
    <>
      <span style={{ background: color }} />
      {label}
    </>
  );

  if (onClick) {
    return (
      <button type="button" className={`legend-item legend-toggle ${active ? "active" : ""}`} onClick={onClick}>
        {content}
      </button>
    );
  }

  return <span className={`legend-item ${active ? "active" : ""}`}>{content}</span>;
}

function facilityMapFeatureLabel(featureType: AccessibilityFeatureType) {
  switch (featureType) {
    case "accessibleEntrance":
      return "단차 없는 출입";
    case "elevator":
      return "엘리베이터";
    case "accessibleToilet":
      return "장애인 화장실";
    case "accessibleParking":
      return "장애인 주차";
    case "chargingStation":
      return "충전 가능";
    case "accessibleRoom":
      return "객실 이용";
    case "guidanceFacility":
      return "안내시설";
  }
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
