import { describe, expect, it } from "vitest";
import source from "./App.tsx?raw";

describe("App selected facility sync", () => {
  it("reconciles the selected facility when the facility payload is refetched", () => {
    expect(source).toContain("const updatedFacility = filteredFacilityPayload.facilities.features.find");
    expect(source).toContain("setSelectedFacility(updatedFacility)");
  });

  it("clears the selected facility when the latest visible payload no longer contains it", () => {
    expect(source).toContain("if (!updatedFacility) {");
    expect(source).toContain("setSelectedFacility(null)");
    expect(source).toContain("setFacilityPickedLocation(null)");
    expect(source).toContain("setFacilityLocationPickEnabled(false)");
  });
});
