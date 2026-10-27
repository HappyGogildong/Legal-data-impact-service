import { describe, expect, it } from "vitest";
import {
  addDays,
  articleAnchor,
  citationChip,
  citationLabels,
  isIsoDate,
  lawHref,
  monthDay,
  parseLawTab,
  parseRange,
  parseTrendingWindow,
  rankChange,
  rangeBounds,
  safeExternalUrl,
  sourceAnchor,
  sourceDetailHref,
  statusNotice,
} from "./law-view";

describe("기간 필터", () => {
  it("알 수 없는 값·없음은 이번 달(month)", () => {
    expect(parseRange("week")).toBe("week");
    expect(parseRange("quarter")).toBe("quarter");
    expect(parseRange("year")).toBe("month");
    expect(parseRange(undefined)).toBe("month");
    expect(parseRange(["week"])).toBe("month");
    expect(parseRange("toString")).toBe("month");
  });

  it("오늘부터 7·30·90일 — 달·해를 넘긴다", () => {
    expect(addDays("2026-12-28", 7)).toBe("2027-01-04");
    expect(rangeBounds("week", "2026-10-25")).toEqual({
      from: "2026-10-25",
      to: "2026-11-01",
    });
    expect(rangeBounds("quarter", "2026-10-25").to).toBe("2027-01-23");
  });
});

describe("날짜·주소", () => {
  it("달력에 있는 YYYY-MM-DD 만", () => {
    expect(isIsoDate("2026-11-01")).toBe(true);
    expect(isIsoDate("2026-02-30")).toBe(false);
    expect(isIsoDate("2026-13-01")).toBe(false);
    expect(isIsoDate("20261101")).toBe(false);
  });

  it("법령 상세 주소·조문 고유 주소", () => {
    expect(lawHref("001809", "2026-11-01")).toBe("/laws/001809/2026-11-01");
    expect(articleAnchor("6의3")).toBe("art-6-3");
    expect(sourceAnchor("LAW:001809@2026-11-01:art:18#2")).toBe("art-18");
    expect(sourceAnchor("LAW:001809@2026-11-01:addenda:제1조")).toBeNull();
  });

  it("근거 → 상세 주소 — 조문은 #art-N, 시행일 없는 인용키는 null", () => {
    expect(sourceDetailHref("LAW:001809@2026-11-01:art:6의3")).toBe(
      "/laws/001809/2026-11-01#art-6-3",
    );
    expect(sourceDetailHref("LAW:001809@2026-11-01:addenda:제1조")).toBe(
      "/laws/001809/2026-11-01",
    );
    expect(sourceDetailHref("LAW:001809:art:18")).toBeNull();
    expect(sourceDetailHref("근거")).toBeNull();
  });

  it("짧은 날짜·외부 링크는 http(s)만", () => {
    expect(monthDay("2026-11-01")).toBe("11-01");
    expect(safeExternalUrl("https://www.law.go.kr/x")).toBe(
      "https://www.law.go.kr/x",
    );
    expect(safeExternalUrl("javascript:alert(1)")).toBeNull();
    expect(safeExternalUrl(undefined)).toBeNull();
  });
});

describe("상태 배너", () => {
  it("시행예정이면 없음, 시행·과거·철회는 안내", () => {
    expect(statusNotice("시행예정", "2026-11-01")).toBeNull();
    expect(statusNotice("시행중", "2026-08-04")?.title).toBe(
      "이 시행예정본은 2026-08-04에 시행되었어요",
    );
    expect(statusNotice("과거", "2026-08-04")?.body).toContain("다시 개정");
    expect(statusNotice("철회", "2026-08-04")?.title).toBe(
      "이 시행예정본은 철회되었어요",
    );
  });
});

describe("자주 찾는 법령", () => {
  it("탭·구간 파싱 — 모르는 값은 기본(곧 시행·이번 주)", () => {
    expect(parseLawTab("trending")).toBe("trending");
    expect(parseLawTab("x")).toBe("upcoming");
    expect(parseLawTab(undefined)).toBe("upcoming");
    expect(parseTrendingWindow("day")).toBe("day");
    expect(parseTrendingWindow("month")).toBe("month");
    expect(parseTrendingWindow("toString")).toBe("week");
    expect(parseTrendingWindow(["day"])).toBe("week");
  });

  it("변화 표시 — 직전 순위 대비, 없으면 NEW, 글자로도 뜻을 준다", () => {
    expect(rankChange(1, 3)).toEqual({
      kind: "up",
      text: "▲2",
      label: "2계단 올랐어요",
    });
    expect(rankChange(4, 3)).toEqual({
      kind: "down",
      text: "▼1",
      label: "1계단 내려갔어요",
    });
    expect(rankChange(2, 2)).toEqual({
      kind: "same",
      text: "–",
      label: "순위 변동 없어요",
    });
    expect(rankChange(5, null)).toEqual({
      kind: "new",
      text: "NEW",
      label: "새로 올랐어요",
    });
    expect(rankChange(5, undefined).kind).toBe("new");
  });
});

describe("요약 인용 칩", () => {
  const detail = {
    changedArticles: [
      {
        sourceId: "LAW:001809@2026-11-03:art:18",
        label: "제18조(사업계획의 통합심의)",
      },
    ],
    addenda: [
      {
        sourceId: "LAW:001809@2026-11-03:addenda:제1조",
        label: "부칙 제1조(시행일)",
      },
    ],
  };

  it("바뀐 조문은 같은 페이지 조문 주소, 부칙은 칩만, 모르는 근거는 '근거'", () => {
    const labels = citationLabels(detail);
    expect(citationChip("LAW:001809@2026-11-03:art:18#2", labels)).toEqual({
      text: "제18조",
      href: "#art-18",
    });
    expect(citationChip("LAW:001809@2026-11-03:addenda:제1조", labels)).toEqual(
      {
        text: "부칙 제1조",
        href: null,
      },
    );
    expect(citationChip("LAW:001809@2026-11-03:amend", labels)).toEqual({
      text: "근거",
      href: null,
    });
  });
});
