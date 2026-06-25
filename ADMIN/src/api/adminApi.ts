import type {
  AreaOption,
  AdminPlaceDetailResponse,
  AdminPlaceUpdateRequest,
  AdminHazardRouteReview,
  AdminRoadSegmentAttributesUpdateRequest,
  AdminRoadSegmentUpdateResponse,
  AdminRoutingApplyStateResponse,
  AdminRoutePreviewRequest,
  AdminRoutePreviewResponse,
  AdminDashboardSummaryResponse,
  AdminHazardReportDetail,
  AdminHazardReportDeleteResponse,
  AdminHazardReportListResponse,
  AdminHazardReportStatusResponse,
  AdminMeResponse,
  AdminAreaAssignmentListResponse,
  AdminAuditLogListResponse,
  AdminDashboardBottleneckResponse,
  AdminBottleneckMonitoringResponse,
  AdminUserListResponse,
  AdminUserResponse,
  FacilityPayload,
  ManualEditDocument,
  PlaceAccessibilityFeature,
  RoadNetworkEditApplyResponse,
  RoadNetworkEditJobResponse,
  AdminRouteStatsResponse,
  HazardReportStatus,
  SegmentPayload,
  StartAdminHazardRouteReviewRequest,
  TokenResponse,
  UserRole,
  UpdateAdminHazardRouteReviewRequest,
  WorkStatus,
  AssignmentType,
  GeoPoint,
  BridgePayload,
  SegmentFeature,
} from "../types";

const configuredBackendApiUrl = import.meta.env.VITE_BACKEND_API_URL as string | undefined;

function defaultBackendApiUrl() {
  if (typeof window === "undefined") {
    return "http://localhost:8080";
  }
  if (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1") {
    return "http://localhost:8080";
  }
  return "https://api.busaneumgil.com";
}

export const backendApiUrl = (configuredBackendApiUrl || defaultBackendApiUrl()).replace(/\/$/, "");

export const adminAccessTokenStorageKey = "busan-eumgil-ADMIN:access-token";
export const adminAccessTokenRefreshedEvent = "busan-eumgil-ADMIN:access-token-refreshed";

interface ApiResponse<T> {
  status: string;
  data: T;
  message: string;
}

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

interface FetchAdminHazardReportsParams {
  status?: HazardReportStatus | "";
  cursor?: number | null;
  size?: number;
  accessToken: string;
}

