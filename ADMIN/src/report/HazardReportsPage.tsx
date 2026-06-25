import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  applyAdminRoutingOverrides,
  approveAdminHazardReport,
  completeAdminHazardRouteReview,
  deleteAdminHazardReport,
  fetchAdminRoutingApplyState,
  fetchAdminDashboardSummary,
  fetchAdminHazardReportDetail,
  fetchAdminHazardReports,
  fetchAdminRoadNetworkPayload,
  rejectAdminHazardReport,
  reverseGeocodePlace,
  startAdminHazardRouteReview,
  updateAdminHazardRouteReview,
} from "../api/adminApi";
import type {
  AdminHazardReportDetail,
  AdminHazardReportSummary,
  AdminMeResponse,
  AdminRoutingApplyStateResponse,
  GeoPoint,
  HazardReportStatus,
  HazardReportType,
  SegmentFeature,
  SegmentFeatureType,
  SegmentPayload,
} from "../types";
import { HazardReportLocationPreview } from "./HazardReportLocationPreview";
import { HazardReportRoadviewPreview } from "./HazardReportRoadviewPreview";
import { HazardRouteReviewWorkspace } from "./HazardRouteReviewWorkspace";
import {
  createKakaoMapLink,
  createKakaoRoadviewLink,
  formatHazardCoordinateValue,
  formatHazardCoordinates,
  formatHazardRegionLabel,
  formatHazardTrackingId,
  pickHazardPrimaryImage,
  resolveHazardDisplayAddress,
  type HazardReverseGeocodeResult,
} from "./hazardReportPresentation";
import {
  canDeleteHazardReport,
  canRejectHazardReport,
  canStartHazardApprove,
  canStartHazardRestore,
  clearStoredHazardRouteReview,
  completeHazardRouteReview,
  deriveHazardDbSyncStatus,
  deriveHazardDisplayStatus,
  hydrateHazardRouteReviewRecord,
  isHazardReviewActive,
  isHazardRoutingApplyPending,
  loadStoredHazardRouteReview,
  resolveActiveHazardRouteReview,
  routeReviewCompletionClassName,
  routeReviewCompletionMessage,
  shouldPersistHazardRouteReviewDraft,
  startHazardRouteReview,
  storeHazardRouteReview,
  toAdminHazardRouteReviewIntent,
  toAdminHazardRouteReviewUpdateRequest,
  type HazardRouteReviewRecord,
} from "./hazardRouteReviewState";

type HazardFilterKey = "" | HazardReportStatus;
type HazardBadgeTone = "blue" | "orange" | "green" | "red" | "purple" | "gray";

const reportStatusLabel: Record<HazardReportStatus, string> = {
  PENDING: "대기",
  APPROVED: "승인",
  REJECTED: "반려",
};

const reportTypeLabel: Record<HazardReportType, string> = {
  STAIRS_STEP: "계단·단차 있음",
  BRAILLE_BLOCK: "점자블록 문제",
  SIDEWALK_MISSING: "인도 없음",
  RAMP: "경사로 문제",
  SIDEWALK_WIDTH: "인도폭 문제",
  OTHER_OBSTACLE: "기타 장애물",
};

const percentFormatter = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 1,
});

const HAZARD_ROUTE_REVIEW_RADIUS_METER = 300;
const HAZARD_ROUTE_REVIEW_SEGMENT_LIMIT = 500;

type PreviewHazardRecord = {
  summary: AdminHazardReportSummary;
  detail: AdminHazardReportDetail;
  address: string;
  region: string;
};

const previewStreetImageA = createPreviewStreetImage("#d5e7ff", "#8ab4ff", "현장 이미지 A");
const previewStreetImageB = createPreviewStreetImage("#ffe7cc", "#f59e0b", "현장 이미지 B");
const previewStreetImageC = createPreviewStreetImage("#e7f7e9", "#22c55e", "현장 이미지 C");

const previewHazardRecords: PreviewHazardRecord[] = [
  createPreviewHazardRecord({
    reportId: 1,
    reporterUserId: "김서준",
    reportType: "STAIRS_STEP",
    status: "APPROVED",
    createdAt: "2024-05-18T09:30:00",
    address: "부산진구 중앙대로 672",
    region: "부산광역시 부산진구 부전동",
    point: { lat: 35.095398, lng: 128.853876 },
    description: "횡단보도 공사로가 너무 가파르고 미끄러워 이동이 불편합니다.",
    imageUrls: [previewStreetImageA, previewStreetImageB, previewStreetImageC],
  }),
  createPreviewHazardRecord({
    reportId: 2,
    reporterUserId: "7c91fa8a-b750-4466-8189-f2a76db85dc",
    reportType: "SIDEWALK_MISSING",
    status: "APPROVED",
    createdAt: "2024-05-18T09:29:00",
    address: "해운대구 해운대로 264",
    region: "부산광역시 해운대구 우동",
    point: { lat: 35.16318, lng: 129.16301 },
    description: "카트가 지나가기 어려운 구간이라 우회가 필요합니다.",
    imageUrls: [previewStreetImageB, previewStreetImageA],
  }),
  createPreviewHazardRecord({
    reportId: 3,
    reporterUserId: "1234cdef-bb31-4780-9344-21c30adf0cc2",
    reportType: "SIDEWALK_MISSING",
    status: "REJECTED",
    createdAt: "2024-05-18T04:29:00",
    address: "동구 중앙대로 206",
    region: "부산광역시 동구 초량동",
    point: { lat: 35.116991, lng: 129.041411 },
    description: "공사 구간은 맞지만 이미 복구가 완료된 것으로 보입니다.",
    imageUrls: [previewStreetImageC],
  }),
  createPreviewHazardRecord({
    reportId: 4,
    reporterUserId: "12a9ecf1-2c53-47f9-986f-49cd4db0e182",
    reportType: "SIDEWALK_MISSING",
    status: "APPROVED",
    createdAt: "2024-05-18T04:14:00",
    address: "동구 중앙대로 206",
    region: "부산광역시 동구 초량동",
    point: { lat: 35.117022, lng: 129.041332 },
    description: "유모차 통과가 어려울 정도로 임시 펜스가 좁게 설치돼 있습니다.",
    imageUrls: [previewStreetImageA],
  }),
  createPreviewHazardRecord({
    reportId: 5,
    reporterUserId: "1e093cdc-84c5-4e80-9b31-9f27f7d8b021",
    reportType: "SIDEWALK_WIDTH",
    status: "PENDING",
    createdAt: "2024-05-18T01:23:00",
    address: "중구 남포길26번길 38",
    region: "부산광역시 중구 남포동",
    point: { lat: 35.098235, lng: 129.032387 },
    description: "보행 폭이 너무 좁아 휠체어 진입이 어렵습니다.",
    imageUrls: [previewStreetImageB],
  }),
  createPreviewHazardRecord({
    reportId: 6,
    reporterUserId: "1e093cdc-84c5-4e80-9b31-9f27f7d8b021",
    reportType: "STAIRS_STEP",
    status: "APPROVED",
    createdAt: "2024-05-17T22:16:00",
    address: "중구 태종로63번길 38",
    region: "부산광역시 중구 남포동",
    point: { lat: 35.099581, lng: 129.030812 },
    description: "임시 단차가 생겨 경로 제외가 필요합니다.",
    imageUrls: [previewStreetImageA, previewStreetImageC],
  }),
  createPreviewHazardRecord({
    reportId: 7,
    reporterUserId: "12acfdce1-9b88-40bf-8a4d-8af9d0cf1821",
    reportType: "OTHER_OBSTACLE",
    status: "REJECTED",
    createdAt: "2024-05-15T23:03:00",
    address: "수영구 광안해변로 219",
    region: "부산광역시 수영구 광안동",
    point: { lat: 35.153175, lng: 129.118991 },
    description: "낙석 신고였지만 현장 확인 결과 이동 동선과 무관했습니다.",
    imageUrls: [previewStreetImageC],
  }),
  createPreviewHazardRecord({
    reportId: 8,
    reporterUserId: "1234cdef-0f72-48e8-83f6-22134fd0ad15",
    reportType: "STAIRS_STEP",
    status: "REJECTED",
    createdAt: "2024-05-15T15:57:00",
    address: "금정구 중앙대로 1717",
    region: "부산광역시 금정구 부곡동",
    point: { lat: 35.229145, lng: 129.090811 },
    description: "원상복구 검토가 필요한 임시 구조물입니다.",
    imageUrls: [previewStreetImageB, previewStreetImageC],
  }),
  createPreviewHazardRecord({
    reportId: 9,
    reporterUserId: "1234cdef-0f72-48e8-83f6-22134fd0ad15",
    reportType: "BRAILLE_BLOCK",
    status: "APPROVED",
    createdAt: "2024-05-15T15:49:00",
    address: "금정구 중앙대로 1717",
    region: "부산광역시 금정구 부곡동",
    point: { lat: 35.229221, lng: 129.090612 },
    description: "점자블록 훼손 구간으로 지도 반영 대기 상태입니다.",
    imageUrls: [previewStreetImageC, previewStreetImageA],
  }),
  createPreviewHazardRecord({
    reportId: 10,
    reporterUserId: "170b0c2b-e4d1-46f8-9d11-e9a0fa84b8cc",
    reportType: "SIDEWALK_WIDTH",
    status: "REJECTED",
    createdAt: "2024-05-15T15:37:00",
    address: "수영구 수영로 521",
    region: "부산광역시 수영구 수영동",
    point: { lat: 35.166514, lng: 129.114342 },
    description: "현장 검수 결과 기준 폭 이상으로 확인됐습니다.",
    imageUrls: [previewStreetImageA],
  }),
];

const previewSummary = {
  period: {
    from: "2024-05-01",
    to: "2024-05-31",
  },
  reports: {
    totalReports: 328,
    newReports: 12,
    pendingReports: 124,
    approvedReports: 162,
    rejectedReports: 42,
  },
  dbSyncPendingCount: 5,
  latestSnapshotAt: "2024-05-01T14:30:00",
};

interface HazardReportsPageProps {
  accessToken: string;
  adminPrincipal: AdminMeResponse;
  onLogout: () => void;
  preview?: boolean;
}

