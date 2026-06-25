import type { EditAction, EditNodeRef, RoadNodeFeature, SegmentFeature } from "../types";
import type { Coord } from "./routeGraph";

type AddSegmentType = Extract<EditAction, { action: "add_segment" }>["segmentType"];
const DEFAULT_NODE_SNAP_DISTANCE_METER = 1.0;

export interface CoordBounds {
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
}

export interface SegmentEndpointNodeCandidate {
  nodeId: string;
  sourceNodeKey?: string | null;
  coord: Coord;
  nodeRef?: EditNodeRef;
}

export interface SnappedSegmentEndpoint {
  coord: Coord;
  snapped: boolean;
  nodeId?: string;
  distanceMeter?: number;
  nodeRef: EditNodeRef;
}

export function deletedEdgeIds(edits: EditAction[]): Set<string> {
  return new Set(
    edits
      .filter((edit): edit is Extract<EditAction, { action: "delete_segment" }> => edit.action === "delete_segment")
      .map((edit) => String(edit.edgeId)),
  );
}

export function visibleSegmentFeatures(segments: SegmentFeature[], edits: EditAction[]): SegmentFeature[] {
  const deleted = deletedEdgeIds(edits);
  return segments.filter((feature) => !deleted.has(String(feature.properties.edgeId)));
}

export function draftSegmentFeatures(edits: EditAction[]): SegmentFeature[] {
  return edits.flatMap((edit, index) => {
    if (edit.action !== "add_segment") return [];
    return [
      {
        type: "Feature",
        geometry: edit.geom,
        properties: {
          edgeId: `draft:${index}`,
          fromNodeId: edit.fromNode ? nodeRefId(edit.fromNode) : undefined,
          toNodeId: edit.toNode ? nodeRefId(edit.toNode) : undefined,
          segmentType: edit.segmentType,
        },
      } satisfies SegmentFeature,
    ];
  });
}

export function twoPointAddDraft(segmentType: AddSegmentType, points: Coord[], endpoints?: SnappedSegmentEndpoint[]): {
  edit: Extract<EditAction, { action: "add_segment" }> | null;
  remainingPoints: Coord[];
  rejectedReason?: string;
} {
  if (points.length < 2) return { edit: null, remainingPoints: points };
  const coordinates = points.slice(0, 2);
  const fromNode = endpoints?.[0]?.nodeRef;
  const toNode = endpoints?.[1]?.nodeRef;
  return {
    edit: {
      action: "add_segment",
      segmentType,
      geom: { type: "LineString", coordinates },
      ...(fromNode && toNode ? { fromNode, toNode } : {}),
    },
    remainingPoints: [],
  };
}

export function describeSideLineNodeSnap(nodeId: string, distanceMeter: number): string {
  return `SIDE_LINE 끝점을 node #${nodeId}에 ${formatAdjustmentDistance(distanceMeter)} 보정했습니다. 저장 시 보정 좌표로 반영됩니다.`;
}

export function describeCrossWalkProjection(distanceMeter: number): string {
  return `CROSS_WALK 끝점을 기존 선 위로 ${formatAdjustmentDistance(distanceMeter)} 보정했습니다. 저장 시 보정 좌표로 반영됩니다.`;
}

export function roadNodeCandidates(nodes: RoadNodeFeature[]): SegmentEndpointNodeCandidate[] {
  return nodes.map((node) => ({
    nodeId: String(node.properties.vertexId),
    sourceNodeKey: node.properties.sourceNodeKey ?? null,
    coord: node.geometry.coordinates,
    nodeRef: {
      mode: "existing",
      vertexId: node.properties.vertexId,
      sourceNodeKey: node.properties.sourceNodeKey ?? null,
      geom: { type: "Point", coordinates: node.geometry.coordinates },
      snapDistanceMeter: 0,
    },
  }));
}

export function draftEndpointNodeCandidates(edits: EditAction[]): SegmentEndpointNodeCandidate[] {
  return edits.flatMap((edit, index) => {
    if (edit.action !== "add_segment") return [];
    const [first, last] = edit.geom.coordinates;
    return [
      nodeRefCandidate(edit.fromNode, `draft:${index}:from`, first),
      nodeRefCandidate(edit.toNode, `draft:${index}:to`, last),
    ].filter((candidate): candidate is SegmentEndpointNodeCandidate => Boolean(candidate));
  });
}

