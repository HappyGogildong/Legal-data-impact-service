"use client";

import Link from "next/link";
import { useId } from "react";
import { useToday } from "@/hooks/use-browser";
import type { LawBrief, LawHeader } from "@/lib/api/types";
import { dDayLabel, daysUntil } from "@/lib/dates";
import { lawHref } from "@/lib/law-view";

type LawHeaderViewProps = {
  law: LawHeader;
  alternatives: LawBrief[];
  onPickAlternative: (alternative: LawBrief) => void;
};

/** 결과 상단 — 법령명·D-N·시행일·종류·부처, 같은 법령의 다른 시행예정본(D43). */
export function LawHeaderView({
  law,
  alternatives,
  onPickAlternative,
}: LawHeaderViewProps) {
  const titleId = useId();
  const pickable = alternatives.filter(
    (alternative) => alternative.lawId && alternative.effectiveDate,
  );
  const today = useToday();
  const days =
    today && law.effectiveDate ? daysUntil(law.effectiveDate, today) : null;
  const meta = [
    law.effectiveDate && `${law.effectiveDate} 시행`,
    law.lawType,
    law.ministry,
  ]
    .filter(Boolean)
    .join(" · ");
  return (
    <section
      aria-labelledby={titleId}
      className="rounded-lg border border-border bg-card p-3.5"
    >
      <div className="flex flex-wrap items-center gap-2">
        <h1 id={titleId} className="text-lg font-bold">
          {law.title ?? (law.lawId ? `법령 ${law.lawId}` : "법령 정보")}
        </h1>
        {days !== null && (
          <span className="rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold text-navy">
            {dDayLabel(days)}
          </span>
        )}
      </div>
      {meta && <p className="mt-0.5 text-xs text-muted-foreground">{meta}</p>}
      {law.lawId && law.effectiveDate && (
        <Link
          href={lawHref(law.lawId, law.effectiveDate)}
          className="mt-1 inline-block text-xs font-bold text-navy"
        >
          법령 상세 보기 →
        </Link>
      )}
      {pickable.length > 0 && (
        <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
          <span className="text-muted-foreground">다른 시행일</span>
          {pickable.map((alternative) => (
            <button
              key={`${alternative.lawId}@${alternative.effectiveDate}`}
              type="button"
              onClick={() => onPickAlternative(alternative)}
              className="rounded-full border border-border bg-white px-2.5 py-1"
            >
              {alternative.effectiveDate} 시행본으로 보기
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