type HazardReportSessionMeta = {
  viewedAt?: string;
  viewedBy?: string;
  handledAt?: string;
  handledBy?: string;
};

type HazardAreaScope = {
  gu: string;
  dong: string;
};

export function HazardReportsPage({ accessToken, adminPrincipal, onLogout, preview = false }: HazardReportsPageProps) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<HazardFilterKey>("PENDING");
  const [searchQuery, setSearchQuery] = useState("");
  const [cursorStack, setCursorStack] = useState<Array<number | null>>([null]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [isGalleryOpen, setIsGalleryOpen] = useState(false);
  const [detailPaneMode, setDetailPaneMode] = useState<"detail" | "review">("detail");
  const [isCompletingRouteReview, setIsCompletingRouteReview] = useState(false);
  const [routeReviewCompletionNotice, setRouteReviewCompletionNotice] = useState<{
    message: string;
    className: string;
  } | null>(null);
  const [routeReviewSaveNotice, setRouteReviewSaveNotice] = useState<{
    message: string;
    className: string;
  } | null>(null);
  const [reportSessionMeta, setReportSessionMeta] = useState<Record<number, HazardReportSessionMeta>>(() => {
    if (!preview) {
      return {};
    }

    return previewHazardRecords.reduce<Record<number, HazardReportSessionMeta>>((meta, record) => {
      if (record.summary.status === "PENDING") {
        return meta;
      }

      meta[record.summary.reportId] = {
        handledAt: record.summary.createdAt,
        handledBy: "Admin",
      };
      return meta;
    }, {});
  });
  const [routeReviewDrafts, setRouteReviewDrafts] = useState<Record<number, HazardRouteReviewRecord>>(() => {
    if (typeof window === "undefined") {
      return {};
    }

    const drafts: Record<number, HazardRouteReviewRecord> = {};
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index);
      if (!key?.startsWith("busan-eumgil-ADMIN:hazard-route-review:")) {
        continue;
      }
      const reportId = Number(key.slice("busan-eumgil-ADMIN:hazard-route-review:".length));
      if (!Number.isFinite(reportId)) {
        continue;
      }
      const review = loadStoredHazardRouteReview(reportId);
      if (review) {
        drafts[reportId] = review;
      }
    }
    return drafts;
  });
  const cursor = cursorStack[cursorStack.length - 1] ?? null;
  const hasToken = Boolean(accessToken);

  const reportsQuery = useQuery({
    queryKey: ["admin-hazard-reports", status, cursor, accessToken],
    queryFn: () => fetchAdminHazardReports({ status, cursor, size: 10, accessToken }),
    enabled: !preview && hasToken,
    retry: false,
  });

  const summaryQuery = useQuery({
    queryKey: ["admin-dashboard-summary", accessToken],
    queryFn: () => fetchAdminDashboardSummary({ accessToken }),
    enabled: !preview && hasToken,
    retry: false,
    staleTime: 300_000,
  });
  const routingApplyStateQuery = useQuery({
    queryKey: ["admin-routing-apply-state", accessToken],
    queryFn: () => fetchAdminRoutingApplyState(accessToken),
    enabled: !preview && hasToken,
    retry: false,
    staleTime: 15_000,
  });
  const applyRoutingMutation = useMutation({
    mutationFn: () => applyAdminRoutingOverrides(accessToken),
    onMutate: () => {
      setRouteReviewCompletionNotice({
        message: "경로 반영 API 호출 중입니다.",
        className: "warning-box",
      });
    },
    onSuccess: (response) => {
      void queryClient.invalidateQueries({ queryKey: ["admin-routing-apply-state"] });
      void queryClient.refetchQueries({ queryKey: ["admin-routing-apply-state"] });
      const notice = routingApplyNotice(response.routingApplyStatus);
      setRouteReviewCompletionNotice({
        message: response.message ?? notice.message,
        className: notice.className,
      });
    },
    onError: (error) => {
      setRouteReviewCompletionNotice({
        message: error instanceof Error ? error.message : "경로 반영에 실패했습니다.",
        className: "error-box",
      });
    },
  });

  const reportListData = preview
    ? {
        content: previewHazardRecords.map((record) => record.summary),
        size: 10,
        nextCursor: 11,
        hasNext: true,
      }
    : reportsQuery.data;

  const filteredReports = useMemo(() => {
    const normalizedKeyword = searchQuery.trim().toLowerCase();
    return (reportListData?.content ?? []).filter((report) => {
      if (!normalizedKeyword) {
        return true;
      }

      const haystack = [
        reportTypeLabel[report.reportType] ?? report.reportType,
        report.reporterUserId,
        formatHazardTrackingId(report.createdAt, report.reportId),
        formatDateTime(report.createdAt),
      ]
        .join(" ")
        .toLowerCase();

      return haystack.includes(normalizedKeyword);
    });
  }, [reportListData, searchQuery]);

  useEffect(() => {
    if (!filteredReports.length) {
      if (selectedReportId != null) {
        setSelectedReportId(null);
      }
      return;
    }

    if (selectedReportId != null && !filteredReports.some((report) => report.reportId === selectedReportId)) {
      setSelectedReportId(null);
    }
  }, [filteredReports, selectedReportId]);

  const selectedReport = useMemo(
    () => filteredReports.find((report) => report.reportId === selectedReportId) ?? null,
    [filteredReports, selectedReportId],
  );
  const filteredReportReviewDrafts = useMemo(() => {
    const drafts = new Map<number, HazardRouteReviewRecord | null>();
    filteredReports.forEach((report) => {
      drafts.set(
        report.reportId,
        routeReviewDrafts[report.reportId] ?? hydrateHazardRouteReviewRecord(report.latestRouteReview),
      );
    });
    return drafts;
  }, [filteredReports, routeReviewDrafts]);
  const selectedReportReviewDraft = selectedReport
    ? filteredReportReviewDrafts.get(selectedReport.reportId) ?? null
    : null;

  const detailQuery = useQuery({
    queryKey: ["admin-hazard-report-detail", selectedReportId, accessToken],
    queryFn: () => fetchAdminHazardReportDetail(selectedReportId as number, accessToken),
    enabled: !preview && hasToken && selectedReportId != null,
    retry: false,
  });

  const previewRecord = useMemo(
    () => previewHazardRecords.find((record) => record.summary.reportId === selectedReportId) ?? null,
    [selectedReportId],
  );

  function markReportViewed(reportId: number) {
    const viewedAt = new Date().toISOString();
    setReportSessionMeta((current) => {
      const existing = current[reportId];
      if (existing?.viewedAt) {
        return current;
      }

      return {
        ...current,
        [reportId]: {
          ...existing,
          viewedAt,
          viewedBy: adminPrincipal.userId,
        },
      };
    });
  }

  function markReportHandled(reportId: number) {
    const handledAt = new Date().toISOString();
    setReportSessionMeta((current) => {
      const existing = current[reportId];
      return {
        ...current,
        [reportId]: {
          ...existing,
          viewedAt: existing?.viewedAt ?? handledAt,
          viewedBy: existing?.viewedBy ?? adminPrincipal.userId,
          handledAt,
          handledBy: adminPrincipal.userId,
        },
      };
    });
  }

  function setRouteReviewDraft(nextReview: HazardRouteReviewRecord | null) {
    if (!nextReview) {
      return;
    }
    storeHazardRouteReview(nextReview);
    setRouteReviewDrafts((current) => ({
      ...current,
      [nextReview.reportId]: nextReview,
    }));
  }

  function clearRouteReviewDraft(reportId: number) {
    clearStoredHazardRouteReview(reportId);
    setRouteReviewDrafts((current) => {
      const nextDrafts = { ...current };
      delete nextDrafts[reportId];
      return nextDrafts;
    });
  }

  function handleSelectReport(reportId: number) {
    setSelectedReportId(reportId);
    setRouteReviewCompletionNotice(null);
    setRouteReviewSaveNotice(null);
    markReportViewed(reportId);
  }

  const approveMutation = useMutation({
    mutationFn: (reportId: number) => approveAdminHazardReport(reportId, accessToken),
    onSuccess: (response) => {
      markReportHandled(response.reportId);
      setSelectedReportId(response.reportId);
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-reports"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-report-detail", response.reportId] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard-summary"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-routing-apply-state"] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reportId: number) => rejectAdminHazardReport(reportId, accessToken),
    onSuccess: (response) => {
      markReportHandled(response.reportId);
      clearRouteReviewDraft(response.reportId);
      setSelectedReportId(response.reportId);
      setDetailPaneMode("detail");
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-reports"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-report-detail", response.reportId] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard-summary"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (reportId: number) => deleteAdminHazardReport(reportId, accessToken),
    onSuccess: (response) => {
      clearRouteReviewDraft(response.reportId);
      setSelectedReportId(null);
      setDetailPaneMode("detail");
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-reports"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-report-detail", response.reportId] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard-summary"] });
    },
  });

  const startRouteReviewMutation = useMutation({
    mutationFn: ({
      reportId,
      intent,
    }: {
      reportId: number;
      intent: "approve" | "restore";
    }) => startAdminHazardRouteReview(reportId, {
      intent: toAdminHazardRouteReviewIntent(intent),
    }, accessToken),
    onSuccess: (response) => {
      const hydratedReview = hydrateHazardRouteReviewRecord(response);
      if (hydratedReview) {
        setRouteReviewDraft(hydratedReview);
      }
      markReportViewed(response.reportId);
      setSelectedReportId(response.reportId);
      setDetailPaneMode("review");
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-report-detail", response.reportId] });
    },
  });

  const updateRouteReviewMutation = useMutation({
    mutationFn: ({ reportId, review }: { reportId: number; review: HazardRouteReviewRecord }) => updateAdminHazardRouteReview(
      reportId,
      toAdminHazardRouteReviewUpdateRequest(review),
      accessToken,
    ),
    onMutate: () => {
      setRouteReviewSaveNotice({
        message: "검수 초안 저장 중",
        className: "warning-box",
      });
    },
    onSuccess: (response) => {
      const hydratedReview = hydrateHazardRouteReviewRecord(response);
      if (hydratedReview) {
        setRouteReviewDraft(hydratedReview);
      }
      setRouteReviewSaveNotice({
        message: "검수 초안 저장 완료",
        className: "success-box",
      });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-report-detail", response.reportId] });
    },
    onError: (error) => {
      setRouteReviewSaveNotice({
        message: error instanceof Error ? error.message : "검수 초안 저장에 실패했습니다.",
        className: "error-box",
      });
    },
  });

  const completeRouteReviewMutation = useMutation({
    mutationFn: (reportId: number) => completeAdminHazardRouteReview(reportId, accessToken),
    onSuccess: (response) => {
      const hydratedReview = hydrateHazardRouteReviewRecord(response);
      if (hydratedReview) {
        setRouteReviewDraft(hydratedReview);
      }
      markReportHandled(response.reportId);
      setSelectedReportId(response.reportId);
      setDetailPaneMode("detail");
      setRouteReviewCompletionNotice({
        message: routeReviewCompletionMessage(response.routingApplyStatus),
        className: routeReviewCompletionClassName(response.routingApplyStatus),
      });
      void queryClient.invalidateQueries({ queryKey: ["admin-routing-apply-state"] });
      void queryClient.refetchQueries({ queryKey: ["admin-routing-apply-state"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-route-review-network"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-reports"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-hazard-report-detail", response.reportId] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard-summary"] });
    },
  });

  const detail = preview ? previewRecord?.detail ?? null : detailQuery.data;
  const serverReviewDraft = useMemo(
    () => {
      if (!detail) {
        return null;
      }
      return resolveActiveHazardRouteReview(
        detail.status,
        hydrateHazardRouteReviewRecord(detail.latestRouteReview),
      );
    },
    [detail],
  );
  const activeReport = detail ?? selectedReport;
  const activeReviewDraft = activeReport
    ? resolveActiveHazardRouteReview(
      activeReport.status,
      routeReviewDrafts[activeReport.reportId] ?? null,
    ) ?? serverReviewDraft ?? selectedReportReviewDraft ?? null
    : null;
  const previewImages = detail?.imageUrls ?? (selectedReport?.representativeImageUrl ? [selectedReport.representativeImageUrl] : []);
  const selectedImageUrl = pickHazardPrimaryImage(previewImages, selectedImageIndex);
  const reportPoint = activeReport?.reportPoint ?? null;

  useEffect(() => {
    if (preview || !serverReviewDraft) {
      return;
    }
    setRouteReviewDraft(serverReviewDraft);
  }, [preview, serverReviewDraft]);

  const locationQuery = useQuery<HazardReverseGeocodeResult>({
    queryKey: ["admin-hazard-report-address", activeReport?.reportId, reportPoint?.lat, reportPoint?.lng, accessToken],
    queryFn: () => reverseGeocodePlace(reportPoint as GeoPoint, accessToken),
    enabled: !preview && hasToken && reportPoint != null,
    retry: false,
    staleTime: 300_000,
  });

  const locationAddress = preview ? previewRecord?.address ?? null : resolveHazardDisplayAddress(locationQuery.data);
  const locationRegion = preview ? previewRecord?.region ?? "" : formatHazardRegionLabel(locationQuery.data);
  const areaScope = activeReport ? resolveHazardAreaScope(preview ? previewRecord?.region ?? null : null, preview ? null : locationQuery.data) : null;
  const routeReviewAreaScope = activeReviewDraft?.gu && activeReviewDraft?.dong
    ? { gu: activeReviewDraft.gu, dong: activeReviewDraft.dong }
    : areaScope;
  const routeNetworkQuery = useQuery({
    queryKey: [
      "admin-hazard-route-review-network",
      routeReviewAreaScope?.gu,
      routeReviewAreaScope?.dong,
      reportPoint?.lat,
      reportPoint?.lng,
      HAZARD_ROUTE_REVIEW_RADIUS_METER,
      accessToken,
    ],
    queryFn: () => fetchAdminRoadNetworkPayload({
      gu: routeReviewAreaScope?.gu,
      dong: routeReviewAreaScope?.dong,
      centerLat: reportPoint?.lat,
      centerLng: reportPoint?.lng,
      radiusMeter: HAZARD_ROUTE_REVIEW_RADIUS_METER,
      accessToken,
      limit: HAZARD_ROUTE_REVIEW_SEGMENT_LIMIT,
    }),
    enabled: !preview
      && hasToken
      && reportPoint != null
      && Boolean(routeReviewAreaScope?.gu && routeReviewAreaScope?.dong)
      && detailPaneMode === "review"
      && isHazardReviewActive(activeReviewDraft),
    retry: false,
    staleTime: 300_000,
  });
  const coordinateLabel = reportPoint ? formatHazardCoordinates(reportPoint) : "";
  const mapLink = reportPoint && activeReport
    ? createKakaoMapLink(reportPoint, `${reportTypeLabel[activeReport.reportType] ?? activeReport.reportType} 신고 위치`)
    : null;
  const roadviewLink = reportPoint ? createKakaoRoadviewLink(reportPoint) : null;
  const displayStatus = activeReport ? deriveHazardDisplayStatus(activeReport.status, activeReviewDraft) : null;
  const hasRouteReviewScope = preview || Boolean(routeReviewAreaScope?.gu && routeReviewAreaScope?.dong);
  const canStartApprove = Boolean(detail && canStartHazardApprove(detail.status, activeReviewDraft));
  const canReject = Boolean(
    detail
    && canRejectHazardReport(detail.status, activeReviewDraft)
    && !approveMutation.isPending
    && !rejectMutation.isPending
    && !deleteMutation.isPending
    && !preview,
  );
  const canDelete = Boolean(
    detail && canDeleteHazardReport(detail.status, activeReviewDraft)
    && !deleteMutation.isPending
    && !preview,
  );
  const showsDeleteAction = detail ? canDeleteHazardReport(detail.status, null) : false;
  const canRestore = Boolean(detail && canStartHazardRestore(detail.status, activeReviewDraft));
  const isRouteReviewMode = Boolean(activeReport && activeReviewDraft && activeReviewDraft.stage === "IN_PROGRESS" && detailPaneMode === "review");
  const actionError = approveMutation.error instanceof Error
    ? approveMutation.error
    : rejectMutation.error instanceof Error
      ? rejectMutation.error
      : deleteMutation.error instanceof Error
        ? deleteMutation.error
        : startRouteReviewMutation.error instanceof Error
          ? startRouteReviewMutation.error
          : updateRouteReviewMutation.error instanceof Error
            ? updateRouteReviewMutation.error
            : completeRouteReviewMutation.error instanceof Error
              ? completeRouteReviewMutation.error
              : null;

  useEffect(() => {
    setSelectedImageIndex(0);
    setIsGalleryOpen(false);
  }, [detail?.reportId, selectedReportId]);

  useEffect(() => {
    if (!selectedReportId) {
      setDetailPaneMode("detail");
      return;
    }
    const nextDraft = routeReviewDrafts[selectedReportId]
      ?? (detail?.reportId === selectedReportId ? serverReviewDraft : null)
      ?? selectedReportReviewDraft;
    setDetailPaneMode(nextDraft?.stage === "IN_PROGRESS" ? "review" : "detail");
  }, [detail?.reportId, routeReviewDrafts, selectedReportId, selectedReportReviewDraft, serverReviewDraft]);

  function changeStatus(nextStatus: HazardFilterKey) {
    setStatus(nextStatus);
    setCursorStack([null]);
    setSelectedReportId(null);
  }

  function startRouteReview(intent: "approve" | "restore") {
    if (!activeReport) {
      return;
    }
    if (!preview) {
      if (!areaScope?.gu || !areaScope?.dong) {
        return;
      }
      startRouteReviewMutation.mutate({
        reportId: activeReport.reportId,
        intent,
      });
      return;
    }

    const now = new Date().toISOString();
    const nextReview = startHazardRouteReview({
      reportId: activeReport.reportId,
      intent,
      reviewerUserId: adminPrincipal.userId,
      now,
      existing: routeReviewDrafts[activeReport.reportId] ?? null,
    });
    setRouteReviewDraft(nextReview);
    setRouteReviewSaveNotice({
      message: "검수 초안 저장 완료",
      className: "success-box",
    });
    markReportViewed(activeReport.reportId);
    setDetailPaneMode("review");
  }

  function handleRouteReviewChange(nextReview: HazardRouteReviewRecord) {
    const previousReview = activeReport ? routeReviewDrafts[activeReport.reportId] ?? serverReviewDraft : null;
    setRouteReviewDraft(nextReview);
    if (!shouldPersistHazardRouteReviewDraft(previousReview, nextReview)) {
      setRouteReviewSaveNotice(null);
      return;
    }
    if (preview) {
      setRouteReviewSaveNotice({
        message: "검수 초안 저장 완료",
        className: "success-box",
      });
      return;
    }
    updateRouteReviewMutation.mutate({
      reportId: nextReview.reportId,
      review: nextReview,
    });
  }

  async function completeRouteReviewFlow() {
    if (!activeReport || !activeReviewDraft) {
      return;
    }

    setIsCompletingRouteReview(true);
    try {
      if (!preview) {
        await updateRouteReviewMutation.mutateAsync({
          reportId: activeReport.reportId,
          review: activeReviewDraft,
        });
        await completeRouteReviewMutation.mutateAsync(activeReport.reportId);
        return;
      }

      const completedReview = completeHazardRouteReview(activeReviewDraft, new Date().toISOString());
      setRouteReviewDraft(completedReview);
      markReportHandled(activeReport.reportId);
      setDetailPaneMode("detail");
      setRouteReviewCompletionNotice({
        message: routeReviewCompletionMessage("SKIPPED"),
        className: routeReviewCompletionClassName("SKIPPED"),
      });
    } finally {
      setIsCompletingRouteReview(false);
    }
  }

  const reportSummary = preview ? previewSummary.reports : summaryQuery.data?.reports;
  const routingApplyState = preview ? null : routingApplyStateQuery.data ?? null;
  const totalCount = reportSummary?.totalReports ?? reportListData?.content.length ?? 0;
  const pendingCount = reportSummary?.pendingReports ?? countReportsByStatus(reportListData?.content, "PENDING");
  const approvedCount = reportSummary?.approvedReports ?? countReportsByStatus(reportListData?.content, "APPROVED");
  const rejectedCount = reportSummary?.rejectedReports ?? countReportsByStatus(reportListData?.content, "REJECTED");
  const visibleDbSyncPendingCount = filteredReports.reduce((count, report) => (
    isHazardRoutingApplyPending(filteredReportReviewDrafts.get(report.reportId) ?? null, routingApplyState) ? count + 1 : count
  ), 0);
  const dbSyncPendingCount = preview
    ? previewSummary.dbSyncPendingCount
    : visibleDbSyncPendingCount;
  const currentPage = cursorStack.length;
  const paginationItems = buildVisiblePageNumbers(currentPage, Boolean(reportListData?.hasNext));
  const detailTrackingId = activeReport ? formatHazardTrackingId(activeReport.createdAt, activeReport.reportId) : null;
  const detailHistory = activeReport ? buildHazardTimeline(activeReport, adminPrincipal.userId, activeReviewDraft) : [];
  const hasDbSyncQueue = preview
    ? dbSyncPendingCount > 0
    : Boolean(routingApplyState?.dirty || routingApplyState?.applying);
  const latestReportStamp = preview
    ? previewSummary.latestSnapshotAt
    : activeReport?.createdAt
      ?? reportListData?.content[0]?.createdAt
      ?? summaryQuery.data?.period.to
      ?? "";
  const operationSnapshot = activeReport ? buildOperationSnapshot(activeReport.status, activeReviewDraft, routingApplyState) : null;
  const activeSessionMeta = activeReport ? reportSessionMeta[activeReport.reportId] : undefined;
  const processedAt = activeSessionMeta?.handledAt
    ?? activeReviewDraft?.completedAt
    ?? (isHazardReviewActive(activeReviewDraft) ? null : activeReport?.status === "PENDING" ? null : activeReport?.createdAt ?? null);
  const processedBy = activeSessionMeta?.handledBy ?? activeReviewDraft?.reviewerUserId ?? (activeReport?.status === "PENDING" ? null : null);
  const viewedAt = activeSessionMeta?.viewedAt ?? null;
  const viewedBy = activeSessionMeta?.viewedBy ?? null;
  const previewRouteReviewPayload = preview && reportPoint ? createPreviewRouteReviewPayload(reportPoint, activeReport?.reportType ?? "OTHER_OBSTACLE") : undefined;

  return (
    <section className="hazard-page admin-page-content">
      <div className="hazard-summary-strip">
        <HazardSummaryCard
          label="전체 제보"
          value={String(totalCount)}
          detail={reportSummary ? `오늘 +${reportSummary.newReports}` : "최근 집계"}
          share={null}
          tone="blue"
          icon="report"
        />
        <HazardSummaryCard
          label="대기"
          value={String(pendingCount)}
          detail="검토 필요"
          share={formatPercentShare(pendingCount, totalCount)}
          tone="orange"
          icon="hourglass"
        />
        <HazardSummaryCard
          label="승인"
          value={String(approvedCount)}
          detail="처리 완료"
          share={formatPercentShare(approvedCount, totalCount)}
          tone="green"
          icon="check"
        />
        <HazardSummaryCard
          label="반려"
          value={String(rejectedCount)}
          detail="조치 보류"
          share={formatPercentShare(rejectedCount, totalCount)}
          tone="red"
          icon="close"
        />
      </div>

      <div className="hazard-content-grid">
        <section className="admin-dashboard-card hazard-list-shell">
          <div className="hazard-list-shell__header">
            <div className="hazard-tab-strip" role="tablist" aria-label="불편신고 상태 필터">
              {[
                { key: "" as HazardFilterKey, label: "전체", count: totalCount },
                { key: "PENDING" as HazardFilterKey, label: "대기", count: pendingCount },
                { key: "APPROVED" as HazardFilterKey, label: "승인", count: approvedCount },
                { key: "REJECTED" as HazardFilterKey, label: "반려", count: rejectedCount },
              ].map((tab) => (
                <button
                  key={tab.label}
                  type="button"
                  className={`hazard-tab-button ${status === tab.key ? "active" : ""}`}
                  role="tab"
                  aria-selected={status === tab.key}
                  onClick={() => changeStatus(tab.key)}
                >
                  <span>{tab.label}</span>
                  <strong>{tab.count}</strong>
                </button>
              ))}
            </div>

            <label className="hazard-search-field">
              <input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="제목, 내용, 위치 검색"
                aria-label="불편신고 검색"
              />
              <span className="hazard-search-field__icon" aria-hidden="true">
                <HazardUiIcon name="search" />
              </span>
            </label>
          </div>

          <div className="hazard-inline-banner">
            <HazardUiIcon name="info" />
            <span>승인/반려/원상복구는 신고별로 개별 처리됩니다. 처리한 항목은 경로 반영 필요 상태로 모아 두었다가 별도 경로 반영으로 적용합니다.</span>
          </div>

          {reportsQuery.error instanceof Error && <p className="error-box">{reportsQuery.error.message}</p>}
          {reportsQuery.isLoading && !preview && <p className="muted">제보 목록을 불러오는 중입니다.</p>}

          <div className="hazard-table-shell" role="table" aria-label="도로 상태 제보 목록">
            <div className="hazard-table-head" role="row">
              <span>상태</span>
              <span>유형</span>
              <span>제보자</span>
              <span>위치</span>
              <span>등록</span>
              <span>반영</span>
              <span aria-hidden="true" />
            </div>

            {filteredReports.map((report) => (
              <HazardReportRow
                key={report.reportId}
                report={report}
                accessToken={accessToken}
                preview={preview}
                previewAddress={previewHazardRecords.find((record) => record.summary.reportId === report.reportId)?.address}
                reviewDraft={filteredReportReviewDrafts.get(report.reportId) ?? null}
                routingApplyState={routingApplyState}
                seen={Boolean(reportSessionMeta[report.reportId]?.viewedAt)}
                selected={report.reportId === selectedReportId}
                onClick={() => handleSelectReport(report.reportId)}
              />
            ))}

            {!reportsQuery.isLoading && (hasToken || preview) && !filteredReports.length && (
              <div className="hazard-table-empty">조회된 제보가 없습니다.</div>
            )}
          </div>

          <div className="hazard-list-shell__footer">
            <div className="hazard-pagination">
              <button
                type="button"
                className="hazard-page-nav"
                disabled={cursorStack.length <= 1}
                onClick={() => {
                  setCursorStack((value) => value.slice(0, -1));
                  setSelectedReportId(null);
                }}
              >
                <span aria-hidden="true">‹</span>
              </button>

              {paginationItems.map((pageNumber) => {
                const isFuturePage = pageNumber > cursorStack.length;
                return (
                  <button
                    key={pageNumber}
                    type="button"
                    className={`hazard-page-number ${pageNumber === currentPage ? "active" : ""}`}
                    disabled={isFuturePage && !reportListData?.hasNext}
                    onClick={() => {
                      if (pageNumber <= cursorStack.length) {
                        setCursorStack((value) => value.slice(0, pageNumber));
                        setSelectedReportId(null);
                        return;
                      }

                      if (!reportListData?.nextCursor) return;
                      setCursorStack((value) => [...value, reportListData?.nextCursor ?? null]);
                      setSelectedReportId(null);
                    }}
                  >
                    {pageNumber}
                  </button>
                );
              })}

              <button
                type="button"
                className="hazard-page-nav"
                disabled={!reportListData?.hasNext || !reportListData.nextCursor}
                onClick={() => {
                  if (!reportListData?.nextCursor) return;
                  setCursorStack((value) => [...value, reportListData.nextCursor]);
                  setSelectedReportId(null);
                }}
              >
                <span aria-hidden="true">›</span>
              </button>
            </div>

          </div>

          <div className={`hazard-bulk-bar ${hasDbSyncQueue ? "" : "empty"}`}>
            <div className="hazard-bulk-bar__summary">
              <strong>{hasDbSyncQueue
                ? dbSyncPendingCount > 0
                  ? `현재 목록 경로 반영 필요 ${dbSyncPendingCount}건`
                  : "다른 목록에 경로 반영 필요 항목이 있습니다."
                : "지금 반영할 항목이 없습니다."}</strong>
              <span>{dbSyncPendingCount > 0 ? `현재 목록 ${dbSyncPendingCount}건` : "현재 목록 반영 필요 없음"}</span>
              <small>{latestReportStamp ? `마지막 집계 ${formatLongDateTime(latestReportStamp)}` : "마지막 집계 준비 중"}</small>
            </div>
            <button
              type="button"
              className="hazard-bulk-bar__button"
              onClick={() => applyRoutingMutation.mutate()}
              disabled={applyRoutingMutation.isPending || routingApplyStateQuery.data?.applying || !routingApplyStateQuery.data?.dirty}
            >
              전체 경로 반영
            </button>
          </div>
        </section>

        <aside className="admin-dashboard-card hazard-detail-shell">
          {!selectedReportId && !reportsQuery.isLoading && (
            <div className="hazard-empty-state">
              <strong>제보를 선택하면 상세 검토 패널이 열립니다.</strong>
              <span>지도, 첨부 이미지, 운영 상태를 한 번에 확인할 수 있도록 오른쪽 검토 영역을 구성했습니다.</span>
            </div>
          )}

          {detailQuery.isLoading && !preview && !activeReport && <p className="muted">상세를 불러오는 중입니다.</p>}
          {detailQuery.error instanceof Error && !preview && <p className="error-box">{detailQuery.error.message}</p>}

          {activeReport && (
            <>
              <div className="hazard-detail-shell__header">
                <div className="hazard-detail-shell__title">
                  <strong>{isRouteReviewMode ? "경로 검수" : "신고 상세"}</strong>
                  <span>{detailTrackingId ?? "#"}</span>
                </div>

                <div className="hazard-detail-shell__actions">
                  {isRouteReviewMode && activeReviewDraft && (
                    <button type="button" className="hazard-toolbar-chip">
                      {activeReviewDraft.intent === "restore" ? "원상복구 검수 진행중" : "승인 검수 진행중"}
                    </button>
                  )}
                  {activeReviewDraft?.stage === "IN_PROGRESS" && (
                    <button
                      type="button"
                      className="hazard-toolbar-chip ghost"
                      onClick={() => setDetailPaneMode(isRouteReviewMode ? "detail" : "review")}
                    >
                      <HazardUiIcon name={isRouteReviewMode ? "back" : "check"} />
                      {isRouteReviewMode ? "신고 상세로" : "검수 이어서"}
                    </button>
                  )}
                  <button type="button" className="hazard-toolbar-chip ghost" onClick={() => setSelectedReportId(null)}>
                    <HazardUiIcon name="list" />
                    목록
                  </button>
                </div>
              </div>

              {isRouteReviewMode && activeReviewDraft && reportPoint ? (
                <HazardRouteReviewWorkspace
                  review={activeReviewDraft}
                  reportTypeLabel={reportTypeLabel[activeReport.reportType] ?? activeReport.reportType}
                  reportPoint={reportPoint}
                  locationAddress={locationAddress}
                  locationRegion={locationRegion}
                  description={detail?.description}
                  networkPayload={preview ? previewRouteReviewPayload : routeNetworkQuery.data}
                  networkLoading={preview ? false : routeNetworkQuery.isLoading}
                  networkError={preview ? null : routeNetworkQuery.error instanceof Error ? routeNetworkQuery.error : null}
                  areaScopeLabel={routeReviewAreaScope ? `${routeReviewAreaScope.gu} ${routeReviewAreaScope.dong}` : null}
                  routingApplyState={preview ? null : routingApplyStateQuery.data}
                  applyingRouting={applyRoutingMutation.isPending}
                  refreshingRoutingState={routingApplyStateQuery.isFetching}
                  savingReview={updateRouteReviewMutation.isPending}
                  reviewSaveMessage={routeReviewSaveNotice?.message ?? null}
                  reviewSaveClassName={routeReviewSaveNotice?.className}
                  onApplyRouting={() => applyRoutingMutation.mutate()}
                  onBack={() => setDetailPaneMode("detail")}
                  onReviewChange={handleRouteReviewChange}
                  onComplete={completeRouteReviewFlow}
                  completing={isCompletingRouteReview || completeRouteReviewMutation.isPending}
                  accessToken={accessToken}
                  areaGu={routeReviewAreaScope?.gu}
                  areaDong={routeReviewAreaScope?.dong}
                />
              ) : (
                <>
              <div className="hazard-preview-grid">
                <div className="hazard-preview-card">
                  <div className="hazard-preview-card__label">지도</div>
                  {reportPoint ? (
                    <>
                      <HazardReportLocationPreview
                        point={reportPoint}
                        label={reportTypeLabel[activeReport.reportType] ?? activeReport.reportType}
                      />
                      {mapLink && (
                        <a className="hazard-preview-card__expand" href={mapLink} target="_blank" rel="noreferrer">
                          <HazardUiIcon name="expand" />
                        </a>
                      )}
                    </>
                  ) : (
                    <div className="hazard-preview-card__empty">좌표 정보가 없습니다.</div>
                  )}
                </div>

                <div className="hazard-preview-card">
                  <div className="hazard-preview-card__label">로드뷰</div>
                  {reportPoint ? (
                    <HazardReportRoadviewPreview
                      point={reportPoint}
                      label={reportTypeLabel[activeReport.reportType] ?? activeReport.reportType}
                    />
                  ) : (
                    <div className="hazard-preview-card__empty">좌표 정보가 없어 로드뷰를 표시할 수 없습니다.</div>
                  )}
                  {roadviewLink && (
                    <a className="hazard-preview-card__expand" href={roadviewLink} target="_blank" rel="noreferrer">
                      <HazardUiIcon name="expand" />
                    </a>
                  )}
                </div>
              </div>

              <div className="hazard-detail-grid">
                <section className="hazard-detail-card">
                  <h3>운영 정보</h3>
                  <dl className="hazard-detail-list">
                    <DetailRow
                      label="상태"
                      value={displayStatus?.label ?? reportStatusLabel[activeReport.status]}
                      badgeTone={displayStatus?.tone ?? statusToneFromReport(activeReport.status)}
                    />
                    <DetailRow label="유형" value={reportTypeLabel[activeReport.reportType] ?? activeReport.reportType} />
                    <DetailRow label="제보자" value={shortId(activeReport.reporterUserId, 12)} />
                    <DetailRow
                      label="위치"
                      value={locationAddress ?? "좌표 기준 위치 확인 중"}
                      secondary={locationRegion || undefined}
                    />
                    <DetailRow label="좌표" value={coordinateLabel || "-"} />
                    <DetailRow label="등록일" value={formatPanelDateTime(activeReport.createdAt)} />
                    <div className="hazard-detail-list__row hazard-detail-list__row--description">
                      <dt>내용</dt>
                      <dd>
                        <div className="hazard-detail-list__description">
                          {detail?.description?.trim()
                            || (detailQuery.isLoading && !preview
                              ? "상세 설명을 불러오는 중입니다."
                              : "작성된 제보 설명이 없습니다.")}
                        </div>
                      </dd>
                    </div>
                  </dl>
                </section>

                <section className="hazard-detail-card hazard-operation-card">
                  <h3>운영 현황</h3>
                  {operationSnapshot && (
                    <div className="hazard-operation-grid">
                      <OperationRow label="지도 반영 상태" status={operationSnapshot.mapSync} />
                      <OperationRow label="추천 경로 제외" status={operationSnapshot.routeExclusion} />
                      <OperationRow label="경로 반영 상태" status={operationSnapshot.dbSync} />
                      <OperationRow label="원상복구 상태" status={operationSnapshot.recovery} />
                    </div>
                  )}

                  <div className="hazard-operation-summary">
                    <div>
                      <span>확인 관리자</span>
                      <strong>{viewedBy ? shortId(viewedBy, 12) : "-"}</strong>
                    </div>
                    <div>
                      <span>확인 시각</span>
                      <StackedDateTimeValue value={viewedAt} />
                    </div>
                    <div>
                      <span>처리 관리자</span>
                      <strong>{processedBy ? shortId(processedBy, 12) : activeReport.status === "PENDING" ? "-" : "이력 연동 예정"}</strong>
                    </div>
                    <div>
                      <span>최종 처리</span>
                      <StackedDateTimeValue value={processedAt} />
                    </div>
                  </div>

                  <div className="hazard-history-card">
                    <strong>처리 이력</strong>
                    <ul className="hazard-history-list">
                      {detailHistory.map((item) => (
                        <li key={`${item.label}-${item.time}`}>
                          <i className={`tone-${item.tone}`} aria-hidden="true" />
                          <div>
                            <span>{item.time}</span>
                            <strong>{item.label}</strong>
                          </div>
                          <em>{item.meta}</em>
                        </li>
                      ))}
                    </ul>
                  </div>
                </section>
              </div>

              <section className="hazard-detail-card hazard-attachments-card">
                <div className="hazard-attachments-card__header">
                  <h3>첨부 이미지 ({previewImages.length})</h3>
                  <button
                    type="button"
                    className="hazard-thumb-more"
                    disabled={!previewImages.length}
                    onClick={() => setIsGalleryOpen(true)}
                  >
                    전체 보기
                  </button>
                </div>

                {previewImages.length > 0 ? (
                  <div className="hazard-thumb-strip">
                    {previewImages.map((imageUrl, index) => (
                      <button
                        key={`${imageUrl}-${index}`}
                        type="button"
                        className={`hazard-thumb-button ${selectedImageUrl === imageUrl ? "selected" : ""}`}
                        onClick={() => {
                          setSelectedImageIndex(index);
                          setIsGalleryOpen(true);
                        }}
                        aria-label={`첨부 이미지 ${index + 1} 보기`}
                      >
                        <img src={imageUrl} alt="" aria-hidden="true" />
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="hazard-preview-card__empty attachment-empty">첨부 이미지가 없습니다.</div>
                )}
              </section>

              {detail && (
                <div className="hazard-detail-actionbar">
                  {activeReviewDraft?.stage === "IN_PROGRESS" ? (
                    <button
                      type="button"
                      className="hazard-action-button approve"
                      onClick={() => setDetailPaneMode("review")}
                    >
                      <HazardUiIcon name="check" />
                      검수 이어서
                    </button>
                  ) : (
                  <button
                    type="button"
                    className="hazard-action-button approve"
                    disabled={!canStartApprove || !hasRouteReviewScope || startRouteReviewMutation.isPending}
                    onClick={() => startRouteReview("approve")}
                  >
                    <HazardUiIcon name="check" />
                    승인 검수
                  </button>
                  )}
                  <button
                    type="button"
                    className={`hazard-action-button ${showsDeleteAction ? "delete" : "reject"}`}
                    disabled={showsDeleteAction ? !canDelete : !canReject}
                    onClick={() => {
                      if (preview) return;
                      if (showsDeleteAction) {
                        if (!confirm("처리된 제보를 삭제할까요? 제보와 검수 이력만 삭제되고 세그먼트/라우팅 상태는 유지됩니다.")) {
                          return;
                        }
                        deleteMutation.mutate(detail.reportId);
                        return;
                      }
                      rejectMutation.mutate(detail.reportId);
                    }}
                  >
                    <HazardUiIcon name={showsDeleteAction ? "trash" : "close"} />
                    {showsDeleteAction ? "삭제" : "반려"}
                  </button>
                  <button
                    type="button"
                    className="hazard-action-button restore"
                    disabled={!canRestore || activeReviewDraft?.stage === "IN_PROGRESS" || !hasRouteReviewScope || startRouteReviewMutation.isPending}
                    onClick={() => startRouteReview("restore")}
                  >
                    <HazardUiIcon name="refresh" />
                    원상복구 검수
                  </button>
                </div>
              )}

              <div className="hazard-bottom-note">
                <HazardUiIcon name="info" />
                <span>승인과 원상복구는 먼저 경로 검수 초안을 시작한 뒤 처리 완료됩니다. 검수 도중 목록으로 나가도 마지막 초안은 이어서 검토할 수 있습니다.</span>
              </div>

              {!preview && actionError && (
                <p className="error-box">
                  {actionError.message}
                </p>
              )}

              {routeReviewCompletionNotice && (
                <p className={routeReviewCompletionNotice.className}>
                  {routeReviewCompletionNotice.message}
                </p>
              )}

              {isGalleryOpen && previewImages.length > 0 && selectedImageUrl && (
                <div className="hazard-gallery-modal" role="dialog" aria-modal="true" aria-label="첨부 이미지 전체 보기">
                  <button
                    type="button"
                    className="hazard-gallery-modal__backdrop"
                    aria-label="첨부 이미지 닫기"
                    onClick={() => setIsGalleryOpen(false)}
                  />
                  <div className="hazard-gallery-modal__panel">
                    <div className="hazard-gallery-modal__header">
                      <div>
                        <strong>첨부 이미지 전체 보기</strong>
                        <span>{reportTypeLabel[activeReport.reportType] ?? activeReport.reportType}</span>
                      </div>
                      <button
                        type="button"
                        className="hazard-gallery-modal__close"
                        aria-label="첨부 이미지 닫기"
                        onClick={() => setIsGalleryOpen(false)}
                      >
                        <HazardUiIcon name="close" />
                      </button>
                    </div>

                    <div className="hazard-gallery-modal__viewer">
                      <button
                        type="button"
                        className="hazard-gallery-modal__nav"
                        aria-label="이전 이미지"
                        disabled={selectedImageIndex <= 0}
                        onClick={() => setSelectedImageIndex((value) => Math.max(0, value - 1))}
                      >
                        ‹
                      </button>

                      <div className="hazard-gallery-modal__image-shell">
                        <img src={selectedImageUrl} alt={`제보 ${activeReport.reportId} 첨부 이미지 ${selectedImageIndex + 1}`} />
                      </div>

                      <button
                        type="button"
                        className="hazard-gallery-modal__nav"
                        aria-label="다음 이미지"
                        disabled={selectedImageIndex >= previewImages.length - 1}
                        onClick={() => setSelectedImageIndex((value) => Math.min(previewImages.length - 1, value + 1))}
                      >
                        ›
                      </button>
                    </div>

                    <div className="hazard-gallery-modal__footer">
                      <span>{selectedImageIndex + 1} / {previewImages.length}</span>
                      <div className="hazard-gallery-modal__thumbs">
                        {previewImages.map((imageUrl, index) => (
                          <button
                            key={`${imageUrl}-modal-${index}`}
                            type="button"
                            className={`hazard-gallery-modal__thumb ${selectedImageUrl === imageUrl ? "selected" : ""}`}
                            onClick={() => setSelectedImageIndex(index)}
                            aria-label={`첨부 이미지 ${index + 1} 선택`}
                          >
                            <img src={imageUrl} alt="" aria-hidden="true" />
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              )}
                </>
              )}
            </>
          )}
        </aside>
      </div>
    </section>
  );
}

function HazardSummaryCard({
  label,
  value,
  detail,
  share,
  tone,
  icon,
}: {
  label: string;
  value: string;
  detail: string;
  share: string | null;
  tone: Exclude<HazardBadgeTone, "purple" | "gray">;
  icon: "report" | "hourglass" | "check" | "close";
}) {
  return (
    <article className={`hazard-summary-card tone-${tone}`}>
      <span className="hazard-summary-card__icon" aria-hidden="true">
        <HazardUiIcon name={icon} />
      </span>
      <div className="hazard-summary-card__copy">
        <p>{label}</p>
        <strong>{value}</strong>
      </div>
      <div className="hazard-summary-card__meta">
        <small>{detail}</small>
        {share && <em>{share}</em>}
      </div>
    </article>
  );
}

function HazardReportRow({
  report,
  accessToken,
  preview,
  previewAddress,
  reviewDraft,
  routingApplyState,
  seen,
  selected,
  onClick,
}: {
  report: AdminHazardReportSummary;
  accessToken: string;
  preview: boolean;
  previewAddress?: string;
  reviewDraft?: HazardRouteReviewRecord | null;
  routingApplyState?: AdminRoutingApplyStateResponse | null;
  seen: boolean;
  selected: boolean;
  onClick: () => void;
}) {
  const dbStatus = deriveHazardDbSyncStatus(report.status, reviewDraft, routingApplyState);
  const displayStatus = deriveHazardDisplayStatus(report.status, reviewDraft);

  return (
    <button className={`hazard-row ${selected ? "selected" : ""} ${seen ? "is-seen" : "is-unseen"}`} type="button" role="row" onClick={onClick}>
      <span className="hazard-row-status">
        <i className={`hazard-row-unread-dot ${seen ? "is-hidden" : ""}`} aria-hidden="true" />
        <span className={`hazard-chip tone-${displayStatus.tone}`}>{displayStatus.label}</span>
      </span>
      <span className="hazard-row-type">
        <HazardTypeIcon reportType={report.reportType} />
        {reportTypeLabel[report.reportType] ?? report.reportType}
      </span>
      <span className="hazard-row-reporter" title={report.reporterUserId}>{shortId(report.reporterUserId, 8)}</span>
      <HazardReportLocationCell point={report.reportPoint} accessToken={accessToken} preview={preview} previewAddress={previewAddress} />
      <span className="hazard-row-date">{formatDateTime(report.createdAt)}</span>
      <span className={`hazard-inline-status tone-${dbStatus.tone}`}>{dbStatus.label}</span>
      <span className="hazard-row-arrow" aria-hidden="true">›</span>
    </button>
  );
}

function HazardReportLocationCell({
  point,
  accessToken,
  preview,
  previewAddress,
}: {
  point: GeoPoint;
  accessToken: string;
  preview: boolean;
  previewAddress?: string;
}) {
  const addressQuery = useQuery<HazardReverseGeocodeResult>({
    queryKey: ["admin-hazard-row-address", point.lat, point.lng, accessToken],
    queryFn: () => reverseGeocodePlace(point, accessToken),
    enabled: !preview && Boolean(accessToken),
    retry: false,
    staleTime: 300_000,
  });

  const address = preview ? previewAddress ?? null : resolveHazardDisplayAddress(addressQuery.data);
  const label = address || formatCompactCoordinates(point);

  return (
    <span className="hazard-row-location" title={label}>
      {addressQuery.isLoading && !preview ? "위치 확인 중" : label}
    </span>
  );
}

function DetailRow({
  label,
  value,
  secondary,
  badgeTone,
}: {
  label: string;
  value: string;
  secondary?: string;
  badgeTone?: HazardBadgeTone;
}) {
  return (
    <div className="hazard-detail-list__row">
      <dt>{label}</dt>
      <dd>
        <div className="hazard-detail-list__value">
          {badgeTone ? (
            <span className={`hazard-chip tone-${badgeTone}`}>{value}</span>
          ) : (
            <strong>{value}</strong>
          )}
          {secondary && <small>{secondary}</small>}
        </div>
      </dd>
    </div>
  );
}

function OperationRow({
  label,
  status,
}: {
  label: string;
  status: { label: string; tone: HazardBadgeTone };
}) {
  return (
    <div className="hazard-operation-row">
      <span className="hazard-operation-row__label">{label}</span>
      <span className={`hazard-inline-status hazard-operation-row__status tone-${status.tone}`}>{status.label}</span>
    </div>
  );
}

function HazardTypeIcon({ reportType }: { reportType: HazardReportType }) {
  if (reportType === "BRAILLE_BLOCK") {
    return (
      <span className="hazard-type-icon tone-green" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <rect x="2.5" y="2.5" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="6.9" y="2.5" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="11.3" y="2.5" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="2.5" y="6.9" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="6.9" y="6.9" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="11.3" y="6.9" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="2.5" y="11.3" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="6.9" y="11.3" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
          <rect x="11.3" y="11.3" width="2.2" height="2.2" rx="0.5" fill="currentColor" />
        </svg>
      </span>
    );
  }

  if (reportType === "SIDEWALK_WIDTH") {
    return (
      <span className="hazard-type-icon tone-purple" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <path d="M3 8h10" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
          <path d="m5 5-2 3 2 3M11 5l2 3-2 3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    );
  }

  if (reportType === "OTHER_OBSTACLE") {
    return (
      <span className="hazard-type-icon tone-orange" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <path d="M8 3 13 12.5H3L8 3Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
          <path d="M8 6.2v2.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="8" cy="11.1" r="0.8" fill="currentColor" />
        </svg>
      </span>
    );
  }

  if (reportType === "SIDEWALK_MISSING") {
    return (
      <span className="hazard-type-icon tone-red" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <path d="M4 4 12 12M12 4 4 12" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
        </svg>
      </span>
    );
  }

  if (reportType === "STAIRS_STEP") {
    return (
      <span className="hazard-type-icon tone-blue" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <path d="M3 11h2.8V8.3h2.7V5.6H11V3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    );
  }

  if (reportType === "RAMP") {
    return (
      <span className="hazard-type-icon tone-blue" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <path d="M3 11h8l2-5.4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
          <path d="m11.5 4.6 1.5 1.1 1-1.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    );
  }

  return (
    <span className="hazard-type-icon tone-blue" aria-hidden="true">
      <svg viewBox="0 0 16 16" fill="none">
        <path d="M3 10.8h10" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        <path d="M5 10.8 8 5.2l3 5.6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  );
}

function StackedDateTimeValue({ value }: { value: string | null }) {
  const parts = value ? splitPanelDateTime(value) : null;

  if (!parts) {
    return <strong className="hazard-operation-summary__value">-</strong>;
  }

  return (
    <strong className="hazard-operation-summary__value hazard-operation-summary__time">
      <span>{parts.date}</span>
      <span>{parts.time}</span>
    </strong>
  );
}

function HazardUiIcon({
  name,
}: {
  name:
    | "report"
    | "hourglass"
    | "back"
    | "check"
    | "close"
    | "trash"
    | "refresh"
    | "calendar"
    | "search"
    | "expand"
    | "copy"
    | "list"
    | "info"
    | "database";
}) {
  switch (name) {
    case "report":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M6 3.5h5.8L15.5 7v9.1a1.4 1.4 0 0 1-1.4 1.4H6a1.4 1.4 0 0 1-1.4-1.4V4.9A1.4 1.4 0 0 1 6 3.5Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
          <path d="M11.5 3.8v3.5h3.3" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
          <path d="M7.2 10.2h5.6M7.2 13h5.6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      );
    case "hourglass":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M6 3.5h8M6 16.5h8M7 3.5v2.1c0 1.3.7 2.6 1.9 3.3l1.1.6-1.1.6A3.9 3.9 0 0 0 7 13.4v3.1M13 3.5v2.1c0 1.3-.7 2.6-1.9 3.3l-1.1.6 1.1.6a3.9 3.9 0 0 1 1.9 3.3v3.1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "back":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="m11.9 5.4-4.8 4.6 4.8 4.6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "check":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="m5.4 10.3 3 3 6.2-6.5" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "close":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M5.8 5.8 14.2 14.2M14.2 5.8 5.8 14.2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    case "trash":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M7 5.2V4.6A1.6 1.6 0 0 1 8.6 3h2.8A1.6 1.6 0 0 1 13 4.6v.6M4.5 5.2h11" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <path d="m6.1 7.4.6 8A1.7 1.7 0 0 0 8.4 17h3.2a1.7 1.7 0 0 0 1.7-1.6l.6-8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M8.7 9.1v5.2M11.3 9.1v5.2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        </svg>
      );
    case "refresh":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M15.5 6.5V4m0 2.5H13M4.7 8.2a5.7 5.7 0 0 1 10.8-1.7M4.5 13.5V16m0-2.5H7m8.3-1.7a5.7 5.7 0 0 1-10.8 1.7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "calendar":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <rect x="3.5" y="4.5" width="13" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
          <path d="M6.5 3.5v3M13.5 3.5v3M3.5 8.5h13" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      );
    case "search":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <circle cx="9" cy="9" r="4.7" stroke="currentColor" strokeWidth="1.8" />
          <path d="m12.6 12.6 3.2 3.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      );
    case "expand":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M12.5 4.5h3v3M15.5 4.5l-4.4 4.4M7.5 15.5h-3v-3M4.5 15.5l4.4-4.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "copy":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <rect x="7" y="6" width="8.5" height="10" rx="2" stroke="currentColor" strokeWidth="1.8" />
          <path d="M5.5 13.5H5A2.5 2.5 0 0 1 2.5 11V5A2.5 2.5 0 0 1 5 2.5h6A2.5 2.5 0 0 1 13.5 5v.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      );
    case "list":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M5 5.5h10M5 10h10M5 14.5h10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="3.3" cy="5.5" r="1" fill="currentColor" />
          <circle cx="3.3" cy="10" r="1" fill="currentColor" />
          <circle cx="3.3" cy="14.5" r="1" fill="currentColor" />
        </svg>
      );
    case "info":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <circle cx="10" cy="10" r="7.2" stroke="currentColor" strokeWidth="1.8" />
          <path d="M10 8.2v4.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="10" cy="5.9" r="1" fill="currentColor" />
        </svg>
      );
    case "database":
      return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <ellipse cx="10" cy="5.5" rx="5.8" ry="2.6" stroke="currentColor" strokeWidth="1.7" />
          <path d="M4.2 5.5v4.2c0 1.4 2.6 2.6 5.8 2.6s5.8-1.2 5.8-2.6V5.5" stroke="currentColor" strokeWidth="1.7" />
          <path d="M4.2 9.7v4.3c0 1.4 2.6 2.5 5.8 2.5s5.8-1.1 5.8-2.5V9.7" stroke="currentColor" strokeWidth="1.7" />
        </svg>
      );
  }
}

function countReportsByStatus(reports: AdminHazardReportSummary[] | undefined, status: HazardReportStatus) {
  return (reports ?? []).filter((report) => report.status === status).length;
}

function buildVisiblePageNumbers(currentPage: number, hasNextPage: boolean) {
  const numbers = currentPage > 1 ? [Math.max(1, currentPage - 1), currentPage] : [1];
  if (hasNextPage) {
    numbers.push(currentPage + 1);
  }
  return [...new Set(numbers)].sort((left, right) => left - right);
}

function buildHazardTimeline(
  report: Pick<AdminHazardReportDetail, "createdAt" | "status">,
  adminUserId: string,
  review?: HazardRouteReviewRecord | null,
) {
  const baseTime = formatLongDateTime(report.createdAt);
  const reviewerLabel = `(${shortId(review?.reviewerUserId ?? adminUserId, 8)})`;

  if (review?.stage === "IN_PROGRESS") {
    return [
      { time: baseTime, label: "신고 접수", meta: "(사용자)", tone: "blue" as HazardBadgeTone },
      {
        time: formatLongDateTime(review.startedAt),
        label: review.intent === "restore" ? "원상복구 경로 검수" : "승인 경로 검수",
        meta: reviewerLabel,
        tone: "orange" as HazardBadgeTone,
      },
      { time: "-", label: "처리 완료 대기", meta: "-", tone: "gray" as HazardBadgeTone },
    ];
  }

  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return [
      {
        time: report.status === "REJECTED" ? baseTime : "-",
        label: report.status === "REJECTED" ? "반려 이력" : "기존 처리 이력",
        meta: report.status === "REJECTED" ? reviewerLabel : "-",
        tone: "red" as HazardBadgeTone,
      },
      {
        time: formatLongDateTime(review.completedAt ?? review.updatedAt),
        label: "원상복구 완료",
        meta: reviewerLabel,
        tone: "purple" as HazardBadgeTone,
      },
      { time: "-", label: "경로 반영 필요", meta: "(경로 반영 대기)", tone: "gray" as HazardBadgeTone },
    ];
  }

  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return [
      { time: baseTime, label: "신고 접수", meta: "(사용자)", tone: "blue" as HazardBadgeTone },
      {
        time: formatLongDateTime(review.startedAt),
        label: "경로 검수 시작",
        meta: reviewerLabel,
        tone: "orange" as HazardBadgeTone,
      },
      {
        time: formatLongDateTime(review.completedAt ?? review.updatedAt),
        label: "처리 완료",
        meta: reviewerLabel,
        tone: "green" as HazardBadgeTone,
      },
    ];
  }

  if (report.status === "APPROVED") {
    return [
      { time: baseTime, label: "처리 완료", meta: reviewerLabel, tone: "green" as HazardBadgeTone },
      { time: "-", label: "경로 반영", meta: "-", tone: "gray" as HazardBadgeTone },
      { time: "-", label: "원상복구 검수 가능", meta: "-", tone: "purple" as HazardBadgeTone },
    ];
  }

  if (report.status === "REJECTED") {
    return [
      { time: baseTime, label: "반려", meta: reviewerLabel, tone: "red" as HazardBadgeTone },
      { time: "-", label: "후속 조치 없음", meta: "-", tone: "gray" as HazardBadgeTone },
      { time: "-", label: "경로 반영", meta: "-", tone: "gray" as HazardBadgeTone },
    ];
  }

  return [
    { time: baseTime, label: "신고 접수", meta: "(사용자)", tone: "blue" as HazardBadgeTone },
    { time: "-", label: "승인 검수 대기", meta: "(Admin)", tone: "orange" as HazardBadgeTone },
    { time: "-", label: "경로 반영", meta: "-", tone: "gray" as HazardBadgeTone },
  ];
}

function buildOperationSnapshot(
  status: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
  routingApplyState?: AdminRoutingApplyStateResponse | null,
) {
  return {
    mapSync: summarizeMapSyncStatus(status, review),
    routeExclusion: summarizeRouteExclusionStatus(status, review),
    dbSync: deriveHazardDbSyncStatus(status, review, routingApplyState),
    recovery: summarizeRecoveryStatus(status, review),
  };
}

function summarizeMapSyncStatus(status: HazardReportStatus, review?: HazardRouteReviewRecord | null) {
  if (review?.stage === "IN_PROGRESS") {
    return { label: review.intent === "restore" ? "복구 검수" : "검수 중", tone: "blue" as HazardBadgeTone };
  }
  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return { label: "복구 완료", tone: "purple" as HazardBadgeTone };
  }
  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return { label: "검수 완료", tone: "green" as HazardBadgeTone };
  }
  if (status === "APPROVED") {
    return { label: "검수 완료", tone: "green" as HazardBadgeTone };
  }
  if (status === "REJECTED") {
    return { label: "검토 종료", tone: "gray" as HazardBadgeTone };
  }
  return { label: "검토 전", tone: "gray" as HazardBadgeTone };
}

