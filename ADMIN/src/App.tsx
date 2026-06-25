import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { QueryClient, QueryClientProvider, useInfiniteQuery, useMutation, useQuery } from "@tanstack/react-query";
import adminLogoUrl from "./assets/app_logo.png";
import { BottleneckMonitoringPage } from "./bottleneck/BottleneckMonitoringPage";
import { bottleneckMonitoringMockResponse } from "./bottleneck/bottleneckMonitoringContract";
import {
  createAdminRoadNetworkEditJob,
  fetchAdminAreaAssignments,
  fetchAdminAuditLogs,
  fetchAdminAreas,
  fetchAdminBottleneckMonitoring,
  fetchAdminDashboardBottlenecks,
  fetchAdminDashboardSummary,
  fetchAdminFacilityPayload,
  fetchAdminPlaceDetail,
  fetchAdminRoadNetworkBridges,
  fetchAdminRoadNetworkPayload,
  fetchAdminRoadNetworkEditJob,
  fetchAdminHazardReports,
  fetchAdminRouteStats,
  fetchAdminUsers,
  adminAccessTokenRefreshedEvent,
  getStoredAdminAccessToken,
  logoutAdminSession,
  reverseGeocodePlace,
  storeAdminAccessToken,
  updateAdminAreaAssignmentStatus,
  updateAdminPlace,
  updateAdminPlaceAccessibilityFeatures,
  updateAdminUserRole,
  upsertAdminAreaAssignment,
} from "./api/adminApi";
import { AdminAuthPanel } from "./auth/AdminAuthPanel";
import { filterFacilityPayloadByCategories } from "./facility/facilityFilters";
import { adminShellClassName } from "./layout/adminLayout";
import { AdminBottleneckKakaoMap } from "./map/AdminBottleneckKakaoMap";
import { FacilityMap } from "./map/FacilityMap";
import { facilityCategoryColor, facilityCategoryLabel, facilityCategoryOrder } from "./map/facilityStyle";
import { SegmentMap, type RoadviewDockState } from "./map/SegmentMap";
import type { BottleneckHotspot, BottleneckRouteSegment } from "./map/adminBottleneckMap";
import { HazardReportsPage } from "./report/HazardReportsPage";
import { RouteStatsPage } from "./route/RouteStatsPage";
import { RouteTuningPage } from "./route/RouteTuningPage";
import { routeStatsMockResponse } from "./route/routeStatsContract";
import { useAdminStore } from "./store/adminStore";
import type {
  AccessibilityFeatureType,
  AdminAuditLog,
  AdminDashboardBottleneckResponse,
  AdminDashboardDailyMovementMetric,
  AdminDashboardSummaryResponse,
  AdminMeResponse,
  AdminPage,
  AdminPlaceDetailResponse,
  AdminPlaceUpdateRequest,
  FacilityPayload,
  FacilityFeature,
  PlaceAccessibilityFeature,
  PlaceCategory,
  RoadNetworkEditJobResponse,
  SegmentFeature,
  WorkStatus,
  AdminUserResponse,
  Assignment,
  AssignmentType,
  GeoPoint,
  UserRole,
} from "./types";

const placeCategories: PlaceCategory[] = [...facilityCategoryOrder];
const allDongScope = "전체";

interface DashboardDateRange {
  from: string;
  to: string;
}

const accessibilityFeatureTypes: AccessibilityFeatureType[] = [
  "accessibleEntrance",
  "elevator",
  "accessibleToilet",
  "accessibleParking",
  "chargingStation",
  "accessibleRoom",
  "guidanceFacility",
];

const auditLogActions = [
  { value: "ROAD_NETWORK_EDIT_APPLY", label: "보행 네트워크 반영" },
  { value: "ROAD_SEGMENT_ATTRIBUTES_UPDATE", label: "segment 속성 변경" },
  { value: "PLACE_BASIC_UPDATE", label: "편의시설 기본 정보 변경" },
  { value: "PLACE_ACCESSIBILITY_FEATURES_REPLACE", label: "편의시설 접근성 변경" },
];

const userTypeLabels: Record<string, string> = {
  LOW_VISION: "저시력",
  MOBILITY_IMPAIRED: "이동약자",
};

const reportTypeLabels: Record<string, string> = {
  STAIRS_STEP: "계단/단차",
  BRAILLE_BLOCK: "점자블록",
  SIDEWALK_MISSING: "보도 없음",
  RAMP: "경사로",
  SIDEWALK_WIDTH: "보도 폭",
  OTHER_OBSTACLE: "기타 장애물",
};

const reportStatusLabels: Record<string, string> = {
  PENDING: "접수",
  APPROVED: "처리완료",
  REJECTED: "반려",
};

const integerFormatter = new Intl.NumberFormat("ko-KR");
const decimalFormatter = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 1,
});
const percentFormatter = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 1,
  style: "percent",
});

const configuredGrafanaDashboardUrl = import.meta.env.VITE_GRAFANA_DASHBOARD_URL as string | undefined;
const grafanaDashboardUrl = configuredGrafanaDashboardUrl?.trim() ?? "";

const adminBottleneckHotspots: BottleneckHotspot[] = [
  { id: "choryang-entry", name: "초량 이바구길 입구", lat: 35.116888, lng: 129.039221, averageSpeedMps: 0.28, reportCount: 16, sampleCount: 1180 },
  { id: "gamcheon-alley", name: "감천문화마을 골목길", lat: 35.097577, lng: 129.010124, averageSpeedMps: 0.32, reportCount: 12, sampleCount: 780 },
  { id: "jungang-stairs", name: "중앙동 계단 구간", lat: 35.104917, lng: 129.036899, averageSpeedMps: 0.38, reportCount: 9, sampleCount: 690 },
  { id: "bupyeong-market", name: "부평시장 횡단 보행축", lat: 35.102253, lng: 129.026106, averageSpeedMps: 0.41, reportCount: 7, sampleCount: 720 },
  { id: "nampo-transfer", name: "남포역 환승 보행축", lat: 35.097914, lng: 129.034979, averageSpeedMps: 0.39, reportCount: 6, sampleCount: 760 },
  { id: "yeongdo-bridge", name: "영도다리 진입로", lat: 35.093927, lng: 129.043808, averageSpeedMps: 0.55, reportCount: 5, sampleCount: 650 },
  { id: "yeongdo-hillside", name: "영도구 경사 보행로", lat: 35.089234, lng: 129.051942, averageSpeedMps: 0.62, reportCount: 2, sampleCount: 320 },
];

const adminBottleneckRoutes: BottleneckRouteSegment[] = [
  {
    id: "choryang-nampo-slope",
    averageSpeedMps: 0.28,
    sampleCount: 1180,
    points: [
      { lat: 35.116888, lng: 129.039221 },
      { lat: 35.115918, lng: 129.038934 },
      { lat: 35.114683, lng: 129.03852 },
      { lat: 35.113583, lng: 129.037852 },
      { lat: 35.112301, lng: 129.037324 },
      { lat: 35.110802, lng: 129.036855 },
      { lat: 35.108583, lng: 129.036069 },
      { lat: 35.106853, lng: 129.034996 },
      { lat: 35.106141, lng: 129.034992 },
      { lat: 35.105158, lng: 129.034707 },
      { lat: 35.103911, lng: 129.034426 },
      { lat: 35.103153, lng: 129.03466 },
      { lat: 35.10231, lng: 129.034683 },
      { lat: 35.100942, lng: 129.035079 },
      { lat: 35.0999, lng: 129.035249 },
      { lat: 35.098203, lng: 129.035611 },
      { lat: 35.097181, lng: 129.035176 },
      { lat: 35.097914, lng: 129.034979 },
    ],
  },
  {
    id: "bupyeong-nampo-market",
    averageSpeedMps: 0.39,
    sampleCount: 760,
    points: [
      { lat: 35.102253, lng: 129.026106 },
      { lat: 35.101774, lng: 129.027158 },
      { lat: 35.101733, lng: 129.028434 },
      { lat: 35.101743, lng: 129.028476 },
      { lat: 35.101735, lng: 129.028796 },
      { lat: 35.100561, lng: 129.029342 },
      { lat: 35.10016, lng: 129.029692 },
      { lat: 35.099753, lng: 129.029747 },
      { lat: 35.099328, lng: 129.030157 },
      { lat: 35.099216, lng: 129.030229 },
      { lat: 35.098902, lng: 129.030189 },
      { lat: 35.098702, lng: 129.031514 },
      { lat: 35.0985, lng: 129.032266 },
      { lat: 35.097675, lng: 129.033921 },
      { lat: 35.098134, lng: 129.034072 },
      { lat: 35.098329, lng: 129.034817 },
      { lat: 35.098148, lng: 129.03489 },
      { lat: 35.097914, lng: 129.034979 },
    ],
  },
  {
    id: "choryang-bupyeong-market",
    averageSpeedMps: 0.44,
    sampleCount: 980,
    points: [
      { lat: 35.116888, lng: 129.039221 },
      { lat: 35.115816, lng: 129.039047 },
      { lat: 35.113733, lng: 129.037953 },
      { lat: 35.112194, lng: 129.037312 },
      { lat: 35.10947, lng: 129.036605 },
      { lat: 35.106992, lng: 129.035031 },
      { lat: 35.106141, lng: 129.034992 },
      { lat: 35.104103, lng: 129.034543 },
      { lat: 35.103168, lng: 129.034277 },
      { lat: 35.10194, lng: 129.034736 },
      { lat: 35.1001, lng: 129.035685 },
      { lat: 35.098203, lng: 129.035611 },
      { lat: 35.09711, lng: 129.034783 },
      { lat: 35.098598, lng: 129.031962 },
      { lat: 35.099216, lng: 129.030229 },
      { lat: 35.100581, lng: 129.029636 },
      { lat: 35.101743, lng: 129.028476 },
      { lat: 35.102253, lng: 129.026106 },
    ],
  },
  {
    id: "jungang-nampo-stairs",
    averageSpeedMps: 0.38,
    sampleCount: 690,
    points: [
      { lat: 35.104917, lng: 129.036899 },
      { lat: 35.103681, lng: 129.036982 },
      { lat: 35.103613, lng: 129.037 },
      { lat: 35.101652, lng: 129.037102 },
      { lat: 35.100271, lng: 129.037183 },
      { lat: 35.100243, lng: 129.03657 },
      { lat: 35.100223, lng: 129.035861 },
      { lat: 35.100037, lng: 129.035592 },
      { lat: 35.0999, lng: 129.035249 },
      { lat: 35.099572, lng: 129.035386 },
      { lat: 35.098807, lng: 129.035559 },
      { lat: 35.09762, lng: 129.035561 },
      { lat: 35.097527, lng: 129.035574 },
      { lat: 35.097253, lng: 129.035641 },
      { lat: 35.097181, lng: 129.035176 },
      { lat: 35.097677, lng: 129.035127 },
      { lat: 35.09792, lng: 129.035006 },
      { lat: 35.097914, lng: 129.034979 },
    ],
  },
  {
    id: "bupyeong-jungang-corridor",
    averageSpeedMps: 0.41,
    sampleCount: 720,
    points: [
      { lat: 35.102253, lng: 129.026106 },
      { lat: 35.101746, lng: 129.027767 },
      { lat: 35.101743, lng: 129.028476 },
      { lat: 35.101753, lng: 129.029206 },
      { lat: 35.10016, lng: 129.029692 },
      { lat: 35.099775, lng: 129.030082 },
      { lat: 35.099216, lng: 129.030229 },
      { lat: 35.098851, lng: 129.030651 },
      { lat: 35.0985, lng: 129.032266 },
      { lat: 35.097675, lng: 129.033921 },
      { lat: 35.097132, lng: 129.034852 },
      { lat: 35.097527, lng: 129.035574 },
      { lat: 35.098203, lng: 129.035611 },
      { lat: 35.0999, lng: 129.035249 },
      { lat: 35.1001, lng: 129.035685 },
      { lat: 35.100271, lng: 129.037183 },
      { lat: 35.103062, lng: 129.037036 },
      { lat: 35.104917, lng: 129.036899 },
    ],
  },
  {
    id: "gamcheon-hillside-loop",
    averageSpeedMps: 0.32,
    sampleCount: 780,
    points: [
      { lat: 35.097577, lng: 129.010124 },
      { lat: 35.097875, lng: 129.009922 },
      { lat: 35.098123, lng: 129.009639 },
      { lat: 35.098239, lng: 129.009961 },
      { lat: 35.09826, lng: 129.010184 },
      { lat: 35.098616, lng: 129.009941 },
      { lat: 35.098989, lng: 129.009585 },
      { lat: 35.099251, lng: 129.009499 },
      { lat: 35.09941, lng: 129.009395 },
      { lat: 35.099386, lng: 129.009272 },
      { lat: 35.099359, lng: 129.009112 },
      { lat: 35.09933, lng: 129.008916 },
      { lat: 35.09931, lng: 129.008634 },
      { lat: 35.099351, lng: 129.008407 },
      { lat: 35.099349, lng: 129.008291 },
      { lat: 35.099621, lng: 129.008121 },
      { lat: 35.099728, lng: 129.008013 },
      { lat: 35.099854, lng: 129.007924 },
    ],
  },
  {
    id: "yeongdo-hillside-walkway",
    averageSpeedMps: 0.55,
    sampleCount: 650,
    points: [
      { lat: 35.089234, lng: 129.051942 },
      { lat: 35.089611, lng: 129.051763 },
      { lat: 35.089972, lng: 129.051704 },
      { lat: 35.090206, lng: 129.051695 },
      { lat: 35.090357, lng: 129.051633 },
      { lat: 35.090828, lng: 129.051654 },
      { lat: 35.091232, lng: 129.051795 },
      { lat: 35.091652, lng: 129.051647 },
      { lat: 35.091803, lng: 129.051498 },
      { lat: 35.092088, lng: 129.050849 },
      { lat: 35.092475, lng: 129.050571 },
      { lat: 35.092684, lng: 129.050007 },
      { lat: 35.092246, lng: 129.049555 },
      { lat: 35.092224, lng: 129.048951 },
      { lat: 35.09187, lng: 129.0479 },
      { lat: 35.091869, lng: 129.04734 },
      { lat: 35.091857, lng: 129.046527 },
      { lat: 35.092081, lng: 129.046193 },
      { lat: 35.092625, lng: 129.046328 },
      { lat: 35.092816, lng: 129.045943 },
      { lat: 35.093714, lng: 129.045785 },
      { lat: 35.093927, lng: 129.043808 },
    ],
  },
];

