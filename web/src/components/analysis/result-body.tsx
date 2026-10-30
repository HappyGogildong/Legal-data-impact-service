"use client";

import { useState } from "react";
import { useIsDesktop } from "@/hooks/use-browser";
import type {
  ActionRevision,
  AnalysisResponse,
  CitedSource,
  Unmet,
} from "@/lib/api/types";
import { presentDimensions, sourceIndex } from "@/lib/analysis-view";
import { DimensionCard } from "./dimension-card";
import { LawInfoPanel } from "./law-info-panel";
import { SourcePanel } from "./source-panel";
import { SourceSheet } from "./source-sheet";

export type CiteContext = {
  sources: Map<string, CitedSource>;
  selected: string | null;
  onCite: (sourceId: string) => void;
};

type ResultBodyProps = {
  response: AnalysisResponse;
  revision?: ActionRevision;
  header: React.ReactNode;
  renderUnmetAction?: (unmet: Unmet | undefined) => React.ReactNode;
  afterCards?: (context: CiteContext) => React.ReactNode;
  footer?: React.ReactNode;
};

/**
 * 분석 결과 본문 — S02(실시간)와 이력 상세(읽기 전용, D73)가 함께 쓴다. 모바일 한 열(접이식 카드·아래 시트),
 * 데스크톱 2단(본문 + 오른쪽 법령 정보/근거 패널). 인용을 고르면 패널·시트가 근거로 바뀌고 그 인용이 뒷받침하는
 * 문장을 옅게 강조한다(visual-design §3).
 */
export function ResultBody({
  response,
  revision,
  header,
  renderUnmetAction,
  afterCards,
  footer,
}: ResultBodyProps) {
  const isDesktop = useIsDesktop();
  const [selected, setSelected] = useState<string | null>(null);
  const law = response.law;
  if (!law) return null;

  const sources = sourceIndex(response);
  for (const source of revision?.sources ?? []) {
    if (source.sourceId) sources.set(source.sourceId, source);
  }
  const selectedSource = selected ? (sources.get(selected) ?? null) : null;

  return (
    <div className="mx-auto max-w-[1120px] px-4 py-4 lg:py-6">
      {header}
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
          {afterCards?.({ sources, selected, onCite: setSelected })}
          {response.disclaimer && (
            <p className="text-[11px] text-muted-foreground">
              {response.disclaimer}
            </p>
          )}
          {!isDesktop && footer}
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
            {footer}
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