function summarizeRouteExclusionStatus(status: HazardReportStatus, review?: HazardRouteReviewRecord | null) {
  if (review?.stage === "IN_PROGRESS") {
    return { label: review.intent === "restore" ? "복구 검토" : "검수 중", tone: "blue" as HazardBadgeTone };
  }
  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return { label: "제외 해제", tone: "purple" as HazardBadgeTone };
  }
  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return { label: "제외 예정", tone: "orange" as HazardBadgeTone };
  }
  if (status === "APPROVED") {
    return { label: "제외 예정", tone: "orange" as HazardBadgeTone };
  }
  if (status === "REJECTED") {
    return { label: "-", tone: "gray" as HazardBadgeTone };
  }
  return { label: "검토 전", tone: "gray" as HazardBadgeTone };
}

function summarizeRecoveryStatus(status: HazardReportStatus, review?: HazardRouteReviewRecord | null) {
  if (review?.stage === "IN_PROGRESS") {
    return review.intent === "restore"
      ? { label: "검수 중", tone: "blue" as HazardBadgeTone }
      : { label: "-", tone: "gray" as HazardBadgeTone };
  }
  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return { label: "완료", tone: "purple" as HazardBadgeTone };
  }
  if (status === "REJECTED") {
    return { label: "-", tone: "gray" as HazardBadgeTone };
  }
  if (status === "APPROVED") {
    return { label: "가능", tone: "purple" as HazardBadgeTone };
  }
  return { label: "-", tone: "gray" as HazardBadgeTone };
}

