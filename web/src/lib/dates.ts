const SEOUL = "Asia/Seoul";
const DAY_MS = 86_400_000;

/** 서울 기준 날짜 "YYYY-MM-DD" — 시행일은 한국 날짜다. */
export function seoulDate(now: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: SEOUL,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/** 서울 기준 오늘 — 서버 렌더링용(요청 시점에 부른다: `await connection()` 뒤). 브라우저 컴포넌트는 `useToday`. */
export function seoulToday(): string {
  return seoulDate(new Date());
}

/** 시행일까지 남은 날 — 오늘이 시행일이면 0, 지났으면 음수. 두 값 모두 "YYYY-MM-DD". */
export function daysUntil(effectiveDate: string, today: string): number {
  return Math.round((Date.parse(effectiveDate) - Date.parse(today)) / DAY_MS);
}

export function dDayLabel(days: number): string {
  if (days > 0) return `D-${days}`;
  if (days === 0) return "D-DAY";
  return "시행 중";
}

/** ISO 시각 → 서울 "YYYY-MM-DD HH:mm". */
export function formatSeoulDateTime(iso: string): string {
  const date = new Date(iso);
  const time = new Intl.DateTimeFormat("en-GB", {
    timeZone: SEOUL,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
  return `${seoulDate(date)} ${time}`;
}
