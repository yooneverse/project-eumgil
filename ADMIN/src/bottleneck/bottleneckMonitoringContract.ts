import type { BottleneckHotspot, BottleneckRouteSegment } from "../map/adminBottleneckMap";
import type {
  AdminDashboardBottleneckResponse,
  AdminDashboardTopBottleneck,
} from "../types";

const integerFormatter = new Intl.NumberFormat("ko-KR");

const distributionWeights = [0.352, 0.25, 0.219, 0.117, 0.062];

const fallbackTopBottlenecks: AdminDashboardTopBottleneck[] = [
  { rank: 1, id: "seomyeon-exit7", name: "서면역 7번 출구 주변", averageSpeedMps: 0.28, reportCount: 14, sampleCount: 612 },
  { rank: 2, id: "haeundae-beach-entry", name: "해운대 해변로 입구", averageSpeedMps: 0.33, reportCount: 11, sampleCount: 478 },
  { rank: 3, id: "busan-square-crosswalk", name: "부산역 광장 횡단보도 앞", averageSpeedMps: 0.37, reportCount: 8, sampleCount: 365 },
  { rank: 4, id: "biff-square", name: "남포동 BIFF 광장 주변", averageSpeedMps: 0.41, reportCount: 6, sampleCount: 312 },
  { rank: 5, id: "gwangalli-access", name: "광안리 해변 접근로", averageSpeedMps: 0.48, reportCount: 5, sampleCount: 289 },
  { rank: 6, id: "yeongdo-bridge-rise", name: "영도다리 진입 경사로", averageSpeedMps: 0.45, reportCount: 4, sampleCount: 246 },
  { rank: 7, id: "bupyeong-market", name: "부평시장 연결 보행축", averageSpeedMps: 0.52, reportCount: 3, sampleCount: 218 },
];

const fallbackHotspots: BottleneckHotspot[] = [
  { id: "seomyeon", name: "서면역 7번 출구 주변", lat: 35.157377, lng: 129.059187, averageSpeedMps: 0.28, reportCount: 14, sampleCount: 612 },
  { id: "busan-station", name: "부산역 광장 횡단보도 앞", lat: 35.115187, lng: 129.041367, averageSpeedMps: 0.37, reportCount: 8, sampleCount: 365 },
  { id: "nampo-biff", name: "남포동 BIFF 광장 주변", lat: 35.098598, lng: 129.030493, averageSpeedMps: 0.41, reportCount: 6, sampleCount: 312 },
  { id: "gwangalli", name: "광안리 해변 접근로", lat: 35.153174, lng: 129.11858, averageSpeedMps: 0.48, reportCount: 5, sampleCount: 289 },
  { id: "haeundae", name: "해운대 해변로 입구", lat: 35.158728, lng: 129.160384, averageSpeedMps: 0.33, reportCount: 11, sampleCount: 478 },
  { id: "yeongdo", name: "영도다리 진입 경사로", lat: 35.093927, lng: 129.043808, averageSpeedMps: 0.45, reportCount: 4, sampleCount: 246 },
];

const fallbackRouteSegments: BottleneckRouteSegment[] = [
  {
    id: "seomyeon-corridor",
    averageSpeedMps: 0.28,
    sampleCount: 612,
    points: [
      { lat: 35.159132, lng: 129.057879 },
      { lat: 35.158492, lng: 129.058431 },
      { lat: 35.157982, lng: 129.058901 },
      { lat: 35.157377, lng: 129.059187 },
      { lat: 35.156802, lng: 129.059647 },
      { lat: 35.156114, lng: 129.060208 },
    ],
  },
  {
    id: "busan-station-crossing",
    averageSpeedMps: 0.37,
    sampleCount: 365,
    points: [
      { lat: 35.116281, lng: 129.040245 },
      { lat: 35.115793, lng: 129.040798 },
      { lat: 35.115187, lng: 129.041367 },
      { lat: 35.114503, lng: 129.041911 },
    ],
  },
  {
    id: "nampo-biff-corridor",
    averageSpeedMps: 0.41,
    sampleCount: 312,
    points: [
      { lat: 35.10003, lng: 129.031635 },
      { lat: 35.099308, lng: 129.031154 },
      { lat: 35.098598, lng: 129.030493 },
      { lat: 35.097875, lng: 129.029919 },
    ],
  },
  {
    id: "gwangalli-access-road",
    averageSpeedMps: 0.48,
    sampleCount: 289,
    points: [
      { lat: 35.154254, lng: 129.119983 },
      { lat: 35.153704, lng: 129.119122 },
      { lat: 35.153174, lng: 129.11858 },
      { lat: 35.152427, lng: 129.117759 },
    ],
  },
];

const trendTemplate = {
  labels: ["05.01", "05.06", "05.11", "05.16", "05.21", "05.26", "05.31"],
  totalSeries: [61, 74, 70, 82, 98, 114, 121],
  severeSeries: [18, 22, 20, 24, 28, 25, 29],
};

