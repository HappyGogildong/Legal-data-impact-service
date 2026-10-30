"use client";

import { useState } from "react";
import type {
  ActionRevision,
  AnalysisResponse,
  LawBrief,
  Unmet,
} from "@/lib/api/types";
import type { AskRequest } from "@/lib/ask-store";
import { readSituation, situationKey } from "@/lib/situation-store";
import { personalizedQuery } from "@/components/laws/law-actions";
import { ActionSituation } from "./action-situation";
import { FollowUpBar } from "./follow-up-bar";
import { LawHeaderView } from "./law-header-view";
import { ResultBody } from "./result-body";

type ResultViewProps = {
  response: AnalysisResponse;
  ask: AskRequest;
  onAsk: (next: AskRequest) => void;
  renderUnmetAction?: (unmet: Unmet | undefined) => React.ReactNode;
};

/**
 * S02 분석 결과 — 실시간 요소(상황 질문·보기 버튼·후속 질문)를 ResultBody 위에 얹는다.
 */
export function ResultView({
  response,
  ask,
  onAsk,
  renderUnmetAction,
}: ResultViewProps) {
  const law = response.law;
  const lawKey =
    law?.lawId && law.effectiveDate
      ? situationKey(law.lawId, law.effectiveDate)
      : null;
  const setId = response.situation?.setId;
  // 상황 확인으로 다시 만든 대응 방안(D71) — 같은 탭에서 이 법령 버전의 같은 질문 묶음(setId)으로 확인한 적이 있으면
  // 이어서 보여 준다. 묶음이 없는 응답(질문 없음·대응 방안 미충족)은 옛 재생성 결과를 되살리지 않는다.
  const [revision, setRevision] = useState<ActionRevision | undefined>(() =>
    lawKey && setId ? readSituation(lawKey, setId).revision : undefined,
  );
  if (!law) return null; // 발견 응답은 AskScreen 이 S04 로 보낸다 — 여기 오는 응답엔 law 가 있다

  const { lawId, effectiveDate, title } = law;
  const questions = response.situation?.questions ?? [];
  const pickAlternative = (alternative: LawBrief) => {
    if (alternative.lawId && alternative.effectiveDate) {
      onAsk({
        query: ask.query,
        lawRef: {
          lawId: alternative.lawId,
          effectiveDate: alternative.effectiveDate,
        },
      });
    }
  };

  return (
    <ResultBody
      response={response}
      revision={revision}
      header={
        <LawHeaderView
          law={law}
          alternatives={response.alternatives ?? []}
          onPickAlternative={pickAlternative}
        />
      }
      renderUnmetAction={renderUnmetAction}
      afterCards={({ sources, selected, onCite }) => (
        <>
          {response.answer?.action &&
            questions.length > 0 &&
            setId &&
            lawId &&
            effectiveDate && (
              <ActionSituation
                law={{ lawId, effectiveDate, title }}
                questions={questions}
                setId={setId}
                sources={sources}
                selected={selected}
                onCite={onCite}
                onRevised={setRevision}
                onAsk={onAsk}
              />
            )}
          {!hasPersonalized(response) && lawId && effectiveDate && title && (
            <button
              type="button"
              onClick={() =>
                onAsk({
                  query: personalizedQuery(title),
                  lawRef: { lawId, effectiveDate },
                })
              }
              className="self-start rounded-md border border-navy px-3 py-1.5 text-[13px] font-bold text-navy"
            >
              내 영향·대응 방안 보기
            </button>
          )}
        </>
      )}
      footer={<FollowUpBar law={law} onAsk={onAsk} />}
    />
  );
}

/** 응답에 내 영향·대응 방안(답 또는 미충족 안내)이 이미 있는가 — 있으면 보기 버튼을 두지 않는다. */
function hasPersonalized(response: AnalysisResponse): boolean {
  return Boolean(
    response.answer?.impact ||
    response.answer?.action ||
    response.unmet?.impact ||
    response.unmet?.action,
  );
}
