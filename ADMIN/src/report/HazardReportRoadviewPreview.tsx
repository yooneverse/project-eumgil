import { useEffect, useRef, useState } from "react";
import { loadKakaoMap, type KakaoRoadview, type KakaoRoadviewClient } from "../map/kakaoLoader";
import type { GeoPoint } from "../types";

interface HazardReportRoadviewPreviewProps {
  point: GeoPoint;
  label: string;
  helperMessage?: string;
}

type RoadviewStatus = "loading" | "ready" | "empty" | "error";

export function HazardReportRoadviewPreview({
  point,
  label,
  helperMessage = "",
}: HazardReportRoadviewPreviewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const roadviewRef = useRef<KakaoRoadview | null>(null);
  const roadviewClientRef = useRef<KakaoRoadviewClient | null>(null);
  const [status, setStatus] = useState<RoadviewStatus>("loading");
  const [message, setMessage] = useState<string>("로드뷰를 불러오는 중입니다.");

  useEffect(() => {
    let disposed = false;

    async function renderRoadview() {
      try {
        setStatus("loading");
        setMessage("로드뷰를 불러오는 중입니다.");

        await loadKakaoMap();
        if (disposed || !containerRef.current || !window.kakao?.maps) return;

        if (!window.kakao.maps.Roadview || !window.kakao.maps.RoadviewClient) {
          setStatus("error");
          setMessage("Kakao Roadview를 사용할 수 없습니다.");
          return;
        }

        const latLng = new window.kakao.maps.LatLng(point.lat, point.lng);
        roadviewClientRef.current ??= new window.kakao.maps.RoadviewClient();
        roadviewRef.current ??= new window.kakao.maps.Roadview(containerRef.current);

        roadviewClientRef.current.getNearestPanoId(latLng, 80, (panoId) => {
          if (disposed) return;

          if (!panoId) {
            setStatus("empty");
            setMessage(helperMessage ? `주변에서 Kakao 로드뷰를 찾지 못했습니다. ${helperMessage}` : "주변에서 Kakao 로드뷰를 찾지 못했습니다.");
            return;
          }

          roadviewRef.current?.setPanoId(panoId, latLng);
          window.setTimeout(() => {
            roadviewRef.current?.relayout?.();
          }, 0);
          setStatus("ready");
          setMessage("");
        });
      } catch (error: unknown) {
        if (disposed) return;
        setStatus("error");
        setMessage(error instanceof Error ? error.message : "로드뷰를 준비하지 못했습니다.");
      }
    }

    void renderRoadview();

    return () => {
      disposed = true;
    };
  }, [point.lat, point.lng]);

  return (
    <div className="hazard-roadview" role="img" aria-label={`${label} 로드뷰 미리보기`}>
      <div ref={containerRef} className="hazard-roadview__canvas" />
      {status !== "ready" && (
        <div className="hazard-roadview__overlay">
          <strong>{status === "loading" ? "로드뷰를 불러오는 중입니다." : "로드뷰를 표시하지 못했습니다."}</strong>
          <span>{message || "잠시 후 다시 시도해주세요."}</span>
        </div>
      )}
    </div>
  );
}
