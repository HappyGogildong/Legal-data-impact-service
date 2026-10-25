import Link from "next/link";
import type { LawListItem } from "@/lib/api/types";
import { lawHref, monthDay } from "@/lib/law-view";
import { DDayBadge } from "./d-day-badge";

/** 법령 목록 한 줄 — 법령명(→ S05)·D-N. detailed 면 부처·시행일(S06). 홈·S06 공용. */
export function LawRow({
  item,
  today,
  detailed = false,
}: {
  item: LawListItem;
  today: string;
  detailed?: boolean;
}) {
  if (!item.lawId || !item.effectiveDate) return null;
  return (
    <li className="flex items-center justify-between gap-3 py-2">
      <div className="min-w-0">
        <Link
          href={lawHref(item.lawId, item.effectiveDate)}
          className="font-bold hover:underline"
        >
          {item.title ?? `법령 ${item.lawId}`}
        </Link>
        {detailed && item.ministry && (
          <p className="text-xs text-muted-foreground">{item.ministry}</p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-1.5 text-xs text-muted-foreground">
        {detailed && <span>{monthDay(item.effectiveDate)}</span>}
        <DDayBadge effectiveDate={item.effectiveDate} today={today} />
      </div>
    </li>
  );
}
