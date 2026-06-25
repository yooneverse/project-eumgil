import type { PlaceCategory } from "../types";

export const facilityCategoryOrder: PlaceCategory[] = [
  "PUBLIC_OFFICE",
  "WELFARE",
  "HEALTHCARE",
  "TOURIST_SPOT",
  "FOOD_CAFE",
  "ACCOMMODATION",
  "ETC",
];

const categoryColors: Record<PlaceCategory, string> = {
  PUBLIC_OFFICE: "#2563eb",
  WELFARE: "#16a34a",
  HEALTHCARE: "#dc2626",
  TOURIST_SPOT: "#7c3aed",
  FOOD_CAFE: "#d97706",
  ACCOMMODATION: "#0f766e",
  ETC: "#64748b",
};

const categoryLabels: Record<PlaceCategory, string> = {
  PUBLIC_OFFICE: "공공",
  WELFARE: "복지",
  HEALTHCARE: "의료",
  TOURIST_SPOT: "관광",
  FOOD_CAFE: "음식/카페",
  ACCOMMODATION: "숙박",
  ETC: "기타",
};

export function facilityCategoryColor(category?: string | null): string {
  return isPlaceCategory(category) ? categoryColors[category] : categoryColors.ETC;
}

export function facilityCategoryLabel(category?: string | null): string {
  return isPlaceCategory(category) ? categoryLabels[category] : categoryLabels.ETC;
}

function isPlaceCategory(category?: string | null): category is PlaceCategory {
  return facilityCategoryOrder.includes(category as PlaceCategory);
}
