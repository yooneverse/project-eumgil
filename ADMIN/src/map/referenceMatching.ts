import type { ReferencePointFeature, RoadAttributeFeature, SegmentFeature } from "../types";

export interface ReferenceMatch {
  sourceId: string;
  label: string;
  layer: string;
  distanceM: number;
  matchConfidence: "HIGH" | "REVIEW" | "LOW";
}

type Coord = [number, number];

export function nearestRoadAttributeMatches(segment: SegmentFeature | null, features: RoadAttributeFeature[], limit = 3): ReferenceMatch[] {
  if (!segment) return [];
  return features
    .map((feature) => ({
      sourceId: feature.properties.sourceId || feature.properties.handoffEdgeId,
      label: feature.properties.slopeLevelLabel || feature.properties.slopeLevel || "도로 속성",
      layer: "도로 속성",
      distanceM: lineDistanceM(segment.geometry.coordinates, feature.geometry.coordinates),
      matchConfidence: "REVIEW" as const,
    }))
    .filter((match) => match.distanceM <= 20)
    .sort((left, right) => left.distanceM - right.distanceM)
    .slice(0, limit)
    .map((match) => ({ ...match, matchConfidence: match.distanceM <= 5 ? "HIGH" : "REVIEW" }));
}

export function nearestPointMatches(segment: SegmentFeature | null, features: ReferencePointFeature[], layerLabel: string, limit = 5): ReferenceMatch[] {
  if (!segment) return [];
  return features
    .map((feature) => {
      const distanceM = pointLineDistanceM(feature.geometry.coordinates, segment.geometry.coordinates);
      return {
        sourceId: feature.properties.sourceId,
        label: feature.properties.label || layerLabel,
        layer: layerLabel,
        distanceM,
        matchConfidence: confidenceFromDistance(distanceM, feature.properties.matchConfidence),
      };
    })
    .filter((match) => match.distanceM <= 30)
    .sort((left, right) => left.distanceM - right.distanceM)
    .slice(0, limit);
}

export function pointLineDistanceM(point: Coord, line: Coord[]): number {
  if (line.length === 0) return Number.POSITIVE_INFINITY;
  if (line.length === 1) return distanceM(point, line[0]);
  let best = Number.POSITIVE_INFINITY;
  for (let index = 0; index < line.length - 1; index += 1) {
    best = Math.min(best, pointSegmentDistanceM(point, line[index], line[index + 1]));
  }
  return best;
}

function lineDistanceM(left: Coord[], right: Coord[]): number {
  const candidates = [
    ...left.map((point) => pointLineDistanceM(point, right)),
    ...right.map((point) => pointLineDistanceM(point, left)),
  ];
  return Math.min(...candidates);
}

function pointSegmentDistanceM(point: Coord, start: Coord, end: Coord): number {
  const originLat = point[1];
  const p = project(point, originLat);
  const a = project(start, originLat);
  const b = project(end, originLat);
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  if (dx === 0 && dy === 0) return Math.hypot(p[0] - a[0], p[1] - a[1]);
  const t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy)));
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
}

function distanceM(left: Coord, right: Coord): number {
  return Math.hypot(...projectDelta(left, right));
}

function project(coord: Coord, originLat: number): [number, number] {
  const [lng, lat] = coord;
  const metersPerDegreeLat = 111_320;
  const metersPerDegreeLng = metersPerDegreeLat * Math.cos((originLat * Math.PI) / 180);
  return [lng * metersPerDegreeLng, lat * metersPerDegreeLat];
}

function projectDelta(left: Coord, right: Coord): [number, number] {
  const originLat = (left[1] + right[1]) / 2;
  const l = project(left, originLat);
  const r = project(right, originLat);
  return [l[0] - r[0], l[1] - r[1]];
}

function confidenceFromDistance(distanceM: number, sourceConfidence?: string): "HIGH" | "REVIEW" | "LOW" {
  if (sourceConfidence === "HIGH") return "HIGH";
  if (distanceM <= 5) return "HIGH";
  if (distanceM <= 20) return "REVIEW";
  return "LOW";
}
