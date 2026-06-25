import type { GeoPoint } from "../types";

export interface HazardReverseGeocodeResult {
  displayAddress: string | null;
  roadAddress: string | null;
  address: string | null;
  region1DepthName: string | null;
  region2DepthName: string | null;
  region3DepthName: string | null;
}

export function formatHazardCoordinateValue(value: number) {
  return value.toFixed(6);
}

export function formatHazardCoordinates(point: GeoPoint) {
  return `${formatHazardCoordinateValue(point.lat)}, ${formatHazardCoordinateValue(point.lng)}`;
}

export function resolveHazardDisplayAddress(result?: HazardReverseGeocodeResult | null) {
  return [result?.displayAddress, result?.roadAddress, result?.address].find((value) => Boolean(value?.trim()))?.trim() ?? null;
}

export function formatHazardRegionLabel(result?: HazardReverseGeocodeResult | null) {
  const values = [result?.region1DepthName, result?.region2DepthName, result?.region3DepthName]
    .map((value) => value?.trim() ?? "")
    .filter(Boolean);

  return values.filter((value, index) => values.indexOf(value) === index).join(" ");
}

export function pickHazardPrimaryImage(imageUrls: string[], selectedIndex: number) {
  if (!imageUrls.length) return null;
  const normalizedIndex = Math.min(Math.max(selectedIndex, 0), imageUrls.length - 1);
  return imageUrls[normalizedIndex] ?? imageUrls[0] ?? null;
}

export function createKakaoMapLink(point: GeoPoint, label: string) {
  const encodedLabel = encodeURIComponent(label);
  return `https://map.kakao.com/link/map/${encodedLabel},${formatHazardCoordinateValue(point.lat)},${formatHazardCoordinateValue(point.lng)}`;
}

export function createKakaoRoadviewLink(point: GeoPoint) {
  return `https://map.kakao.com/link/roadview/${formatHazardCoordinateValue(point.lat)},${formatHazardCoordinateValue(point.lng)}`;
}

export function formatHazardTrackingId(createdAt: string, reportId: number) {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return `#report-${String(reportId).padStart(4, "0")}`;
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `#${year}-${month}${day}-${String(reportId).padStart(4, "0")}`;
}
