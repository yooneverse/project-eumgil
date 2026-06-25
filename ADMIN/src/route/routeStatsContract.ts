import type { BottleneckHotspot, BottleneckRouteSegment } from "../map/adminBottleneckMap";

export type RouteStatsMobilityFilter =
  | "ALL"
  | "MOBILITY_SUPPORT"
  | "POWER_WHEELCHAIR"
  | "MANUAL_WHEELCHAIR"
  | "VISUAL_IMPAIRMENT";
export type RouteStatsTimeGranularity = "DAILY" | "WEEKLY" | "MONTHLY";
export type RouteStatsMapMode = "HEATMAP" | "WAYPOINT" | "DENSITY";
export type RouteStatsDistributionMetric = "COUNT" | "SHARE";

export interface RouteStatsSelectOption<T extends string> {
  value: T;
  label: string;
}

export interface RouteStatsFilterState {
  mobility: RouteStatsMobilityFilter;
  timeGranularity: RouteStatsTimeGranularity;
}

export interface RouteStatsTopRoute {
  rank: number;
  name: string;
  routeCount: number;
  share: number;
  tone: "danger" | "hot" | "warm" | "clear";
}

export interface RouteStatsBreakdownItem {
  label: string;
  count: number;
  share: number;
  color: string;
}

export interface RouteStatsHeatmapMatrix {
  title: string;
  helperText: string;
  xLabels: string[];
  yLabels: string[];
  values: number[][];
}

export interface RouteStatsSeries {
  label: string;
  color: string;
  values: number[];
}

export interface RouteStatsDistanceDistribution {
  buckets: string[];
  series: Array<{
    label: string;
    color: string;
    count: number[];
    share: number[];
  }>;
}

export interface RouteStatsAverageDistanceItem {
  label: string;
  kilometer: number;
  color: string;
}

export interface RouteStatsInfoItem {
  label: string;
  value: string;
}

export interface RouteStatsResponse {
  summary: {
    totalTrips: number;
    metricLabel: string;
  };
  filters: {
    mobilityOptions: RouteStatsSelectOption<RouteStatsMobilityFilter>[];
    timeGranularityOptions: RouteStatsSelectOption<RouteStatsTimeGranularity>[];
    defaults: RouteStatsFilterState;
  };
  map: {
    title: string;
    legendMinLabel: string;
    legendMaxLabel: string;
    metricOptions: RouteStatsSelectOption<string>[];
    selectedMetric: string;
    modeOptions: RouteStatsSelectOption<RouteStatsMapMode>[];
    selectedMode: RouteStatsMapMode;
    showDistrictBoundary: boolean;
    hotspots: BottleneckHotspot[];
    routeSegments: BottleneckRouteSegment[];
  };
  topRoutesDefinition: string;
  topRoutes: RouteStatsTopRoute[];
  typeBreakdown: RouteStatsBreakdownItem[];
  hourlyHeatmap: RouteStatsHeatmapMatrix;
  speedTrend: {
    labels: string[];
    series: RouteStatsSeries[];
  };
  distanceDistribution: RouteStatsDistanceDistribution;
  averageDistance: RouteStatsAverageDistanceItem[];
  infoItems: RouteStatsInfoItem[];
}

const routeHotspots: BottleneckHotspot[] = [
  { id: "nampo-port", name: "남포역-자갈치역", lat: 35.097914, lng: 129.034979, averageSpeedMps: 0.31, reportCount: 24, sampleCount: 1610 },
  { id: "yeongdo-entry", name: "영도대교 진입부", lat: 35.093927, lng: 129.043808, averageSpeedMps: 0.36, reportCount: 13, sampleCount: 1180 },
  { id: "bupyeong-market", name: "부평시장 보행축", lat: 35.102253, lng: 129.026106, averageSpeedMps: 0.42, reportCount: 11, sampleCount: 1020 },
  { id: "choryang", name: "초량 이바구길 진입", lat: 35.116888, lng: 129.039221, averageSpeedMps: 0.37, reportCount: 16, sampleCount: 1260 },
  { id: "gamcheon", name: "감천문화마을", lat: 35.097577, lng: 129.010124, averageSpeedMps: 0.33, reportCount: 18, sampleCount: 1100 },
  { id: "songdo", name: "송도해수욕장", lat: 35.075527, lng: 129.016861, averageSpeedMps: 0.58, reportCount: 5, sampleCount: 690 },
  { id: "gwangalli", name: "광안리 보행로", lat: 35.153169, lng: 129.118666, averageSpeedMps: 0.62, reportCount: 4, sampleCount: 760 },
];

