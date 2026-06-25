import type { GeoPoint } from "../types";

export type BottleneckSpeedBand = "danger" | "hot" | "warm" | "clear";
export type BottleneckLayerMode = "cluster" | "summary" | "detail";

export interface BottleneckHotspot extends GeoPoint {
  id: string;
  name: string;
  averageSpeedMps: number;
  reportCount: number;
  sampleCount: number;
}

export interface BottleneckRouteSegment {
  id: string;
  points: GeoPoint[];
  averageSpeedMps: number;
  sampleCount: number;
}

export interface BottleneckCluster extends GeoPoint {
  id: string;
  itemCount: number;
  routeCount: number;
  hotspotCount: number;
  sampleCount: number;
  reportCount: number;
  averageSpeedMps: number;
}

export function speedBand(averageSpeedMps: number): BottleneckSpeedBand {
  if (averageSpeedMps < 0.35) return "danger";
  if (averageSpeedMps < 0.5) return "hot";
  if (averageSpeedMps < 0.7) return "warm";
  return "clear";
}

export function bottleneckFillColor(averageSpeedMps: number): string {
  const colors: Record<BottleneckSpeedBand, string> = {
    danger: "#ef4444",
    hot: "#f97316",
    warm: "#facc15",
    clear: "#22c55e",
  };
  return colors[speedBand(averageSpeedMps)];
}

export function bottleneckStrokeColor(averageSpeedMps: number): string {
  const colors: Record<BottleneckSpeedBand, string> = {
    danger: "#991b1b",
    hot: "#c2410c",
    warm: "#ca8a04",
    clear: "#15803d",
  };
  return colors[speedBand(averageSpeedMps)];
}

export function bottleneckRadiusMeter(sampleCount: number): number {
  return Math.max(42, Math.min(110, Math.round(37 + Math.sqrt(Math.max(0, sampleCount)) * 1.5)));
}

export function collectBottleneckBoundsPoints(
  hotspots: BottleneckHotspot[],
  routeSegments: BottleneckRouteSegment[],
): GeoPoint[] {
  return [
    ...hotspots.map(({ lat, lng }) => ({ lat, lng })),
    ...routeSegments.flatMap((segment) => segment.points),
  ];
}

export function bottleneckLayerMode(mapLevel: number): BottleneckLayerMode {
  if (mapLevel >= 6) return "cluster";
  if (mapLevel >= 5) return "summary";
  return "detail";
}

export function clusterBottlenecks(
  hotspots: BottleneckHotspot[],
  routeSegments: BottleneckRouteSegment[],
  gridSizeDegrees = 0.012,
): BottleneckCluster[] {
  const buckets = new Map<string, {
    latSum: number;
    lngSum: number;
    speedSum: number;
    weightSum: number;
    itemCount: number;
    routeCount: number;
    hotspotCount: number;
    sampleCount: number;
    reportCount: number;
  }>();

  const addPoint = (
    point: GeoPoint,
    averageSpeedMps: number,
    sampleCount: number,
    reportCount: number,
    kind: "route" | "hotspot",
  ) => {
    const key = clusterKey(point, gridSizeDegrees);
    const weight = Math.max(1, sampleCount);
    const bucket = buckets.get(key) ?? {
      latSum: 0,
      lngSum: 0,
      speedSum: 0,
      weightSum: 0,
      itemCount: 0,
      routeCount: 0,
      hotspotCount: 0,
      sampleCount: 0,
      reportCount: 0,
    };
    bucket.latSum += point.lat * weight;
    bucket.lngSum += point.lng * weight;
    bucket.speedSum += averageSpeedMps * weight;
    bucket.weightSum += weight;
    bucket.itemCount += 1;
    bucket.routeCount += kind === "route" ? 1 : 0;
    bucket.hotspotCount += kind === "hotspot" ? 1 : 0;
    bucket.sampleCount += sampleCount;
    bucket.reportCount += reportCount;
    buckets.set(key, bucket);
  };

  hotspots.forEach((hotspot) => {
    addPoint(hotspot, hotspot.averageSpeedMps, hotspot.sampleCount, hotspot.reportCount, "hotspot");
  });

  routeSegments.forEach((segment) => {
    addPoint(
      routeSegmentCenter(segment),
      segment.averageSpeedMps,
      segment.sampleCount,
      0,
      "route",
    );
  });

  return Array.from(buckets.entries())
    .map(([id, bucket]) => ({
      id,
      lat: bucket.latSum / bucket.weightSum,
      lng: bucket.lngSum / bucket.weightSum,
      itemCount: bucket.itemCount,
      routeCount: bucket.routeCount,
      hotspotCount: bucket.hotspotCount,
      sampleCount: bucket.sampleCount,
      reportCount: bucket.reportCount,
      averageSpeedMps: bucket.speedSum / bucket.weightSum,
    }))
    .sort((a, b) => b.sampleCount - a.sampleCount);
}

export function routeSegmentCenter(segment: BottleneckRouteSegment): GeoPoint {
  if (!segment.points.length) return { lat: 0, lng: 0 };
  const middle = segment.points[Math.floor(segment.points.length / 2)];
  return middle;
}

function clusterKey(point: GeoPoint, gridSizeDegrees: number): string {
  const latBucket = Math.floor(point.lat / gridSizeDegrees);
  const lngBucket = Math.floor(point.lng / gridSizeDegrees);
  return `${latBucket}:${lngBucket}`;
}
