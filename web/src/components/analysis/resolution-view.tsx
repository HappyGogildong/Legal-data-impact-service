"use client";

import { useRouter } from "next/navigation";
import { useToday } from "@/hooks/use-browser";
import type { AnalysisResponse, LawRefInput } from "@/lib/api/types";
import { setDraft } from "@/lib/ask-store";
import { dDayLabel, daysUntil } from "@/lib/dates";

type ResolutionViewProps = {
  response: AnalysisResponse;
  query: string;
  onPick: (lawRef: LawRefInput) => void;
};

/** S03 해소 안내 — 분석하지 않고 상황별로(fail-closed). 단정하는 말투를 쓰지 않는다(screen-plan S03). */
export function ResolutionView({
  response,
  query,
  onPick,
}: ResolutionViewProps) {
  const router = useRouter();
  const today = useToday();
  const editQuestion = () => {
    setDraft(query);
    router.push("/");
  };
  const editButton = (
    <button
      type="button"
      onClick={editQuestion}
      className="mt-3 rounded-md border border-border bg-white px-4 py-2 text-[13px] font-bold"
    >
      질문 고치기
    </button>
  );

  if (response.resolution === "AMBIGUOUS") {
    const candidates = (response.candidates ?? []).filter(
      (candidate) => candidate.lawId && candidate.effectiveDate,
    );
    return (
      <div className="mx-auto max-w-[640px] px-4 py-6">
        <h1 className="text-lg font-bold">어떤 법령인지 골라 주세요</h1>
        {response.message && (
          <p className="text-sm text-muted-foreground">{response.message}</p>
        )}
        <ul className="mt-3 flex flex-col gap-2">
          {candidates.map((candidate) => (
            <li key={`${candidate.lawId}@${candidate.effectiveDate}`}>
              <button
                type="button"
                onClick={() =>
                  onPick({
                    lawId: candidate.lawId!,
                    effectiveDate: candidate.effectiveDate!,
                  })
                }
                className="flex w-full items-center justify-between gap-2 rounded-lg border border-border bg-card p-3 text-left"
              >
                <span>
                  <b className="block">{candidate.title}</b>
                  <span className="text-xs text-muted-foreground">
                    {candidate.effectiveDate} 시행
                  </span>
                </span>
                {today && (
                  <span className="rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold text-navy">
                    {dDayLabel(daysUntil(candidate.effectiveDate!, today))}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
        {editButton}
      </div>
    );
  }

  const notFound = response.resolution === "NOT_FOUND_YET";
  return (
    <div className="mx-auto max-w-[640px] px-4 py-10 text-center">
      <h1 className="text-lg font-bold">
        {notFound
          ? "시행예정 법령으로 확인되지 않았어요"
          : "법령을 확인할 수 없어요"}
      </h1>
      <p className="text-sm text-muted-foreground">
        {notFound
          ? "이미 시행 중이거나 아직 공포 전인 법령일 수 있어요."
          : "정확한 법령명을 알려 주세요. 예) 주택법, 근로기준법"}
      </p>
      {response.message && (
        <p className="mt-1 text-xs text-muted-foreground">{response.message}</p>
      )}
      {editButton}
    </div>
  );
}