interface FetchAdminAuditLogsParams {
  action?: string;
  gu?: string;
  dong?: string;
  actorUserId?: string;
  cursor?: number | null;
  size?: number;
  accessToken: string;
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${backendApiUrl}${path}`, {
    credentials: "include",
    ...init,
  });
  const body = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  if (!response.ok) {
    throw new ApiRequestError(body?.message || `${response.status} ${response.statusText}`, response.status);
  }
  if (!body) {
    throw new Error("응답을 읽을 수 없습니다.");
  }
  return body.data;
}

async function requestAdminJson<T>(path: string, accessToken: string, init?: RequestInit): Promise<T> {
  const normalizedToken = normalizeAdminAccessToken(accessToken);
  if (!normalizedToken) {
    throw new Error("Access Token을 입력하세요.");
  }
  let requestToken = normalizedToken;
  if (shouldRefreshAdminAccessToken(normalizedToken)) {
    requestToken = await reissueAdminAccessToken();
  }
  try {
    return await requestAdminJsonWithToken<T>(path, requestToken, init);
  } catch (error) {
    if (!(error instanceof ApiRequestError) || error.status !== 401) {
      throw error;
    }
    const refreshedToken = await reissueAdminAccessToken();
    if (isRetryableAdminRequest(init)) {
      return requestAdminJsonWithToken<T>(path, refreshedToken, init);
    }
    throw new ApiRequestError("인증이 갱신되었습니다. 다시 시도해주세요.", 401);
  }
}

function isRetryableAdminRequest(init?: RequestInit) {
  const method = (init?.method || "GET").toUpperCase();
  return method === "GET" || method === "HEAD";
}

function shouldRefreshAdminAccessToken(accessToken: string) {
  const expiresAt = parseJwtExpiresAt(accessToken);
  return expiresAt !== null && expiresAt <= Date.now() + 30_000;
}

function parseJwtExpiresAt(accessToken: string) {
  const payload = accessToken.split(".")[1];
  if (!payload || typeof window === "undefined") return null;
  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const paddedBase64 = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    const decoded = JSON.parse(window.atob(paddedBase64)) as { exp?: unknown };
    return typeof decoded.exp === "number" ? decoded.exp * 1000 : null;
  } catch {
    return null;
  }
}

async function requestAdminJsonWithToken<T>(path: string, accessToken: string, init?: RequestInit): Promise<T> {
  return requestJson<T>(path, {
    ...init,
    headers: {
      ...init?.headers,
      Authorization: `Bearer ${accessToken}`,
    },
  });
}

export function getStoredAdminAccessToken() {
  if (typeof window === "undefined") return "";
  return normalizeAdminAccessToken(window.localStorage.getItem(adminAccessTokenStorageKey) || "");
}

export function normalizeAdminAccessToken(accessToken: string) {
  return accessToken.trim().replace(/^Bearer\s+/i, "");
}

export function storeAdminAccessToken(accessToken: string) {
  if (typeof window === "undefined") return;
  const normalizedToken = normalizeAdminAccessToken(accessToken);
  if (!normalizedToken) {
    window.localStorage.removeItem(adminAccessTokenStorageKey);
    return;
  }
  window.localStorage.setItem(adminAccessTokenStorageKey, normalizedToken);
}

export async function reissueAdminAccessToken({ persist = true }: { persist?: boolean } = {}) {
  const response = await requestJson<TokenResponse>("/auth/reissue", {
    method: "POST",
  });
  const normalizedToken = normalizeAdminAccessToken(response.accessToken);
  if (!persist) {
    return normalizedToken;
  }
  storeAdminAccessToken(normalizedToken);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent(adminAccessTokenRefreshedEvent, { detail: normalizedToken }));
  }
  return normalizedToken;
}

export async function logoutAdminSession(accessToken: string) {
  const normalizedToken = normalizeAdminAccessToken(accessToken);
  if (!normalizedToken) return;
  try {
    await logoutAdminSessionWithToken(normalizedToken);
  } catch (error) {
    if (!(error instanceof ApiRequestError) || error.status !== 401) {
      throw error;
    }
    await logoutAdminSessionWithToken(await reissueAdminAccessToken({ persist: false }));
  }
}

async function logoutAdminSessionWithToken(accessToken: string) {
  await requestJson<void>("/auth/logout", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
}

export async function fetchAdminMe(accessToken: string): Promise<AdminMeResponse> {
  return requestAdminJson<AdminMeResponse>("/admin/me", accessToken);
}

export async function fetchAdminUsers(accessToken: string): Promise<AdminUserResponse[]> {
  const response = await requestAdminJson<AdminUserListResponse>("/admin/users", accessToken);
  return response.users;
}

export async function fetchAdminDashboardSummary({
  accessToken,
  from,
  to,
}: {
  accessToken: string;
  from?: string;
  to?: string;
}): Promise<AdminDashboardSummaryResponse> {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const suffix = params.size ? `?${params.toString()}` : "";
  return requestAdminJson<AdminDashboardSummaryResponse>(`/admin/dashboard/summary${suffix}`, accessToken);
}

export async function fetchAdminDashboardBottlenecks({
  accessToken,
  from,
  to,
  limit = 12,
}: {
  accessToken: string;
  from?: string;
  to?: string;
  limit?: number;
}): Promise<AdminDashboardBottleneckResponse> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  return requestAdminJson<AdminDashboardBottleneckResponse>(`/admin/dashboard/bottlenecks?${params.toString()}`, accessToken);
}

export async function fetchAdminRouteStats(accessToken: string): Promise<AdminRouteStatsResponse> {
  return requestAdminJson<AdminRouteStatsResponse>("/admin/dashboard/route-stats", accessToken);
}

export async function fetchAdminBottleneckMonitoring(accessToken: string): Promise<AdminBottleneckMonitoringResponse> {
  return requestAdminJson<AdminBottleneckMonitoringResponse>("/admin/dashboard/bottleneck-monitoring", accessToken);
}

export async function updateAdminUserRole(
  userId: string,
  role: UserRole,
  accessToken: string,
): Promise<AdminUserResponse> {
  return requestAdminJson<AdminUserResponse>(`/admin/users/${userId}/role`, accessToken, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ role }),
  });
}

export async function fetchAdminAreas(accessToken: string): Promise<AreaOption[]> {
  const response = await requestAdminJson<{ areas: AreaOption[] }>("/admin/areas", accessToken);
  return response.areas;
}

export async function fetchAdminAreaAssignments(accessToken: string) {
  const response = await requestAdminJson<AdminAreaAssignmentListResponse>("/admin/area-assignments", accessToken);
  return response.assignments;
}

export async function fetchAdminAuditLogs({
  action,
  gu,
  dong,
  actorUserId,
  cursor,
  size = 50,
  accessToken,
}: FetchAdminAuditLogsParams): Promise<AdminAuditLogListResponse> {
  const params = new URLSearchParams({ size: String(size) });
  if (cursor != null) params.set("cursor", String(cursor));
  if (action) params.set("action", action);
  if (gu) params.set("gu", gu);
  if (dong) params.set("dong", dong);
  if (actorUserId) params.set("actorUserId", actorUserId);
  return requestAdminJson<AdminAuditLogListResponse>(`/admin/audit-logs?${params.toString()}`, accessToken);
}

export async function upsertAdminAreaAssignment(
  request: {
    gu: string;
    dong: string;
    assignmentType: AssignmentType;
    assigneeUserId: string | null;
    status: WorkStatus;
  },
  accessToken: string,
) {
  return requestAdminJson<AdminAreaAssignmentListResponse["assignments"][number]>("/admin/area-assignments", accessToken, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function updateAdminAreaAssignmentStatus(
  assignmentId: number,
  status: WorkStatus,
  accessToken: string,
) {
  return requestAdminJson<AdminAreaAssignmentListResponse["assignments"][number]>(
    `/admin/area-assignments/${assignmentId}/status`,
    accessToken,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ status }),
    },
  );
}

export async function fetchAdminRoadNetworkPayload({
  gu,
  dong,
  centerLat,
  centerLng,
  radiusMeter,
  accessToken,
  limit,
}: {
  gu?: string;
  dong?: string;
  centerLat?: number;
  centerLng?: number;
  radiusMeter?: number;
  accessToken: string;
  limit?: number;
}): Promise<SegmentPayload> {
  const params = new URLSearchParams();
  if (gu && dong) {
    params.set("gu", gu);
    params.set("dong", dong);
  }
  if (typeof limit === "number") {
    params.set("limit", String(limit));
  }
  if (typeof centerLat === "number" && typeof centerLng === "number" && typeof radiusMeter === "number") {
    params.set("centerLat", String(centerLat));
    params.set("centerLng", String(centerLng));
    params.set("radiusMeter", String(radiusMeter));
  }
  const queryString = params.toString();
  return requestAdminJson<SegmentPayload>(
    queryString ? `/admin/road-network/segments?${queryString}` : "/admin/road-network/segments",
    accessToken,
  );
}

export async function fetchAdminRoadSegment({
  edgeId,
  gu,
  dong,
  accessToken,
}: {
  edgeId: string | number;
  gu: string;
  dong?: string;
  accessToken: string;
}): Promise<SegmentFeature> {
  const params = new URLSearchParams({ gu });
  if (dong) {
    params.set("dong", dong);
  }
  return requestAdminJson<SegmentFeature>(
    `/admin/road-network/segments/${edgeId}?${params.toString()}`,
    accessToken,
  );
}

export async function fetchAdminRoadNetworkBridges({
  gu,
  dong,
  accessToken,
}: {
  gu: string;
  dong: string;
  accessToken: string;
}): Promise<BridgePayload> {
  const params = new URLSearchParams({ gu, dong });
  return requestAdminJson<BridgePayload>(`/admin/road-network/bridges?${params.toString()}`, accessToken);
}

export async function applyAdminRoadNetworkEdits(
  document: ManualEditDocument,
  accessToken: string,
): Promise<RoadNetworkEditApplyResponse> {
  return requestAdminJson<RoadNetworkEditApplyResponse>("/admin/road-network/edits/apply", accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ gu: document.gu, dong: document.dong, edits: document.edits }),
  });
}

export async function createAdminRoadNetworkEditJob(
  document: ManualEditDocument,
  accessToken: string,
): Promise<RoadNetworkEditJobResponse> {
  return requestAdminJson<RoadNetworkEditJobResponse>("/admin/road-network/edits/jobs", accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ gu: document.gu, dong: document.dong, edits: document.edits }),
  });
}

export async function fetchAdminRoadNetworkEditJob(
  jobId: number,
  accessToken: string,
): Promise<RoadNetworkEditJobResponse> {
  return requestAdminJson<RoadNetworkEditJobResponse>(`/admin/road-network/edits/jobs/${jobId}`, accessToken);
}

export async function previewAdminRoute(
  request: AdminRoutePreviewRequest,
  accessToken: string,
): Promise<AdminRoutePreviewResponse> {
  return requestAdminJson<AdminRoutePreviewResponse>("/admin/routes/preview", accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function updateAdminRoadSegmentAttributes(
  edgeId: string | number,
  gu: string,
  dong: string,
  request: AdminRoadSegmentAttributesUpdateRequest,
  accessToken: string,
) {
  const params = new URLSearchParams({ gu, dong });
  return requestAdminJson<AdminRoadSegmentUpdateResponse>(
    `/admin/road-network/segments/${edgeId}/attributes?${params.toString()}`,
    accessToken,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );
}

export async function fetchAdminRoutingApplyState(
  accessToken: string,
): Promise<AdminRoutingApplyStateResponse> {
  return requestAdminJson<AdminRoutingApplyStateResponse>("/admin/routing/overrides/apply-state", accessToken);
}

export async function applyAdminRoutingOverrides(
  accessToken: string,
): Promise<AdminRoutingApplyStateResponse> {
  return requestAdminJson<AdminRoutingApplyStateResponse>("/admin/routing/overrides/apply", accessToken, {
    method: "POST",
  });
}

export async function reverseGeocodePlace(point: GeoPoint, accessToken: string) {
  const params = new URLSearchParams({ lat: String(point.lat), lng: String(point.lng) });
  return requestAdminJson<{
    displayAddress: string | null;
    roadAddress: string | null;
    address: string | null;
    region1DepthName: string | null;
    region2DepthName: string | null;
    region3DepthName: string | null;
  }>(`/places/reverse-geocode?${params.toString()}`, accessToken);
}

export async function fetchAdminFacilityPayload({
  gu,
  dong,
  accessToken,
  limit = 20000,
}: {
  gu?: string;
  dong?: string;
  accessToken: string;
  limit?: number;
}): Promise<FacilityPayload> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (gu && dong) {
    params.set("gu", gu);
    params.set("dong", dong);
  }
  return requestAdminJson<FacilityPayload>(`/admin/places/facilities?${params.toString()}`, accessToken);
}

export async function fetchAdminPlaceDetail(placeId: number, accessToken: string): Promise<AdminPlaceDetailResponse> {
  return requestAdminJson<AdminPlaceDetailResponse>(`/admin/places/${placeId}`, accessToken);
}

export async function updateAdminPlace(
  placeId: number,
  gu: string,
  dong: string,
  request: AdminPlaceUpdateRequest,
  accessToken: string,
): Promise<AdminPlaceDetailResponse> {
  const params = new URLSearchParams({ gu, dong });
  return requestAdminJson<AdminPlaceDetailResponse>(`/admin/places/${placeId}?${params.toString()}`, accessToken, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function updateAdminPlaceAccessibilityFeatures(
  placeId: number,
  gu: string,
  dong: string,
  features: PlaceAccessibilityFeature[],
  accessToken: string,
): Promise<AdminPlaceDetailResponse> {
  const params = new URLSearchParams({ gu, dong });
  return requestAdminJson<AdminPlaceDetailResponse>(
    `/admin/places/${placeId}/accessibility-features?${params.toString()}`,
    accessToken,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ features }),
    },
  );
}

export async function fetchAdminHazardReports({
  status,
  cursor,
  size = 10,
  accessToken,
}: FetchAdminHazardReportsParams): Promise<AdminHazardReportListResponse> {
  const params = new URLSearchParams({ size: String(size) });
  if (status) params.set("status", status);
  if (cursor) params.set("cursor", String(cursor));
  return requestAdminJson<AdminHazardReportListResponse>(`/admin/hazard-reports?${params.toString()}`, accessToken);
}

export async function fetchAdminHazardReportDetail(
  reportId: number,
  accessToken: string,
): Promise<AdminHazardReportDetail> {
  return requestAdminJson<AdminHazardReportDetail>(`/admin/hazard-reports/${reportId}`, accessToken);
}

export async function approveAdminHazardReport(
  reportId: number,
  accessToken: string,
): Promise<AdminHazardReportStatusResponse> {
  return requestAdminJson<AdminHazardReportStatusResponse>(`/admin/hazard-reports/${reportId}/approve`, accessToken, {
    method: "PATCH",
  });
}

export async function rejectAdminHazardReport(
  reportId: number,
  accessToken: string,
): Promise<AdminHazardReportStatusResponse> {
  return requestAdminJson<AdminHazardReportStatusResponse>(`/admin/hazard-reports/${reportId}/reject`, accessToken, {
    method: "PATCH",
  });
}

export async function deleteAdminHazardReport(
  reportId: number,
  accessToken: string,
): Promise<AdminHazardReportDeleteResponse> {
  return requestAdminJson<AdminHazardReportDeleteResponse>(`/admin/hazard-reports/${reportId}`, accessToken, {
    method: "DELETE",
  });
}

export async function startAdminHazardRouteReview(
  reportId: number,
  request: StartAdminHazardRouteReviewRequest,
  accessToken: string,
): Promise<AdminHazardRouteReview> {
  return requestAdminJson<AdminHazardRouteReview>(`/admin/hazard-reports/${reportId}/route-review/start`, accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function updateAdminHazardRouteReview(
  reportId: number,
  request: UpdateAdminHazardRouteReviewRequest,
  accessToken: string,
): Promise<AdminHazardRouteReview> {
  return requestAdminJson<AdminHazardRouteReview>(`/admin/hazard-reports/${reportId}/route-review`, accessToken, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function completeAdminHazardRouteReview(
  reportId: number,
  accessToken: string,
): Promise<AdminHazardRouteReview> {
  return requestAdminJson<AdminHazardRouteReview>(`/admin/hazard-reports/${reportId}/route-review/complete`, accessToken, {
    method: "POST",
  });
}
