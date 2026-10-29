"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
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
import { analysisQueryKey, type AskRequest } from "@/lib/ask-store";
import { unmetCopy } from "@/lib/analysis-view";
import {
  clearSituation,
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
  setId,
  sources,
  selected,
  onCite,
  onRevised,
  onAsk,
}: {
  law: { lawId: string; effectiveDate: string; title?: string };
  questions: SituationQuestion[];
  /** 서버가 이 질문 묶음에 붙인 식별자 — 재생성 요청에 돌려보내고, 저장된 답이 이 묶음의 것인지 가른다 */
  setId: string;
  sources: Map<string, CitedSource>;
  selected: string | null;
  onCite: (sourceId: string) => void;
  onRevised: (revision: ActionRevision) => void;
  onAsk: (next: AskRequest) => void;
}) {
  const key = situationKey(law.lawId, law.effectiveDate);
  // 저장된 상태는 같은 질문 묶음(setId)으로 만든 것만 쓴다 — 다시 받아 질문이 바뀌었으면 옛 답은 버린다
  const identity = setId;
  const [answers, setAnswers] = useState(
    () => readSituation(key, identity).answers,
  );
  const [expired, setExpired] = useState(false);
  const queryClient = useQueryClient();
  const lawRef = { lawId: law.lawId, effectiveDate: law.effectiveDate };

  const revise = useMutation({
    // 지금 질문에 대한 답만, 비어 있지 않고 모름이 아닌 것만 보낸다
    mutationFn: () =>
      reviseAction(
        lawRef,
        setId,
        questions.flatMap((q) => {
          const value = q.id ? answers[q.id] : undefined;
          return q.id && value?.trim() && value !== UNKNOWN
            ? [{ questionId: q.id, answer: value }]
            : [];
        }),
      ),
    onSuccess: (revision) => {
      if (revision.unmet) return;
      writeSituation(key, { answers, revision, identity });
      onRevised(revision);
    },
    onError: (error) => {
      if (isUnauthorized(error)) setExpired(true);
    },
  });

  const answer = (questionId: string, value: string) => {
    const next = { ...answers, [questionId]: value };
    if (value === "") delete next[questionId]; // 지운 날짜는 답이 없는 것
    setAnswers(next);
    writeSituation(key, {
      ...readSituation(key, identity),
      answers: next,
      identity,
    });
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
              {q.text}: {answers[q.id!]}
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
          <button
            type="button"
            onClick={() => {
              clearSituation(key); // 새로 받는 질문에 옛 답·재생성 결과를 잇지 않는다
              const next = { query: personalizedQuery(law.title), lawRef };
              // 주 경로("내 영향·대응 방안 보기")와 같은 질문이라 캐시(staleTime 무한)를 그대로 돌려주면 서버에 질문이
              // 다시 저장되지 않는다 — 캐시를 비워(활성이면 다시 부른다) 분석을 새로 받는다
              void queryClient.resetQueries({
                queryKey: analysisQueryKey(next),
              });
              onAsk(next);
            }}
            className="mt-1 text-[12.5px] font-bold text-navy"
          >
            다시 받기
          </button>
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
      {revise.isError && !isUnauthorized(revise.error) && (
        <p className="text-[13px] text-removed">잠시 후 다시 시도해 주세요.</p>
      )}
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