const addressTemplates = [
  "부산진구 중앙대로 672",
  "해운대구 해운대로 264",
  "동구 중앙대로 206",
  "중구 비프광장로 38",
  "수영구 광안해변로 219",
  "영도구 태종로 45",
  "중구 중구로 31",
];

const reportDateTemplates = [
  "2024.05.31",
  "2024.05.31",
  "2024.05.30",
  "2024.05.29",
  "2024.05.28",
  "2024.05.27",
  "2024.05.26",
];

const typeTemplates = [
  { label: "좁은 보행로", tone: "blue" as const },
  { label: "경사/단차", tone: "green" as const },
  { label: "횡단 주의", tone: "orange" as const },
  { label: "시설물 장애", tone: "purple" as const },
  { label: "경사/단차", tone: "green" as const },
  { label: "좁은 보행로", tone: "blue" as const },
  { label: "횡단 주의", tone: "orange" as const },
];

const statusTemplates = [
  { label: "심각", tone: "danger" as const },
  { label: "심각", tone: "danger" as const },
  { label: "주의", tone: "warning" as const },
  { label: "주의", tone: "warning" as const },
  { label: "보통", tone: "neutral" as const },
  { label: "보통", tone: "neutral" as const },
  { label: "보통", tone: "neutral" as const },
];

const distributionLabels = [
  { label: "좁은 보행로", color: "#3b82f6" },
  { label: "경사/단차", color: "#4cc9a6" },
  { label: "횡단 주의", color: "#ffb648" },
  { label: "시설물 장애", color: "#8b7cf6" },
  { label: "기타", color: "#b9c4d4" },
];

export interface BottleneckMonitoringResponse {
  title: string;
  subtitle: string;
  dateRangeLabel: string;
  exportLabel: string;
  summaryCards: Array<{
    label: string;
    valueLabel: string;
    deltaLabel: string;
    comparisonLabel: string;
    tone: "danger" | "warning" | "success";
    icon: "alert" | "fire" | "users" | "check";
  }>;
  trend: {
    labels: string[];
    series: Array<{
      label: string;
      color: string;
      values: number[];
    }>;
    maxValue: number;
  };
  distribution: {
    totalCount: number;
    items: Array<{
      label: string;
      count: number;
      share: number;
      color: string;
    }>;
  };
  map: {
    hotspots: BottleneckHotspot[];
    routeSegments: BottleneckRouteSegment[];
  };
  table: {
    filters: {
      typeLabel: string;
      statusLabel: string;
      sortLabel: string;
      pageSizeLabel: string;
    };
    rows: Array<{
      rank: number;
      location: string;
      address: string;
      typeLabel: string;
      typeTone: "blue" | "green" | "orange" | "purple";
      affectedUsersLabel: string;
      statusLabel: string;
      statusTone: "danger" | "warning" | "neutral";
      reportedAt: string;
    }>;
    pagination: {
      currentPage: number;
      pages: number[];
    };
  };
  impactTop: {
    sortLabel: string;
    items: Array<{
      rank: number;
      location: string;
      affectedUsersLabel: string;
      statusLabel: string;
      statusTone: "danger" | "warning" | "neutral";
    }>;
  };
}

