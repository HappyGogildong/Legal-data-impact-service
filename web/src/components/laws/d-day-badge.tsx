import { dDayLabel, daysUntil } from "@/lib/dates";

/** 시행일 배지 — D-N(서버가 정한 오늘 기준, 서버 렌더링 HTML 에 바로 들어간다). */
export function DDayBadge({
  effectiveDate,
  today,
}: {
  effectiveDate: string;
  today: string;
}) {
  return (
    <span className="rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold whitespace-nowrap text-navy">
      {dDayLabel(daysUntil(effectiveDate, today))}
    </span>
  );
}
