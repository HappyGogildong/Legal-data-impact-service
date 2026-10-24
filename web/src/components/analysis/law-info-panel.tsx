import type { LawHeader } from "@/lib/api/types";

/** 데스크톱 오른쪽 패널 기본 상태 — 법령 정보(법령 상세 S05 링크는 2단계). */
export function LawInfoPanel({ law }: { law: LawHeader }) {
  const rows = [
    ["법령", law.title ?? law.lawId],
    ["시행일", law.effectiveDate],
    ["종류", law.lawType],
    ["소관부처", law.ministry],
  ].filter((row): row is [string, string] => Boolean(row[1]));
  return (
    <section
      aria-label="법령 정보"
      className="rounded-lg border border-border bg-card p-3.5 text-sm"
    >
      <h2 className="mb-1 font-bold">법령 정보</h2>
      <dl>
        {rows.map(([term, value]) => (
          <div
            key={term}
            className="flex justify-between gap-3 border-b border-line-soft py-2 last:border-0"
          >
            <dt className="text-muted-foreground">{term}</dt>
            <dd className="text-right">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
