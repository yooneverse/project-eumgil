import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { attachKakaoWheelZoom, loadKakaoMap, type KakaoMap, type KakaoOverlay } from "./kakaoLoader";
import {
  bottleneckLayerMode,
  bottleneckFillColor,
  bottleneckRadiusMeter,
  bottleneckStrokeColor,
  collectBottleneckBoundsPoints,
  speedBand,
  type BottleneckHotspot,
  type BottleneckLayerMode,
  type BottleneckRouteSegment,
} from "./adminBottleneckMap";
import type { GeoPoint } from "../types";

interface AdminBottleneckKakaoMapProps {
  hotspots: BottleneckHotspot[];
  routeSegments: BottleneckRouteSegment[];
  presentationMode?: "heatmap" | "segment";
}

export function AdminBottleneckKakaoMap({
  hotspots,
  routeSegments,
  presentationMode = "heatmap",
}: AdminBottleneckKakaoMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const heatCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const overlaysRef = useRef<KakaoOverlay[]>([]);
  const detachWheelZoomRef = useRef<(() => void) | null>(null);
  const fittedBoundsKeyRef = useRef("");
  const heatmapRafRef = useRef<number | null>(null);
  const heatCanvasPanLayerRef = useRef<HTMLElement | null>(null);
  const heatCanvasBasePanRef = useRef({ x: 0, y: 0 });
  const [mapReady, setMapReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapLevel, setMapLevel] = useState(6);
  const [renderVersion, setRenderVersion] = useState(0);

  const layerMode = useMemo(() => {
    const baseMode = bottleneckLayerMode(mapLevel);
    if (presentationMode === "segment" && baseMode === "cluster") {
      return "summary";
    }
    return baseMode;
  }, [mapLevel, presentationMode]);
  const boundsPoints = useMemo(
    () => collectBottleneckBoundsPoints(hotspots, routeSegments),
    [hotspots, routeSegments],
  );
  const boundsKey = useMemo(
    () => boundsPoints.map((point) => `${point.lat.toFixed(6)},${point.lng.toFixed(6)}`).join("|"),
    [boundsPoints],
  );

  function scheduleHeatmapRender() {
    if (heatmapRafRef.current != null) return;
    heatmapRafRef.current = window.requestAnimationFrame(() => {
      heatmapRafRef.current = null;
      setRenderVersion((value) => value + 1);
    });
  }

  function resolveHeatCanvasPanLayer() {
    const container = containerRef.current;
    const rootLayer = container?.firstElementChild;
    const panLayer = rootLayer?.firstElementChild;
    return panLayer instanceof HTMLElement ? panLayer : null;
  }

  function readPanLayerOffset(element: HTMLElement | null) {
    if (!element) return null;
    const x = Number.parseFloat(element.style.left || "0");
    const y = Number.parseFloat(element.style.top || "0");
    if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
    return { x, y };
  }

  function captureHeatCanvasBasePan() {
    if (!heatCanvasPanLayerRef.current || !heatCanvasPanLayerRef.current.isConnected) {
      heatCanvasPanLayerRef.current = resolveHeatCanvasPanLayer();
    }
    const offset = readPanLayerOffset(heatCanvasPanLayerRef.current);
    heatCanvasBasePanRef.current = offset ?? { x: 0, y: 0 };
  }

  function syncHeatCanvasPanTransform() {
    const canvas = heatCanvasRef.current;
    if (!canvas) return;

    if (!heatCanvasPanLayerRef.current || !heatCanvasPanLayerRef.current.isConnected) {
      heatCanvasPanLayerRef.current = resolveHeatCanvasPanLayer();
    }

    const panLayer = heatCanvasPanLayerRef.current;
    if (!panLayer) {
      canvas.style.transform = "";
      return;
    }

    const currentOffset = readPanLayerOffset(panLayer);
    if (!currentOffset) {
      canvas.style.transform = "";
      return;
    }

    const deltaX = currentOffset.x - heatCanvasBasePanRef.current.x;
    const deltaY = currentOffset.y - heatCanvasBasePanRef.current.y;
    if (Math.abs(deltaX) < 0.5 && Math.abs(deltaY) < 0.5) {
      canvas.style.transform = "";
      return;
    }

    canvas.style.transform = `translate(${deltaX}px, ${deltaY}px)`;
  }

  function resetHeatCanvasPanTransform() {
    if (heatCanvasRef.current) {
      heatCanvasRef.current.style.transform = "";
    }
  }

  useEffect(() => {
    let disposed = false;

    loadKakaoMap()
      .then(() => {
        if (disposed || !containerRef.current || mapRef.current || !window.kakao?.maps) return;
        const center = new window.kakao.maps.LatLng(35.1089, 129.035);
        mapRef.current = new window.kakao.maps.Map(containerRef.current, {
          center,
          level: 6,
        });
        setMapLevel(mapRef.current.getLevel?.() ?? 6);
        detachWheelZoomRef.current?.();
        detachWheelZoomRef.current = attachKakaoWheelZoom(containerRef.current, () => mapRef.current, setMapLevel);
        window.kakao.maps.event.addListener(mapRef.current, "zoom_changed", () => {
          setMapLevel(mapRef.current?.getLevel?.() ?? 6);
          resetHeatCanvasPanTransform();
          scheduleHeatmapRender();
        });
        window.kakao.maps.event.addListener(mapRef.current, "center_changed", () => {
          syncHeatCanvasPanTransform();
        });
        window.kakao.maps.event.addListener(mapRef.current, "drag", () => {
          syncHeatCanvasPanTransform();
        });
        window.kakao.maps.event.addListener(mapRef.current, "idle", () => {
          heatCanvasPanLayerRef.current = resolveHeatCanvasPanLayer();
          captureHeatCanvasBasePan();
          scheduleHeatmapRender();
        });
        window.requestAnimationFrame(() => {
          heatCanvasPanLayerRef.current = resolveHeatCanvasPanLayer();
          captureHeatCanvasBasePan();
        });
        setMapReady(true);
      })
      .catch((reason: Error) => setMapError(reason.message));

    return () => {
      disposed = true;
      if (heatmapRafRef.current != null) {
        window.cancelAnimationFrame(heatmapRafRef.current);
        heatmapRafRef.current = null;
      }
      heatCanvasPanLayerRef.current = null;
      heatCanvasBasePanRef.current = { x: 0, y: 0 };
      detachWheelZoomRef.current?.();
      overlaysRef.current.forEach((overlay) => overlay.setMap(null));
      overlaysRef.current = [];
    };
  }, []);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !window.kakao?.maps) return;
    overlaysRef.current.forEach((overlay) => overlay.setMap(null));
    overlaysRef.current = [];

    if (layerMode !== "cluster") {
      const visibleSegments = layerMode === "summary"
        ? summaryRouteSegments(routeSegments)
        : routeSegments;

      visibleSegments.forEach((segment) => {
        const segmentOverlays = createRouteSegmentOverlays(segment, {
          mode: layerMode,
        });
        segmentOverlays.forEach((overlay) => {
          overlay.setMap(mapRef.current);
          overlaysRef.current.push(overlay);
        });
      });

      if (layerMode === "detail") {
        hotspots.forEach((hotspot) => {
          const hotspotOverlays = createHotspotOverlays(hotspot);
          hotspotOverlays.forEach((overlay) => {
            overlay.setMap(mapRef.current);
            overlaysRef.current.push(overlay);
          });
        });
      }
    }

    const currentLocation = createCurrentLocationOverlay({ lat: 35.1159, lng: 129.0404 });
    if (currentLocation) {
      currentLocation.setMap(mapRef.current);
      overlaysRef.current.push(currentLocation);
    }
  }, [hotspots, layerMode, mapReady, routeSegments]);

  useEffect(() => {
    if (presentationMode !== "heatmap" && heatCanvasRef.current) {
      const canvas = heatCanvasRef.current;
      const context = canvas.getContext("2d");
      context?.clearRect(0, 0, canvas.width, canvas.height);
      resetHeatCanvasPanTransform();
    }
  }, [presentationMode]);

  useLayoutEffect(() => {
    if (presentationMode !== "heatmap") return;
    if (!mapReady || !mapRef.current || !heatCanvasRef.current || !containerRef.current || !window.kakao?.maps) return;
    drawHeatmapCanvas({
      canvas: heatCanvasRef.current,
      container: containerRef.current,
      hotspots,
      map: mapRef.current,
      mode: layerMode,
      routeSegments,
    });
    resetHeatCanvasPanTransform();
  }, [hotspots, layerMode, mapReady, presentationMode, renderVersion, routeSegments]);

  useEffect(() => {
    if (!mapReady || fittedBoundsKeyRef.current === boundsKey) return;
    fitMapBounds(boundsPoints);
    fittedBoundsKeyRef.current = boundsKey;
  }, [boundsKey, boundsPoints, mapReady]);

  useEffect(() => {
    if (!mapReady || !containerRef.current) return;

    let frame = 0;
    const relayout = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        const map = mapRef.current;
        if (!map) return;
        map.relayout?.();
        if (boundsPoints.length > 0) {
          fitMapBounds(boundsPoints);
        }
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
  }, [boundsKey, boundsPoints, mapReady]);

  function fitMapBounds(points: GeoPoint[]) {
    if (!mapRef.current || !window.kakao?.maps || points.length === 0) return;
    const bounds = new window.kakao.maps.LatLngBounds();
    points.forEach((point) => bounds.extend(new window.kakao!.maps.LatLng(point.lat, point.lng)));
    mapRef.current.setBounds?.(bounds);
  }

  function zoom(delta: number) {
    const map = mapRef.current;
    if (!map) return;
    const nextLevel = Math.max(1, Math.min(14, (map.getLevel?.() ?? mapLevel) + delta));
    map.setLevel(nextLevel);
    setMapLevel(nextLevel);
  }

  if (mapError) {
    return (
      <BottleneckMapFallback
        error={mapError}
        hotspots={hotspots}
        routeSegments={routeSegments}
      />
    );
  }

  return (
    <div className="admin-kakao-map">
      <div ref={containerRef} className="admin-kakao-map__canvas" aria-label="카카오 지도 기반 병목 구간 시각화" />
      <canvas
        ref={heatCanvasRef}
        className={`admin-kakao-map__heat-canvas ${presentationMode === "segment" ? "is-hidden" : ""}`}
        aria-hidden="true"
      />
      {!mapReady && <div className="admin-kakao-map__loading">Kakao 지도 SDK를 불러오는 중입니다.</div>}
      <div className="map-zoom-control admin-kakao-map__zoom">
        <button type="button" aria-label="지도 확대" onClick={() => zoom(-1)}>+</button>
        <button type="button" aria-label="지도 축소" onClick={() => zoom(1)}>−</button>
      </div>
      <button className="map-locate-button admin-kakao-map__fit" type="button" aria-label="병목 구간 전체 보기" onClick={() => fitMapBounds(boundsPoints)}>
        <span aria-hidden="true">⌾</span>
      </button>
      {presentationMode === "heatmap" ? <HeatmapLegend /> : <SegmentLegend />}
    </div>
  );
}

