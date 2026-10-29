import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  askKey,
  clearDraft,
  setAsk,
  setDraft,
  useAsk,
  useDraft,
} from "./ask-store";

describe("ask-store", () => {
  it("질문은 탭 sessionStorage 에 두고 구독자에게 알린다", () => {
    const { result } = renderHook(() => useAsk());
    expect(result.current).toBeNull();

    act(() =>
      setAsk({
        query: "주택법",
        lawRef: { lawId: "001809", effectiveDate: "2026-08-04" },
      }),
    );

    expect(result.current).toEqual({
      query: "주택법",
      lawRef: { lawId: "001809", effectiveDate: "2026-08-04" },
    });
    expect(sessionStorage.getItem("lia.ask")).toContain("주택법");
  });

  it("askKey — 같은 질문·법령이면 같은 키", () => {
    expect(askKey({ query: "a" })).toBe(askKey({ query: "a" }));
    expect(askKey({ query: "a" })).not.toBe(
      askKey({
        query: "a",
        lawRef: { lawId: "1", effectiveDate: "2026-01-01" },
      }),
    );
  });

  it("askKey 는 프로필 기준 찾기 여부를 구분한다", () => {
    expect(askKey({ query: "전세", profileSearch: true })).not.toBe(
      askKey({ query: "전세" }),
    );
    expect(askKey({ query: "전세", profileSearch: false })).toBe(
      askKey({ query: "전세" }),
    );
  });

  it("초안 — 질문 고치기로 넘긴 문장을 질문창이 받는다", () => {
    const { result } = renderHook(() => useDraft());
    act(() => setDraft("자본시장법"));
    expect(result.current).toBe("자본시장법");
    act(() => clearDraft());
    expect(result.current).toBe("");
  });
});