export function composeBottleneckMonitoringData(
  api?: AdminDashboardBottleneckResponse,
): BottleneckMonitoringResponse {
  const bottlenecks = api?.topBottlenecks?.length
    ? api.topBottlenecks.slice(0, 7)
    : fallbackTopBottlenecks;

  const sampleTotal = bottlenecks.reduce((sum, item) => sum + item.sampleCount, 0);
  const reportTotal = bottlenecks.reduce((sum, item) => sum + item.reportCount, 0);
  const totalCount = api
    ? clamp(Math.round(sampleTotal / 18), 72, 180)
    : 128;
  const realCount = api
    ? clamp(Math.round(totalCount * 0.19), 18, 42)
    : 24;
  const affectedUsers = api
    ? clamp(Math.round(sampleTotal * 1.42), 1420, 7890)
    : 3256;
  const resolvedCount = api
    ? clamp(Math.round((realCount + reportTotal) * 1.25), 12, 96)
    : 42;
  const distributionCounts = allocateCounts(totalCount, distributionWeights);
  const baseDate = api?.period?.to ?? "2024-05-31";
  const tableRows = bottlenecks.map((item, index) => ({
    rank: index + 1,
    location: item.name,
    address: addressTemplates[index] ?? "부산광역시",
    typeLabel: typeTemplates[index]?.label ?? "좁은 보행로",
    typeTone: typeTemplates[index]?.tone ?? "blue",
    affectedUsersLabel: `${integerFormatter.format(deriveAffectedUsers(item, index))}명`,
    statusLabel: statusTemplates[index]?.label ?? "보통",
    statusTone: statusTemplates[index]?.tone ?? "neutral",
    reportedAt: api?.period
      ? formatDate(offsetDate(baseDate, index))
      : reportDateTemplates[index] ?? "2024.05.31",
  }));

  return {
    title: "병목구간 통계",
    subtitle: "이동 약자의 통행이 불편한 병목구간 현황을 확인할 수 있습니다.",
    dateRangeLabel: api?.period
      ? `${formatDate(api.period.from)} ~ ${formatDate(api.period.to)}`
      : "2024.05.01 ~ 2024.05.31",
    exportLabel: "CSV 다운로드",
    summaryCards: [
      {
        label: "병목구간 총수",
        valueLabel: `${integerFormatter.format(totalCount)}건`,
        deltaLabel: `▲ ${integerFormatter.format(clamp(Math.round(totalCount * 0.09), 8, 18))}건 (10.3%)`,
        comparisonLabel: "지난 기간 대비",
        tone: "danger",
        icon: "alert",
      },
      {
        label: "실제 병목구간",
        valueLabel: `${integerFormatter.format(realCount)}건`,
        deltaLabel: `▲ ${integerFormatter.format(clamp(Math.round(realCount * 0.17), 4, 8))}건 (20.0%)`,
        comparisonLabel: "지난 기간 대비",
        tone: "warning",
        icon: "fire",
      },
      {
        label: "영향을 받은 사용자",
        valueLabel: `${integerFormatter.format(affectedUsers)}명`,
        deltaLabel: `▲ ${integerFormatter.format(clamp(Math.round(affectedUsers * 0.16), 180, 560))}명 (18.6%)`,
        comparisonLabel: "지난 기간 대비",
        tone: "warning",
        icon: "users",
      },
      {
        label: "해결 완료",
        valueLabel: `${integerFormatter.format(resolvedCount)}건`,
        deltaLabel: `▲ ${integerFormatter.format(clamp(Math.round(resolvedCount * 0.19), 8, 16))}건 (23.5%)`,
        comparisonLabel: "지난 기간 대비",
        tone: "success",
        icon: "check",
      },
    ],
    trend: {
      labels: trendTemplate.labels,
      maxValue: 150,
      series: [
        {
          label: "전체",
          color: "#4b82f6",
          values: scaleTrend(trendTemplate.totalSeries, totalCount / 128),
        },
        {
          label: "심각",
          color: "#ff6b6b",
          values: scaleTrend(trendTemplate.severeSeries, realCount / 24),
        },
      ],
    },
    distribution: {
      totalCount,
      items: distributionLabels.map((item, index) => ({
        label: item.label,
        count: distributionCounts[index],
        share: distributionCounts[index] / totalCount,
        color: item.color,
      })),
    },
    map: {
      hotspots: api?.hotspots?.length
        ? api.hotspots.map((item) => ({
          id: item.id,
          name: item.name,
          lat: item.lat,
          lng: item.lng,
          averageSpeedMps: item.averageSpeedMps,
          reportCount: item.reportCount,
          sampleCount: item.sampleCount,
        }))
        : fallbackHotspots,
      routeSegments: api?.routeSegments?.length
        ? api.routeSegments.map((item) => ({
          id: item.id,
          points: item.points,
          averageSpeedMps: item.averageSpeedMps,
          sampleCount: item.sampleCount,
        }))
        : fallbackRouteSegments,
    },
    table: {
      filters: {
        typeLabel: "전체 유형",
        statusLabel: "전체 상태",
        sortLabel: "최신순",
        pageSizeLabel: "10개씩 보기",
      },
      rows: tableRows,
      pagination: {
        currentPage: 1,
        pages: [1, 2, 3, 4, 5],
      },
    },
    impactTop: {
      sortLabel: "영향 사용자",
      items: tableRows.slice(0, 5).map((row) => ({
        rank: row.rank,
        location: row.location,
        affectedUsersLabel: row.affectedUsersLabel,
        statusLabel: row.statusLabel,
        statusTone: row.statusTone,
      })),
    },
  };
}

export const bottleneckMonitoringMockResponse = composeBottleneckMonitoringData();

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function allocateCounts(total: number, weights: number[]) {
  const raw = weights.map((weight) => total * weight);
  const rounded = raw.map((value) => Math.floor(value));
  let remainder = total - rounded.reduce((sum, value) => sum + value, 0);

  while (remainder > 0) {
    const index = raw
      .map((value, rawIndex) => ({ rawIndex, remainder: value - rounded[rawIndex] }))
      .sort((left, right) => right.remainder - left.remainder)[0]?.rawIndex ?? 0;
    rounded[index] += 1;
    remainder -= 1;
  }

  return rounded;
}

function deriveAffectedUsers(item: AdminDashboardTopBottleneck, index: number) {
  return clamp(Math.round(item.sampleCount), 148, 980);
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace(/-/g, ".");
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}.${month}.${day}`;
}

function offsetDate(value: string, index: number) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  date.setDate(date.getDate() - index);
  return date.toISOString();
}

function scaleTrend(values: number[], ratio: number) {
  return values.map((value) => Math.round(value * (0.85 + ratio * 0.15)));
}
