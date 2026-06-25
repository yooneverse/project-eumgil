import { describe, expect, it } from "vitest";
import { buildRoutePreview } from "./routeGraph";
import type { SegmentFeature } from "../types";

function segment(edgeId: string, fromNodeId: string, toNodeId: string, coordinates: Array<[number, number]>): SegmentFeature {
  return {
    type: "Feature",
    geometry: { type: "LineString", coordinates },
    properties: { edgeId, fromNodeId, toNodeId, segmentType: "SIDE_LINE" },
  };
}

describe("buildRoutePreview", () => {
  it("builds a connected route through existing segments", () => {
    const route = buildRoutePreview(
      [
        segment("1", "A", "B", [[129.0, 35.0], [129.001, 35.0]]),
        segment("2", "B", "C", [[129.001, 35.0], [129.002, 35.0]]),
      ],
      [],
      [129.0, 35.0],
      [129.002, 35.0],
    );

    expect(route).not.toBeNull();
    expect(route?.coordinates[0]).toEqual([129.0, 35.0]);
    expect(route?.coordinates[route.coordinates.length - 1]).toEqual([129.002, 35.0]);
  });

  it("respects delete_segment edits", () => {
    const route = buildRoutePreview(
      [
        segment("1", "A", "B", [[129.0, 35.0], [129.001, 35.0]]),
        segment("2", "B", "C", [[129.001, 35.0], [129.002, 35.0]]),
      ],
      [{ action: "delete_segment", edgeId: "2" }],
      [129.0, 35.0],
      [129.002, 35.0],
    );

    expect(route).toBeNull();
  });
});
