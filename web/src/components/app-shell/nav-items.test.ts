import { describe, expect, it } from "vitest";
import { NAV_ITEMS, isActive } from "./nav-items";

describe("NAV_ITEMS", () => {
  it("주 메뉴는 홈·알림·안내·내 정보", () => {
    expect(NAV_ITEMS.map((item) => item.label)).toEqual([
      "홈",
      "알림",
      "안내",
      "내 정보",
    ]);
  });
});

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
