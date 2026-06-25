import { describe, expect, it } from "vitest";
import { adminShellClassName } from "./adminLayout";

describe("admin layout classes", () => {
  it("collapses the left sidebar without changing the page shell contract", () => {
    expect(adminShellClassName(true)).toBe("app-shell sidebar-collapsed");
    expect(adminShellClassName(false)).toBe("app-shell");
  });
});
