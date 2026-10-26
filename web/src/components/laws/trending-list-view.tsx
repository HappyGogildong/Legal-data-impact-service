import Link from "next/link";
import type { TrendingItem } from "@/lib/api/types";
import {
  TRENDING_WINDOW_KEYS,
  TRENDING_WINDOWS,
  type TrendingWindowKey,
} from "@/lib/law-view";
import { cn } from "@/lib/utils";
import { TrendingLawRow } from "./trending-law-row";

export const TRENDING_PAGE_SIZE = 20;

/** S06 자주 찾는 — 구간 필터(링크)·순위(D-N·변화)·집계 안내. 서버 렌더링, 상태 없음. */
export function TrendingListView({
  window,
  today,
  items,
}: {
  window: TrendingWindowKey;
  today: string;
  items: TrendingItem[];
}) {
  return (
    <>
      <nav aria-label="집계 기간" className="mb-3 flex gap-1.5">
        {TRENDING_WINDOW_KEYS.map((key) => (
          <Link
            key={key}
            href={`/laws?tab=trending&window=${key}`}
            aria-current={key === window ? "page" : undefined}
            className={cn(
              "rounded-full border px-3 py-1 text-[12.5px]",
              key === window
                ? "border-navy bg-navy font-bold text-white"
                : "border-border bg-white",
            )}
          >
            {TRENDING_WINDOWS[key].label}
          </Link>
        ))}
      </nav>
      {items.length === 0 ? (
        <p className="rounded-lg border border-border bg-card p-3.5 text-sm text-muted-foreground">
          아직 집계된 조회가 없어요.
        </p>
      ) : (
        <ul className="divide-y divide-line-soft rounded-lg border border-border bg-card px-3.5">
          {items.map((item) => (
            <TrendingLawRow
              key={`${item.lawId}@${item.effectiveDate}`}
              item={item}
              today={today}
            />
          ))}
        </ul>
      )}
      <p className="mt-2 text-xs text-muted-foreground">
        순위는 조회 횟수만 집계해요. 누가 봤는지는 남기지 않아요.
      </p>
    </>
  );
}
