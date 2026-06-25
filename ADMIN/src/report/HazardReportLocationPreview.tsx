import { useEffect, useRef, useState } from "react";
import { loadKakaoMap, type KakaoMap, type KakaoOverlay } from "../map/kakaoLoader";
import type { GeoPoint } from "../types";

interface HazardReportLocationPreviewProps {
  point: GeoPoint;
  label: string;
  roadviewPoint?: GeoPoint | null;
  onPickRoadviewPoint?: (point: GeoPoint) => void;
}

export function HazardReportLocationPreview({
  point,
  label,
  roadviewPoint = null,
  onPickRoadviewPoint,
}: HazardReportLocationPreviewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const reportMarkerRef = useRef<KakaoOverlay | null>(null);
  const roadviewMarkerRef = useRef<KakaoOverlay | null>(null);
  const onPickRoadviewPointRef = useRef(onPickRoadviewPoint);
  const [mapReady, setMapReady] = useState(false);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    onPickRoadviewPointRef.current = onPickRoadviewPoint;
  }, [onPickRoadviewPoint]);

  useEffect(() => {
    let disposed = false;

    setStatus("loading");
    setErrorMessage(null);

    void loadKakaoMap()
      .then(() => {
        if (disposed || !containerRef.current || !window.kakao?.maps) return;

        if (!mapRef.current) {
          mapRef.current = new window.kakao.maps.Map(containerRef.current, {
            center: new window.kakao.maps.LatLng(point.lat, point.lng),
            level: 3,
            draggable: true,
            scrollwheel: false,
            disableDoubleClick: true,
            disableDoubleClickZoom: true,
            keyboardShortcuts: false,
          });

          window.kakao.maps.event.addListener(mapRef.current, "click", (event: unknown) => {
            const latLng = (event as { latLng?: { getLat: () => number; getLng: () => number } }).latLng;
            if (!latLng) {
              return;
            }
            onPickRoadviewPointRef.current?.({
              lat: latLng.getLat(),
              lng: latLng.getLng(),
            });
          });
        }

        setMapReady(true);
        setStatus("ready");
      })
      .catch((error: unknown) => {
        if (disposed) return;
        setMapReady(false);
        setStatus("error");
        setErrorMessage(error instanceof Error ? error.message : "지도 미리보기를 준비하지 못했습니다.");
      });

    return () => {
      disposed = true;
      reportMarkerRef.current?.setMap(null);
      roadviewMarkerRef.current?.setMap(null);
    };
  }, []);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !window.kakao?.maps) return;
    const center = new window.kakao.maps.LatLng(point.lat, point.lng);
    mapRef.current.setCenter(center);
    mapRef.current.setLevel(3);
    mapRef.current.relayout?.();
  }, [mapReady, point.lat, point.lng]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !window.kakao?.maps) {
      return;
    }

    reportMarkerRef.current?.setMap(null);
    reportMarkerRef.current = createLocationPreviewMarker(point, "report", mapRef.current);

    const hasRoadviewPoint = roadviewPoint != null && !isSamePoint(point, roadviewPoint);
    roadviewMarkerRef.current?.setMap(null);
    roadviewMarkerRef.current = hasRoadviewPoint
      ? createLocationPreviewMarker(roadviewPoint, "roadview", mapRef.current)
      : null;
  }, [mapReady, point.lat, point.lng, roadviewPoint?.lat, roadviewPoint?.lng]);

  return (
    <div className="hazard-location-map" role="img" aria-label={`${label} 지도 미리보기`}>
      <div ref={containerRef} className="hazard-location-map__canvas" />
      {status !== "ready" && (
        <div className="hazard-location-map__overlay">
          <strong>{status === "loading" ? "위치를 불러오는 중입니다." : "지도를 표시하지 못했습니다."}</strong>
          <span>{status === "loading" ? "좌표를 기준으로 Kakao 지도를 준비하고 있습니다." : errorMessage ?? "잠시 후 다시 시도해주세요."}</span>
        </div>
      )}
    </div>
  );
}

function createLocationPreviewMarker(
  point: GeoPoint,
  tone: "report" | "roadview",
  map: KakaoMap,
): KakaoOverlay | null {
  if (!window.kakao?.maps) {
    return null;
  }

  const marker = document.createElement("div");

  if (tone === "report") {
    marker.className = "hazard-location-map__pin-marker hazard-location-map__pin-marker--report";
    marker.setAttribute("aria-hidden", "true");

    const core = document.createElement("span");
    core.className = "hazard-location-map__pin-marker-core";
    marker.append(core);
  } else {
    marker.className = "hazard-location-map__marker hazard-location-map__marker--roadview";
    marker.textContent = "로드뷰";
  }

  return new window.kakao.maps.CustomOverlay({
    map,
    position: new window.kakao.maps.LatLng(point.lat, point.lng),
    content: marker,
    xAnchor: 0.5,
    yAnchor: 1,
    zIndex: tone === "report" ? 3 : 2,
  });
}

function isSamePoint(a: GeoPoint, b: GeoPoint) {
  return Math.abs(a.lat - b.lat) < 0.000001 && Math.abs(a.lng - b.lng) < 0.000001;
}
