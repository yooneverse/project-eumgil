import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { RouteStatsPage } from "./RouteStatsPage";
import { routeStatsMockResponse } from "./routeStatsContract";

describe("RouteStatsPage", () => {
  it("renders the approved route statistics dashboard using the route stats contract", () => {
    const html = renderToStaticMarkup(<RouteStatsPage data={routeStatsMockResponse} />);

    expect(html).toContain("route-stats-page");
    expect(html).toContain("route-stats-top-layout");
    expect(html).toContain("route-stats-routes-card");
    expect(html).toContain("route-stats-heat-grid-panel");
    expect(html).toContain("route-stats-donut-card");
    expect(html).toContain("route-stats-map-shell");
    expect(html).toContain("route-stats-inline-metric");
    expect(html).toContain("route-stats-visual-body");
    expect(html).toContain("이동 경로 밀도 히트맵");
    expect(html).toContain("주요 이동 구간 TOP 7");
    expect(html).toContain("집계 기준: 경로가 해당 대표 이동축을 1회 이상 통과하면 1건으로 집계");
    expect(html).toContain("이동 유형 비율");
    expect(html).toContain("요일·시간대 이동 분포");
    expect(html).toContain("시간대별 평균 속도");
    expect(html).toContain("거리 구간별 분포");
    expect(html).toContain("평균 이동 거리");
    expect(html).toContain("필터 초기화");
    expect(html).toContain("전체");
    expect(html).toContain("보행약자");
    expect(html).toContain("자동휠체어");
    expect(html).toContain("시각장애인");
    expect(html).toContain("표시 지표");
    expect(html).toContain("행정동 경계");
    expect(html).toContain("35,284건");
    expect(html).not.toContain("낮음");
    expect(html).not.toContain("높음");
    expect(html).toContain("낙동강하구 철새공원 순환축");
    expect(html).toContain("동래읍성 역사 보행축");
    expect(html).not.toContain("온천천 카페거리 생활축");
    expect(html).not.toContain("가로는 출발 시간대, 세로는 요일 기준 이동 건수 밀도");
    expect(html).not.toContain("성별");
    expect(html).not.toContain("연령대");
    expect(html).not.toContain("거주지");
    expect(html).not.toContain("통계 데이터 안내");
    expect(html).not.toContain("route-stats-side-panel");
    expect(html).not.toContain("route-stats-side-summary-grid");
    expect(html).not.toContain("route-stats-aux-column");
    expect(html).not.toContain("route-stats-map-footer");
    expect(html.indexOf("요일·시간대 이동 분포")).toBeLessThan(html.indexOf("시간대별 평균 속도"));
  });

  it("shows only loading state when real route stats data has not arrived yet", () => {
    const html = renderToStaticMarkup(<RouteStatsPage loading />);

    expect(html).toContain("경로/이동 통계를 불러오는 중입니다.");
    expect(html).not.toContain("35,284건");
    expect(html).not.toContain("주요 이동 구간 TOP 7");
  });

  it("does not render duplicate loading state when stale data already exists", () => {
    const html = renderToStaticMarkup(<RouteStatsPage data={routeStatsMockResponse} loading />);

    expect(html).not.toContain("경로/이동 통계를 불러오는 중입니다.");
  });
});