function drawHeatmapCanvas({
  canvas,
  container,
  hotspots,
  map,
  mode,
  routeSegments,
}: {
  canvas: HTMLCanvasElement;
  container: HTMLElement;
  hotspots: BottleneckHotspot[];
  map: KakaoMap;
  mode: BottleneckLayerMode;
  routeSegments: BottleneckRouteSegment[];
}) {
  const width = container.clientWidth;
  const height = container.clientHeight;
  const dpr = window.devicePixelRatio || 1;
  if (width <= 0 || height <= 0) return;

  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);

  const context = canvas.getContext("2d");
  const projection = map.getProjection?.();
  if (!context || !projection || !window.kakao?.maps) return;

  context.setTransform(1, 0, 0, 1, 0, 0);
  context.clearRect(0, 0, canvas.width, canvas.height);

  const intensityCanvas = document.createElement("canvas");
  intensityCanvas.width = canvas.width;
  intensityCanvas.height = canvas.height;
  const intensityContext = intensityCanvas.getContext("2d");
  if (!intensityContext) return;

  intensityContext.setTransform(dpr, 0, 0, dpr, 0, 0);
  intensityContext.clearRect(0, 0, width, height);
  const segments = mode === "detail" ? routeSegments : summaryRouteSegments(routeSegments);
  segments.forEach((segment) => {
    const sampledPoints = sampleRoutePoints(segment.points, mode === "detail" ? 3 : 2);
    [...segment.points, ...sampledPoints].forEach((point) => {
      const projected = projectKakaoPoint(point, projection);
      if (!projected || !isVisiblePoint(projected, width, height, 52)) return;
      drawIntensitySpot(intensityContext, projected.x, projected.y, {
        radius: heatCanvasRadius(
          segment.sampleCount,
          segment.averageSpeedMps,
          mode === "cluster" ? "cluster" : mode === "detail" ? "route" : "summary",
        ),
        intensity: heatIntensityWeight(segment.sampleCount, segment.averageSpeedMps, mode),
      });
    });
  });

  if (mode === "detail") {
    hotspots.forEach((hotspot) => {
      const projected = projectKakaoPoint(hotspot, projection);
      if (!projected || !isVisiblePoint(projected, width, height, 70)) return;
      drawIntensitySpot(intensityContext, projected.x, projected.y, {
        radius: heatCanvasRadius(hotspot.sampleCount, hotspot.averageSpeedMps, "hotspot"),
        intensity: heatIntensityWeight(hotspot.sampleCount, hotspot.averageSpeedMps, "detail") * 1.08,
      });
    });
  }

  paintHeatmapImage(context, intensityContext, canvas.width, canvas.height, mode);
}

