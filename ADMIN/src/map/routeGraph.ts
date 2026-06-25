import type { EditAction, SegmentFeature } from "../types";

export type Coord = [number, number];

export interface RoutePreview {
  coordinates: Coord[];
  distanceMeter: number;
}

interface GraphEdge {
  to: string;
  distanceMeter: number;
  coordinates: Coord[];
}

export function buildRoutePreview(
  segments: SegmentFeature[],
  edits: EditAction[],
  from: Coord | null,
  to: Coord | null,
): RoutePreview | null {
  if (!from || !to) return null;

  const deletedEdgeIds = new Set(
    edits
      .filter((edit): edit is Extract<EditAction, { action: "delete_segment" }> => edit.action === "delete_segment")
      .map((edit) => String(edit.edgeId)),
  );

  const graph = new Map<string, GraphEdge[]>();
  const nodeCoords = new Map<string, Coord>();

  function addNode(nodeId: string, coord: Coord) {
    if (!graph.has(nodeId)) graph.set(nodeId, []);
    if (!nodeCoords.has(nodeId)) nodeCoords.set(nodeId, coord);
  }

  function addEdge(fromNodeId: string, toNodeId: string, coordinates: Coord[], distanceMeter?: number) {
    if (coordinates.length < 2) return;
    addNode(fromNodeId, coordinates[0]);
    addNode(toNodeId, coordinates[coordinates.length - 1]);
    const distance = distanceMeter && Number.isFinite(distanceMeter) ? distanceMeter : lineDistanceMeter(coordinates);
    graph.get(fromNodeId)!.push({ to: toNodeId, distanceMeter: distance, coordinates });
    graph.get(toNodeId)!.push({ to: fromNodeId, distanceMeter: distance, coordinates: [...coordinates].reverse() });
  }

  segments.forEach((feature) => {
    const edgeId = String(feature.properties.edgeId);
    const coordinates = feature.geometry.coordinates;
    if (coordinates.length < 2) return;
    const fromNodeId = String(feature.properties.fromNodeId ?? `edge:${edgeId}:from:${coordKey(coordinates[0])}`);
    const toNodeId = String(feature.properties.toNodeId ?? `edge:${edgeId}:to:${coordKey(coordinates[coordinates.length - 1])}`);
    addNode(fromNodeId, coordinates[0]);
    addNode(toNodeId, coordinates[coordinates.length - 1]);
    if (deletedEdgeIds.has(edgeId)) return;
    addEdge(fromNodeId, toNodeId, coordinates, Number(feature.properties.lengthMeter));
  });

  edits.forEach((edit, index) => {
    if (edit.action !== "add_segment") return;
    const coordinates = edit.geom.coordinates;
    addEdge(`draft:${index}:from`, `draft:${index}:to`, coordinates);
  });

  const start = nearestNode(from, nodeCoords);
  const goal = nearestNode(to, nodeCoords);
  if (!start || !goal) return null;

  return shortestPath(graph, start.nodeId, goal.nodeId);
}

function shortestPath(graph: Map<string, GraphEdge[]>, startId: string, goalId: string): RoutePreview | null {
  const distances = new Map<string, number>([[startId, 0]]);
  const previous = new Map<string, { nodeId: string; coordinates: Coord[] }>();
  const queue: Array<{ nodeId: string; distanceMeter: number }> = [{ nodeId: startId, distanceMeter: 0 }];

  while (queue.length) {
    queue.sort((a, b) => a.distanceMeter - b.distanceMeter);
    const current = queue.shift()!;
    if (current.distanceMeter !== distances.get(current.nodeId)) continue;
    if (current.nodeId === goalId) break;

    for (const edge of graph.get(current.nodeId) ?? []) {
      const nextDistance = current.distanceMeter + edge.distanceMeter;
      if (nextDistance >= (distances.get(edge.to) ?? Infinity)) continue;
      distances.set(edge.to, nextDistance);
      previous.set(edge.to, { nodeId: current.nodeId, coordinates: edge.coordinates });
      queue.push({ nodeId: edge.to, distanceMeter: nextDistance });
    }
  }

  if (startId !== goalId && !previous.has(goalId)) return null;

  const coordinates: Coord[] = [];
  let cursor = goalId;
  while (cursor !== startId) {
    const step = previous.get(cursor);
    if (!step) return null;
    coordinates.unshift(...step.coordinates);
    cursor = step.nodeId;
  }

  return {
    coordinates: dedupeAdjacentCoords(coordinates),
    distanceMeter: distances.get(goalId) ?? 0,
  };
}

function nearestNode(coord: Coord, nodeCoords: Map<string, Coord>): { nodeId: string; distanceMeter: number } | null {
  let best: { nodeId: string; distanceMeter: number } | null = null;
  for (const [nodeId, nodeCoord] of nodeCoords.entries()) {
    const distance = distanceMeter(coord, nodeCoord);
    if (!best || distance < best.distanceMeter) best = { nodeId, distanceMeter: distance };
  }
  return best;
}

function dedupeAdjacentCoords(coordinates: Coord[]): Coord[] {
  return coordinates.filter((coord, index) => index === 0 || coordKey(coord) !== coordKey(coordinates[index - 1]));
}

function coordKey(coord: Coord): string {
  return `${coord[0].toFixed(7)},${coord[1].toFixed(7)}`;
}

function lineDistanceMeter(coordinates: Coord[]): number {
  return coordinates.slice(1).reduce((sum, coord, index) => sum + distanceMeter(coordinates[index], coord), 0);
}

function distanceMeter(a: Coord, b: Coord): number {
  const radius = 6_371_000;
  const toRad = Math.PI / 180;
  const lat1 = a[1] * toRad;
  const lat2 = b[1] * toRad;
  const dLat = (b[1] - a[1]) * toRad;
  const dLng = (b[0] - a[0]) * toRad;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * radius * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}