const routeSegments: BottleneckRouteSegment[] = [
  {
    id: "yeongdo-corridor",
    averageSpeedMps: 0.31,
    sampleCount: 1610,
    points: [
      { lat: 35.10231, lng: 129.034683 },
      { lat: 35.100942, lng: 129.035079 },
      { lat: 35.0999, lng: 129.035249 },
      { lat: 35.098203, lng: 129.035611 },
      { lat: 35.097181, lng: 129.035176 },
      { lat: 35.097914, lng: 129.034979 },
      { lat: 35.095992, lng: 129.039012 },
      { lat: 35.093927, lng: 129.043808 },
    ],
  },
  {
    id: "market-connector",
    averageSpeedMps: 0.42,
    sampleCount: 1020,
    points: [
      { lat: 35.102253, lng: 129.026106 },
      { lat: 35.101743, lng: 129.028476 },
      { lat: 35.10016, lng: 129.029692 },
      { lat: 35.099216, lng: 129.030229 },
      { lat: 35.0985, lng: 129.032266 },
      { lat: 35.097675, lng: 129.033921 },
      { lat: 35.097914, lng: 129.034979 },
    ],
  },
  {
    id: "choryang-axis",
    averageSpeedMps: 0.37,
    sampleCount: 1260,
    points: [
      { lat: 35.116888, lng: 129.039221 },
      { lat: 35.114683, lng: 129.03852 },
      { lat: 35.112301, lng: 129.037324 },
      { lat: 35.108583, lng: 129.036069 },
      { lat: 35.106141, lng: 129.034992 },
      { lat: 35.103911, lng: 129.034426 },
      { lat: 35.100942, lng: 129.035079 },
      { lat: 35.097914, lng: 129.034979 },
    ],
  },
  {
    id: "gamcheon-loop",
    averageSpeedMps: 0.33,
    sampleCount: 1100,
    points: [
      { lat: 35.097577, lng: 129.010124 },
      { lat: 35.098123, lng: 129.009639 },
      { lat: 35.098989, lng: 129.009585 },
      { lat: 35.09941, lng: 129.009395 },
      { lat: 35.099349, lng: 129.008291 },
      { lat: 35.099728, lng: 129.008013 },
    ],
  },
  {
    id: "coast-line",
    averageSpeedMps: 0.58,
    sampleCount: 690,
    points: [
      { lat: 35.075527, lng: 129.016861 },
      { lat: 35.079281, lng: 129.019808 },
      { lat: 35.083545, lng: 129.024619 },
      { lat: 35.089132, lng: 129.030143 },
      { lat: 35.097914, lng: 129.034979 },
    ],
  },
  {
    id: "gwangalli-band",
    averageSpeedMps: 0.62,
    sampleCount: 760,
    points: [
      { lat: 35.153169, lng: 129.118666 },
      { lat: 35.151447, lng: 129.117118 },
      { lat: 35.14978, lng: 129.115145 },
      { lat: 35.147625, lng: 129.111928 },
    ],
  },
];

