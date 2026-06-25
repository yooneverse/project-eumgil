import type { FacilityFeature, FacilityPayload, PlaceCategory } from "../types";
import { facilityCategoryOrder } from "../map/facilityStyle";

export function filterFacilityPayloadByCategories(
  payload: FacilityPayload | undefined,
  selectedCategories: readonly PlaceCategory[],
): FacilityPayload | undefined {
  if (!payload) return payload;

  const selectedCategorySet = new Set(selectedCategories);
  const features = payload.facilities.features.filter((feature) => selectedCategorySet.has(feature.properties.category));
  const visibleCategoryCounts = countFacilityCategories(features);

  return {
    ...payload,
    summary: {
      ...payload.summary,
      visibleFacilityCount: features.length,
      visibleCategoryCounts,
    },
    facilities: {
      ...payload.facilities,
      features,
    },
  };
}

export function countFacilityCategories(features: readonly FacilityFeature[]): Record<PlaceCategory, number> {
  const counts = Object.fromEntries(facilityCategoryOrder.map((category) => [category, 0])) as Record<PlaceCategory, number>;

  features.forEach((feature) => {
    counts[feature.properties.category] += 1;
  });

  return counts;
}
