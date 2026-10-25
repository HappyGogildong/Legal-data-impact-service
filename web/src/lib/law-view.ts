import type { LawDetail } from "@/lib/api/types";

/** S06 기간 필터 — 오늘부터 N일 이내(screen-plan S06 "이번 주·이번 달·3개월"). */
export const RANGES = {
  week: { label: "이번 주", days: 7 },
  month: { label: "이번 달", days: 30 },
  quarter: { label: "3개월", days: 90 },
} as const;
export type Range = keyof typeof RANGES;
export const RANGE_KEYS = Object.keys(RANGES) as Range[];

/** S06 한 쪽 크기(백엔드 기본값과 같게). */
export const LAW_PAGE_SIZE = 20;

export function parseRange(value: string | string[] | undefined): Range {
  return typeof value === "string" && Object.hasOwn(RANGES, value)
    ? (value as Range)
    : "month";
}

/** "YYYY-MM-DD" + n일 — 달력 계산만(UTC 자정 기준이라 시간대 영향 없음). */
export function addDays(date: string, days: number): string {
  const time = Date.parse(`${date}T00:00:00Z`) + days * 86_400_000;
  return new Date(time).toISOString().slice(0, 10);
}

export function rangeBounds(
  range: Range,
  today: string,
): { from: string; to: string } {
  return { from: today, to: addDays(today, RANGES[range].days) };
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 달력에 있는 "YYYY-MM-DD" 인가 — 2026-02-30 같은 값은 거른다. */
export function isIsoDate(value: string): boolean {
  if (!ISO_DATE.test(value)) return false;
  const time = Date.parse(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(time) && new Date(time).toISOString().slice(0, 10) === value
  );
}

export function lawHref(lawId: string, effectiveDate: string): string {
  return `/laws/${encodeURIComponent(lawId)}/${effectiveDate}`;
}

/** 조문 고유 주소 — "6의3" → "art-6-3"(screen-plan S05). */
export function articleAnchor(articleNo: string): string {
  return `art-${articleNo.replace(/의/g, "-")}`;
}

/** 인용키의 조문 고유 주소 — 조문 인용이 아니면 null. 하위 청크(#n)는 같은 조문. */
export function sourceAnchor(sourceId: string): string | null {
  const match = sourceId.match(/:art:([^#]+)/);
  return match ? articleAnchor(match[1]) : null;
}

const DATED_SOURCE_ID = /^LAW:([^@:#]+)@(\d{4}-\d{2}-\d{2})(?::art:([^#]+))?/;

/** 근거 → 법령 상세(S05) 주소. 시행일 없는 인용키(시행중 기준선)는 어느 버전인지 몰라 null. */
export function sourceDetailHref(sourceId: string): string | null {
  const match = sourceId.match(DATED_SOURCE_ID);
  if (!match) return null;
  const [, lawId, effectiveDate, articleNo] = match;
  const href = lawHref(lawId, effectiveDate);
  return articleNo ? `${href}#${articleAnchor(articleNo)}` : href;
}

export type StatusNotice = { title: string; body: string };

/** 시행예정이 아닌 버전으로 들어왔을 때의 안내(S05 상태 배너). 시행예정이면 null. */
export function statusNotice(
  status: LawDetail["status"],
  effectiveDate: string | undefined,
): StatusNotice | null {
  switch (status) {
    case "시행중":
      return {
        title: `이 시행예정본은 ${effectiveDate}에 시행되었어요`,
        body: "지금 시행 중인 내용이에요.",
      };
    case "과거":
      return {
        title: `이 시행예정본은 ${effectiveDate}에 시행되었어요`,
        body: "그 뒤 다시 개정되어 지금 내용과 다를 수 있어요.",
      };
    case "철회":
      return {
        title: "이 시행예정본은 철회되었어요",
        body: "예전 답변의 근거를 확인하는 용도로만 남겨 두었어요.",
      };
    default:
      return null;
  }
}

/** 목록의 짧은 날짜 "11-01". */
export function monthDay(date: string): string {
  return date.slice(5);
}

/** 원문 링크 — http(s)만 연다(javascript: 등 차단). */
export function safeExternalUrl(url: string | undefined): string | null {
  return url && /^https?:\/\//i.test(url) ? url : null;
}