export const routeStatsMockResponse: RouteStatsResponse = {
  summary: {
    totalTrips: 35284,
    metricLabel: "총 이동 건수",
  },
  filters: {
    mobilityOptions: [
      { value: "ALL", label: "전체" },
      { value: "MOBILITY_SUPPORT", label: "보행약자" },
      { value: "POWER_WHEELCHAIR", label: "자동휠체어" },
      { value: "MANUAL_WHEELCHAIR", label: "수동휠체어" },
      { value: "VISUAL_IMPAIRMENT", label: "시각장애인" },
    ],
    timeGranularityOptions: [
      { value: "DAILY", label: "일별" },
      { value: "WEEKLY", label: "주별" },
      { value: "MONTHLY", label: "월별" },
    ],
    defaults: {
      mobility: "ALL",
      timeGranularity: "DAILY",
    },
  },
  map: {
    title: "이동 경로 밀도 히트맵",
    legendMinLabel: "낮음",
    legendMaxLabel: "높음",
    metricOptions: [
      { value: "route-density", label: "경로 밀도" },
      { value: "slow-speed", label: "저속 구간" },
      { value: "report-impact", label: "신고 반영도" },
    ],
    selectedMetric: "route-density",
    modeOptions: [
      { value: "HEATMAP", label: "히트맵" },
      { value: "WAYPOINT", label: "점 표시만" },
      { value: "DENSITY", label: "밀도 맵" },
    ],
    selectedMode: "HEATMAP",
    showDistrictBoundary: true,
    hotspots: routeHotspots,
    routeSegments,
  },
  topRoutesDefinition: "집계 기준: 경로가 해당 대표 이동축을 1회 이상 통과하면 1건으로 집계",
  topRoutes: [
    { rank: 1, name: "남포역-광복로 보행축", routeCount: 1248, share: 0.035, tone: "danger" },
    { rank: 2, name: "해운대해수욕장-동백섬 해안축", routeCount: 1102, share: 0.031, tone: "hot" },
    { rank: 3, name: "영도대교 진입 보행축", routeCount: 967, share: 0.027, tone: "hot" },
    { rank: 4, name: "광안리 해변 순환축", routeCount: 904, share: 0.026, tone: "warm" },
    { rank: 5, name: "다대포 해안공원 산책축", routeCount: 781, share: 0.022, tone: "warm" },
    { rank: 6, name: "낙동강하구 철새공원 순환축", routeCount: 672, share: 0.019, tone: "warm" },
    { rank: 7, name: "동래읍성 역사 보행축", routeCount: 636, share: 0.018, tone: "clear" },
    { rank: 8, name: "온천천 카페거리 생활축", routeCount: 592, share: 0.017, tone: "clear" },
    { rank: 9, name: "송도해수욕장-암남공원 해안축", routeCount: 533, share: 0.015, tone: "clear" },
    { rank: 10, name: "부산역-차이나타운 연결축", routeCount: 487, share: 0.014, tone: "clear" },
  ],
  typeBreakdown: [
    { label: "보행약자", count: 14576, share: 0.413, color: "#2f7df6" },
    { label: "자동휠체어", count: 4058, share: 0.115, color: "#8b5cf6" },
    { label: "수동휠체어", count: 9620, share: 0.273, color: "#f59e0b" },
    { label: "시각장애인", count: 7030, share: 0.199, color: "#16a34a" },
  ],
  hourlyHeatmap: {
    title: "요일·시간대 이동 분포",
    helperText: "가로는 출발 시간대, 세로는 요일 기준 이동 건수 밀도",
    xLabels: ["00-04", "04-08", "08-12", "12-16", "16-20", "20-24"],
    yLabels: ["월", "화", "수", "목", "금", "토", "일"],
    values: [
      [0.08, 0.14, 0.42, 0.55, 0.61, 0.31],
      [0.07, 0.13, 0.39, 0.53, 0.58, 0.29],
      [0.09, 0.15, 0.44, 0.57, 0.63, 0.34],
      [0.08, 0.16, 0.47, 0.6, 0.67, 0.36],
      [0.11, 0.21, 0.58, 0.72, 0.81, 0.49],
      [0.16, 0.24, 0.51, 0.64, 0.76, 0.62],
      [0.12, 0.18, 0.33, 0.46, 0.59, 0.41],
    ],
  },
  speedTrend: {
    labels: ["00시", "04시", "08시", "12시", "16시", "20시"],
    series: [
      { label: "보행약자", color: "#2f7df6", values: [10.4, 11.1, 10.8, 10.1, 10.9, 10.3] },
      { label: "자동휠체어", color: "#8b5cf6", values: [8.8, 9.2, 8.9, 8.4, 9.1, 8.7] },
      { label: "수동휠체어", color: "#f59e0b", values: [6.4, 6.8, 6.3, 5.9, 6.5, 6.1] },
      { label: "시각장애인", color: "#16a34a", values: [5.8, 6.1, 5.7, 5.4, 6.0, 5.6] },
    ],
  },
  distanceDistribution: {
    buckets: ["~1km", "1~3km", "3~5km", "5~10km", "10km+"],
    series: [
      { label: "보행약자", color: "#2f7df6", count: [19, 23, 17, 9, 4], share: [0.19, 0.23, 0.17, 0.09, 0.04] },
      { label: "자동휠체어", color: "#8b5cf6", count: [12, 21, 18, 11, 6], share: [0.12, 0.21, 0.18, 0.11, 0.06] },
      { label: "수동휠체어", color: "#f59e0b", count: [28, 25, 13, 6, 2], share: [0.28, 0.25, 0.13, 0.06, 0.02] },
      { label: "시각장애인", color: "#16a34a", count: [24, 27, 15, 7, 2], share: [0.24, 0.27, 0.15, 0.07, 0.02] },
    ],
  },
  averageDistance: [
    { label: "전체", kilometer: 14.6, color: "#2f7df6" },
    { label: "보행약자", kilometer: 12.3, color: "#56a9f5" },
    { label: "자동휠체어", kilometer: 10.8, color: "#8b5cf6" },
    { label: "수동휠체어", kilometer: 8.4, color: "#f59e0b" },
    { label: "시각장애인", kilometer: 7.6, color: "#16a34a" },
  ],
  infoItems: [
    { label: "수집 기간", value: "2025.05.12 ~ 2025.05.18 (최근 7일)" },
    { label: "수집 기준", value: "사용자 동의 기반 익명화 이동 로그" },
    { label: "업데이트 주기", value: "1시간 단위 (최종 업데이트 15:00)" },
    { label: "활용 주의", value: "운영 판단용 참고 데이터이며 개별 사용자 추적은 제공하지 않음" },
  ],
};