type DashboardIconName =
  | "home"
  | "users"
  | "route"
  | "speedometer"
  | "warning"
  | "chat"
  | "chart"
  | "map"
  | "report"
  | "content"
  | "settings"
  | "database"
  | "building"
  | "heart"
  | "medical"
  | "camera"
  | "utensils"
  | "bed"
  | "more"
  | "calendar"
  | "refresh"
  | "bell"
  | "user"
  | "clock"
  | "mobile"
  | "chevron";

type AdminNavSectionId = "statistics" | "review" | "management";

type AdminNavItem =
  | { type: "page"; page: AdminPage }
  | { type: "section"; id: AdminNavSectionId; label: string; icon: DashboardIconName; pages: AdminPage[] };

const adminNavItems: AdminNavItem[] = [
  { type: "page", page: "home" },
  {
    type: "section",
    id: "statistics",
    label: "통계",
    icon: "chart",
    pages: ["routeStats", "bottleneckMonitoring"],
  },
  {
    type: "section",
    id: "review",
    label: "검수",
    icon: "map",
    pages: ["routeTuning", "network"],
  },
  {
    type: "section",
    id: "management",
    label: "관리",
    icon: "users",
    pages: ["users", "facilities", "hazards", "notices"],
  },
  { type: "page", page: "logs" },
];

const adminNavIcons: Record<AdminPage, DashboardIconName> = {
  home: "home",
  network: "map",
  routeTuning: "route",
  routeStats: "chart",
  bottleneckMonitoring: "warning",
  facilities: "content",
  hazards: "warning",
  notices: "report",
  users: "users",
  logs: "database",
};

const facilityFilterItems: { category: PlaceCategory; icon: DashboardIconName }[] = [
  { category: "PUBLIC_OFFICE", icon: "building" },
  { category: "WELFARE", icon: "heart" },
  { category: "HEALTHCARE", icon: "medical" },
  { category: "TOURIST_SPOT", icon: "camera" },
  { category: "FOOD_CAFE", icon: "utensils" },
  { category: "ACCOMMODATION", icon: "bed" },
  { category: "ETC", icon: "more" },
];

