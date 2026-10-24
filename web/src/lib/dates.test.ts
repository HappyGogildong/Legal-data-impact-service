import { describe, expect, it } from "vitest";
import { dDayLabel, daysUntil, formatSeoulDateTime, seoulDate } from "./dates";

describe("dates", () => {
  it("seoulDate — UTC 15시는 서울 기준 다음 날", () => {
    expect(seoulDate(new Date("2026-10-23T15:30:00Z"))).toBe("2026-10-24");
  });

  it("daysUntil·dDayLabel", () => {
    expect(daysUntil("2026-08-04", "2026-07-25")).toBe(10);
    expect(dDayLabel(10)).toBe("D-10");
    expect(dDayLabel(0)).toBe("D-DAY");
    expect(dDayLabel(-3)).toBe("시행 중");
  });

  it("formatSeoulDateTime — 오프셋이 달라도 서울 시각", () => {
    expect(formatSeoulDateTime("2026-10-22T19:12:31Z")).toBe(
      "2026-10-23 04:12",
    );
  });
});
