import { describe, expect, it } from "vitest";
import source from "./App.tsx?raw";

describe("App selected road segment sync", () => {
  it("reconciles the selected segment when road-network payload is refetched", () => {
    expect(source).toContain("const updatedSegment = payloadQuery.data.segments.features.find");
    expect(source).toContain("setSelectedSegment(updatedSegment)");
  });
});