export function segmentEndpointNodeCandidates(segments: SegmentFeature[]): SegmentEndpointNodeCandidate[] {
  const candidatesByNodeId = new Map<string, Coord>();
  segments.forEach((feature) => {
    const coordinates = feature.geometry.coordinates;
    const first = coordinates[0];
    const last = coordinates[coordinates.length - 1];
    const fromNodeId = feature.properties.fromNodeId;
    const toNodeId = feature.properties.toNodeId;
    if (first && fromNodeId !== undefined && fromNodeId !== null) {
      candidatesByNodeId.set(String(fromNodeId), first);
    }
    if (last && toNodeId !== undefined && toNodeId !== null) {
      candidatesByNodeId.set(String(toNodeId), last);
    }
  });
  return Array.from(candidatesByNodeId, ([nodeId, coord]) => ({ nodeId, coord }));
}

function nodeRefCandidate(nodeRef: EditNodeRef | undefined, fallbackNodeId: string, fallbackCoord: Coord | undefined): SegmentEndpointNodeCandidate | null {
  if (nodeRef) {
    return {
      nodeId: nodeRefId(nodeRef),
      sourceNodeKey: nodeRef.sourceNodeKey ?? null,
      coord: nodeRef.geom.coordinates,
      nodeRef,
    };
  }
  if (!fallbackCoord) return null;
  return {
    nodeId: fallbackNodeId,
    sourceNodeKey: fallbackNodeId,
    coord: fallbackCoord,
  };
}

function nodeRefId(nodeRef: EditNodeRef): string {
  return nodeRef.mode === "existing" ? String(nodeRef.vertexId) : nodeRef.tempNodeId;
}

export function snapToSegmentEndpointNode(
  coord: Coord,
  candidates: SegmentEndpointNodeCandidate[],
  snapDistanceMeter = DEFAULT_NODE_SNAP_DISTANCE_METER,
): SnappedSegmentEndpoint {
  let nearest: { candidate: SegmentEndpointNodeCandidate; distanceMeter: number } | null = null;
  for (const candidate of candidates) {
    const distance = distanceMeter(coord, candidate.coord);
    if (distance <= snapDistanceMeter && (!nearest || distance < nearest.distanceMeter)) {
      nearest = { candidate, distanceMeter: distance };
    }
  }
  if (!nearest) {
    return {
      coord,
      snapped: false,
      nodeRef: newNodeRef(coord),
    };
  }
  return {
    coord: nearest.candidate.coord,
    snapped: true,
    nodeId: nearest.candidate.nodeId,
    distanceMeter: nearest.distanceMeter,
    nodeRef: nearest.candidate.nodeRef ?? {
      mode: "existing",
      vertexId: nearest.candidate.nodeId,
      sourceNodeKey: nearest.candidate.sourceNodeKey ?? null,
      geom: { type: "Point", coordinates: nearest.candidate.coord },
      snapDistanceMeter: nearest.distanceMeter,
    },
  };
}

export function isSameSnappedNode(left: SnappedSegmentEndpoint | undefined, right: SnappedSegmentEndpoint | undefined): boolean {
  if (!left || !right) return false;
  if (left.nodeRef.mode === "existing" && right.nodeRef.mode === "existing") {
    return String(left.nodeRef.vertexId) === String(right.nodeRef.vertexId);
  }
  if (left.nodeRef.mode === "new" && right.nodeRef.mode === "new") {
    return left.nodeRef.tempNodeId === right.nodeRef.tempNodeId;
  }
  return false;
}

export function newNodeRef(coord: Coord): EditNodeRef {
  const key = `manual_node:${coord[0].toFixed(8)}:${coord[1].toFixed(8)}`;
  return {
    mode: "new",
    tempNodeId: key,
    sourceNodeKey: key,
    geom: { type: "Point", coordinates: coord },
    snapDistanceMeter: null,
  };
}

export function resetPolygonDeleteSelection(active: boolean): { active: boolean; points: Coord[]; pointCount: number } {
  return { active, points: [], pointCount: 0 };
}

export function coordBounds(a: Coord, b: Coord): CoordBounds {
  return {
    minLng: Math.min(a[0], b[0]),
    minLat: Math.min(a[1], b[1]),
    maxLng: Math.max(a[0], b[0]),
    maxLat: Math.max(a[1], b[1]),
  };
}

export function segmentsIntersectingBounds(segments: SegmentFeature[], bounds: CoordBounds): SegmentFeature[] {
  return segments.filter((feature) => lineIntersectsBounds(feature.geometry.coordinates, bounds));
}

export function segmentsTouchingPolygon(segments: SegmentFeature[], polygon: Coord[]): SegmentFeature[] {
  if (polygon.length < 3) return [];
  const bounds = coordBoundsForCoords(polygon);
  return segments.filter((feature) => boundsIntersect(featureCoordBounds(feature.geometry.coordinates), bounds))
    .filter((feature) => feature.geometry.coordinates.some((coord) => pointInPolygon(coord, polygon)));
}

