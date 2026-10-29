import { afterEach, describe, expect, it, vi } from "vitest";
import { readSituation, situationKey, writeSituation } from "./situation-store";

afterEach(() => vi.restoreAllMocks());

describe("situation-store", () => {
  it("쓴 값을 같은 키로 읽는다", () => {
    const key = situationKey("001809", "2026-08-04");
    writeSituation(key, { answers: { q1: "예" } });
    expect(readSituation(key)).toEqual({ answers: { q1: "예" } });
    expect(key).toBe("lia.situation.001809@2026-08-04");
  });

  it("키(법령 버전)마다 분리된다", () => {
    const first = situationKey("001809", "2026-08-04");
    const second = situationKey("001809", "2027-02-01");
    writeSituation(first, { answers: { q1: "예" } });
    writeSituation(second, { answers: { q1: "아니오" } });
    expect(readSituation(first).answers.q1).toBe("예");
    expect(readSituation(second).answers.q1).toBe("아니오");
  });

  it("값이 없으면 빈 답", () => {
    expect(readSituation(situationKey("none", "2026-01-01"))).toEqual({
      answers: {},
    });
  });

  it("sessionStorage 가 throw 해도 메모리로 동작한다", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    const key = situationKey("mem", "2026-08-04");
    writeSituation(key, { answers: { q1: "모름" } });
    expect(readSituation(key)).toEqual({ answers: { q1: "모름" } });
  });
});
