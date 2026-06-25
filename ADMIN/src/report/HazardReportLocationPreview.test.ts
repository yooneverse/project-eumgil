import { describe, expect, it } from "vitest";
import appSource from "../App.tsx?raw";
import facilityMapSource from "../map/FacilityMap.tsx?raw";
import segmentMapSource from "../map/SegmentMap.tsx?raw";
import source from "./HazardReportLocationPreview.tsx?raw";
import roadviewSource from "./HazardReportRoadviewPreview.tsx?raw";

const mapOverlaySources = [
  source,
  roadviewSource,
  appSource,
  segmentMapSource,
  facilityMapSource,
];

const removedInstructionCopies = [
  "지도를 드래그한 뒤 원하는 지점을 클릭하면 해당 위치 기준 로드뷰를 확인할 수 있습니다.",
  "Roadview 도구를 누른 뒤 지도를 클릭하면 Kakao Roadview를 엽니다.",
  "편의시설 점을 클릭하면 근처 Roadview를 엽니다.",
  "지도에서 클릭한 지점 근처 Roadview를 엽니다.",
  "지도를 클릭해 가까운 로드뷰 지점을 다시 선택해보세요.",
];

describe("HazardReportLocationPreview map chrome", () => {
  it("does not cover the map with the old roadview instruction hint", () => {
    expect(source).not.toContain("hazard-location-map__hint");
    for (const sourceText of mapOverlaySources) {
      for (const copy of removedInstructionCopies) {
        expect(sourceText).not.toContain(copy);
      }
    }
  });

  it("renders roadview empty state only while the dock is open", () => {
    expect(appSource).toContain("roadviewDock.open && roadviewDock.message");
    expect(appSource).not.toContain("{roadviewDock.message && <div className=\"roadview-empty\"");
  });

  it("renders location markers after the Kakao map instance becomes ready", () => {
    expect(source).toContain("const [mapReady, setMapReady] = useState(false);");
    expect(source).toContain("setMapReady(true);");
    expect(source).toContain("if (!mapReady || !mapRef.current || !window.kakao?.maps)");
    expect(source).toContain("[mapReady, point.lat, point.lng, roadviewPoint?.lat, roadviewPoint?.lng]");
  });
});
