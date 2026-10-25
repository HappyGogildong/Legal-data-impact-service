import Link from "next/link";
import type { LawListItem } from "@/lib/api/types";
import { LawRow } from "./law-row";

/** 홈 "곧 시행되는 법령"(S01) — D-N 가까운 순. 비면 그리지 않는다. */
export function UpcomingLawList({
  items,
  today,
}: {
  items: LawListItem[];
  today: string;
}) {
  if (items.length === 0) return null;
  return (
    <section
      aria-labelledby="upcoming-laws-title"
      className="rounded-lg border border-border bg-card p-3.5"
    >
      <div className="mb-1 flex items-center justify-between">
        <h2 id="upcoming-laws-title" className="font-bold">
          곧 시행되는 법령
        </h2>
        <Link href="/laws" className="text-[12.5px] font-bold text-navy">
          전체 보기
        </Link>
      </div>
      <ul className="divide-y divide-line-soft">
        {items.map((item) => (
          <LawRow
            key={`${item.lawId}@${item.effectiveDate}`}
            item={item}
            today={today}
          />
        ))}
      </ul>
    </section>
  );
}
