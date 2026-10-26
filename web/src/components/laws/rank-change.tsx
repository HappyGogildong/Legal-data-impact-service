import { rankChange } from "@/lib/law-view";
import { cn } from "@/lib/utils";

const TONE = {
  up: "text-added",
  down: "text-removed",
  same: "text-muted-foreground",
  new: "rounded-sm bg-chip px-1 text-navy",
} as const;

/** 순위 변화 — 기호·숫자 글자와 화면 읽기용 문장을 함께(색만으로 뜻을 전하지 않는다). */
export function RankChange({
  rank,
  previousRank,
}: {
  rank: number;
  previousRank: number | null | undefined;
}) {
  const change = rankChange(rank, previousRank);
  return (
    <span
      className={cn(
        "text-[11px] font-bold whitespace-nowrap",
        TONE[change.kind],
      )}
    >
      <span aria-hidden>{change.text}</span>
      <span className="sr-only">{change.label}</span>
    </span>
  );
}
