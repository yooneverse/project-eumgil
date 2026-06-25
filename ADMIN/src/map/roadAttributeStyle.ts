const slopeColors: Record<string, string> = {
  VERY_GENTLE: "#15803d",
  GENTLE: "#65a30d",
  SLOPED: "#d97706",
  CAUTION: "#ea580c",
  STEEP: "#dc2626",
  VERY_STEEP: "#be123c",
  DANGEROUS: "#7f1d1d",
};

const widthWeights: Record<string, number> = {
  VERY_NARROW: 2,
  NARROW: 3,
  MEDIUM: 4,
  WIDE: 5,
  VERY_WIDE: 6,
};

export function roadAttributeStrokeColor(slopeLevel?: string | null): string {
  return slopeColors[slopeLevel ?? ""] ?? "#64748b";
}

export function roadAttributeStrokeWeight(widthLevel?: string | null): number {
  return widthWeights[widthLevel ?? ""] ?? 4;
}

export function roadAttributeStrokeStyle(surfaceType?: string | null): "solid" | "shortdash" | "shortdot" {
  if (surfaceType === "UNPAVED") return "shortdash";
  if (!surfaceType || surfaceType === "UNKNOWN") return "shortdot";
  return "solid";
}
