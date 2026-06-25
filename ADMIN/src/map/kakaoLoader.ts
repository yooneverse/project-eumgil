declare global {
  interface Window {
    kakao?: {
      maps: {
        load: (callback: () => void) => void;
        LatLng: new (lat: number, lng: number) => unknown;
        LatLngBounds: new () => { extend: (latLng: unknown) => void };
        Map: new (container: HTMLElement, options: Record<string, unknown>) => KakaoMap;
        Polyline: new (options: Record<string, unknown>) => KakaoOverlay;
        Polygon: new (options: Record<string, unknown>) => KakaoOverlay;
        Circle: new (options: Record<string, unknown>) => KakaoOverlay;
        Marker: new (options: Record<string, unknown>) => KakaoOverlay;
        CustomOverlay: new (options: Record<string, unknown>) => KakaoOverlay;
        Roadview?: new (container: HTMLElement) => KakaoRoadview;
        RoadviewClient?: new () => KakaoRoadviewClient;
        event: {
          addListener: (target: unknown, eventName: string, callback: (...args: unknown[]) => void) => void;
        };
      };
    };
  }
}

export interface KakaoMap {
  setCenter: (latLng: unknown) => void;
  getCenter?: () => unknown;
  setBounds?: (bounds: unknown) => void;
  getBounds?: () => unknown;
  getLevel?: () => number;
  setLevel: (level: number) => void;
  getProjection?: () => KakaoMapProjection;
  relayout?: () => void;
}

export interface KakaoOverlay {
  setMap: (map: KakaoMap | null) => void;
}

export interface KakaoMapProjection {
  containerPointFromCoords: (latLng: unknown) => { x: number; y: number };
}

export interface KakaoRoadview {
  setPanoId: (panoId: number | string, position: unknown) => void;
  relayout?: () => void;
  getViewpoint?: () => { pan?: number | string };
}

export interface KakaoRoadviewClient {
  getNearestPanoId: (position: unknown, radius: number, callback: (panoId: number | string | null) => void) => void;
}

export function attachKakaoWheelZoom(
  container: HTMLElement,
  getMap: () => KakaoMap | null,
  onLevelChange?: (level: number) => void,
) {
  const handleWheel = (event: WheelEvent) => {
    const target = event.target instanceof HTMLElement ? event.target : null;
    if (target?.closest(".map-toolbar, .map-status, .facility-selected-map-card")) {
      return;
    }

    const map = getMap();
    if (!map) return;

    event.preventDefault();
    event.stopPropagation();

    const currentLevel = map.getLevel?.() ?? 6;
    const nextLevel = Math.max(1, Math.min(14, currentLevel + (event.deltaY > 0 ? 1 : -1)));
    if (nextLevel === currentLevel) return;

    map.setLevel(nextLevel);
    onLevelChange?.(nextLevel);
  };

  container.addEventListener("wheel", handleWheel, { passive: false, capture: true });
  return () => container.removeEventListener("wheel", handleWheel, { capture: true });
}

let loadingPromise: Promise<void> | null = null;

export function loadKakaoMap(): Promise<void> {
  const appKey = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined;

  if (!appKey) {
    return Promise.reject(new Error("VITE_KAKAO_MAP_KEY가 없습니다."));
  }

  if (window.kakao?.maps) {
    return new Promise((resolve) => window.kakao?.maps.load(resolve));
  }

  if (loadingPromise) return loadingPromise;

  loadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false&libraries=services,clusterer`;
    script.onload = () => {
      if (!window.kakao?.maps) {
        reject(new Error("Kakao Map SDK를 초기화할 수 없습니다."));
        return;
      }
      window.kakao.maps.load(resolve);
    };
    script.onerror = () => reject(new Error("Kakao Map SDK 로드 실패"));
    document.head.appendChild(script);
  });

  return loadingPromise;
}