function statusToneFromReport(status: HazardReportStatus): HazardBadgeTone {
  switch (status) {
    case "APPROVED":
      return "green";
    case "REJECTED":
      return "red";
    default:
      return "orange";
  }
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return `${month}.${day} ${hours}:${minutes}`;
}

function formatLongDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatPanelDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return `${year}.${month}.${day} ${hours}:${minutes}`;
}

function splitPanelDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return {
    date: `${year}.${month}.${day}`,
    time: `${hours}:${minutes}`,
  };
}

function resolveHazardAreaScope(
  previewRegion: string | null,
  geocode?: HazardReverseGeocodeResult | null,
): HazardAreaScope | null {
  const gu = geocode?.region2DepthName?.trim()
    || previewRegion?.split(" ").map((value) => value.trim()).filter(Boolean)[1]
    || "";
  const dong = geocode?.region3DepthName?.trim()
    || previewRegion?.split(" ").map((value) => value.trim()).filter(Boolean)[2]
    || "";

  if (!gu || !dong) {
    return null;
  }

  return { gu, dong };
}

function formatPeriodRange(from?: string | null, to?: string | null) {
  if (!from || !to) {
    return "2024.05.01 - 2024.05.31";
  }

  const start = from.replace(/-/g, ".");
  const end = to.replace(/-/g, ".");
  return `${start} - ${end}`;
}