function projectKakaoPoint(point: GeoPoint, projection: ReturnType<NonNullable<KakaoMap["getProjection"]>>) {
  if (!window.kakao?.maps) return null;
  return projection.containerPointFromCoords(new window.kakao.maps.LatLng(point.lat, point.lng));
}

function isVisiblePoint(point: { x: number; y: number }, width: number, height: number, padding: number) {
  return point.x >= -padding && point.x <= width + padding && point.y >= -padding && point.y <= height + padding;
}

function drawIntensitySpot(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  options: { radius: number; intensity: number },
) {
  const gradient = context.createRadialGradient(x, y, 0, x, y, options.radius);
  gradient.addColorStop(0, `rgba(255,255,255,${options.intensity})`);
  gradient.addColorStop(0.22, `rgba(255,255,255,${options.intensity * 0.82})`);
  gradient.addColorStop(0.48, `rgba(255,255,255,${options.intensity * 0.42})`);
  gradient.addColorStop(0.72, `rgba(255,255,255,${options.intensity * 0.16})`);
  gradient.addColorStop(1, "rgba(255,255,255,0)");
  context.fillStyle = gradient;
  context.beginPath();
  context.arc(x, y, options.radius, 0, Math.PI * 2);
  context.fill();
}

function paintHeatmapImage(
  context: CanvasRenderingContext2D,
  intensityContext: CanvasRenderingContext2D,
  pixelWidth: number,
  pixelHeight: number,
  mode: BottleneckLayerMode,
) {
  const imageData = intensityContext.getImageData(0, 0, pixelWidth, pixelHeight);
  const data = imageData.data;
  const modeBoost = mode === "detail" ? 1.72 : mode === "summary" ? 1.48 : 1.3;
  const maxAlpha = mode === "detail" ? 128 : mode === "summary" ? 112 : 86;

  for (let index = 0; index < data.length; index += 4) {
    const rawIntensity = data[index + 3] / 255;
    if (rawIntensity < 0.006) {
      data[index + 3] = 0;
      continue;
    }

    const intensity = Math.min(1, Math.pow(rawIntensity * modeBoost, 0.82));
    const [red, green, blue] = heatPaletteColor(intensity);
    data[index] = red;
    data[index + 1] = green;
    data[index + 2] = blue;
    data[index + 3] = Math.round(Math.min(maxAlpha, 10 + intensity * maxAlpha));
  }

  context.putImageData(imageData, 0, 0);
}

