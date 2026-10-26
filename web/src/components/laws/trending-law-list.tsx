import Link from "next/link";
import type { TrendingItem } from "@/lib/api/types";
import { TrendingLawRow } from "./trending-law-row";

/** 홈 "자주 찾는 법령 · 이번 주"(S01) — 비면 그리지 않는다. */
export function TrendingLawList({ items }: { items: TrendingItem[] }) {
  if (items.length === 0) return null;
  return (
    <section
      aria-labelledby="trending-laws-title"
      className="rounded-lg border border-border bg-card p-3.5"
    >
      <div className="mb-1 flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <h2 id="trending-laws-title" className="font-bold">
            자주 찾는 법령
          </h2>
          <span className="text-xs text-muted-foreground">이번 주</span>
        </div>
        <Link
          href="/laws?tab=trending"
          className="text-[12.5px] font-bold text-navy"
        >
          전체 보기
        </Link>
      </div>
      <ul className="divide-y divide-line-soft">
        {items.map((item) => (
          <TrendingLawRow
            key={`${item.lawId}@${item.effectiveDate}`}
            item={item}
          />
        ))}
      </ul>
    </section>
  );
}