function boundsIntersect(a: CoordBounds, b: CoordBounds): boolean {
  return a.minLng <= b.maxLng && a.maxLng >= b.minLng && a.minLat <= b.maxLat && a.maxLat >= b.minLat;
}

function lineIntersectsBounds(coordinates: Coord[], bounds: CoordBounds): boolean {
  if (coordinates.some((coord) => pointInBounds(coord, bounds))) return true;
  if (!boundsIntersect(featureCoordBounds(coordinates), bounds)) return false;

  return coordinates.slice(1).some((coord, index) => segmentIntersectsBounds(coordinates[index], coord, bounds));
}

function featureCoordBounds(coordinates: Coord[]): CoordBounds {
  return coordinates.reduce<CoordBounds>(
    (acc, [lng, lat]) => ({
      minLng: Math.min(acc.minLng, lng),
      minLat: Math.min(acc.minLat, lat),
      maxLng: Math.max(acc.maxLng, lng),
      maxLat: Math.max(acc.maxLat, lat),
    }),
    { minLng: Infinity, minLat: Infinity, maxLng: -Infinity, maxLat: -Infinity },
  );
}

function coordBoundsForCoords(coordinates: Coord[]): CoordBounds {
  return coordinates.reduce<CoordBounds>(
    (acc, [lng, lat]) => ({
      minLng: Math.min(acc.minLng, lng),
      minLat: Math.min(acc.minLat, lat),
      maxLng: Math.max(acc.maxLng, lng),
      maxLat: Math.max(acc.maxLat, lat),
    }),
    { minLng: Infinity, minLat: Infinity, maxLng: -Infinity, maxLat: -Infinity },
  );
}

function pointInPolygon(coord: Coord, polygon: Coord[]): boolean {
  const [x, y] = coord;
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, yi] = polygon[i];
    const [xj, yj] = polygon[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

function pointInBounds([lng, lat]: Coord, bounds: CoordBounds): boolean {
  return lng >= bounds.minLng && lng <= bounds.maxLng && lat >= bounds.minLat && lat <= bounds.maxLat;
}

function segmentIntersectsBounds(a: Coord, b: Coord, bounds: CoordBounds): boolean {
  const bottomLeft: Coord = [bounds.minLng, bounds.minLat];
  const bottomRight: Coord = [bounds.maxLng, bounds.minLat];
  const topRight: Coord = [bounds.maxLng, bounds.maxLat];
  const topLeft: Coord = [bounds.minLng, bounds.maxLat];

  return (
    segmentsIntersect(a, b, bottomLeft, bottomRight) ||
    segmentsIntersect(a, b, bottomRight, topRight) ||
    segmentsIntersect(a, b, topRight, topLeft) ||
    segmentsIntersect(a, b, topLeft, bottomLeft)
  );
}

function segmentsIntersect(a: Coord, b: Coord, c: Coord, d: Coord): boolean {
  const abC = direction(a, b, c);
  const abD = direction(a, b, d);
  const cdA = direction(c, d, a);
  const cdB = direction(c, d, b);

  if (abC === 0 && pointOnSegment(a, b, c)) return true;
  if (abD === 0 && pointOnSegment(a, b, d)) return true;
  if (cdA === 0 && pointOnSegment(c, d, a)) return true;
  if (cdB === 0 && pointOnSegment(c, d, b)) return true;

  return abC !== abD && cdA !== cdB;
}

function direction(a: Coord, b: Coord, c: Coord): -1 | 0 | 1 {
  const cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  if (Math.abs(cross) < 1e-12) return 0;
  return cross > 0 ? 1 : -1;
}

function pointOnSegment(a: Coord, b: Coord, c: Coord): boolean {
  return (
    c[0] >= Math.min(a[0], b[0]) &&
    c[0] <= Math.max(a[0], b[0]) &&
    c[1] >= Math.min(a[1], b[1]) &&
    c[1] <= Math.max(a[1], b[1])
  );
}

function distanceMeter(a: Coord, b: Coord) {
  const metersPerDegreeLat = 111_320;
  const originLat = (a[1] + b[1]) / 2;
  const metersPerDegreeLng = metersPerDegreeLat * Math.cos((originLat * Math.PI) / 180);
  const dx = (a[0] - b[0]) * metersPerDegreeLng;
  const dy = (a[1] - b[1]) * metersPerDegreeLat;
  return Math.hypot(dx, dy);
}

function formatAdjustmentDistance(distanceMeter: number): string {
  return `${distanceMeter.toFixed(1)}m`;
}