function heatPaletteColor(intensity: number): [number, number, number] {
  const stops: Array<{ at: number; color: [number, number, number] }> = [
    { at: 0, color: [59, 130, 246] },
    { at: 0.28, color: [45, 212, 191] },
    { at: 0.48, color: [34, 197, 94] },
    { at: 0.66, color: [250, 204, 21] },
    { at: 0.82, color: [249, 115, 22] },
    { at: 1, color: [239, 68, 68] },
  ];
  const nextIndex = stops.findIndex((stop) => intensity <= stop.at);
  if (nextIndex <= 0) return stops[0].color;
  const previous = stops[nextIndex - 1];
  const next = stops[nextIndex];
  const ratio = (intensity - previous.at) / (next.at - previous.at);
  return interpolateColor(previous.color, next.color, ratio);
}

function interpolateColor(
  from: [number, number, number],
  to: [number, number, number],
  ratio: number,
): [number, number, number] {
  return [
    Math.round(from[0] + (to[0] - from[0]) * ratio),
    Math.round(from[1] + (to[1] - from[1]) * ratio),
    Math.round(from[2] + (to[2] - from[2]) * ratio),
  ];
}

function heatIntensityWeight(sampleCount: number, averageSpeedMps: number, mode: BottleneckLayerMode) {
  const base = mode === "detail" ? 0.064 : mode === "summary" ? 0.05 : 0.031;
  const sampleBoost = 0.86 + Math.min(0.34, Math.sqrt(Math.max(0, sampleCount)) / 120);
  const speedBoost = averageSpeedMps < 0.35 ? 1.28 : averageSpeedMps < 0.5 ? 1.12 : averageSpeedMps < 0.7 ? 0.96 : 0.82;
  return base * sampleBoost * speedBoost;
}

