import { describe, expect, it } from "vitest";
import mapComponentSource from "./AdminBottleneckKakaoMap.tsx?raw";
import {
  bottleneckLayerMode,
  bottleneckFillColor,
  bottleneckRadiusMeter,
  bottleneckStrokeColor,
  clusterBottlenecks,
  collectBottleneckBoundsPoints,
  speedBand,
  type BottleneckHotspot,
  type BottleneckRouteSegment,
} from "./adminBottleneckMap";

const hotspots: BottleneckHotspot[] = [
  {
    id: "h1",
    name: "초량 이바구길 입구",
    lat: 35.1169,
    lng: 129.0392,
    averageSpeedMps: 0.28,
    reportCount: 16,
    sampleCount: 920,
  },
];

const routes: BottleneckRouteSegment[] = [
  {
    id: "r1",
    averageSpeedMps: 0.41,
    sampleCount: 640,
    points: [
      { lat: 35.1171, lng: 129.0372 },
      { lat: 35.118, lng: 129.04 },
    ],
  },
];

describe("admin bottleneck map helpers", () => {
  it("maps slow walking speed to stronger heat bands", () => {
    expect(speedBand(0.25)).toBe("danger");
    expect(speedBand(0.42)).toBe("hot");
    expect(speedBand(0.58)).toBe("warm");
    expect(speedBand(0.86)).toBe("clear");
  });

  it("keeps circle radius bounded for map readability", () => {
    expect(bottleneckRadiusMeter(10)).toBe(42);
    expect(bottleneckRadiusMeter(920)).toBe(82);
    expect(bottleneckRadiusMeter(9999)).toBe(110);
  });

  it("uses semantic colors for Kakao overlays", () => {
    expect(bottleneckFillColor(0.28)).toBe("#ef4444");
    expect(bottleneckStrokeColor(0.28)).toBe("#991b1b");
    expect(bottleneckFillColor(0.82)).toBe("#22c55e");
  });

  it("collects every route and hotspot point for bounds fitting", () => {
    expect(collectBottleneckBoundsPoints(hotspots, routes)).toEqual([
      { lat: 35.1169, lng: 129.0392 },
      { lat: 35.1171, lng: 129.0372 },
      { lat: 35.118, lng: 129.04 },
    ]);
  });

  it("progressively reveals clusters and details by map zoom level", () => {
    expect(bottleneckLayerMode(7)).toBe("cluster");
    expect(bottleneckLayerMode(5)).toBe("summary");
    expect(bottleneckLayerMode(4)).toBe("detail");
  });

  it("clusters nearby route and hotspot candidates for the initial map view", () => {
    const clusters = clusterBottlenecks(hotspots, routes, 0.02);

    expect(clusters).toHaveLength(1);
    expect(clusters[0]).toMatchObject({
      itemCount: 2,
      routeCount: 1,
      hotspotCount: 1,
      reportCount: 16,
      sampleCount: 1560,
    });
    expect(clusters[0].averageSpeedMps).toBeCloseTo(0.33, 2);
  });

  it("re-renders the heatmap while the map is being dragged, not only after idle", () => {
    expect(mapComponentSource).toContain("useLayoutEffect");
    expect(mapComponentSource).toContain('const heatmapRafRef = useRef<number | null>(null);');
    expect(mapComponentSource).toContain('window.kakao.maps.event.addListener(mapRef.current, "center_changed"');
    expect(mapComponentSource).toContain('window.kakao.maps.event.addListener(mapRef.current, "drag"');
    expect(mapComponentSource).toContain("const deltaX = currentOffset.x - heatCanvasBasePanRef.current.x;");
    expect(mapComponentSource).toContain("const deltaY = currentOffset.y - heatCanvasBasePanRef.current.y;");
    expect(mapComponentSource).toContain("resetHeatCanvasPanTransform();");
  });
});
