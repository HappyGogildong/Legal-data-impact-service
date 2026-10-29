import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAllSituations,
  clearSituation,
  clearSituationMemory,
  readSituation,
  situationKey,
  writeSituation,
} from "./situation-store";

const ID = "0123456789abcdef";

beforeEach(() => {
  sessionStorage.clear();
  clearSituationMemory();
});
afterEach(() => vi.restoreAllMocks());

describe("situation-store", () => {
  it("쓴 값을 같은 키로 읽는다", () => {
    const key = situationKey("001809", "2026-08-04");
    writeSituation(key, { answers: { q1: "예" }, identity: ID });
    expect(readSituation(key, ID)).toEqual({
      answers: { q1: "예" },
      identity: ID,
    });
    expect(key).toBe("lia.situation.001809@2026-08-04");
  });

  it("키(법령 버전)마다 분리된다", () => {
    const first = situationKey("001809", "2026-08-04");
    const second = situationKey("001809", "2027-02-01");
    writeSituation(first, { answers: { q1: "예" }, identity: ID });
    writeSituation(second, { answers: { q1: "아니오" }, identity: ID });
    expect(readSituation(first, ID).answers.q1).toBe("예");
    expect(readSituation(second, ID).answers.q1).toBe("아니오");
  });

  it("값이 없으면 빈 답", () => {
    expect(readSituation(situationKey("none", "2026-01-01"), ID)).toEqual({
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
    writeSituation(key, { answers: { q1: "모름" }, identity: ID });
    expect(readSituation(key, ID)).toEqual({
      answers: { q1: "모름" },
      identity: ID,
    });
  });

  it("setItem 만 throw 해도(읽기는 됨) 메모리 값을 읽는다", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    const key = situationKey("quota", "2026-08-04");
    writeSituation(key, { answers: { q1: "예" }, identity: ID });
    expect(readSituation(key, ID)).toEqual({
      answers: { q1: "예" },
      identity: ID,
    });
  });

  it("모양이 틀린 JSON 은 빈 상태로", () => {
    const key = situationKey("bad", "2026-08-04");
    for (const raw of [
      "null",
      "[]",
      "42",
      '{"answers":"예"}',
      '{"answers":{"q1":1}}',
      '{"answers":{},"revision":"x"}',
      "{not json",
    ]) {
      sessionStorage.setItem(key, raw);
      expect(readSituation(key, ID)).toEqual({ answers: {} });
    }
  });

  it("질문 묶음 식별자(setId)가 다르면 빈 상태로 보고, 다음 쓰기가 덮어쓴다", () => {
    const key = situationKey("001809", "2026-08-04");
    const old = "0123456789abcdef";
    const fresh = "fedcba9876543210";

    writeSituation(key, { answers: { q1: "예" }, identity: old });
    expect(readSituation(key, fresh)).toEqual({ answers: {} });
    expect(readSituation(key, old).answers.q1).toBe("예");

    writeSituation(key, { answers: { q1: "아니오" }, identity: fresh });
    expect(readSituation(key, old)).toEqual({ answers: {} });
    expect(readSituation(key, fresh).answers.q1).toBe("아니오");
  });

  it("clearAllSituations 는 lia.situation.* 키만 저장소와 메모리에서 모두 지운다", () => {
    const first = situationKey("001809", "2026-08-04");
    const second = situationKey("001809", "2027-02-01");
    writeSituation(first, { answers: { q1: "예" }, identity: ID });
    writeSituation(second, { answers: { q1: "아니오" }, identity: ID });
    sessionStorage.setItem("lia.ask", "keep");
    sessionStorage.setItem("other", "keep");

    clearAllSituations();

    expect(readSituation(first, ID)).toEqual({ answers: {} });
    expect(readSituation(second, ID)).toEqual({ answers: {} });
    expect(sessionStorage.getItem(first)).toBeNull();
    expect(sessionStorage.getItem(second)).toBeNull();
    expect(sessionStorage.getItem("lia.ask")).toBe("keep");
    expect(sessionStorage.getItem("other")).toBe("keep");
  });

  it("clearAllSituations 는 sessionStorage 가 throw 해도 메모리는 비운다", () => {
    const key = situationKey("mem", "2026-08-04");
    writeSituation(key, { answers: { q1: "예" }, identity: ID });
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new Error("blocked");
    });

    expect(() => clearAllSituations()).not.toThrow();

    expect(readSituation(key, ID)).toEqual({ answers: {} });
  });

  it("clearSituation 은 저장소와 메모리 모두 비운다", () => {
    const key = situationKey("001809", "2026-08-04");
    writeSituation(key, { answers: { q1: "예" }, identity: ID });
    clearSituation(key);
    expect(readSituation(key, ID)).toEqual({ answers: {} });
    expect(sessionStorage.getItem(key)).toBeNull();
  });
});