function heatCanvasRadius(sampleCount: number, averageSpeedMps: number, variant: "cluster" | "summary" | "route" | "hotspot") {
  const base = variant === "hotspot" ? 40 : variant === "summary" ? 30 : variant === "cluster" ? 22 : 28;
  const sampleBoostLimit = variant === "cluster" ? 11 : variant === "hotspot" ? 17 : 16;
  const sampleBoost = Math.min(sampleBoostLimit, Math.sqrt(Math.max(0, sampleCount)) * 0.28);
  const speedBoost = averageSpeedMps < 0.35 ? 5 : averageSpeedMps < 0.5 ? 3 : averageSpeedMps < 0.7 ? 1 : 0;
  return Math.round(base + sampleBoost + speedBoost);
}

function createRouteSegmentOverlays(
  segment: BottleneckRouteSegment,
  options: { mode: Exclude<BottleneckLayerMode, "cluster"> },
): KakaoOverlay[] {
  if (!window.kakao?.maps || segment.points.length < 2) return [];
  const path = segment.points.map((point) => new window.kakao!.maps.LatLng(point.lat, point.lng));
  const coreWeight = Math.max(3, Math.min(6, Math.round(3 + Math.sqrt(segment.sampleCount) / 34)));
  const isSummary = options.mode === "summary";
  const ribbonLayers = [
    { color: "#2dd4bf", weight: coreWeight + 16, opacity: isSummary ? 0.025 : 0.032, zIndex: 14 },
    { color: "#38bdf8", weight: coreWeight + 12, opacity: isSummary ? 0.035 : 0.045, zIndex: 15 },
    { color: "#facc15", weight: coreWeight + 8, opacity: isSummary ? 0.055 : 0.07, zIndex: 16 },
    { color: bottleneckFillColor(segment.averageSpeedMps), weight: coreWeight + 3, opacity: isSummary ? 0.12 : 0.16, zIndex: 20 },
    { color: bottleneckFillColor(segment.averageSpeedMps), weight: coreWeight, opacity: isSummary ? 0.24 : 0.31, zIndex: 22 },
  ].map((layer) => new window.kakao!.maps.Polyline({
    path,
    strokeWeight: layer.weight,
    strokeColor: layer.color,
    strokeOpacity: layer.opacity,
    strokeStyle: "solid",
    zIndex: layer.zIndex,
  }));

  return [
    ...ribbonLayers,
  ];
}

