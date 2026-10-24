import { cn } from "@/lib/utils";

const STYLES: Record<string, string> = {
  개정: "bg-amend-bg text-amend",
  신설: "bg-added-bg text-added",
  삭제: "bg-removed-bg text-removed",
  이동: "bg-chip text-navy",
};

/** 변경 배지 — 색과 글자를 함께(색만으로 의미를 전하지 않는다, visual-design §2). */
export function ChangeBadge({ type }: { type?: string }) {
  if (!type || !STYLES[type]) return null;
  return (
    <span
      className={cn(
        "rounded-sm px-1.5 py-0.5 text-[11px] font-bold whitespace-nowrap",
        STYLES[type],
      )}
    >
      {type}
    </span>
  );
}
