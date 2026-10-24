"use client";

import { useState } from "react";
import { useIsDesktop } from "@/hooks/use-browser";
import type { AnalysisResponse, LawBrief, Unmet } from "@/lib/api/types";
import { presentDimensions, sourceIndex } from "@/lib/analysis-view";
import type { AskRequest } from "@/lib/ask-store";
import { DimensionCard } from "./dimension-card";
import { DiscoveryNotice } from "./discovery-notice";
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
  if (!law) return <DiscoveryNotice query={ask.query} />;

  const sources = sourceIndex(response);
  const selectedSource = selected ? (sources.get(selected) ?? null) : null;
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
              result={response.answer?.[dimension]}
              unmet={response.unmet?.[dimension]}
              sources={sources}
              selected={selected}
              onCite={setSelected}
              defaultOpen={
                dimension === "summary" || (isDesktop && dimension === "diff")
              }
              renderUnmetAction={renderUnmetAction}
            />
          ))}
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
