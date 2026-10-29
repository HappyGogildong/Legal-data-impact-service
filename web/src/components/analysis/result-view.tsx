"use client";

import { useState } from "react";
import { useIsDesktop } from "@/hooks/use-browser";
import type {
  ActionRevision,
  AnalysisResponse,
  LawBrief,
  Unmet,
} from "@/lib/api/types";
import { presentDimensions, sourceIndex } from "@/lib/analysis-view";
import type { AskRequest } from "@/lib/ask-store";
import { readSituation, situationKey } from "@/lib/situation-store";
import { personalizedQuery } from "@/components/laws/law-actions";
import { ActionSituation } from "./action-situation";
import { DimensionCard } from "./dimension-card";
import { FollowUpBar } from "./follow-up-bar";
import { LawHeaderView } from "./law-header-view";
import { LawInfoPanel } from "./law-info-panel";
import { SourcePanel } from "./source-panel";
import { SourceSheet } from "./source-sheet";

type ResultViewProps = {
  response: AnalysisResponse;
  ask: AskRequest;
  onAsk: (next: AskRequest) => void;
  renderUnmetAction?: (unmet: Unmet | undefined) => React.ReactNode;
};

/**
 * S02 분석 결과 — 모바일 한 열(접이식 카드·아래 시트), 데스크톱 2단(본문 + 오른쪽 법령 정보/근거 패널).
 * 인용을 고르면 패널·시트가 근거로 바뀌고 그 인용이 뒷받침하는 문장을 옅게 강조한다(visual-design §3).
 */
export function ResultView({
  response,
  ask,
  onAsk,
  renderUnmetAction,
}: ResultViewProps) {
  const isDesktop = useIsDesktop();
  const [selected, setSelected] = useState<string | null>(null);
  const law = response.law;
  const lawKey =
    law?.lawId && law.effectiveDate
      ? situationKey(law.lawId, law.effectiveDate)
      : null;
  // 상황 확인으로 다시 만든 대응 방안(D71) — 같은 탭에서 이 법령 버전으로 확인한 적이 있으면 이어서 보여 준다
  const [revision, setRevision] = useState<ActionRevision | undefined>(() =>
    lawKey
      ? readSituation(lawKey, response.situation?.setId).revision
      : undefined,
  );
  if (!law) return null; // 발견 응답은 AskScreen 이 S04 로 보낸다 — 여기 오는 응답엔 law 가 있다

  const { lawId, effectiveDate, title } = law;
  const sources = sourceIndex(response);
  for (const source of revision?.sources ?? []) {
    if (source.sourceId) sources.set(source.sourceId, source);
  }
  const selectedSource = selected ? (sources.get(selected) ?? null) : null;
  const questions = response.situation?.questions ?? [];
  const setId = response.situation?.setId;
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
  const followUp = <FollowUpBar law={law} onAsk={onAsk} />;

  return (
    <div className="mx-auto max-w-[1120px] px-4 py-4 lg:py-6">
      <LawHeaderView
        law={law}
        alternatives={response.alternatives ?? []}
        onPickAlternative={pickAlternative}
      />
      <div className="mt-2.5 lg:grid lg:grid-cols-[1.7fr_1fr] lg:items-start lg:gap-4">
        <div className="flex flex-col gap-2.5">
          {presentDimensions(response).map((dimension) => (
            <DimensionCard
              key={dimension}
              dimension={dimension}
              result={
                dimension === "action"
                  ? (revision?.action ?? response.answer?.action)
                  : response.answer?.[dimension]
              }
              unmet={response.unmet?.[dimension]}
              sources={sources}
              selected={selected}
              onCite={setSelected}
              defaultOpen={
                dimension === "summary" || (isDesktop && dimension === "diff")
              }
              renderUnmetAction={renderUnmetAction}
              stepNotes={
                dimension === "action"
                  ? // 재생성본이면 그 메모만 — 기본 응답의 메모는 기본 단계 목록의 번호다
                    revision
                    ? (revision.stepNotes ?? [])
                    : response.situation?.stepNotes
                  : undefined
              }
            />
          ))}
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
                onCite={setSelected}
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
          {response.disclaimer && (
            <p className="text-[11px] text-muted-foreground">
              {response.disclaimer}
            </p>
          )}
          {!isDesktop && followUp}
        </div>
        {isDesktop && (
          <aside className="sticky top-4 flex max-h-[calc(100dvh-2rem)] flex-col gap-2.5 overflow-y-auto">
            {selectedSource ? (
              <SourcePanel
                source={selectedSource}
                onClose={() => setSelected(null)}
              />
            ) : (
              <LawInfoPanel law={law} />
            )}
            {followUp}
          </aside>
        )}
      </div>
      {!isDesktop && (
        <SourceSheet
          source={selectedSource}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
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
