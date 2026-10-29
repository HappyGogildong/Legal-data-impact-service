"use client";

import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { personalizedQuery } from "@/components/laws/law-actions";
import { reviseAction } from "@/lib/api/analysis";
import { isUnauthorized } from "@/lib/api/errors";
import type {
  ActionRevision,
  CitedSource,
  SituationQuestion,
} from "@/lib/api/types";
import type { AskRequest } from "@/lib/ask-store";
import { unmetCopy } from "@/lib/analysis-view";
import {
  readSituation,
  situationKey,
  writeSituation,
} from "@/lib/situation-store";
import { PersonalizeAction } from "./personalize-action";
import { SituationCheck } from "./situation-check";

const UNKNOWN = "모름";

/**
 * 대응 방안 상황 확인 컨테이너(D71) — 답은 탭 저장소에, 확인하면 대응 방안만 다시 만든다.
 * 성공하면 onRevised 로 새 대응 방안을 넘기고(결과 화면이 대응 방안 카드만 바꾼다), 미충족은 사유별 안내.
 */
export function ActionSituation({
  law,
  questions,
  sources,
  selected,
  onCite,
  onRevised,
  onAsk,
}: {
  law: { lawId: string; effectiveDate: string; title?: string };
  questions: SituationQuestion[];
  sources: Map<string, CitedSource>;
  selected: string | null;
  onCite: (sourceId: string) => void;
  onRevised: (revision: ActionRevision) => void;
  onAsk: (next: AskRequest) => void;
}) {
  const key = situationKey(law.lawId, law.effectiveDate);
  const [answers, setAnswers] = useState(() => readSituation(key).answers);
  const [expired, setExpired] = useState(false);
  const lawRef = { lawId: law.lawId, effectiveDate: law.effectiveDate };

  const revise = useMutation({
    mutationFn: () =>
      reviseAction(
        lawRef,
        Object.entries(answers)
          .filter(([, answer]) => answer !== UNKNOWN)
          .map(([questionId, answer]) => ({ questionId, answer })),
      ),
    onSuccess: (revision) => {
      if (revision.unmet) return;
      writeSituation(key, { answers, revision });
      onRevised(revision);
    },
    onError: (error) => {
      if (isUnauthorized(error)) setExpired(true);
    },
  });

  const answer = (questionId: string, value: string) => {
    const next = { ...answers, [questionId]: value };
    setAnswers(next);
    writeSituation(key, { ...readSituation(key), answers: next });
  };

  const unmet = revise.data?.unmet;
  const confirmed = questions.filter(
    (q) => q.id && answers[q.id] && answers[q.id] !== UNKNOWN,
  );

  return (
    <div className="flex flex-col gap-2">
      {revise.isSuccess && !unmet && confirmed.length > 0 && (
        <ul className="flex flex-wrap gap-1.5 text-[12px]">
          {confirmed.map((q) => (
            <li key={q.id} className="rounded-sm bg-chip px-1.5 py-0.5">
              {q.text}: {answers[q.id!]} ✎
            </li>
          ))}
        </ul>
      )}
      <SituationCheck
        questions={questions}
        answers={answers}
        onAnswer={answer}
        onSubmit={() => revise.mutate()}
        pending={revise.isPending}
        sources={sources}
        selected={selected}
        onCite={onCite}
      />
      {unmet?.reason === "SITUATION_EXPIRED" && (
        <div className="text-[13px]">
          <p className="font-bold">상황 질문이 만료됐어요</p>
          {law.title && (
            <button
              type="button"
              onClick={() =>
                onAsk({ query: personalizedQuery(law.title!), lawRef })
              }
              className="mt-1 text-[12.5px] font-bold text-navy"
            >
              다시 받기
            </button>
          )}
        </div>
      )}
      {unmet?.reason === "INSUFFICIENT_GROUNDING" && (
        <p className="text-[13px] text-muted-foreground">
          근거가 부족해 다시 만들지 못했어요
        </p>
      )}
      {unmet &&
        unmet.reason !== "SITUATION_EXPIRED" &&
        unmet.reason !== "INSUFFICIENT_GROUNDING" && (
          <div className="text-[13px]">
            <p className="font-bold">{unmetCopy(unmet).title}</p>
            {unmetCopy(unmet).body && (
              <p className="text-muted-foreground">{unmetCopy(unmet).body}</p>
            )}
            <PersonalizeAction unmet={unmet} />
          </div>
        )}
      {revise.isError && !expired && (
        <p className="text-[13px] text-removed">잠시 후 다시 시도해 주세요.</p>
      )}
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
