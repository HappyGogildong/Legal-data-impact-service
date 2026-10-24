import { describe, expect, it } from "vitest";
import { isActive } from "./nav-items";

describe("isActive", () => {
  it("'/' 는 홈에서만 활성이다", () => {
    expect(isActive("/", "/")).toBe(true);
    expect(isActive("/info", "/")).toBe(false);
  });

  it("경로 구간 경계에서 맞춘다", () => {
    expect(isActive("/info", "/info")).toBe(true);
    expect(isActive("/info/x", "/info")).toBe(true);
    expect(isActive("/information", "/info")).toBe(false);
  });
});