function formatPercentShare(value: number, total: number) {
  if (!total) return "-";
  return `${percentFormatter.format((value / total) * 100)}%`;
}

function formatCompactCoordinates(point: GeoPoint) {
  return `${formatHazardCoordinateValue(point.lat)}, ${formatHazardCoordinateValue(point.lng)}`;
}

function shortId(value: string, visibleLength: number) {
  return value.length > visibleLength ? `${value.slice(0, visibleLength)}...` : value;
}

function createPreviewRouteReviewPayload(point: GeoPoint, reportType: HazardReportType): SegmentPayload {
  const lngStep = 0.00042;
  const latStep = 0.00028;
  const baseLng = point.lng;
  const baseLat = point.lat;
  const line = (...coordinates: Array<[number, number]>) => coordinates;
  const pointCoord = (lng: number, lat: number): [number, number] => [lng, lat];
  type PreviewSegmentProperties = SegmentFeature["properties"];

  const issueOverrides: Record<HazardReportType, Partial<PreviewSegmentProperties>> = {
    STAIRS_STEP: {
      walkAccess: "NO",
      stairsState: "YES",
      featureTypes: ["STAIRS"],
    },
    BRAILLE_BLOCK: {
      brailleBlockState: "NO",
      featureTypes: ["BRAILLE_BLOCK"],
    },
    SIDEWALK_MISSING: {
      walkAccess: "NO",
    },
    RAMP: {
      walkAccess: "YES",
      avgSlopePercent: 11.4,
    },
    SIDEWALK_WIDTH: {
      widthState: "NARROW",
      widthMeter: 0.92,
    },
    OTHER_OBSTACLE: {
      walkAccess: "NO",
    },
  };

  const baseProperties = {
    segmentType: "SIDE_LINE" as const,
    walkAccess: "YES",
    brailleBlockState: "YES",
    widthState: "ADEQUATE_150",
    stairsState: "NO",
    signalState: "YES",
    avgSlopePercent: 2.1,
    widthMeter: 1.85,
    featureTypes: [] as SegmentFeatureType[],
  };

  const segments: SegmentPayload["segments"]["features"] = [
    {
      type: "Feature" as const,
      geometry: {
        type: "LineString" as const,
        coordinates: line(
          pointCoord(baseLng - lngStep, baseLat),
          pointCoord(baseLng - lngStep * 0.35, baseLat),
          pointCoord(baseLng + lngStep * 0.4, baseLat),
          pointCoord(baseLng + lngStep, baseLat),
        ),
      },
      properties: {
        edgeId: "preview-edge-main",
        fromNodeId: "preview-node-1",
        toNodeId: "preview-node-2",
        lengthMeter: 62.3,
        ...baseProperties,
        ...issueOverrides[reportType],
      },
    },
    {
      type: "Feature" as const,
      geometry: {
        type: "LineString" as const,
        coordinates: line(
          pointCoord(baseLng - lngStep * 0.82, baseLat + latStep),
          pointCoord(baseLng + lngStep * 0.85, baseLat + latStep),
        ),
      },
      properties: {
        edgeId: "preview-edge-north",
        fromNodeId: "preview-node-3",
        toNodeId: "preview-node-4",
        lengthMeter: 57.8,
        ...baseProperties,
        widthMeter: 2.05,
        avgSlopePercent: 1.2,
      },
    },
    {
      type: "Feature" as const,
      geometry: {
        type: "LineString" as const,
        coordinates: line(
          pointCoord(baseLng - lngStep * 0.76, baseLat - latStep),
          pointCoord(baseLng + lngStep * 0.9, baseLat - latStep),
        ),
      },
      properties: {
        edgeId: "preview-edge-south",
        fromNodeId: "preview-node-5",
        toNodeId: "preview-node-6",
        lengthMeter: 59.1,
        ...baseProperties,
        widthState: "ADEQUATE_120",
        widthMeter: 1.38,
        avgSlopePercent: 2.8,
      },
    },
    {
      type: "Feature" as const,
      geometry: {
        type: "LineString" as const,
        coordinates: line(
          pointCoord(baseLng - lngStep * 0.12, baseLat + latStep * 1.4),
          pointCoord(baseLng - lngStep * 0.12, baseLat - latStep * 1.45),
        ),
      },
      properties: {
        edgeId: "preview-edge-vertical",
        fromNodeId: "preview-node-7",
        toNodeId: "preview-node-8",
        lengthMeter: 31.4,
        ...baseProperties,
        segmentType: "CROSS_WALK",
        featureTypes: ["CROSSWALK", "AUDIO_SIGNAL"],
      },
    },
    {
      type: "Feature" as const,
      geometry: {
        type: "LineString" as const,
        coordinates: line(
          pointCoord(baseLng + lngStep * 0.22, baseLat + latStep * 0.95),
          pointCoord(baseLng + lngStep * 0.74, baseLat + latStep * 1.48),
        ),
      },
      properties: {
        edgeId: "preview-edge-diagonal",
        fromNodeId: "preview-node-9",
        toNodeId: "preview-node-10",
        lengthMeter: 24.8,
        ...baseProperties,
        avgSlopePercent: 4.3,
      },
    },
  ];

  const roadNodes: NonNullable<SegmentPayload["roadNodes"]>["features"] = Array.from(new Map(
    segments.flatMap((segment) => {
      const [first, last] = [segment.geometry.coordinates[0], segment.geometry.coordinates[segment.geometry.coordinates.length - 1]];
      return [
        [`${segment.properties.fromNodeId}`, {
          type: "Feature" as const,
          geometry: { type: "Point" as const, coordinates: first },
          properties: { vertexId: `${segment.properties.fromNodeId}` },
        }],
        [`${segment.properties.toNodeId}`, {
          type: "Feature" as const,
          geometry: { type: "Point" as const, coordinates: last },
          properties: { vertexId: `${segment.properties.toNodeId}` },
        }],
      ];
    }),
  ).values());

  return {
    summary: {
      segmentCount: segments.length,
      nodeCount: roadNodes.length,
      visibleSegmentCount: segments.length,
      bridgeCandidateCount: 0,
      visibleBridgeCandidateCount: 0,
    },
    bbox: [
      baseLng - lngStep * 1.15,
      baseLat - latStep * 1.6,
      baseLng + lngStep * 1.15,
      baseLat + latStep * 1.6,
    ],
    segments: {
      type: "FeatureCollection",
      features: segments,
    },
    roadNodes: {
      type: "FeatureCollection",
      features: roadNodes,
    },
    areaBoundary: null,
    bridges: {
      type: "FeatureCollection",
      features: [],
    },
  };
}

