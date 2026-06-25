import { describe, expect, it } from "vitest";
import { filterFacilityPayloadByCategories } from "./facilityFilters";
import type { FacilityFeature, FacilityPayload, PlaceCategory } from "../types";

function feature(placeId: string, category: PlaceCategory): FacilityFeature {
  return {
    type: "Feature",
    geometry: {
      type: "Point",
      coordinates: [129.0756, 35.1796],
    },
    properties: {
      placeId,
      name: `${category}-${placeId}`,
      category,
      address: "부산광역시",
    },
  };
}

function payload(features: FacilityFeature[]): FacilityPayload {
  return {
    summary: {
      facilityCount: features.length,
      visibleFacilityCount: features.length,
      providerPlaceIdCount: 2,
      categoryCounts: {
        PUBLIC_OFFICE: 1,
        WELFARE: 1,
        TOURIST_SPOT: 1,
      },
      visibleCategoryCounts: {
        PUBLIC_OFFICE: 1,
        WELFARE: 1,
        TOURIST_SPOT: 1,
      },
    },
    facilities: {
      type: "FeatureCollection",
      features,
    },
  };
}

describe("facility category filters", () => {
  it("keeps only the selected categories and recalculates visible counts", () => {
    const source = payload([
      feature("1", "PUBLIC_OFFICE"),
      feature("2", "WELFARE"),
      feature("3", "TOURIST_SPOT"),
    ]);

    const filtered = filterFacilityPayloadByCategories(source, ["PUBLIC_OFFICE", "TOURIST_SPOT"]);

    expect(filtered?.facilities.features.map((item) => item.properties.placeId)).toEqual(["1", "3"]);
    expect(filtered?.summary?.visibleFacilityCount).toBe(2);
    expect(filtered?.summary?.visibleCategoryCounts?.PUBLIC_OFFICE).toBe(1);
    expect(filtered?.summary?.visibleCategoryCounts?.TOURIST_SPOT).toBe(1);
    expect(filtered?.summary?.visibleCategoryCounts?.WELFARE).toBe(0);
    expect(source.facilities.features).toHaveLength(3);
  });

  it("supports an empty selection without mutating total summary values", () => {
    const source = payload([feature("1", "PUBLIC_OFFICE")]);

    const filtered = filterFacilityPayloadByCategories(source, []);

    expect(filtered?.facilities.features).toEqual([]);
    expect(filtered?.summary?.visibleFacilityCount).toBe(0);
    expect(filtered?.summary?.facilityCount).toBe(1);
    expect(filtered?.summary?.providerPlaceIdCount).toBe(2);
  });
});