const pageMeta: Record<AdminPage, { label: string; description: string }> = {
  home: {
    label: "대시보드",
    description: "부산이음길 서비스 운영 현황을 한눈에 확인하세요.",
  },
  network: {
    label: "보행 네트워크 검수",
    description: "",
  },
  routeTuning: {
    label: "경로 검수",
    description: "",
  },
  routeStats: {
    label: "경로/이동 통계",
    description: "",
  },
  bottleneckMonitoring: {
    label: "병목구간 통계",
    description: "",
  },
  facilities: {
    label: "편의시설 관리",
    description: "보행약자 편의시설 위치와 접근성 속성을 검수합니다.",
  },
  hazards: {
    label: "불편제보 관리",
    description: "사용자가 등록한 도로 상태 제보를 확인하고 승인 또는 반려합니다.",
  },
  notices: {
    label: "공지사항 관리",
    description: "공지사항 관리 기능은 현재 준비중입니다.",
  },
  users: {
    label: "사용자 관리",
    description: "관리자 권한과 구 단위 담당자, 작업 상태를 관리합니다.",
  },
  logs: {
    label: "로그",
    description: "관리자 화면에서 수행한 변경 작업을 최신순으로 확인합니다.",
  },
};

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function AdminApp() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [expandedNavSections, setExpandedNavSections] = useState<Record<AdminNavSectionId, boolean>>({
    statistics: true,
    review: true,
    management: true,
  });
  const roadviewContainerRef = useRef<HTMLDivElement | null>(null);
  const [roadviewDock, setRoadviewDock] = useState<RoadviewDockState>({
    open: false,
    message: "",
  });
  const [selectedFacility, setSelectedFacility] = useState<FacilityFeature | null>(null);
  const [selectedFacilityCategories, setSelectedFacilityCategories] = useState<PlaceCategory[]>(() => [...placeCategories]);
  const [selectedSegment, setSelectedSegment] = useState<SegmentFeature | null>(null);
  const [networkDetailPanelCollapsed, setNetworkDetailPanelCollapsed] = useState(false);
  const [facilityLocationPickEnabled, setFacilityLocationPickEnabled] = useState(false);
  const [facilityPickedLocation, setFacilityPickedLocation] = useState<{ point: GeoPoint; address?: string; nonce: number } | null>(null);
  const [accessToken, setAccessToken] = useState(getStoredAdminAccessToken);
  const [tokenInput, setTokenInput] = useState(accessToken);
  const [adminPrincipal, setAdminPrincipal] = useState<AdminMeResponse | null>(null);
  const [activeRoadEditJobId, setActiveRoadEditJobId] = useState<number | null>(null);
  const [lastRoadEditJob, setLastRoadEditJob] = useState<RoadNetworkEditJobResponse | null>(null);
  const [auditLogAction, setAuditLogAction] = useState("");
  const [auditLogGu, setAuditLogGu] = useState("");
  const [auditLogDong, setAuditLogDong] = useState("");
  const [auditLogActorUserId, setAuditLogActorUserId] = useState("");
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [isDashboardRangeOpen, setIsDashboardRangeOpen] = useState(false);
  const [dashboardRange, setDashboardRange] = useState<DashboardDateRange>(() => createDashboardPresetRange(6));
  const [dashboardRangeDraft, setDashboardRangeDraft] = useState<DashboardDateRange>(() => createDashboardPresetRange(6));
  const completedRoadEditJobIdRef = useRef<number | null>(null);
  const submittedRoadEditAssignmentIdRef = useRef<string | null>(null);
  const workspaceRef = useRef<HTMLElement | null>(null);
  const notificationPanelRef = useRef<HTMLDivElement | null>(null);
  const dashboardRangePanelRef = useRef<HTMLDivElement | null>(null);
  const {
    page,
    selectedAssignmentId,
    selectedGu,
    selectedDong,
    draftEdits,
    setPage,
    setSelectedArea,
    undoDraftEdit,
    clearDraft,
    addDraftEdit,
    clearDraftForAssignment,
  } = useAdminStore();

  const hasToken = Boolean(accessToken);
  const previewPage = typeof window !== "undefined"
    ? new URLSearchParams(window.location.search).get("preview")
    : null;
  const fullShellPreviewEnabled = typeof window !== "undefined"
    && import.meta.env.DEV
    && (previewPage === "routeStats" || previewPage === "bottleneckMonitoring" || previewPage === "hazards" || previewPage === "notices");
  const currentAdmin = adminPrincipal ?? (fullShellPreviewEnabled
    ? {
        userId: "7e7e00a4-bf81-4a82-918c-7a5e062dc325",
        role: "ADMIN",
        permissions: ["ADMIN_PREVIEW"],
      }
    : null);
  const isAdminAuthenticated = (hasToken && adminPrincipal?.role === "ADMIN") || Boolean(fullShellPreviewEnabled);
  const usesRealAdminApi = hasToken && adminPrincipal?.role === "ADMIN";
  const showsAreaSelector = page === "network" || page === "facilities" || page === "routeTuning";
  const normalizedDashboardRange = useMemo(
    () => normalizeDashboardDateRange(dashboardRange),
    [dashboardRange],
  );
  const selectedAssignmentType: AssignmentType = page === "facilities" ? "FACILITY" : "ROAD_NETWORK";
  const selectedScopeDong = allDongScope;
  const selectedAssignmentScopeLabel = `${selectedGu} ${selectedScopeDong}`;

  useEffect(() => {
    function handleAccessTokenRefreshed(event: Event) {
      const nextToken = (event as CustomEvent<string>).detail;
      if (!nextToken) return;
      setAccessToken(nextToken);
      setTokenInput(nextToken);
    }

    window.addEventListener(adminAccessTokenRefreshedEvent, handleAccessTokenRefreshed);
    return () => window.removeEventListener(adminAccessTokenRefreshedEvent, handleAccessTokenRefreshed);
  }, []);

  useEffect(() => {
    setRoadviewDock({
      open: false,
      message: "",
    });
  }, [page, selectedGu, selectedDong]);

  useEffect(() => {
    workspaceRef.current?.scrollTo?.({ top: 0, left: 0, behavior: "auto" });
    if (typeof window !== "undefined") {
      const scrollRoot = document.scrollingElement;
      if (scrollRoot) {
        scrollRoot.scrollTop = 0;
        scrollRoot.scrollLeft = 0;
      }
      if (typeof window.scrollTo === "function") {
        window.scrollTo(0, 0);
      }
    }
  }, [page]);

  const areasQuery = useQuery({
    queryKey: ["admin-areas", accessToken],
    queryFn: () => fetchAdminAreas(accessToken),
    enabled: (showsAreaSelector || page === "users" || page === "logs") && usesRealAdminApi,
    retry: false,
  });

  const adminUsersQuery = useQuery({
    queryKey: ["admin-users", accessToken],
    queryFn: () => fetchAdminUsers(accessToken),
    enabled: (page === "users" || page === "logs") && usesRealAdminApi,
    retry: false,
  });

  const dashboardSummaryQuery = useQuery({
    queryKey: ["admin-dashboard-summary", accessToken, normalizedDashboardRange.from, normalizedDashboardRange.to],
    queryFn: () => fetchAdminDashboardSummary({
      accessToken,
      from: normalizedDashboardRange.from,
      to: normalizedDashboardRange.to,
    }),
    enabled: page === "home" && usesRealAdminApi,
    retry: false,
    refetchInterval: 60_000,
  });

  const dashboardBottlenecksQuery = useQuery({
    queryKey: ["admin-dashboard-bottlenecks", accessToken, normalizedDashboardRange.from, normalizedDashboardRange.to],
    queryFn: () => fetchAdminDashboardBottlenecks({
      accessToken,
      from: normalizedDashboardRange.from,
      to: normalizedDashboardRange.to,
      limit: 12,
    }),
    enabled: page === "home" && usesRealAdminApi,
    retry: false,
    refetchInterval: 60_000,
  });

  const routeStatsQuery = useQuery({
    queryKey: ["admin-route-stats", accessToken],
    queryFn: () => fetchAdminRouteStats(accessToken),
    enabled: page === "routeStats" && usesRealAdminApi,
    retry: false,
    refetchInterval: 60_000,
  });

  const bottleneckMonitoringQuery = useQuery({
    queryKey: ["admin-bottleneck-monitoring", accessToken],
    queryFn: () => fetchAdminBottleneckMonitoring(accessToken),
    enabled: page === "bottleneckMonitoring" && usesRealAdminApi,
    retry: false,
    refetchInterval: 60_000,
  });

  const areaAssignmentsQuery = useQuery({
    queryKey: ["admin-area-assignments", accessToken],
    queryFn: () => fetchAdminAreaAssignments(accessToken),
    enabled: usesRealAdminApi,
    retry: false,
  });

  const pendingHazardReportsQuery = useQuery({
    queryKey: ["admin-hazard-reports-pending-count", accessToken],
    queryFn: () => fetchAdminHazardReports({ status: "PENDING", cursor: null, size: 20, accessToken }),
    enabled: usesRealAdminApi,
    retry: false,
    refetchInterval: 30_000,
  });

  const auditLogsQuery = useInfiniteQuery({
    queryKey: ["admin-audit-logs", accessToken, auditLogAction, auditLogGu, auditLogDong, auditLogActorUserId],
    queryFn: ({ pageParam }) => fetchAdminAuditLogs({
      action: auditLogAction,
      gu: auditLogGu,
      dong: auditLogDong,
      actorUserId: auditLogActorUserId,
      cursor: pageParam,
      size: 50,
      accessToken,
    }),
    initialPageParam: null as number | null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
    enabled: page === "logs" && usesRealAdminApi,
    retry: false,
  });
  const auditLogs = auditLogsQuery.data?.pages.flatMap((logPage) => logPage.logs) ?? [];

  const payloadQuery = useQuery({
    queryKey: ["admin-road-network", selectedGu, selectedDong, accessToken],
    queryFn: () => fetchAdminRoadNetworkPayload({ gu: selectedGu, dong: selectedDong, accessToken }),
    enabled: (page === "network" || page === "routeTuning") && usesRealAdminApi,
    retry: false,
  });

  useEffect(() => {
    if (!selectedSegment || !payloadQuery.data) return;
    const updatedSegment = payloadQuery.data.segments.features.find(
      (feature) => String(feature.properties.edgeId) === String(selectedSegment.properties.edgeId),
    );
    if (!updatedSegment) {
      setSelectedSegment(null);
      return;
    }
    if (updatedSegment !== selectedSegment) {
      setSelectedSegment(updatedSegment);
    }
  }, [payloadQuery.data, selectedSegment]);

  const bridgeQuery = useQuery({
    queryKey: ["admin-road-network-bridges", selectedGu, selectedDong, accessToken],
    queryFn: () => fetchAdminRoadNetworkBridges({ gu: selectedGu, dong: selectedDong, accessToken }),
    enabled: page === "network" && Boolean(selectedGu && selectedDong) && isAdminAuthenticated,
    retry: false,
  });

  const facilityQuery = useQuery({
    queryKey: ["admin-facilities", selectedGu, selectedDong, accessToken],
    queryFn: () => fetchAdminFacilityPayload({ gu: selectedGu, dong: selectedDong, accessToken }),
    enabled: page === "facilities" && usesRealAdminApi,
    retry: false,
  });

  const selectedFacilityCategorySet = useMemo(
    () => new Set(selectedFacilityCategories),
    [selectedFacilityCategories],
  );
  const filteredFacilityPayload = useMemo(
    () => filterFacilityPayloadByCategories(facilityQuery.data, selectedFacilityCategories),
    [facilityQuery.data, selectedFacilityCategories],
  );

  useEffect(() => {
    if (page !== "facilities" || !selectedFacility) return;
    if (selectedFacilityCategorySet.has(selectedFacility.properties.category)) return;

    setSelectedFacility(null);
    setFacilityPickedLocation(null);
    setFacilityLocationPickEnabled(false);
  }, [page, selectedFacility, selectedFacilityCategorySet]);

  useEffect(() => {
    if (page !== "facilities" || !selectedFacility || !filteredFacilityPayload) return;
    const updatedFacility = filteredFacilityPayload.facilities.features.find(
      (feature) => String(feature.properties.placeId) === String(selectedFacility.properties.placeId),
    );
    if (!updatedFacility) {
      setSelectedFacility(null);
      setFacilityPickedLocation(null);
      setFacilityLocationPickEnabled(false);
      return;
    }
    if (updatedFacility !== selectedFacility) {
      setSelectedFacility(updatedFacility);
    }
  }, [filteredFacilityPayload, page, selectedFacility]);

  const selectedFacilityPlaceId = selectedFacility ? Number(selectedFacility.properties.placeId) : null;

  const placeDetailQuery = useQuery({
    queryKey: ["admin-place", selectedFacilityPlaceId, accessToken],
    queryFn: () => fetchAdminPlaceDetail(selectedFacilityPlaceId!, accessToken),
    enabled: page === "facilities" && usesRealAdminApi && Number.isFinite(selectedFacilityPlaceId),
    retry: false,
  });

  const applyRoadNetworkMutation = useMutation({
    mutationFn: () => {
      submittedRoadEditAssignmentIdRef.current = selectedAssignmentId;
      return createAdminRoadNetworkEditJob({
        version: "ADMIN-draft-v1",
        assignmentId: `${selectedGu}:${selectedScopeDong}`,
        gu: selectedGu,
        dong: selectedScopeDong,
        role: currentAdmin?.role ?? "ADMIN",
        createdAt: new Date().toISOString(),
        edits: draftEdits,
      }, accessToken);
    },
    onSuccess: (job) => {
      completedRoadEditJobIdRef.current = null;
      setLastRoadEditJob(job);
      setActiveRoadEditJobId(job.jobId);
    },
  });

  const updateUserRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: UserRole }) =>
      updateAdminUserRole(userId, role, accessToken),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
  });

  const upsertAssignmentMutation = useMutation({
    mutationFn: (request: { gu: string; dong: string; assignmentType: AssignmentType; assigneeUserId: string | null; status: WorkStatus }) =>
      upsertAdminAreaAssignment(request, accessToken),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-area-assignments"] });
    },
  });

  const updateAssignmentStatusMutation = useMutation({
    mutationFn: ({ assignmentId, status }: { assignmentId: number; status: WorkStatus }) =>
      updateAdminAreaAssignmentStatus(assignmentId, status, accessToken),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-area-assignments"] });
    },
  });

  const updatePlaceMutation = useMutation({
    mutationFn: ({ placeId, request }: { placeId: number; request: AdminPlaceUpdateRequest }) =>
      updateAdminPlace(placeId, selectedGu, selectedScopeDong, request, accessToken),
    onSuccess: (place) => {
      queryClient.setQueryData(["admin-place", place.placeId, accessToken], place);
      queryClient.invalidateQueries({ queryKey: ["admin-facilities"] });
    },
  });

  const updatePlaceFeaturesMutation = useMutation({
    mutationFn: ({ placeId, features }: { placeId: number; features: PlaceAccessibilityFeature[] }) =>
      updateAdminPlaceAccessibilityFeatures(placeId, selectedGu, selectedScopeDong, features, accessToken),
    onSuccess: (place) => {
      queryClient.setQueryData(["admin-place", place.placeId, accessToken], place);
      queryClient.invalidateQueries({ queryKey: ["admin-facilities"] });
    },
  });

  const roadEditJobQuery = useQuery({
    queryKey: ["admin-road-network-edit-job", activeRoadEditJobId, accessToken],
    queryFn: () => fetchAdminRoadNetworkEditJob(activeRoadEditJobId!, accessToken),
    enabled: activeRoadEditJobId !== null && usesRealAdminApi,
    refetchInterval: activeRoadEditJobId === null ? false : 2000,
    retry: false,
  });

  const activeRoadEditJob = roadEditJobQuery.data ?? lastRoadEditJob;
  const isRoadEditJobRunning = activeRoadEditJob?.status === "PENDING" || activeRoadEditJob?.status === "RUNNING";
  const roadEditResult = activeRoadEditJob?.result ?? null;

  useEffect(() => {
    if (!activeRoadEditJob || activeRoadEditJob.status === "PENDING" || activeRoadEditJob.status === "RUNNING") {
      return;
    }
    setLastRoadEditJob(activeRoadEditJob);
    if (activeRoadEditJob.status === "FAILED") {
      setActiveRoadEditJobId(null);
      return;
    }
    if (completedRoadEditJobIdRef.current === activeRoadEditJob.jobId) {
      return;
    }
    completedRoadEditJobIdRef.current = activeRoadEditJob.jobId;
    clearDraftForAssignment(submittedRoadEditAssignmentIdRef.current ?? undefined);
    submittedRoadEditAssignmentIdRef.current = null;
    setSelectedSegment(null);
    setActiveRoadEditJobId(null);
    queryClient.invalidateQueries({ queryKey: ["admin-road-network"] });
    queryClient.invalidateQueries({ queryKey: ["admin-road-network-bridges"] });
    queryClient.invalidateQueries({ queryKey: ["admin-hazard-route-review-network"] });
  }, [activeRoadEditJob, clearDraftForAssignment]);

  const filteredDongs = useMemo(() => {
    const areas = areasQuery.data ?? [];
    return areas.filter((area) => area.gu === selectedGu);
  }, [areasQuery.data, selectedGu]);

  const auditLogGuOptions = useMemo(() => {
    return Array.from(new Set((areasQuery.data ?? []).map((area) => area.gu))).sort();
  }, [areasQuery.data]);

  const auditLogDongOptions = useMemo(() => {
    const areas = areasQuery.data ?? [];
    return areas
      .filter((area) => !auditLogGu || area.gu === auditLogGu)
      .map((area) => area.dong)
      .filter((dong, index, dongs) => dongs.indexOf(dong) === index)
      .sort();
  }, [areasQuery.data, auditLogGu]);

  const selectedAssignment = useMemo(() => {
    return (areaAssignmentsQuery.data ?? []).find((assignment) =>
      assignment.gu === selectedGu
      && assignment.dong === selectedScopeDong
      && assignment.assignmentType === selectedAssignmentType) ?? null;
  }, [areaAssignmentsQuery.data, selectedAssignmentType, selectedScopeDong, selectedGu]);

  const canEditSelectedArea = selectedAssignment?.assigneeUserId === currentAdmin?.userId;
  const selectedAssignmentLabel = selectedAssignment?.assigneeLabel || selectedAssignment?.assigneeUserId || "미지정";
  const pendingHazardCount = pendingHazardReportsQuery.data?.content.length ?? 0;
  const pendingHazardBadge = pendingHazardReportsQuery.data?.hasNext
    ? `${pendingHazardCount}+`
    : String(pendingHazardCount);
  const pendingHazardNotifications = useMemo(() => {
    return (pendingHazardReportsQuery.data?.content ?? []).slice(0, 4);
  }, [pendingHazardReportsQuery.data]);
  const adminRoleLabel = currentAdmin?.role === "ADMIN" ? "Admin" : currentAdmin?.role ?? "Admin";
  const dashboardSummary = dashboardSummaryQuery.data;
  const dashboardPeriodText = formatPeriodRange(
    dashboardSummary?.period.from ?? normalizedDashboardRange.from,
    dashboardSummary?.period.to ?? normalizedDashboardRange.to,
  );
  const normalizedDashboardRangeDraft = useMemo(
    () => normalizeDashboardDateRange(dashboardRangeDraft),
    [dashboardRangeDraft],
  );
  const dashboardRangeDirty = normalizedDashboardRangeDraft.from !== normalizedDashboardRange.from
    || normalizedDashboardRangeDraft.to !== normalizedDashboardRange.to;

  function logoutAdmin() {
    void logoutAdminSession(accessToken).catch(() => undefined);
    storeAdminAccessToken("");
    setAccessToken("");
    setTokenInput("");
    setAdminPrincipal(null);
  }

  function toggleNavSection(sectionId: AdminNavSectionId) {
    setExpandedNavSections((sections) => ({
      ...sections,
      [sectionId]: !sections[sectionId],
    }));
  }

  function toggleFacilityCategory(category: PlaceCategory) {
    setSelectedFacilityCategories((categories) => {
      const nextCategories = categories.includes(category)
        ? categories.filter((item) => item !== category)
        : [...categories, category];
      return placeCategories.filter((item) => nextCategories.includes(item));
    });
  }

  function selectAllFacilityCategories() {
    setSelectedFacilityCategories([...placeCategories]);
  }

  function clearFacilityCategories() {
    setSelectedFacilityCategories([]);
    setSelectedFacility(null);
    setFacilityPickedLocation(null);
    setFacilityLocationPickEnabled(false);
  }

  useEffect(() => {
    if (!isNotificationOpen && !isDashboardRangeOpen) {
      return;
    }

    function handleOutsidePointer(event: MouseEvent) {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }

      const clickedNotification = notificationPanelRef.current?.contains(target);
      const clickedDashboardRange = dashboardRangePanelRef.current?.contains(target);

      if (!clickedNotification && !clickedDashboardRange) {
        setIsNotificationOpen(false);
        setIsDashboardRangeOpen(false);
      }
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsNotificationOpen(false);
        setIsDashboardRangeOpen(false);
      }
    }

    document.addEventListener("mousedown", handleOutsidePointer);
    document.addEventListener("keydown", handleEscape);

    return () => {
      document.removeEventListener("mousedown", handleOutsidePointer);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isDashboardRangeOpen, isNotificationOpen]);

  useEffect(() => {
    setIsNotificationOpen(false);
    setIsDashboardRangeOpen(false);
  }, [page]);

  useEffect(() => {
    if (!isDashboardRangeOpen) {
      return;
    }
    setDashboardRangeDraft(normalizedDashboardRange);
  }, [isDashboardRangeOpen, normalizedDashboardRange]);

  function openDashboardRangePanel() {
    setDashboardRangeDraft(normalizedDashboardRange);
    setIsNotificationOpen(false);
    setIsDashboardRangeOpen(true);
  }

  function applyDashboardRange() {
    const nextRange = normalizeDashboardDateRange(dashboardRangeDraft);
    setDashboardRange(nextRange);
    setDashboardRangeDraft(nextRange);
    setIsDashboardRangeOpen(false);
  }

  function resetDashboardRange() {
    const nextRange = createDashboardPresetRange(6);
    setDashboardRangeDraft(nextRange);
  }

  function renderNavPage(item: AdminPage, options: { child?: boolean } = {}) {
    const child = options.child ?? false;

    return (
      <button
        key={item}
        className={`nav-page-button ${child ? "nav-child-button" : ""} ${page === item ? "active" : ""}`}
        onClick={() => setPage(item)}
        title={pageMeta[item].label}
      >
        {!child && <DashboardIcon name={adminNavIcons[item]} className="nav-icon" />}
        <span className="nav-label">{pageMeta[item].label}</span>
        {item === "hazards" && pendingHazardCount > 0 && (
          <span className="nav-badge" aria-label={`대기 제보 ${pendingHazardBadge}건`}>
            {pendingHazardBadge}
          </span>
        )}
      </button>
    );
  }

  if (!isAdminAuthenticated || !currentAdmin) {
    return (
      <div className="admin-login-shell">
        <div className="admin-login-panel">
          <span className="login-kicker">BusanEumgil Admin</span>
          <h1>관리자 로그인</h1>
          <p>소셜 로그인 후 관리자 권한이 확인된 계정만 운영 화면에 접근할 수 있습니다.</p>
          <AdminAuthPanel
            accessToken={accessToken}
            tokenInput={tokenInput}
            onAccessTokenChange={setAccessToken}
            onTokenInputChange={setTokenInput}
            onAdminVerified={setAdminPrincipal}
          />
        </div>
      </div>
    );
  }

  return (
    <div className={adminShellClassName(sidebarCollapsed)}>
      <aside className="sidebar">
        <div className="brand">
          <button className="brand-home-button" type="button" aria-label="대시보드로 이동" onClick={() => setPage("home")}>
            <span className="brand-logo-mark" aria-hidden="true">
              <img className="brand-logo" src={adminLogoUrl} alt="" />
            </span>
            <span className="brand-copy">
              <strong>부산이음길</strong>
              <small>Admin</small>
            </span>
          </button>
          <button
            className="sidebar-toggle"
            type="button"
            aria-label={sidebarCollapsed ? "사이드바 펼치기" : "사이드바 접기"}
            onClick={() => setSidebarCollapsed((value) => !value)}
          >
            <span aria-hidden="true" />
          </button>
        </div>
        <nav aria-label="관리자 메뉴">
          {adminNavItems.map((item) => {
            if (item.type === "page") {
              return renderNavPage(item.page);
            }

            const expanded = expandedNavSections[item.id];
            const activeSection = item.pages.includes(page);

            return (
              <div key={item.id} className="nav-section">
                <button
                  type="button"
                  className={`nav-section-toggle ${expanded ? "expanded" : ""} ${activeSection ? "active-section" : ""}`}
                  aria-expanded={expanded}
                  onClick={() => toggleNavSection(item.id)}
                >
                  <DashboardIcon name={item.icon} className="nav-icon" />
                  <span className="nav-section-label">{item.label}</span>
                  <DashboardIcon name="chevron" className="nav-section-chevron" />
                </button>
                {expanded && <div className="nav-section-items">{item.pages.map((pageItem) => renderNavPage(pageItem, { child: true }))}</div>}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-admin-card">
          <div className="sidebar-admin-card__identity">
            <span className="sidebar-admin-card__eyebrow">관리자 세션</span>
            <div className="sidebar-admin-card__profile">
              <span className="sidebar-admin-card__avatar" aria-hidden="true">
                <DashboardIcon name="user" />
              </span>
              <div className="sidebar-admin-card__copy">
                <strong>{adminRoleLabel}</strong>
                <small>{currentAdmin.userId}</small>
              </div>
            </div>
          </div>
          <div className="sidebar-admin-card__actions">
            <button type="button" className="sidebar-admin-card__logout-button" onClick={logoutAdmin}>
              로그아웃
            </button>
          </div>
        </div>
      </aside>

      <main
        ref={workspaceRef}
        className={`workspace ${
          page === "facilities"
            ? "workspace-facilities"
            : page === "network" || page === "routeTuning"
              ? "workspace-map-page"
              : ""
        }`}
      >
        <header className="topbar">
          <div>
            <h1>{pageMeta[page].label}</h1>
            {pageMeta[page].description && <p>{pageMeta[page].description}</p>}
          </div>
          <div className="topbar-utility">
            {page === "home" && (
              <div className="topbar-actions home-topbar-actions">
                <div className="topbar-date-range-shell" ref={dashboardRangePanelRef}>
                  <button
                    className={`topbar-date-range ${isDashboardRangeOpen ? "active" : ""}`}
                    type="button"
                    aria-expanded={isDashboardRangeOpen}
                    aria-haspopup="dialog"
                    onClick={() => {
                      if (isDashboardRangeOpen) {
                        setIsDashboardRangeOpen(false);
                        return;
                      }
                      openDashboardRangePanel();
                    }}
                  >
                    <span>{dashboardPeriodText}</span>
                    <DashboardIcon name="calendar" />
                  </button>
                  {isDashboardRangeOpen && (
                    <div className="topbar-date-range-panel" role="dialog" aria-label="대시보드 기간 선택">
                      <div className="topbar-date-range-panel__header">
                        <strong>조회 기간</strong>
                        <p>대시보드 요약과 병목 후보 지도를 같은 기간으로 다시 조회합니다.</p>
                      </div>
                      <div className="topbar-date-range-panel__presets">
                        <button
                          type="button"
                          onClick={() => setDashboardRangeDraft(createDashboardPresetRange(0))}
                        >
                          오늘
                        </button>
                        <button
                          type="button"
                          onClick={() => setDashboardRangeDraft(createDashboardPresetRange(6))}
                        >
                          최근 7일
                        </button>
                        <button
                          type="button"
                          onClick={() => setDashboardRangeDraft(createDashboardPresetRange(29))}
                        >
                          최근 30일
                        </button>
                      </div>
                      <div className="topbar-date-range-panel__fields">
                        <label>
                          <span>시작일</span>
                          <input
                            type="date"
                            value={dashboardRangeDraft.from}
                            max={dashboardRangeDraft.to}
                            onChange={(event) => setDashboardRangeDraft((current) => ({
                              ...current,
                              from: event.target.value,
                            }))}
                          />
                        </label>
                        <label>
                          <span>종료일</span>
                          <input
                            type="date"
                            value={dashboardRangeDraft.to}
                            min={dashboardRangeDraft.from}
                            onChange={(event) => setDashboardRangeDraft((current) => ({
                              ...current,
                              to: event.target.value,
                            }))}
                          />
                        </label>
                      </div>
                      <div className="topbar-date-range-panel__footer">
                        <button type="button" className="secondary" onClick={resetDashboardRange}>
                          최근 7일로 초기화
                        </button>
                        <button
                          type="button"
                          className="primary"
                          onClick={applyDashboardRange}
                          disabled={!dashboardRangeDirty}
                        >
                          적용
                        </button>
                      </div>
                    </div>
                  )}
                </div>
                <button
                  className="topbar-refresh"
                  type="button"
                  onClick={() => {
                    void dashboardSummaryQuery.refetch();
                    void dashboardBottlenecksQuery.refetch();
                  }}
                  disabled={dashboardSummaryQuery.isFetching || dashboardBottlenecksQuery.isFetching}
                >
                  <DashboardIcon name="refresh" />
                  <span>{dashboardSummaryQuery.isFetching || dashboardBottlenecksQuery.isFetching ? "갱신 중" : "새로고침"}</span>
                </button>
              </div>
            )}
            {page !== "home" && page !== "hazards" && page !== "users" && showsAreaSelector && <div className="topbar-actions admin-topbar-actions">
              <>
                <label>
                  구
                  <select
                    value={selectedGu}
                    disabled={applyRoadNetworkMutation.isPending || isRoadEditJobRunning}
                    onChange={(event) => {
                      const nextGu = event.target.value;
                      const nextDong = (areasQuery.data ?? []).find((area) => area.gu === nextGu)?.dong ?? "";
                      setSelectedArea(nextGu, nextDong);
                      setSelectedFacility(null);
                      setSelectedSegment(null);
                    }}
                  >
                    {[...new Set((areasQuery.data ?? []).map((area) => area.gu))]
                      .filter(Boolean)
                      .map((gu) => (
                        <option key={gu} value={gu}>
                          {gu}
                        </option>
                      ))}
                    {!areasQuery.data?.length && <option value={selectedGu}>{selectedGu}</option>}
                  </select>
                </label>
                <label>
                  동
                  <select
                    value={selectedDong}
                    disabled={applyRoadNetworkMutation.isPending || isRoadEditJobRunning}
                    onChange={(event) => {
                      setSelectedArea(selectedGu, event.target.value);
                      setSelectedFacility(null);
                      setSelectedSegment(null);
                    }}
                  >
                    {filteredDongs.map((area) => (
                      <option key={`${area.gu}-${area.dong}`} value={area.dong}>
                        {area.dong}
                      </option>
                    ))}
                    {!filteredDongs.length && <option value={selectedDong}>{selectedDong}</option>}
                  </select>
                </label>
              </>
            </div>}
            <div className="topbar-notification-shell" ref={notificationPanelRef}>
              <button
                className={`topbar-icon-button topbar-notification-button ${isNotificationOpen ? "active" : ""}`}
                type="button"
                aria-label="알림"
                aria-expanded={isNotificationOpen}
                aria-haspopup="dialog"
                onClick={() => {
                  setIsDashboardRangeOpen(false);
                  setIsNotificationOpen((value) => !value);
                }}
              >
                <DashboardIcon name="bell" />
                {pendingHazardCount > 0 && <span className="topbar-notification-badge">{pendingHazardBadge}</span>}
              </button>
              {isNotificationOpen && (
                <div className="topbar-notification-panel" role="dialog" aria-label="운영 알림">
                  <div className="topbar-notification-panel__header">
                    <div>
                      <strong>운영 알림</strong>
                      <p>
                        {usesRealAdminApi
                          ? pendingHazardCount > 0
                            ? `지금 확인이 필요한 대기 제보 ${pendingHazardBadge}건`
                            : "새로 확인할 대기 제보가 없습니다."
                          : "실시간 알림은 관리자 로그인 후 확인할 수 있습니다."}
                      </p>
                    </div>
                    {usesRealAdminApi && (
                      <button
                        type="button"
                        className="topbar-notification-panel__refresh"
                        onClick={() => {
                          void pendingHazardReportsQuery.refetch();
                        }}
                        disabled={pendingHazardReportsQuery.isFetching}
                      >
                        {pendingHazardReportsQuery.isFetching ? "갱신 중" : "갱신"}
                      </button>
                    )}
                  </div>

                  <div className="topbar-notification-panel__summary">
                    <div className="topbar-notification-metric">
                      <span>대기 제보</span>
                      <strong>{usesRealAdminApi ? `${pendingHazardBadge}건` : "-"}</strong>
                    </div>
                    <div className="topbar-notification-metric">
                      <span>표시 범위</span>
                      <strong>{usesRealAdminApi ? (pendingHazardReportsQuery.data?.hasNext ? "최근 20건" : "전체") : "-"}</strong>
                    </div>
                  </div>

                  {usesRealAdminApi ? (
                    pendingHazardNotifications.length > 0 ? (
                      <div className="topbar-notification-list" role="list">
                        {pendingHazardNotifications.map((report) => (
                          <button
                            key={report.reportId}
                            type="button"
                            className="topbar-notification-item"
                            onClick={() => {
                              setPage("hazards");
                              setIsNotificationOpen(false);
                            }}
                          >
                            <span className="topbar-notification-item__badge">대기 제보</span>
                            <strong>{reportTypeLabels[report.reportType] ?? report.reportType}</strong>
                            <span className="topbar-notification-item__meta">
                              제보 #{String(report.reportId).padStart(4, "0")} · 접수 {formatNotificationDateTime(report.createdAt)}
                            </span>
                          </button>
                        ))}
                      </div>
                    ) : (
                      <div className="topbar-notification-empty">
                        대기 중인 제보가 없습니다. 새로운 제보가 들어오면 여기에 표시됩니다.
                      </div>
                    )
                  ) : (
                    <div className="topbar-notification-empty">
                      실시간 제보 알림은 로그인된 관리자 세션에서만 제공됩니다.
                    </div>
                  )}

                  {(usesRealAdminApi || previewPage === "hazards") && (
                    <div className="topbar-notification-panel__footer">
                      <button
                        type="button"
                        className="topbar-notification-panel__link"
                        onClick={() => {
                          setPage("hazards");
                          setIsNotificationOpen(false);
                        }}
                      >
                        불편제보 관리 열기
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </header>

        {page === "home" && (
          <HomeDashboardPage
            summary={dashboardSummary}
            loading={dashboardSummaryQuery.isLoading}
            error={dashboardSummaryQuery.error}
            grafanaUrl={grafanaDashboardUrl}
            bottlenecks={dashboardBottlenecksQuery.data}
            bottlenecksLoading={dashboardBottlenecksQuery.isLoading}
            bottlenecksError={dashboardBottlenecksQuery.error}
            onOpenRouteStats={() => setPage("routeStats")}
            onOpenBottleneckMonitoring={() => setPage("bottleneckMonitoring")}
            onOpenHazards={() => setPage("hazards")}
          />
        )}

        {page === "hazards" && (
          <HazardReportsPage
            accessToken={accessToken}
            adminPrincipal={currentAdmin}
            onLogout={logoutAdmin}
            preview={previewPage === "hazards" && fullShellPreviewEnabled}
          />
        )}

        {page === "routeTuning" && (
          <RouteTuningPage
            accessToken={accessToken}
            gu={selectedGu}
            dong={selectedDong}
            payload={payloadQuery.data}
            loading={payloadQuery.isLoading}
            error={payloadQuery.error}
            selectedSegment={selectedSegment}
            onSelectSegment={setSelectedSegment}
            canEdit={canEditSelectedArea}
            assignmentMessage={
              canEditSelectedArea
                ? `${selectedAssignmentScopeLabel} 보행 네트워크 담당자로 수정할 수 있습니다.`
                : `${selectedAssignmentScopeLabel} 보행 네트워크 담당자만 수정할 수 있습니다. 현재 담당자: ${selectedAssignmentLabel}`
            }
            roadviewContainerRef={roadviewContainerRef}
            onRoadviewChange={setRoadviewDock}
            onSegmentUpdated={() => {
              queryClient.invalidateQueries({ queryKey: ["admin-road-network"] });
              queryClient.invalidateQueries({ queryKey: ["admin-hazard-route-review-network"] });
            }}
          />
        )}

        {page === "routeStats" && (
          <RouteStatsPage
            data={usesRealAdminApi ? routeStatsQuery.data : routeStatsMockResponse}
            dataSourceMode={usesRealAdminApi ? "real" : "mock"}
            loading={usesRealAdminApi && routeStatsQuery.isLoading}
            error={routeStatsQuery.error}
            updatedAt={usesRealAdminApi ? routeStatsQuery.dataUpdatedAt : undefined}
          />
        )}

        {page === "users" && (
          <UserManagementPage
            currentAdmin={currentAdmin}
            users={adminUsersQuery.data ?? []}
            assignments={areaAssignmentsQuery.data ?? []}
            areas={areasQuery.data ?? []}
            loading={adminUsersQuery.isLoading || areaAssignmentsQuery.isLoading}
            error={adminUsersQuery.error || areaAssignmentsQuery.error}
            userRoleError={updateUserRoleMutation.error}
            userRolePending={updateUserRoleMutation.isPending}
            assignmentPending={upsertAssignmentMutation.isPending || updateAssignmentStatusMutation.isPending}
            onUpdateUserRole={(userId, role) => updateUserRoleMutation.mutate({ userId, role })}
            onUpsertAssignment={(request) => upsertAssignmentMutation.mutate(request)}
            onUpdateAssignmentStatus={(assignmentId, status) => updateAssignmentStatusMutation.mutate({ assignmentId, status })}
          />
        )}

        {page === "logs" && (
          <AuditLogsPage
            logs={auditLogs}
            action={auditLogAction}
            gu={auditLogGu}
            dong={auditLogDong}
            actorUserId={auditLogActorUserId}
            guOptions={auditLogGuOptions}
            dongOptions={auditLogDongOptions}
            users={adminUsersQuery.data ?? []}
            loading={auditLogsQuery.isLoading}
            loadingMore={auditLogsQuery.isFetchingNextPage}
            hasNext={Boolean(auditLogsQuery.hasNextPage)}
            error={auditLogsQuery.error}
            onActionChange={setAuditLogAction}
            onGuChange={(gu) => {
              setAuditLogGu(gu);
              setAuditLogDong("");
            }}
            onDongChange={setAuditLogDong}
            onActorUserIdChange={setAuditLogActorUserId}
            onRefresh={() => void auditLogsQuery.refetch()}
            onLoadMore={() => void auditLogsQuery.fetchNextPage()}
          />
        )}

        {page === "network" && (
          <div className={`editor-layout ${networkDetailPanelCollapsed ? "detail-panel-collapsed" : ""}`}>
            <SegmentMap
              payload={payloadQuery.data}
              bridgePayload={bridgeQuery.data}
              loading={payloadQuery.isLoading}
              error={payloadQuery.error}
              draftEdits={draftEdits}
              onDraftEdit={addDraftEdit}
              draftEditCount={draftEdits.length}
              onUndoDraftEdit={undoDraftEdit}
              onClearDraftEdits={clearDraft}
              selectedSegment={selectedSegment}
              onSelectSegment={setSelectedSegment}
              roadviewContainerRef={roadviewContainerRef}
              onRoadviewChange={setRoadviewDock}
              editable={canEditSelectedArea}
            />
            <button
              type="button"
              className="detail-panel-toggle"
              aria-expanded={!networkDetailPanelCollapsed}
              aria-label={networkDetailPanelCollapsed ? "우측 패널 펼치기" : "우측 패널 접기"}
              onClick={() => setNetworkDetailPanelCollapsed((collapsed) => !collapsed)}
            >
              {networkDetailPanelCollapsed ? "<" : ">"}
            </button>
            <aside className="detail-panel">
              <section className="panel-section roadview-dock-section">
                <div className="roadview-panel docked">
                  <div className="roadview-header">
                    <span>Roadview</span>
                    <button
                      className="roadview-close"
                      type="button"
                      aria-label="Close roadview"
                      onClick={() => roadviewDock.onClose?.()}
                      disabled={!roadviewDock.open}
                    >
                      x
                    </button>
                  </div>
                  <div className="roadview-body">
                    <div ref={roadviewContainerRef} className="roadview-container" />
                    {roadviewDock.open && roadviewDock.message && <div className="roadview-empty">{roadviewDock.message}</div>}
                  </div>
                </div>
              </section>
              <section className="panel-section">
                <h3>편집 기준</h3>
                <p className="muted">
                  보행 네트워크 탭은 segment 추가, 삭제, DB 저장에만 사용합니다. 통행, 계단, 보도 폭 같은 segment 속성 검수는 경로 검수 탭에서 진행합니다.
                </p>
              </section>
              <section className="panel-section">
                <h3>변경 draft</h3>
                <div className="metric-grid">
                  <Metric label="현재 edits" value={draftEdits.length} />
                  <Metric label="visible" value={payloadQuery.data?.summary?.visibleSegmentCount ?? "-"} />
                  <Metric label="전체" value={payloadQuery.data?.summary?.segmentCount ?? "-"} />
                  <Metric label="components" value={bridgeQuery.data?.summary?.componentCount ?? "-"} />
                  <Metric label="endpoints" value={bridgeQuery.data?.summary?.endpointCount ?? "-"} />
                  <Metric label="bridges" value={bridgeQuery.data?.summary?.visibleBridgeCandidateCount ?? "-"} />
                </div>
                <p className="muted">Undo / Clear는 지도 상단 toolbar에서 처리합니다. 상세 목록은 DB 저장 전 최종 확인이 필요할 때만 별도 검토합니다.</p>
              </section>
              <section className="panel-section">
                <h3>검수 흐름</h3>
                <dl className="attribute-detail-list">
                  <AttributeRow label="담당자" value={selectedAssignmentLabel} />
                  <AttributeRow label="상태" value={workStatusLabel(selectedAssignment?.status ?? "NOT_STARTED")} />
                </dl>
                {!canEditSelectedArea && (
                  <p className="error-box">현재 계정은 {selectedAssignmentScopeLabel} 담당자가 아니므로 수정할 수 없습니다.</p>
                )}
                <button
                  className="primary"
                  onClick={() => applyRoadNetworkMutation.mutate()}
                  disabled={!draftEdits.length || !canEditSelectedArea || applyRoadNetworkMutation.isPending || isRoadEditJobRunning}
                >
                  {applyRoadNetworkMutation.isPending || isRoadEditJobRunning ? "DB 저장 중" : "DB 저장"}
                </button>
                {activeRoadEditJob && (
                  <p className="muted">
                    작업 #{activeRoadEditJob.jobId} {activeRoadEditJob.message}
                    {roadEditResult && (
                      <>
                        {" "}추가 {roadEditResult.addedSegments}, 제외 {roadEditResult.skippedSegments ?? 0}, 삭제 {roadEditResult.deletedSegments},
                        생성 node {roadEditResult.createdNodes}, snap {roadEditResult.snappedNodes}
                      </>
                    )}
                  </p>
                )}
                {(applyRoadNetworkMutation.error || roadEditJobQuery.error || activeRoadEditJob?.status === "FAILED") && (
                  <p className="error-box">
                    {applyRoadNetworkMutation.error?.message
                      || roadEditJobQuery.error?.message
                      || activeRoadEditJob?.message}
                  </p>
                )}
                <p className="muted">로컬 draft를 비동기 작업으로 등록한 뒤 DB road_nodes, road_segments, segment_features에 반영합니다.</p>
              </section>
            </aside>
          </div>
        )}

        {page === "bottleneckMonitoring" && (
          <BottleneckMonitoringPage
            data={usesRealAdminApi ? bottleneckMonitoringQuery.data : bottleneckMonitoringMockResponse}
            loading={usesRealAdminApi && bottleneckMonitoringQuery.isLoading}
            error={bottleneckMonitoringQuery.error}
          />
        )}

        {page === "notices" && <NoticeComingSoonPage />}

        {page === "facilities" && (
          <div className="facility-management-layout">
            <div className="facility-main-column">
              <FacilityCategoryFilterPanel
                payload={facilityQuery.data}
                selectedCategories={selectedFacilityCategories}
                onToggleCategory={toggleFacilityCategory}
                onSelectAll={selectAllFacilityCategories}
                onClear={clearFacilityCategories}
              />
              <FacilityMap
                payload={filteredFacilityPayload}
                loading={facilityQuery.isLoading}
                error={facilityQuery.error}
                selectedFeature={selectedFacility}
                selectedDetail={placeDetailQuery.data}
                selectedCategories={selectedFacilityCategories}
                onSelectFeature={setSelectedFacility}
                onClearSelection={() => {
                  setSelectedFacility(null);
                  setFacilityPickedLocation(null);
                  setFacilityLocationPickEnabled(false);
                }}
                roadviewContainerRef={roadviewContainerRef}
                onRoadviewChange={setRoadviewDock}
                locationPickEnabled={facilityLocationPickEnabled}
                onPickLocation={async (point) => {
                  try {
                    const geocode = await reverseGeocodePlace(point, accessToken);
                    setFacilityPickedLocation({
                      point,
                      address: geocode.displayAddress ?? geocode.roadAddress ?? geocode.address ?? undefined,
                      nonce: Date.now(),
                    });
                  } catch {
                    setFacilityPickedLocation({ point, nonce: Date.now() });
                  }
                  setFacilityLocationPickEnabled(false);
                }}
              />
            </div>
            <aside className={`detail-panel facility-detail-panel ${selectedFacility ? "is-editing" : ""}`}>
              <section className="panel-section roadview-dock-section">
                <div className="roadview-panel docked">
                  <div className="roadview-header">
                    <span>Roadview</span>
                    <button
                      className="roadview-close"
                      type="button"
                      aria-label="Close roadview"
                      onClick={() => roadviewDock.onClose?.()}
                      disabled={!roadviewDock.open}
                    >
                      x
                    </button>
                  </div>
                  <div className="roadview-body">
                    <div ref={roadviewContainerRef} className="roadview-container" />
                    {roadviewDock.open && roadviewDock.message && <div className="roadview-empty">{roadviewDock.message}</div>}
                  </div>
                </div>
              </section>
              <section className={`panel-section ${selectedFacility ? "facility-edit-section" : "facility-summary-panel"}`}>
                {selectedFacility ? (
                  <div className="facility-edit-heading">
                    <h3>상세정보 수정</h3>
                    <span
                      className={`facility-assignment-badge ${canEditSelectedArea ? "is-editable" : "is-readonly"}`}
                      title={
                        canEditSelectedArea
                          ? `${selectedAssignmentScopeLabel} 편의시설 담당자로 수정할 수 있습니다.`
                          : `${selectedAssignmentScopeLabel} 편의시설 담당자만 수정할 수 있습니다. 현재 담당자: ${selectedAssignmentLabel}`
                      }
                    >
                      {canEditSelectedArea ? "수정 가능" : "담당자 아님"}
                    </span>
                  </div>
                ) : (
                  <h3>선택 시설 요약</h3>
                )}
                {selectedFacility ? (
                  <FacilityDetails
                    feature={selectedFacility}
                    detail={placeDetailQuery.data}
                    loading={placeDetailQuery.isLoading}
                    error={placeDetailQuery.error}
                    savingBasic={updatePlaceMutation.isPending}
                    savingFeatures={updatePlaceFeaturesMutation.isPending}
                    saveError={updatePlaceMutation.error || updatePlaceFeaturesMutation.error}
                    editable={canEditSelectedArea}
                    pickedLocation={facilityPickedLocation}
                    locationPickEnabled={facilityLocationPickEnabled}
                    onToggleLocationPick={() => setFacilityLocationPickEnabled((value) => !value)}
                    onSaveBasic={(placeId, request) => updatePlaceMutation.mutate({ placeId, request })}
                    onSaveFeatures={(placeId, features) => updatePlaceFeaturesMutation.mutate({ placeId, features })}
                  />
                ) : (
                  <>
                    <div className="metric-grid">
                      <Metric label="지도 표시" value={filteredFacilityPayload?.summary?.visibleFacilityCount ?? "-"} />
                      <Metric label="선택 분류" value={selectedFacilityCategories.length} />
                      <Metric label="제공 기관" value={facilityQuery.data?.summary?.providerPlaceIdCount ?? "-"} />
                    </div>
                    <FacilityCategorySummary payload={filteredFacilityPayload} selectedCategories={selectedFacilityCategories} />
                    <p className="muted facility-summary-note">지도에서 편의시설 점을 클릭하면 하단 카드와 수정 패널이 열립니다.</p>
                  </>
                )}
              </section>
            </aside>
          </div>
        )}

      </main>
    </div>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AdminApp />
    </QueryClientProvider>
  );
}

function NoticeComingSoonPage() {
  return (
    <section className="notice-coming-soon admin-page-content" aria-labelledby="notice-coming-soon-title">
      <div className="notice-coming-soon-card">
        <div className="notice-coming-soon-icon" aria-hidden="true">
          <DashboardIcon name="report" />
        </div>
        <span className="notice-coming-soon-badge">준비중</span>
        <h2 id="notice-coming-soon-title">공지사항 관리 기능은 준비중입니다.</h2>
        <p>현재 공지 등록, 수정, 삭제 기능은 제공되지 않습니다.</p>
      </div>
    </section>
  );
}

type MetricTone = "blue" | "green" | "orange" | "red" | "yellow" | "purple";
type TrendDirection = "up" | "down" | "neutral";

function HomeDashboardPage({
  summary,
  loading,
  error,
  grafanaUrl,
  bottlenecks,
  bottlenecksLoading,
  bottlenecksError,
  onOpenRouteStats,
  onOpenBottleneckMonitoring,
  onOpenHazards,
}: {
  summary?: AdminDashboardSummaryResponse;
  loading: boolean;
  error?: Error | null;
  grafanaUrl: string;
  bottlenecks?: AdminDashboardBottleneckResponse;
  bottlenecksLoading: boolean;
  bottlenecksError?: Error | null;
  onOpenRouteStats?: () => void;
  onOpenBottleneckMonitoring?: () => void;
  onOpenHazards?: () => void;
}) {
  const periodLabel = summary
    ? summary.period.from === summary.period.to
      ? summary.period.from
      : `${summary.period.from} ~ ${summary.period.to}`
    : "오늘";
  const bottleneckCount = bottlenecks?.topBottlenecks.length ?? 0;
  const routeCompletionRate = summary?.routes.navigationCompletionRate ?? 0;
  const activeRouteCount = summary?.routes.activeRouteSessions ?? 0;
  const averageSessionText = minutesToClock(summary?.routes.averageNavigationMinutes ?? 0);
  const pendingReportCount = summary?.reports.pendingReports ?? 0;

  const overviewMetrics = summary
    ? [
      {
        title: "전체 이용자",
        value: formatInteger(summary.users.totalUsers),
        unit: "명",
        delta: `신규 ${formatInteger(summary.users.newUsers)}명`,
        trend: "neutral" as TrendDirection,
        caption: "선택 기간",
        icon: "users" as DashboardIconName,
        tone: "blue" as MetricTone,
      },
      {
        title: "기록된 이동 경로",
        value: formatInteger(summary.routes.totalNavigationSessions),
        unit: "건",
        delta: `기간 ${formatInteger(summary.routes.navigationStarted)}건`,
        trend: "neutral" as TrendDirection,
        caption: "DB 집계",
        icon: "route" as DashboardIconName,
        tone: "green" as MetricTone,
      },
      {
        title: "평균 이동 속도",
        value: (summary.routes.averageRouteSpeedMps ?? 0) > 0 ? formatSpeed(summary.routes.averageRouteSpeedMps) : "-",
        unit: "m/s",
        delta: `${formatInteger(summary.routes.navigationCompleted)}건 완료`,
        trend: "neutral" as TrendDirection,
        caption: "경로 스냅샷 기준",
        icon: "speedometer" as DashboardIconName,
        tone: "orange" as MetricTone,
      },
      {
        title: "병목 구간 수",
        value: bottlenecksLoading && !bottlenecks ? "-" : formatInteger(bottleneckCount),
        unit: "개",
        delta: `제보 ${formatInteger(summary.reports.pendingReports)}건 대기`,
        trend: "neutral" as TrendDirection,
        caption: bottlenecks?.telemetryBased ? "실측 기반" : "경로+제보 후보",
        icon: "warning" as DashboardIconName,
        tone: "red" as MetricTone,
      },
      {
        title: "불편 제보 접수",
        value: formatInteger(summary.reports.newReports),
        unit: "건",
        delta: `전체 ${formatInteger(summary.reports.totalReports)}건`,
        trend: "neutral" as TrendDirection,
        caption: "선택 기간",
        icon: "chat" as DashboardIconName,
        tone: "yellow" as MetricTone,
      },
    ]
    : [];

  return (
    <section className="home-dashboard admin-home-dashboard">
      {loading && !summary && <p className="muted admin-dashboard-message">홈 지표를 불러오는 중입니다.</p>}
      {error && <p className="error-box admin-dashboard-message">{error.message}</p>}

      {summary && (
        <>
          <div className="admin-kpi-strip">
            {overviewMetrics.map((metric) => (
              <OverviewMetricCard key={metric.title} {...metric} />
            ))}
          </div>

          <div className="admin-home-main">
            <div className="admin-home-left">
              <MovementChartCard metrics={summary.routes.dailyMovement ?? []} onMore={onOpenRouteStats} />
              <BottleneckTableCard
                bottlenecks={bottlenecks}
                loading={bottlenecksLoading}
                error={bottlenecksError}
                onMore={onOpenBottleneckMonitoring}
              />
              <RecentReportsCard summary={summary} onMore={onOpenHazards} />
            </div>

            <div className="admin-home-right">
              <HeatmapCard
                grafanaUrl={grafanaUrl}
                telemetryEnabled={summary.telemetry.enabled}
                bottlenecks={bottlenecks}
                loading={bottlenecksLoading}
                error={bottlenecksError}
              />
              <BottomMetricStrip
                routeCompletionRate={routeCompletionRate}
                activeRouteCount={activeRouteCount}
                averageSessionText={averageSessionText}
                pendingReportCount={pendingReportCount}
                periodLabel={periodLabel}
              />
            </div>
          </div>

        </>
      )}
    </section>
  );
}

function OverviewMetricCard({
  title,
  value,
  unit,
  delta,
  trend,
  caption,
  icon,
  tone,
}: {
  title: string;
  value: string;
  unit: string;
  delta: string;
  trend: TrendDirection;
  caption: string;
  icon: DashboardIconName;
  tone: MetricTone;
}) {
  return (
    <article className={`overview-metric-card overview-metric-card-${tone}`}>
      <div className="overview-metric-copy">
        <span>{title}</span>
        <strong>
          {value}<small>{unit}</small>
        </strong>
        <p className={`metric-trend metric-trend-${trend}`}>
          <span>{trend === "neutral" || delta === "-" ? delta : `${trend === "up" ? "▲" : "▼"} ${delta}`}</span>
          <em>{caption}</em>
        </p>
      </div>
      <span className="overview-metric-icon" aria-hidden="true">
        <DashboardIcon name={icon} />
      </span>
    </article>
  );
}

function DashboardCardHeader({
  title,
  action,
  onActionClick,
  actionTarget,
}: {
  title: string;
  action?: string;
  onActionClick?: () => void;
  actionTarget?: string;
}) {
  return (
    <header className="admin-card-header">
      <h3>{title}</h3>
      {action && (
        <button
          type="button"
          className="admin-card-more"
          onClick={onActionClick}
          data-action-target={actionTarget}
        >
          {action}
          <DashboardIcon name="chevron" />
        </button>
      )}
    </header>
  );
}

function MovementChartCard({
  metrics,
  onMore,
}: {
  metrics: AdminDashboardDailyMovementMetric[];
  onMore?: () => void;
}) {
  const chartMetrics = metrics.slice(-7);
  const plot = {
    left: 42,
    right: 432,
    top: 36,
    bottom: 186,
  };
  const routeMax = Math.max(1, ...chartMetrics.map((metric) => metric.routeCount));
  const userMax = Math.max(1, ...chartMetrics.map((metric) => metric.activeUserCount));
  const routePoints = chartMetrics.map((metric, index) => [
    chartX(index, chartMetrics.length, plot.left, plot.right),
    chartY(metric.routeCount, routeMax, plot.top, plot.bottom),
  ] as const);
  const userPoints = chartMetrics.map((metric, index) => [
    chartX(index, chartMetrics.length, plot.left, plot.right),
    chartY(metric.activeUserCount, userMax, plot.top, plot.bottom),
  ] as const);

  return (
    <article className="admin-dashboard-card movement-chart-card">
      <DashboardCardHeader
        title="사용자 이동 통계"
        action="더보기"
        onActionClick={onMore}
        actionTarget="routeStats"
      />
      <div className="movement-legend">
        <span className="legend-route">이동 경로 수 (건)</span>
        <span className="legend-users">이용자 수 (명)</span>
      </div>
      {chartMetrics.length > 0 ? (
        <svg className="movement-chart" viewBox="0 0 472 226" role="img" aria-label="일자별 이동 경로 수와 이용자 수 추이">
          {[36, 80, 124, 168].map((y) => (
            <line key={y} x1="38" y1={y} x2="432" y2={y} className="chart-grid-line" />
          ))}
          {routePoints.length > 1 && <path d={smoothChartPath(routePoints)} className="chart-line chart-line-route" />}
          {userPoints.length > 1 && <path d={smoothChartPath(userPoints)} className="chart-line chart-line-users" />}
          {routePoints.map(([cx, cy]) => (
            <circle key={`r-${cx}-${cy}`} cx={cx} cy={cy} r="4" className="chart-dot chart-dot-route" />
          ))}
          {userPoints.map(([cx, cy]) => (
            <circle key={`u-${cx}-${cy}`} cx={cx} cy={cy} r="4" className="chart-dot chart-dot-users" />
          ))}
          {chartMetrics.map((metric, index) => (
            <text key={metric.date} x={chartX(index, chartMetrics.length, plot.left, plot.right)} y="214" className="chart-axis-label">
              {formatChartDate(metric.date)}
            </text>
          ))}
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => (
            <text key={ratio} x="30" y={plot.bottom - (plot.bottom - plot.top) * ratio + 4} className="chart-axis-label chart-y-label">
              {formatAxisNumber(routeMax * ratio)}
            </text>
          ))}
        </svg>
      ) : (
        <p className="admin-card-empty">표시할 이동 통계가 없습니다.</p>
      )}
    </article>
  );
}

function chartX(index: number, length: number, left: number, right: number) {
  if (length <= 1) return (left + right) / 2;
  return left + (index * (right - left)) / (length - 1);
}

function chartY(value: number, max: number, top: number, bottom: number) {
  return bottom - (Math.max(0, value) / max) * (bottom - top);
}

function formatChartDate(date: string) {
  return date.slice(5).replace("-", ".");
}

function formatAxisNumber(value: number) {
  if (value >= 1000) {
    return `${Math.round(value / 1000).toLocaleString("ko-KR")},000`;
  }
  return String(Math.round(value));
}

function smoothChartPath(points: readonly (readonly [number, number])[]) {
  if (points.length === 0) return "";
  if (points.length === 1) return `M ${points[0][0]} ${points[0][1]}`;

  return points.reduce((path, point, index) => {
    if (index === 0) return `M ${point[0]} ${point[1]}`;
    const previous = points[index - 1];
    const controlDistance = (point[0] - previous[0]) * 0.48;
    return `${path} C ${previous[0] + controlDistance} ${previous[1]}, ${point[0] - controlDistance} ${point[1]}, ${point[0]} ${point[1]}`;
  }, "");
}

function BottleneckTableCard({
  bottlenecks,
  loading,
  error,
  onMore,
}: {
  bottlenecks?: AdminDashboardBottleneckResponse;
  loading: boolean;
  error?: Error | null;
  onMore?: () => void;
}) {
  const rows = (bottlenecks?.topBottlenecks ?? [])
    .slice(0, 5)
    .map((item, index) => {
      const speed = Number.isFinite(item.averageSpeedMps) ? item.averageSpeedMps : 0;
      return {
        rank: index + 1,
        name: item.name,
        speed,
        reports: item.reportCount,
        tone: speedTone(speed),
        bar: speedBarWidth(speed),
      };
    });

  return (
    <article className="admin-dashboard-card bottleneck-rank-card">
      <DashboardCardHeader
        title="병목 구간 TOP 5"
        action="더보기"
        onActionClick={onMore}
        actionTarget="bottleneckMonitoring"
      />
      {loading && <p className="admin-card-inline-state">병목 후보를 불러오는 중입니다.</p>}
      {error && <p className="admin-card-inline-state error">{error.message}</p>}
      <div className="admin-table-scroll">
        <table className="admin-home-table bottleneck-table">
          <thead>
            <tr>
              <th>순위</th>
              <th>구간명</th>
              <th>평균 속도 (m/s)</th>
              <th>불편 제보(건)</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.rank}>
                <td>{row.rank}</td>
                <td title={row.name}>{row.name}</td>
                <td>
                  <span className="speed-cell">
                    <span className={`speed-bar speed-bar-${row.tone}`}>
                      <i style={{ width: `${row.bar}%` }} />
                    </span>
                    <span className="speed-value">{formatSpeed(row.speed)}</span>
                  </span>
                </td>
                <td>{row.reports}</td>
              </tr>
            ))}
            {!loading && !rows.length && (
              <tr>
                <td colSpan={4} className="admin-empty-row">표시할 병목 후보가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </article>
  );
}

function speedTone(speed: number) {
  if (speed < 0.35) return "red";
  if (speed < 0.5) return "orange";
  return "yellow";
}

function speedBarWidth(speed: number) {
  const bounded = Math.max(0.2, Math.min(1.1, speed || 0.2));
  return Math.round(((1.2 - bounded) / 1.0) * 100);
}

function formatSpeed(speed: number) {
  return speed.toFixed(2);
}

function RecentReportsCard({
  summary,
  onMore,
}: {
  summary: AdminDashboardSummaryResponse;
  onMore?: () => void;
}) {
  const rows = (summary.operations.recentReports ?? [])
    .slice(0, 3)
    .map((report) => ({
      at: formatDateTime(report.createdAt).replace(/-/g, "."),
      place: formatCoordinate(report.lat, report.lng),
      content: compactReportSummary(report.description || reportTypeLabels[report.reportType] || report.reportType),
      status: reportStatusLabels[report.status] ?? report.status,
    }));

  return (
    <article className="admin-dashboard-card recent-report-card">
      <DashboardCardHeader
        title="최근 불편 제보"
        action="더보기"
        onActionClick={onMore}
        actionTarget="hazards"
      />
      <div className="admin-table-scroll">
        <table className="admin-home-table recent-report-table">
          <thead>
            <tr>
              <th>접수일시</th>
              <th>위치</th>
              <th>제보 내용</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={`${row.at}-${row.place}`}>
                <td>{row.at}</td>
                <td>{row.place}</td>
                <td>{row.content}</td>
                <td><span className={`report-status ${reportStatusClassName(row.status)}`}>{row.status}</span></td>
              </tr>
            ))}
            {!rows.length && (
              <tr>
                <td colSpan={4} className="admin-empty-row">최근 접수된 불편 제보가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </article>
  );
}

function reportStatusClassName(status: string) {
  if (status === "검토중") return "report-status-review";
  if (status === "처리완료") return "report-status-done";
  if (status === "반려") return "report-status-rejected";
  return "report-status-received";
}

function compactReportSummary(value: string) {
  return value
    .replace("ROAD_SEGMENT_ATTRIBUTES_UPDATE", "보행 구간 수정")
    .replace("PLACE_ACCESSIBILITY_FEATURES_REPLACE", "편의시설 접근성 반영")
    .replace("NARROW", "좁음")
    .slice(0, 24);
}

function formatCoordinate(lat: number, lng: number) {
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || (lat === 0 && lng === 0)) {
    return "좌표 없음";
  }
  return `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
}

function HeatmapCard({
  grafanaUrl,
  telemetryEnabled,
  bottlenecks,
  loading,
  error,
}: {
  grafanaUrl: string;
  telemetryEnabled: boolean;
  bottlenecks?: AdminDashboardBottleneckResponse;
  loading: boolean;
  error?: Error | null;
}) {
  const hasRealCandidateData = Boolean(bottlenecks && bottlenecks.routeSegments.length > 0);
  const hotspots = hasRealCandidateData ? bottlenecks!.hotspots : adminBottleneckHotspots;
  const routeSegments = hasRealCandidateData ? bottlenecks!.routeSegments : adminBottleneckRoutes;
  const sourceLabel = hasRealCandidateData
    ? bottlenecks!.telemetryBased ? "실측" : "DB 후보"
    : telemetryEnabled ? "실측" : "실측 대기";
  const [viewMode, setViewMode] = useState<"heatmap" | "segment">("heatmap");

  return (
    <article className="admin-dashboard-card heatmap-dashboard-card">
      <header className="admin-card-header heatmap-card-header">
        <div>
          <h3>
            병목 구간 히트맵
            <span className={`heatmap-source-badge ${telemetryEnabled || hasRealCandidateData ? "active" : ""}`}>
              {sourceLabel}
            </span>
          </h3>
        </div>
        <div className="heatmap-actions">
          <span className="heatmap-actions__metric" aria-label="현재 지표">
            속도 기준 (m/s)
          </span>
          <div className="heatmap-actions__toggle" role="tablist" aria-label="지도 보기 전환">
            <button
              type="button"
              role="tab"
              aria-selected={viewMode === "heatmap"}
              className={viewMode === "heatmap" ? "active" : ""}
              onClick={() => setViewMode("heatmap")}
            >
              <DashboardIcon name="settings" /> 히트맵
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={viewMode === "segment"}
              className={viewMode === "segment" ? "active" : ""}
              onClick={() => setViewMode("segment")}
            >
              <DashboardIcon name="route" /> 구간
            </button>
          </div>
          {grafanaUrl && (
            <a href={grafanaUrl} target="_blank" rel="noreferrer">Grafana</a>
          )}
        </div>
      </header>

      {loading && <p className="heatmap-map-state">병목 후보를 불러오는 중입니다.</p>}
      {error && <p className="heatmap-map-state error">{error.message}</p>}
      <AdminBottleneckKakaoMap
        hotspots={hotspots}
        routeSegments={routeSegments}
        presentationMode={viewMode}
      />
    </article>
  );
}

function BottomMetricStrip({
  routeCompletionRate,
  activeRouteCount,
  averageSessionText,
  pendingReportCount,
  periodLabel,
}: {
  routeCompletionRate: number;
  activeRouteCount: number;
  averageSessionText: string;
  pendingReportCount: number;
  periodLabel: string;
}) {
  const metrics = [
    {
      title: "길안내 완료율",
      value: formatPercent(routeCompletionRate),
      sub: `기간 ${periodLabel}`,
      icon: "database" as DashboardIconName,
      tone: "purple" as MetricTone,
    },
    {
      title: "활성 경로 수",
      value: `${formatInteger(activeRouteCount)}개`,
      sub: "현재 ACTIVE 세션",
      icon: "route" as DashboardIconName,
      tone: "blue" as MetricTone,
    },
    {
      title: "평균 세션 시간",
      value: averageSessionText,
      sub: `기간 ${periodLabel}`,
      icon: "clock" as DashboardIconName,
      tone: "green" as MetricTone,
    },
    {
      title: "대기 신고",
      value: `${formatInteger(pendingReportCount)}건`,
      sub: "PENDING 상태",
      icon: "report" as DashboardIconName,
      tone: "yellow" as MetricTone,
    },
  ];

  return (
    <div className="admin-home-bottom">
      {metrics.map((metric) => (
        <article key={metric.title} className={`bottom-metric bottom-metric-${metric.tone}`}>
          <div>
            <span>{metric.title}</span>
            <strong>{metric.value}</strong>
            <small>{metric.sub}</small>
          </div>
          <i aria-hidden="true"><DashboardIcon name={metric.icon} /></i>
        </article>
      ))}
    </div>
  );
}

function DashboardIcon({ name, className = "" }: { name: DashboardIconName; className?: string }) {
  const iconPaths: Record<DashboardIconName, ReactNode> = {
    home: (
      <>
        <path d="M3.5 10.5 12 3.5l8.5 7" />
        <path d="M5.5 10v9.5h13V10" />
        <path d="M9.5 19.5v-5h5v5" />
      </>
    ),
    users: (
      <>
        <circle cx="9" cy="8" r="3" />
        <circle cx="17" cy="9" r="2.5" />
        <path d="M3.8 19c.7-3 2.6-4.5 5.2-4.5s4.5 1.5 5.2 4.5" />
        <path d="M13.8 16.2c.8-.8 1.8-1.2 3.1-1.2 2.1 0 3.6 1.2 4.1 3.5" />
      </>
    ),
    route: (
      <>
        <circle cx="6" cy="6" r="2.5" />
        <circle cx="18" cy="18" r="2.5" />
        <path d="M8.3 6h4.2c2.3 0 3.8 1.2 3.8 3s-1.5 3-3.8 3h-1c-2.3 0-3.8 1.2-3.8 3s1.5 3 3.8 3H15" />
      </>
    ),
    speedometer: (
      <>
        <path d="M5 18a7 7 0 1 1 14 0" />
        <path d="M12 18l4-6" />
        <path d="M8 18h8" />
      </>
    ),
    warning: (
      <>
        <path d="M12 4 21 20H3L12 4Z" />
        <path d="M12 9v5" />
        <path d="M12 17.5h.01" />
      </>
    ),
    chat: (
      <>
        <path d="M4.5 6.5h15v10h-8l-4.5 3v-3H4.5v-10Z" />
        <path d="M8 10h8" />
        <path d="M8 13h5" />
      </>
    ),
    chart: (
      <>
        <path d="M4 19V5" />
        <path d="M4 19h16" />
        <path d="m7 15 3-4 3 2 4-6" />
      </>
    ),
    map: (
      <>
        <path d="m4 6 5-2 6 2 5-2v14l-5 2-6-2-5 2V6Z" />
        <path d="M9 4v14" />
        <path d="M15 6v14" />
      </>
    ),
    report: (
      <>
        <path d="M6 4h9l3 3v13H6V4Z" />
        <path d="M14 4v4h4" />
        <path d="M9 13h6" />
        <path d="M9 16h4" />
      </>
    ),
    content: (
      <>
        <path d="M6 4h12v16H6V4Z" />
        <path d="M9 8h6" />
        <path d="M9 12h6" />
        <path d="M9 16h4" />
      </>
    ),
    settings: (
      <>
        <circle cx="12" cy="12" r="3" />
        <path d="M12 3.5v3" />
        <path d="M12 17.5v3" />
        <path d="M3.5 12h3" />
        <path d="M17.5 12h3" />
        <path d="m5.8 5.8 2.1 2.1" />
        <path d="m16.1 16.1 2.1 2.1" />
        <path d="m18.2 5.8-2.1 2.1" />
        <path d="m7.9 16.1-2.1 2.1" />
      </>
    ),
    database: (
      <>
        <ellipse cx="12" cy="6" rx="7" ry="3" />
        <path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
        <path d="M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6" />
      </>
    ),
    building: (
      <>
        <path d="M5.5 20V5.5h13V20" />
        <path d="M8.5 8.5h2" />
        <path d="M13.5 8.5h2" />
        <path d="M8.5 12h2" />
        <path d="M13.5 12h2" />
        <path d="M10 20v-4h4v4" />
      </>
    ),
    heart: (
      <>
        <path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.7A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" />
      </>
    ),
    medical: (
      <>
        <path d="M12 5v14" />
        <path d="M5 12h14" />
        <rect x="4.5" y="4.5" width="15" height="15" rx="3" />
      </>
    ),
    camera: (
      <>
        <path d="M6 8h3l1.2-2h3.6L15 8h3a2 2 0 0 1 2 2v7.5H4V10a2 2 0 0 1 2-2Z" />
        <circle cx="12" cy="13" r="3" />
      </>
    ),
    utensils: (
      <>
        <path d="M7 4v8" />
        <path d="M4.8 4v4.5c0 1.7 1 3 2.2 3s2.2-1.3 2.2-3V4" />
        <path d="M7 12v8" />
        <path d="M16.5 4v16" />
        <path d="M16.5 4c2 1.6 3 3.4 3 5.5 0 1.8-1.2 3-3 3" />
      </>
    ),
    bed: (
      <>
        <path d="M4.5 11V6" />
        <path d="M4.5 16.5h15" />
        <path d="M19.5 16.5v-3.8A2.7 2.7 0 0 0 16.8 10H9" />
        <path d="M4.5 10H9v6.5" />
        <path d="M4.5 20v-3.5" />
        <path d="M19.5 20v-3.5" />
      </>
    ),
    more: (
      <>
        <circle cx="6.5" cy="12" r="1.3" />
        <circle cx="12" cy="12" r="1.3" />
        <circle cx="17.5" cy="12" r="1.3" />
      </>
    ),
    calendar: (
      <>
        <path d="M6 4v3" />
        <path d="M18 4v3" />
        <path d="M4.5 8h15" />
        <path d="M5 6h14v14H5V6Z" />
      </>
    ),
    refresh: (
      <>
        <path d="M19 7v5h-5" />
        <path d="M5 17v-5h5" />
        <path d="M18 12a6 6 0 0 0-10.2-4.2L5 10" />
        <path d="M6 12a6 6 0 0 0 10.2 4.2L19 14" />
      </>
    ),
    bell: (
      <>
        <path d="M7 10a5 5 0 0 1 10 0c0 5 2 6 2 6H5s2-1 2-6" />
        <path d="M10 19a2.2 2.2 0 0 0 4 0" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="3.5" />
        <path d="M5 20c.8-4 3.2-6 7-6s6.2 2 7 6" />
      </>
    ),
    clock: (
      <>
        <circle cx="12" cy="12" r="8" />
        <path d="M12 7.5v5l3.5 2" />
      </>
    ),
    mobile: (
      <>
        <rect x="7.5" y="3.5" width="9" height="17" rx="2" />
        <path d="M11 17.5h2" />
      </>
    ),
    chevron: <path d="m9 6 6 6-6 6" />,
  };

  return (
    <svg className={className ? `dashboard-icon ${className}` : "dashboard-icon"} viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {iconPaths[name]}
    </svg>
  );
}

function FacilityCategoryFilterPanel({
  payload,
  selectedCategories,
  onToggleCategory,
  onSelectAll,
  onClear,
}: {
  payload?: FacilityPayload;
  selectedCategories: PlaceCategory[];
  onToggleCategory: (category: PlaceCategory) => void;
  onSelectAll: () => void;
  onClear: () => void;
}) {
  const selectedCategorySet = new Set(selectedCategories);
  const categoryCounts = payload?.summary?.categoryCounts ?? {};

  return (
    <section className="facility-category-panel">
      <div className="facility-category-header">
        <div>
          <h3>
            카테고리 필터 <span>(다중 선택 가능)</span>
          </h3>
          <p>선택한 카테고리만 지도에 표시됩니다.</p>
        </div>
        <div className="facility-category-actions">
          <button type="button" onClick={onSelectAll} disabled={selectedCategories.length === placeCategories.length}>
            전체 선택
          </button>
          <button type="button" onClick={onClear} disabled={selectedCategories.length === 0}>
            선택 초기화
          </button>
        </div>
      </div>
      <div className="facility-category-grid" aria-label="편의시설 카테고리 필터">
        {facilityFilterItems.map((item) => {
          const selected = selectedCategorySet.has(item.category);
          const color = facilityCategoryColor(item.category);

          return (
            <button
              key={item.category}
              type="button"
              className={`facility-category-card ${selected ? "is-selected" : ""}`}
              onClick={() => onToggleCategory(item.category)}
              aria-pressed={selected}
              style={{ "--facility-accent": color } as CSSProperties}
            >
              <span className="facility-category-card__icon" aria-hidden="true">
                <DashboardIcon name={item.icon} />
              </span>
              <span className="facility-category-card__body">
                <strong>{facilityCategoryLabel(item.category)}</strong>
                <small>{categoryCounts[item.category] ?? 0}개</small>
              </span>
              <span className="facility-category-card__check" aria-hidden="true">
                {selected ? "✓" : ""}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function FacilityCategorySummary({
  payload,
  selectedCategories,
}: {
  payload?: FacilityPayload;
  selectedCategories: PlaceCategory[];
}) {
  const visibleCounts = payload?.summary?.visibleCategoryCounts ?? {};
  const visibleCategories = selectedCategories.filter((category) => (visibleCounts[category] ?? 0) > 0);

  if (selectedCategories.length === 0) {
    return <p className="muted facility-summary-note">선택한 카테고리가 없어 지도에 표시되는 시설이 없습니다.</p>;
  }

  if (visibleCategories.length === 0) {
    return <p className="muted facility-summary-note">현재 선택한 구에는 선택 카테고리 시설이 없습니다.</p>;
  }

  return (
    <div className="facility-summary-list">
      {visibleCategories.map((category) => (
        <div key={category}>
          <span style={{ background: facilityCategoryColor(category) }} />
          <strong>{facilityCategoryLabel(category)}</strong>
          <em>{visibleCounts[category] ?? 0}개</em>
        </div>
      ))}
    </div>
  );
}

function UserManagementPage({
  currentAdmin,
  users,
  assignments,
  areas,
  loading,
  error,
  userRoleError,
  userRolePending,
  assignmentPending,
  onUpdateUserRole,
  onUpsertAssignment,
  onUpdateAssignmentStatus,
}: {
  currentAdmin: AdminMeResponse;
  users: AdminUserResponse[];
  assignments: Assignment[];
  areas: { gu: string; dong: string }[];
  loading: boolean;
  error?: Error | null;
  userRoleError?: Error | null;
  userRolePending: boolean;
  assignmentPending: boolean;
  onUpdateUserRole: (userId: string, role: UserRole) => void;
  onUpsertAssignment: (request: { gu: string; dong: string; assignmentType: AssignmentType; assigneeUserId: string | null; status: WorkStatus }) => void;
  onUpdateAssignmentStatus: (assignmentId: number, status: WorkStatus) => void;
}) {
  const adminUsers = users.filter((user) => user.role === "ADMIN").sort((left, right) => {
    if (left.userId === currentAdmin.userId) return -1;
    if (right.userId === currentAdmin.userId) return 1;
    return left.userId.localeCompare(right.userId);
  });
  const assignmentByArea = new Map(assignments.map((assignment) => [`${assignment.gu}:${assignment.dong}:${assignment.assignmentType}`, assignment]));
  const sourceAreas = areas.length
    ? areas
    : [...new Map(assignments.map((assignment) => [`${assignment.gu}:${assignment.dong}`, { gu: assignment.gu, dong: assignment.dong }])).values()];
  const guOptions = [...new Set(sourceAreas.map((area) => area.gu))].filter(Boolean).sort((left, right) => left.localeCompare(right, "ko"));
  const guAreas = guOptions.map((gu) => ({ gu, dong: allDongScope }));
  const [promoteUserId, setPromoteUserId] = useState("");

  return (
    <div className="user-management-layout">
      <section className="panel-section">
        <h3>관리자 계정</h3>
        <form
          className="admin-promote-form"
          onSubmit={(event) => {
            event.preventDefault();
            const userId = promoteUserId.trim();
            if (!userId) return;
            onUpdateUserRole(userId, "ADMIN");
            setPromoteUserId("");
          }}
        >
          <label>
            userId로 관리자 추가
            <input
              value={promoteUserId}
              placeholder="UUID"
              onChange={(event) => setPromoteUserId(event.target.value)}
            />
          </label>
          <button type="submit" disabled={userRolePending || !promoteUserId.trim()}>
            ADMIN 승격
          </button>
        </form>
        {loading && <p className="muted">사용자 정보를 불러오는 중입니다.</p>}
        {error && <p className="error-box">{error.message}</p>}
        {userRoleError && <p className="error-box">{userRoleError.message}</p>}
        <div className="admin-table-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th>사용자</th>
                <th>권한</th>
                <th>변경</th>
              </tr>
            </thead>
            <tbody>
              {adminUsers.map((user) => (
                <tr key={user.userId}>
                  <td>
                    <strong>{adminUserLabel(user)}</strong>
                    <span>{user.userId}</span>
                  </td>
                  <td>{user.role}</td>
                  <td>
                    <select
                      value={user.role}
                      disabled={userRolePending || user.userId === currentAdmin.userId}
                      onChange={(event) => onUpdateUserRole(user.userId, event.target.value as UserRole)}
                    >
                      <option value="USER">USER</option>
                      <option value="ADMIN">ADMIN</option>
                    </select>
                  </td>
                </tr>
              ))}
              {!adminUsers.length && (
                <tr>
                  <td colSpan={3}>관리자가 없습니다.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel-section">
        <h3>구 담당자 및 작업 상태</h3>
        <p className="muted">보행 네트워크와 편의시설 담당자를 구 단위로 분리합니다. 담당자로 지정된 관리자만 해당 구를 수정할 수 있습니다.</p>
        <AssignmentTable
          title="보행 네트워크 담당 현황"
          assignmentType="ROAD_NETWORK"
          normalizedAreas={guAreas}
          assignmentByArea={assignmentByArea}
          adminUsers={adminUsers}
          assignmentPending={assignmentPending}
          onUpsertAssignment={onUpsertAssignment}
          onUpdateAssignmentStatus={onUpdateAssignmentStatus}
        />
        <AssignmentTable
          title="편의시설 담당 현황"
          assignmentType="FACILITY"
          normalizedAreas={guAreas}
          assignmentByArea={assignmentByArea}
          adminUsers={adminUsers}
          assignmentPending={assignmentPending}
          onUpsertAssignment={onUpsertAssignment}
          onUpdateAssignmentStatus={onUpdateAssignmentStatus}
        />
      </section>
    </div>
  );
}

function AssignmentTable({
  title,
  assignmentType,
  normalizedAreas,
  assignmentByArea,
  adminUsers,
  assignmentPending,
  onUpsertAssignment,
  onUpdateAssignmentStatus,
}: {
  title: string;
  assignmentType: AssignmentType;
  normalizedAreas: { gu: string; dong: string }[];
  assignmentByArea: Map<string, Assignment>;
  adminUsers: AdminUserResponse[];
  assignmentPending: boolean;
  onUpsertAssignment: (request: { gu: string; dong: string; assignmentType: AssignmentType; assigneeUserId: string | null; status: WorkStatus }) => void;
  onUpdateAssignmentStatus: (assignmentId: number, status: WorkStatus) => void;
}) {
  return (
    <>
      <h4>{title}</h4>
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th>구</th>
              <th>범위</th>
              <th>담당자</th>
              <th>상태</th>
              <th>수정일</th>
            </tr>
          </thead>
          <tbody>
            {normalizedAreas.map((area) => {
              const assignment = assignmentByArea.get(`${area.gu}:${area.dong}:${assignmentType}`);
              const status = assignment?.status ?? "NOT_STARTED";
              return (
                <tr key={`${assignmentType}:${area.gu}:${area.dong}`}>
                  <td>{area.gu}</td>
                  <td>{area.dong}</td>
                  <td>
                    <select
                      value={assignment?.assigneeUserId ?? ""}
                      disabled={assignmentPending}
                      onChange={(event) =>
                        onUpsertAssignment({
                          gu: area.gu,
                          dong: area.dong,
                          assignmentType,
                          assigneeUserId: event.target.value || null,
                          status,
                        })
                      }
                    >
                      <option value="">미지정</option>
                      {adminUsers.map((user) => (
                        <option key={user.userId} value={user.userId}>
                          {adminUserLabel(user)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <select
                      value={status}
                      disabled={assignmentPending}
                      onChange={(event) => {
                        const nextStatus = event.target.value as WorkStatus;
                        if (assignment?.assignmentId) {
                          onUpdateAssignmentStatus(assignment.assignmentId, nextStatus);
                          return;
                        }
                        onUpsertAssignment({
                          gu: area.gu,
                          dong: area.dong,
                          assignmentType,
                          assigneeUserId: assignment?.assigneeUserId ?? null,
                          status: nextStatus,
                        });
                      }}
                    >
                      {workStatusOptions.map((item) => (
                        <option key={item} value={item}>
                          {workStatusLabel(item)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>{assignment?.updatedAt ? formatDateTime(assignment.updatedAt) : "-"}</td>
                </tr>
              );
            })}
            {!normalizedAreas.length && (
              <tr>
                <td colSpan={5}>구 목록이 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

function AuditLogsPage({
  logs,
  action,
  gu,
  dong,
  actorUserId,
  guOptions,
  dongOptions,
  users,
  loading,
  loadingMore,
  hasNext,
  error,
  onActionChange,
  onGuChange,
  onDongChange,
  onActorUserIdChange,
  onRefresh,
  onLoadMore,
}: {
  logs: AdminAuditLog[];
  action: string;
  gu: string;
  dong: string;
  actorUserId: string;
  guOptions: string[];
  dongOptions: string[];
  users: AdminUserResponse[];
  loading: boolean;
  loadingMore: boolean;
  hasNext: boolean;
  error?: Error | null;
  onActionChange: (action: string) => void;
  onGuChange: (gu: string) => void;
  onDongChange: (dong: string) => void;
  onActorUserIdChange: (userId: string) => void;
  onRefresh: () => void;
  onLoadMore: () => void;
}) {
  return (
    <section className="audit-log-page">
      <div className="audit-log-controls">
        <div className="audit-log-filters">
          <label>
            작업 종류
            <select value={action} onChange={(event) => onActionChange(event.target.value)}>
              <option value="">전체</option>
              {auditLogActions.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            구
            <select value={gu} onChange={(event) => onGuChange(event.target.value)}>
              <option value="">전체</option>
              {guOptions.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </label>
          <label>
            동
            <select value={dong} onChange={(event) => onDongChange(event.target.value)}>
              <option value="">전체</option>
              {dongOptions.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </label>
          <label>
            작업자
            <select value={actorUserId} onChange={(event) => onActorUserIdChange(event.target.value)}>
              <option value="">전체</option>
              {users.map((user) => (
                <option key={user.userId} value={user.userId}>
                  {adminUserLabel(user)}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button type="button" onClick={onRefresh} disabled={loading}>
          새로고침
        </button>
      </div>
      {loading && <p className="muted">변경 로그를 불러오는 중입니다.</p>}
      {error && <p className="error-box">{error.message}</p>}
      <div className="audit-log-list">
        {logs.map((log) => (
          <article key={log.logId} className="audit-log-card">
            <header>
              <strong>{adminAuditActionLabel(log.action)}</strong>
              <time>{formatDateTime(log.createdAt)}</time>
            </header>
            <p>{log.summary}</p>
            <dl className="audit-log-meta">
              <div>
                <dt>작업자</dt>
                <dd title={log.actorUserId}>{shortId(log.actorUserId)}</dd>
              </div>
              <div>
                <dt>대상</dt>
                <dd>{log.targetType}{log.targetId ? ` #${log.targetId}` : ""}</dd>
              </div>
              <div>
                <dt>구/범위</dt>
                <dd>{log.gu && log.dong ? `${log.gu} ${log.dong}` : "-"}</dd>
              </div>
            </dl>
            {Boolean(log.beforeJson || log.afterJson) && (
              <details className="audit-log-json">
                <summary>변경 전/후 보기</summary>
                <div>
                  <pre>{formatJson(log.beforeJson)}</pre>
                  <pre>{formatJson(log.afterJson)}</pre>
                </div>
              </details>
            )}
          </article>
        ))}
        {!loading && !logs.length && <p className="muted">표시할 변경 로그가 없습니다.</p>}
      </div>
      {hasNext && (
        <button
          className="audit-log-more-button"
          type="button"
          onClick={onLoadMore}
          disabled={loadingMore}
        >
          {loadingMore ? "불러오는 중..." : "더 보기"}
        </button>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

const workStatusOptions: WorkStatus[] = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "HOLD"];

function workStatusLabel(status: WorkStatus) {
  switch (status) {
    case "NOT_STARTED":
      return "미시작";
    case "IN_PROGRESS":
      return "진행중";
    case "COMPLETED":
      return "완료";
    case "HOLD":
      return "보류";
  }
}

function shortId(userId: string) {
  return userId.length > 8 ? userId.slice(0, 8) : userId;
}

function adminUserLabel(user: AdminUserResponse) {
  return shortId(user.userId);
}

function adminAuditActionLabel(action: string) {
  switch (action) {
    case "ROAD_NETWORK_EDIT_APPLY":
      return "보행 네트워크 반영";
    case "ROAD_SEGMENT_ATTRIBUTES_UPDATE":
      return "segment 속성 변경";
    case "PLACE_BASIC_UPDATE":
      return "편의시설 기본 정보 변경";
    case "PLACE_ACCESSIBILITY_FEATURES_REPLACE":
      return "편의시설 접근성 변경";
    default:
      return action;
  }
}

function formatJson(value: unknown) {
  if (value == null) {
    return "-";
  }
  return JSON.stringify(value, null, 2);
}

function formatDateTime(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

function toDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function createDashboardPresetRange(daysBack: number): DashboardDateRange {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - Math.max(0, daysBack));
  return {
    from: toDateInputValue(from),
    to: toDateInputValue(to),
  };
}

function normalizeDashboardDateRange(range: DashboardDateRange): DashboardDateRange {
  const fallback = createDashboardPresetRange(6);
  const baseFrom = range.from || fallback.from;
  const baseTo = range.to || fallback.to;

  if (baseFrom > baseTo) {
    return { from: baseTo, to: baseFrom };
  }

  return {
    from: baseFrom,
    to: baseTo,
  };
}

function formatNotificationDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return formatDateTime(value).replace(/-/g, ".");
  }

  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");

  return `${month}.${day} ${hours}:${minutes}`;
}

