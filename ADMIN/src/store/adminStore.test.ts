import { afterEach, describe, expect, it, vi } from "vitest";
import { adminPageFromSearch, useAdminStore } from "./adminStore";
import type { AdminPage } from "../types";

describe("adminStore role model", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps ADMIN authority limited to ADMIN", () => {
    expect(useAdminStore.getState().role).toBe("ADMIN");
  });

  it("starts from the operations dashboard", () => {
    expect(useAdminStore.getState().page).toBe("home");
  });

  it("accepts a deep-linked admin page from the query string", () => {
    expect(adminPageFromSearch("?page=routeStats")).toBe("routeStats");
    expect(adminPageFromSearch("?page=bottleneckMonitoring")).toBe("bottleneckMonitoring");
  });

  it("maps supported preview targets to their initial admin page", () => {
    expect(adminPageFromSearch("?preview=hazards")).toBe("hazards");
    expect(adminPageFromSearch("?preview=routeStats")).toBe("routeStats");
    expect(adminPageFromSearch("?preview=bottleneckMonitoring")).toBe("bottleneckMonitoring");
    expect(adminPageFromSearch("?preview=notices")).toBe("notices");
  });

  it("keeps explicit page links ahead of preview hints", () => {
    expect(adminPageFromSearch("?page=home&preview=hazards")).toBe("home");
  });

  it("falls back to home for unknown deep-link targets", () => {
    expect(adminPageFromSearch("?page=does-not-exist")).toBe("home");
    expect(adminPageFromSearch("?page=movementPatternAnalysis")).toBe("home");
    expect(adminPageFromSearch("")).toBe("home");
  });

  it("models second MVP workspaces as task tabs", () => {
    const pages: AdminPage[] = [
      "home",
      "routeStats",
      "bottleneckMonitoring",
      "routeTuning",
      "network",
      "users",
      "facilities",
      "hazards",
      "notices",
      "logs",
    ];

    expect(pages).toEqual([
      "home",
      "routeStats",
      "bottleneckMonitoring",
      "routeTuning",
      "network",
      "users",
      "facilities",
      "hazards",
      "notices",
      "logs",
    ]);
  });

  it("persists draft edits locally until they are cleared or applied", () => {
    const storage = new Map<string, string>();
    vi.stubGlobal("window", {
      localStorage: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
      },
    });

    useAdminStore.getState().clearDraft();
    useAdminStore.getState().addDraftEdit({
      action: "add_segment",
      segmentType: "SIDE_LINE",
      geom: { type: "LineString", coordinates: [[129, 35], [129.1, 35.1]] },
    });

    const stored = [...storage.values()][0];
    expect(stored).toContain("add_segment");

    useAdminStore.getState().clearDraft();
    expect(storage.size).toBe(0);
  });

  it("scopes drafts to the selected gu and dong instead of carrying edits across areas", () => {
    const storage = new Map<string, string>();
    vi.stubGlobal("window", {
      localStorage: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
      },
    });

    useAdminStore.getState().setSelectedArea("강서구", "명지동");
    useAdminStore.getState().clearDraft();
    useAdminStore.getState().addDraftEdit({
      action: "delete_segment",
      edgeId: "old-area-edge",
    });

    useAdminStore.getState().setSelectedArea("북구", "덕천동");

    expect(useAdminStore.getState().selectedAssignmentId).toBe("area:북구:덕천동");
    expect(useAdminStore.getState().draftEdits).toEqual([]);
  });
});
