export function roadviewUnavailableMessage(hasClient: boolean, hasRoadview: boolean): string | null {
  if (hasClient && hasRoadview) return null;
  return "Kakao Roadview is unavailable.";
}

export function shouldOpenRoadviewForMode(mode: string): boolean {
  return mode === "roadview";
}