function formatPeriodRange(from: string, to: string) {
  const start = from.replace(/-/g, ".");
  const end = to.replace(/-/g, ".");
  return start === end ? start : `${start} ~ ${end}`;
}

function formatInteger(value: number) {
  return integerFormatter.format(value);
}

function formatDecimal(value: number) {
  return decimalFormatter.format(value);
}

function formatPercent(value: number) {
  return percentFormatter.format(value);
}

function minutesToClock(minutes: number) {
  const totalSeconds = Math.max(0, Math.round(minutes * 60));
  const hours = Math.floor(totalSeconds / 3600);
  const mins = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, mins, seconds].map((part) => String(part).padStart(2, "0")).join(":");
}

function FacilityDetails({
  feature,
  detail,
  loading,
  error,
  savingBasic,
  savingFeatures,
  saveError,
  editable,
  pickedLocation,
  locationPickEnabled,
  onToggleLocationPick,
  onSaveBasic,
  onSaveFeatures,
}: {
  feature: FacilityFeature;
  detail?: AdminPlaceDetailResponse;
  loading: boolean;
  error?: Error | null;
  savingBasic: boolean;
  savingFeatures: boolean;
  saveError?: Error | null;
  editable: boolean;
  pickedLocation: { point: GeoPoint; address?: string; nonce: number } | null;
  locationPickEnabled: boolean;
  onToggleLocationPick: () => void;
  onSaveBasic: (placeId: number, request: AdminPlaceUpdateRequest) => void;
  onSaveFeatures: (placeId: number, features: PlaceAccessibilityFeature[]) => void;
}) {
  const properties = feature.properties;
  const [name, setName] = useState(properties.name || "");
  const [category, setCategory] = useState<PlaceCategory>(properties.category);
  const [address, setAddress] = useState(properties.address || "");
  const [providerPlaceId, setProviderPlaceId] = useState(properties.providerPlaceId || "");
  const [lat, setLat] = useState(String(feature.geometry.coordinates[1] ?? ""));
  const [lng, setLng] = useState(String(feature.geometry.coordinates[0] ?? ""));
  const [features, setFeatures] = useState<Record<AccessibilityFeatureType, boolean>>(() =>
    Object.fromEntries(accessibilityFeatureTypes.map((featureType) => [featureType, false])) as Record<AccessibilityFeatureType, boolean>,
  );

  useEffect(() => {
    setName(properties.name || "");
    setCategory(properties.category);
    setAddress(properties.address || "");
    setProviderPlaceId(properties.providerPlaceId || "");
    setLat(String(feature.geometry.coordinates[1] ?? ""));
    setLng(String(feature.geometry.coordinates[0] ?? ""));
    setFeatures(
      Object.fromEntries(accessibilityFeatureTypes.map((featureType) => [featureType, false])) as Record<AccessibilityFeatureType, boolean>,
    );
  }, [feature]);

  useEffect(() => {
    if (!detail) return;
    setName(detail.name);
    setCategory(detail.category);
    setAddress(detail.address ?? "");
    setProviderPlaceId(detail.providerPlaceId ?? "");
    setLat(String(detail.point.lat));
    setLng(String(detail.point.lng));
    setFeatures(
      Object.fromEntries(
        accessibilityFeatureTypes.map((featureType) => [
          featureType,
          detail.accessibilityFeatures.some((item) => item.featureType === featureType && item.isAvailable),
        ]),
      ) as Record<AccessibilityFeatureType, boolean>,
    );
  }, [detail]);

  useEffect(() => {
    if (!pickedLocation) return;
    setLat(String(pickedLocation.point.lat));
    setLng(String(pickedLocation.point.lng));
    if (pickedLocation.address) {
      setAddress(pickedLocation.address);
    }
  }, [pickedLocation]);

  const placeId = Number(properties.placeId);
  const parsedLat = Number(lat);
  const parsedLng = Number(lng);
  const canSave = Number.isFinite(placeId) && Boolean(detail) && Number.isFinite(parsedLat) && Number.isFinite(parsedLng);
  const formDisabled = !editable || loading || !detail;

  function saveBasic() {
    if (!canSave) return;
    onSaveBasic(placeId, {
      name,
      category,
      address,
      providerPlaceId,
      point: {
        lat: parsedLat,
        lng: parsedLng,
      },
    });
  }

  function saveFeatures() {
    if (!canSave) return;
    onSaveFeatures(
      placeId,
      accessibilityFeatureTypes.map((featureType) => ({
        featureType,
        isAvailable: features[featureType],
      })),
    );
  }

  return (
    <div className="facility-edit-panel">
      <div className="facility-edit-meta">
        <span>place {properties.placeId}</span>
        <span>provider {properties.providerPlaceId || "-"}</span>
        <span>{facilityCategoryLabel(properties.category)}</span>
      </div>
      {loading && <p className="muted facility-edit-status">상세 정보를 불러오는 중입니다.</p>}
      {error && <p className="error-box">{error.message}</p>}
      {saveError && <p className="error-box">{saveError.message}</p>}
      <div className="facility-edit-actions facility-edit-primary-actions">
        <button className="facility-secondary-action" type="button" onClick={onToggleLocationPick} disabled={formDisabled}>
          {locationPickEnabled ? "지도 클릭 대기 중" : "지도 클릭으로 위치 선택"}
        </button>
        <button className="primary" type="button" onClick={saveBasic} disabled={savingBasic || !canSave || !editable}>
          {savingBasic ? "저장 중" : "기본 정보 저장"}
        </button>
      </div>
      <div className="facility-edit-grid">
        <label className="facility-field-wide">
          이름
          <input value={name} disabled={formDisabled} onChange={(event) => setName(event.target.value)} />
        </label>
        <label>
          카테고리
          <select value={category} disabled={formDisabled} onChange={(event) => setCategory(event.target.value as PlaceCategory)}>
            {placeCategories.map((item) => (
              <option key={item} value={item}>
                {facilityCategoryLabel(item)}
              </option>
            ))}
          </select>
        </label>
        <label>
          provider
          <input value={providerPlaceId} disabled={formDisabled} onChange={(event) => setProviderPlaceId(event.target.value)} />
        </label>
        <label className="facility-field-wide">
          주소
          <textarea rows={2} value={address} disabled={formDisabled} title={address} onChange={(event) => setAddress(event.target.value)} />
        </label>
        <label>
          lat
          <input value={lat} disabled={formDisabled} onChange={(event) => setLat(event.target.value)} />
        </label>
        <label>
          lng
          <input value={lng} disabled={formDisabled} onChange={(event) => setLng(event.target.value)} />
        </label>
      </div>
      <div className="facility-edit-feature-list">
        {accessibilityFeatureTypes.map((featureType) => (
          <label key={featureType} className={`facility-feature-option ${features[featureType] ? "is-checked" : ""}`}>
            <input
              type="checkbox"
              disabled={formDisabled}
              checked={features[featureType]}
              onChange={(event) => setFeatures((value) => ({ ...value, [featureType]: event.target.checked }))}
            />
            <span className="facility-feature-checkbox" aria-hidden="true" />
            <span className="facility-feature-label">{accessibilityFeatureLabel(featureType)}</span>
          </label>
        ))}
      </div>
      <div className="facility-edit-actions facility-edit-accessibility-actions">
        <button className="primary" type="button" onClick={saveFeatures} disabled={savingFeatures || !canSave || !editable}>
          {savingFeatures ? "저장 중" : "접근성 저장"}
        </button>
      </div>
    </div>
  );
}

function AttributeRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function accessibilityFeatureLabel(featureType: AccessibilityFeatureType) {
  switch (featureType) {
    case "accessibleEntrance":
      return "단차 없는 출입";
    case "elevator":
      return "엘리베이터";
    case "accessibleToilet":
      return "장애인 화장실";
    case "accessibleParking":
      return "장애인 주차";
    case "chargingStation":
      return "전동보장구 충전";
    case "accessibleRoom":
      return "객실 이용";
    case "guidanceFacility":
      return "안내시설";
  }
}

function formatNumber(value?: number | null) {
  if (typeof value !== "number" || Number.isNaN(value)) return "-";
  return value.toFixed(value % 1 === 0 ? 0 : 1);
}

export default App;