function createHotspotOverlays(hotspot: BottleneckHotspot): KakaoOverlay[] {
  if (!window.kakao?.maps) return [];
  const center = new window.kakao.maps.LatLng(hotspot.lat, hotspot.lng);
  const baseRadius = bottleneckRadiusMeter(hotspot.sampleCount);
  const fillColor = bottleneckFillColor(hotspot.averageSpeedMps);
  const strokeColor = bottleneckStrokeColor(hotspot.averageSpeedMps);
  const circles = [1.12, 0.64].map((ratio, depth) => new window.kakao!.maps.Circle({
    center,
    radius: baseRadius * ratio,
    strokeWeight: depth === 1 ? 1 : 0,
    strokeColor,
    strokeOpacity: depth === 1 ? 0.44 : 0,
    fillColor,
    fillOpacity: [0.035, 0.095][depth],
    zIndex: 24 + depth,
  }));

  return circles;
}

function createCurrentLocationOverlay(point: GeoPoint): KakaoOverlay | null {
  if (!window.kakao?.maps) return null;
  const marker = document.createElement("span");
  marker.className = "current-location-dot admin-kakao-map__current";
  return new window.kakao.maps.CustomOverlay({
    position: new window.kakao.maps.LatLng(point.lat, point.lng),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 0.5,
    zIndex: 40,
  });
}

function heatCellSize(sampleCount: number, averageSpeedMps: number, variant: "route" | "hotspot"): number {
  const base = variant === "hotspot" ? 25 : 27;
  const sampleBoost = Math.min(variant === "hotspot" ? 15 : 18, Math.sqrt(Math.max(0, sampleCount)) * (variant === "hotspot" ? 0.38 : 0.46));
  const speedBoost = averageSpeedMps < 0.35 ? 5 : averageSpeedMps < 0.5 ? 3 : averageSpeedMps < 0.7 ? 1 : 0;
  return Math.round(base + sampleBoost + speedBoost);
}

function sampleRoutePoints(points: GeoPoint[], stepsPerLeg: number): GeoPoint[] {
  return points.slice(0, -1).flatMap((point, index) => {
    const nextPoint = points[index + 1];
    return Array.from({ length: stepsPerLeg }, (_, step) => {
      const ratio = (step + 1) / (stepsPerLeg + 1);
      return {
        lat: point.lat + (nextPoint.lat - point.lat) * ratio,
        lng: point.lng + (nextPoint.lng - point.lng) * ratio,
      };
    });
  });
}

function summaryRouteSegments(routeSegments: BottleneckRouteSegment[]) {
  return [...routeSegments]
    .sort((a, b) => a.averageSpeedMps - b.averageSpeedMps || b.sampleCount - a.sampleCount)
    .slice(0, 4);
}

