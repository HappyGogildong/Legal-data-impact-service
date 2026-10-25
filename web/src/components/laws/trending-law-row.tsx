import Link from "next/link";
import type { TrendingItem } from "@/lib/api/types";
import { lawHref } from "@/lib/law-view";
import { DDayBadge } from "./d-day-badge";
import { RankChange } from "./rank-change";

/** 자주 찾는 한 줄 — 순위·법령명(→ S05)·(S06: D-N)·변화. */
export function TrendingLawRow({
  item,
  today,
}: {
  item: TrendingItem;
  /** 있으면 시행일 D-N 을 보인다(S06) */
  today?: string;
}) {
  if (!item.lawId || !item.effectiveDate || item.rank === undefined) {
    return null;
  }
  return (
    <li className="flex items-center gap-3 py-2">
      <span className="w-5 shrink-0 text-center font-bold text-navy">
        {item.rank}
      </span>
      <Link
        href={lawHref(item.lawId, item.effectiveDate)}
        className="min-w-0 flex-1 truncate font-bold hover:underline"
      >
        {item.title ?? `법령 ${item.lawId}`}
      </Link>
      {today && <DDayBadge effectiveDate={item.effectiveDate} today={today} />}
      <RankChange rank={item.rank} previousRank={item.previousRank} />
    </li>
  );
}
