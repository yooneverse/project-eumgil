import { describe, expect, it } from "vitest";
import type { EditAction, SegmentFeature } from "../types";
import {
  coordBounds,
  describeCrossWalkProjection,
  describeSideLineNodeSnap,
  draftEndpointNodeCandidates,
  draftSegmentFeatures,
  isSameSnappedNode,
  roadNodeCandidates,
  resetPolygonDeleteSelection,
  segmentEndpointNodeCandidates,
  segmentsIntersectingBounds,
  segmentsTouchingPolygon,
  snapToSegmentEndpointNode,
  twoPointAddDraft,
  visibleSegmentFeatures,
} from "./draftSegments";

function segment(edgeId: string, coordinates: Array<[number, number]>, fromNodeId?: string, toNodeId?: string): SegmentFeature {
  return {
    type: "Feature",
    geometry: { type: "LineString", coordinates },
    properties: { edgeId, fromNodeId, toNodeId, segmentType: "SIDE_LINE" },
  };
}

describe("draft segment helpers", () => {
  it("hides deleted existing segments and materializes added draft segments", () => {
    const edits: EditAction[] = [
      { action: "delete_segment", edgeId: "2" },
      {
        action: "add_segment",
        segmentType: "CROSS_WALK",
        geom: { type: "LineString", coordinates: [[129, 35], [129.001, 35]] },
      },
    ];

    expect(visibleSegmentFeatures([segment("1", []), segment("2", [])], edits).map((item) => item.properties.edgeId)).toEqual(["1"]);
    expect(draftSegmentFeatures(edits)).toMatchObject([
      { properties: { edgeId: "draft:1", segmentType: "CROSS_WALK" } },
    ]);
  });

  it("selects segments whose coordinate bounds intersect a drag box", () => {
    const matches = segmentsIntersectingBounds(
      [
        segment("inside", [[129, 35], [129.001, 35.001]]),
        segment("outside", [[130, 36], [130.001, 36.001]]),
      ],
      coordBounds([128.999, 34.999], [129.002, 35.002]),
    );

    expect(matches.map((item) => item.properties.edgeId)).toEqual(["inside"]);
  });

  it("does not select an L-shaped segment whose bbox intersects but line does not", () => {
    const matches = segmentsIntersectingBounds(
      [segment("around", [[129, 35], [129, 35.01], [129.01, 35.01]])],
      coordBounds([129.004, 35.004], [129.006, 35.006]),
    );

    expect(matches).toEqual([]);
  });

  it("matches the existing editor polygon delete rule", () => {
    const matches = segmentsTouchingPolygon(
      [
        segment("inside", [[129.005, 35.005], [129.006, 35.006]]),
        segment("outside", [[129.02, 35.02], [129.03, 35.03]]),
      ],
      [[129, 35], [129.01, 35], [129.01, 35.01], [129, 35.01]],
    );

    expect(matches.map((item) => item.properties.edgeId)).toEqual(["inside"]);
  });

  it("finishes one add draft after exactly two points", () => {
    const result = twoPointAddDraft("CROSS_WALK", [[129, 35], [129.001, 35]]);

    expect(result.edit).toMatchObject({
      action: "add_segment",
      segmentType: "CROSS_WALK",
      geom: { coordinates: [[129, 35], [129.001, 35]] },
    });
    expect(result.remainingPoints).toEqual([]);
  });

  it("allows very short add drafts because valid network links can be short", () => {
    const result = twoPointAddDraft("SIDE_LINE", [[129, 35], [129.000001, 35]]);

    expect(result.edit).toMatchObject({
      action: "add_segment",
      segmentType: "SIDE_LINE",
      geom: { coordinates: [[129, 35], [129.000001, 35]] },
    });
    expect(result.remainingPoints).toEqual([]);
    expect(result.rejectedReason).toBeUndefined();
  });

  it("snaps SIDE_LINE endpoints to nearby existing segment nodes", () => {
    const candidates = segmentEndpointNodeCandidates([
      segment("1", [[129, 35], [129.001, 35]], "10", "11"),
    ]);

    const snapped = snapToSegmentEndpointNode([129.0000005, 35], candidates);
    const unsnapped = snapToSegmentEndpointNode([129.002, 35], candidates);

    expect(snapped).toMatchObject({ snapped: true, nodeId: "10", coord: [129, 35] });
    expect(unsnapped).toMatchObject({ snapped: false, coord: [129.002, 35] });
  });

  it("detects add endpoints that snap to the same existing node", () => {
    const candidates = segmentEndpointNodeCandidates([
      segment("1", [[129, 35], [129.001, 35]], "10", "11"),
    ]);
    const first = snapToSegmentEndpointNode([129.0000005, 35], candidates);
    const second = snapToSegmentEndpointNode([128.9999995, 35], candidates);

    expect(isSameSnappedNode(first, second)).toBe(true);
  });

  it("includes POC-style node refs when creating add drafts", () => {
    const candidates = roadNodeCandidates([
      {
        type: "Feature",
        geometry: { type: "Point", coordinates: [129, 35] },
        properties: { vertexId: 10, sourceNodeKey: "node:10" },
      },
      {
        type: "Feature",
        geometry: { type: "Point", coordinates: [129.001, 35] },
        properties: { vertexId: 11, sourceNodeKey: "node:11" },
      },
    ]);
    const first = snapToSegmentEndpointNode([129.0000005, 35], candidates);
    const second = snapToSegmentEndpointNode([129.0010005, 35], candidates);

    const result = twoPointAddDraft("SIDE_LINE", [first.coord, second.coord], [first, second]);

    expect(result.edit).toMatchObject({
      fromNode: { mode: "existing", vertexId: 10 },
      toNode: { mode: "existing", vertexId: 11 },
    });
  });

  it("uses newly added draft endpoints as SIDE_LINE snap candidates", () => {
    const first = snapToSegmentEndpointNode([129, 35], []);
    const second = snapToSegmentEndpointNode([129.001, 35], []);
    const result = twoPointAddDraft("SIDE_LINE", [first.coord, second.coord], [first, second]);

    const candidates = draftEndpointNodeCandidates(result.edit ? [result.edit] : []);
    const snappedToDraftEnd = snapToSegmentEndpointNode([129.0010005, 35], candidates);

    expect(candidates.map((candidate) => candidate.nodeId)).toEqual([
      "manual_node:129.00000000:35.00000000",
      "manual_node:129.00100000:35.00000000",
    ]);
    expect(snappedToDraftEnd).toMatchObject({
      snapped: true,
      nodeId: "manual_node:129.00100000:35.00000000",
      coord: [129.001, 35],
      nodeRef: { mode: "new", tempNodeId: "manual_node:129.00100000:35.00000000" },
    });
  });

  it("describes SIDE_LINE node snap as a saved adjustment", () => {
    expect(describeSideLineNodeSnap("205921", 0.8873)).toBe(
      "SIDE_LINE 끝점을 node #205921에 0.9m 보정했습니다. 저장 시 보정 좌표로 반영됩니다.",
    );
  });

  it("describes CROSS_WALK projection as a saved adjustment", () => {
    expect(describeCrossWalkProjection(0.8873)).toBe(
      "CROSS_WALK 끝점을 기존 선 위로 0.9m 보정했습니다. 저장 시 보정 좌표로 반영됩니다.",
    );
  });

  it("keeps polygon delete mode active after clearing a completed polygon", () => {
    expect(resetPolygonDeleteSelection(true)).toEqual({ active: true, points: [], pointCount: 0 });
  });
});