function BottleneckMapFallback({
  error,
  hotspots,
  routeSegments,
}: {
  error: string;
  hotspots: BottleneckHotspot[];
  routeSegments: BottleneckRouteSegment[];
}) {
  const points = collectBottleneckBoundsPoints(hotspots, routeSegments);
  const bounds = fallbackBounds(points);

  return (
    <div className="admin-kakao-map admin-kakao-map-fallback" role="img" aria-label="카카오 지도 SDK 미설정 시 병목 구간 대체 시각화">
      <div className="fallback-map-water fallback-map-water-east" />
      <div className="fallback-map-water fallback-map-water-south" />
      <svg className="fallback-map-vector" viewBox="0 0 100 100" aria-hidden="true">
        {routeSegments.map((segment) => {
          const pointsValue = segment.points.map((point) => projectFallbackPoint(point, bounds)).join(" ");
          const strokeWidth = Math.max(1.6, Math.min(3.8, Math.sqrt(segment.sampleCount) / 18));
          return (
            <g key={segment.id}>
              <polyline className="fallback-route-ribbon fallback-route-ribbon-cool" points={pointsValue} style={{ strokeWidth: strokeWidth + 7 }} />
              <polyline className="fallback-route-ribbon fallback-route-ribbon-warm" points={pointsValue} style={{ strokeWidth: strokeWidth + 4 }} />
              <polyline className="fallback-route-ribbon fallback-route-ribbon-core" points={pointsValue} style={{ stroke: bottleneckFillColor(segment.averageSpeedMps), strokeWidth }} />
            </g>
          );
        })}
      </svg>
      {routeSegments.flatMap((segment) => sampleRoutePoints(segment.points, 4).map((point, index) => ({
        ...point,
        id: `${segment.id}-${index}`,
        averageSpeedMps: segment.averageSpeedMps,
        sampleCount: segment.sampleCount,
      }))).map((cell) => {
        const [x, y] = projectFallbackPoint(cell, bounds).split(",");
        return (
          <span
            key={cell.id}
            className={`fallback-heat-dot fallback-heat-dot-${speedBand(cell.averageSpeedMps)}`}
            style={{
              left: `${x}%`,
              top: `${y}%`,
              backgroundColor: bottleneckFillColor(cell.averageSpeedMps),
              width: `${heatCellSize(cell.sampleCount / 12, cell.averageSpeedMps, "route")}px`,
              height: `${heatCellSize(cell.sampleCount / 12, cell.averageSpeedMps, "route")}px`,
            }}
          />
        );
      })}
      {hotspots.map((hotspot, index) => {
        const [x, y] = projectFallbackPoint(hotspot, bounds).split(",");
        return (
          <span
            key={hotspot.id}
            className={`fallback-heat-dot fallback-heat-dot-${speedBand(hotspot.averageSpeedMps)} ${index < 3 ? "fallback-heat-dot-high" : ""}`}
            style={{
              left: `${x}%`,
              top: `${y}%`,
              backgroundColor: bottleneckFillColor(hotspot.averageSpeedMps),
              width: `${heatCellSize(hotspot.sampleCount, hotspot.averageSpeedMps, "hotspot")}px`,
              height: `${heatCellSize(hotspot.sampleCount, hotspot.averageSpeedMps, "hotspot")}px`,
            }}
          />
        );
      })}
      <div className="admin-kakao-map-fallback__notice">
        <strong>Kakao 지도 SDK 대기</strong>
        <span>{error}</span>
      </div>
      <HeatmapLegend />
    </div>
  );
}

function HeatmapLegend() {
  return (
    <div className="heatmap-legend">
      <span>느림</span>
      <i />
      <span>빠름</span>
    </div>
  );
}

function SegmentLegend() {
  return (
    <div className="heatmap-legend heatmap-legend-segment">
      <span><i className="segment-tone segment-tone-fast" aria-hidden="true" />통행 여유</span>
      <span><i className="segment-tone segment-tone-medium" aria-hidden="true" />주의 구간</span>
      <span><i className="segment-tone segment-tone-slow" aria-hidden="true" />병목 구간</span>
    </div>
  );
}

function fallbackBounds(points: GeoPoint[]) {
  if (points.length === 0) {
    return {
      minLat: 35.09,
      maxLat: 35.13,
      minLng: 129.0,
      maxLng: 129.06,
    };
  }
  const latitudes = points.map((point) => point.lat);
  const longitudes = points.map((point) => point.lng);
  const minLat = Math.min(...latitudes);
  const maxLat = Math.max(...latitudes);
  const minLng = Math.min(...longitudes);
  const maxLng = Math.max(...longitudes);
  const latPadding = Math.max((maxLat - minLat) * 0.2, 0.004);
  const lngPadding = Math.max((maxLng - minLng) * 0.2, 0.004);
  return {
    minLat: minLat - latPadding,
    maxLat: maxLat + latPadding,
    minLng: minLng - lngPadding,
    maxLng: maxLng + lngPadding,
  };
}

function projectFallbackPoint(point: GeoPoint, bounds: ReturnType<typeof fallbackBounds>) {
  const x = ((point.lng - bounds.minLng) / (bounds.maxLng - bounds.minLng)) * 100;
  const y = ((bounds.maxLat - point.lat) / (bounds.maxLat - bounds.minLat)) * 100;
  return `${x.toFixed(2)},${y.toFixed(2)}`;
}
