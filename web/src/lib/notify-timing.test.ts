import { describe, expect, it } from "vitest";
import {
  DEFAULT_NOTIFY_BEFORE,
  NOTIFY_OPTIONS,
  timingLabel,
} from "./notify-timing";

describe("notify-timing", () => {
  it("네 시점과 기본 7일 전", () => {
    expect(NOTIFY_OPTIONS.map((option) => option.value)).toEqual([0, 1, 7, 30]);
    expect(DEFAULT_NOTIFY_BEFORE).toBe(7);
  });

  it("표기", () => {
    expect(timingLabel(0)).toBe("당일");
    expect(timingLabel(7)).toBe("7일 전");
  });
});