function createPreviewHazardRecord({
  reportId,
  reporterUserId,
  reportType,
  status,
  createdAt,
  address,
  region,
  point,
  description,
  imageUrls,
}: {
  reportId: number;
  reporterUserId: string;
  reportType: HazardReportType;
  status: HazardReportStatus;
  createdAt: string;
  address: string;
  region: string;
  point: GeoPoint;
  description: string;
  imageUrls: string[];
}): PreviewHazardRecord {
  return {
    summary: {
      reportId,
      reporterUserId,
      reportType,
      reportPoint: point,
      status,
      createdAt,
      representativeImageUrl: imageUrls[0] ?? null,
    },
    detail: {
      reportId,
      reporterUserId,
      reportType,
      description,
      reportPoint: point,
      status,
      createdAt,
      imageUrls,
    },
    address,
    region,
  };
}

function createPreviewStreetImage(background: string, accent: string, label: string) {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="640" height="420" viewBox="0 0 640 420">
      <rect width="640" height="420" fill="${background}"/>
      <rect x="0" y="260" width="640" height="160" fill="#d0d7de"/>
      <path d="M430 0 640 0 640 420 560 420 430 120Z" fill="#c7d2fe" opacity="0.45"/>
      <path d="M120 420 210 210 300 420Z" fill="${accent}" opacity="0.16"/>
      <path d="M0 285 640 210" stroke="#f8fafc" stroke-width="20"/>
      <path d="M0 330 640 260" stroke="#94a3b8" stroke-width="86" opacity="0.6"/>
      <path d="M68 315 572 260" stroke="#ffffff" stroke-width="8" stroke-dasharray="22 18" opacity="0.9"/>
      <rect x="138" y="238" width="152" height="106" rx="16" fill="#facc15" opacity="0.9"/>
      <rect x="156" y="252" width="116" height="12" rx="6" fill="#fde68a"/>
      <rect x="156" y="278" width="116" height="12" rx="6" fill="#fde68a"/>
      <rect x="156" y="304" width="116" height="12" rx="6" fill="#fde68a"/>
      <circle cx="474" cy="162" r="16" fill="${accent}"/>
      <rect x="460" y="174" width="28" height="72" rx="10" fill="#334155"/>
      <text x="24" y="42" fill="#0f172a" font-family="Pretendard, Arial, sans-serif" font-size="28" font-weight="700">${label}</text>
    </svg>
  `;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function routingApplyNotice(status?: string | null) {
  switch (status) {
    case "FAILED":
      return {
        message: "경로 반영에 실패했습니다. 저장된 변경은 유지되며 다시 시도할 수 있습니다.",
        className: "error-box",
      };
    case "APPLIED_WITH_WARNING":
      return {
        message: "경로 반영은 수행됐지만 일부 경고가 있습니다. 운영 상태를 확인해 주세요.",
        className: "warning-box",
      };
    case "SKIPPED":
      return {
        message: "경로 반영 대상이 없습니다.",
        className: "info-box",
      };
    case "APPLIED":
      return {
        message: "검수 완료 즉시 반영 완료되었습니다.",
        className: "success-box",
      };
    default:
      return {
        message: "경로 반영 요청이 처리되었습니다.",
        className: "info-box",
      };
  }
}
