import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { FacilityMap } from "./FacilityMap";
import type { AdminPlaceDetailResponse, FacilityFeature, FacilityPayload } from "../types";

const selectedFeature: FacilityFeature = {
  type: "Feature",
  geometry: {
    type: "Point",
    coordinates: [128.9234, 35.1023],
  },
  properties: {
    placeId: "9836",
    providerPlaceId: "19512345",
    name: "명지 대방노블랜드 오션뷰1차",
    category: "ETC",
    address: "부산광역시 강서구 명지국제5로 30",
  },
};

const detail: AdminPlaceDetailResponse = {
  placeId: 9836,
  providerPlaceId: "19512345",
  name: "명지 대방노블랜드 오션뷰1차",
  category: "ETC",
  address: "부산광역시 강서구 명지국제5로 30",
  point: {
    lat: 35.1023,
    lng: 128.9234,
  },
  accessibilityFeatures: [
    { featureType: "accessibleEntrance", isAvailable: true },
    { featureType: "accessibleToilet", isAvailable: true },
    { featureType: "elevator", isAvailable: false },
  ],
};

const payload: FacilityPayload = {
  summary: {
    visibleFacilityCount: 1,
    visibleCategoryCounts: {
      ETC: 1,
    },
  },
  facilities: {
    type: "FeatureCollection",
    features: [selectedFeature],
  },
};

describe("FacilityMap selected card", () => {
  it("renders a compact map-bottom selected facility card with detail attributes", () => {
    const html = renderToStaticMarkup(
      <FacilityMap
        payload={payload}
        loading={false}
        selectedFeature={selectedFeature}
        selectedDetail={detail}
        selectedCategories={["ETC"]}
        onSelectFeature={() => undefined}
        roadviewContainerRef={{ current: null }}
        onRoadviewChange={() => undefined}
      />,
    );

    expect(html).toContain("facility-selected-map-card");
    expect(html).toContain("명지 대방노블랜드 오션뷰1차");
    expect(html).toContain("부산광역시 강서구 명지국제5로 30");
    expect(html).toContain("단차 없는 출입");
    expect(html).toContain("장애인 화장실");
    expect(html).not.toContain("엘리베이터");
  });
});
