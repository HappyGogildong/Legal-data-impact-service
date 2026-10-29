"use client";

import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import type {
  CitedSource,
  ImpactResult,
  StepNote,
  Unmet,
} from "@/lib/api/types";
import {
  DIMENSION_TITLES,
  cardStatus,
  type Dimension,
} from "@/lib/analysis-view";
import { cn } from "@/lib/utils";
import { ImpactBody } from "./impact-body";
import { UnmetCard } from "./unmet-card";

type DimensionCardProps = {
  dimension: Dimension;
  result?: ImpactResult;
  unmet?: Unmet;
  sources: Map<string, CitedSource>;
  selected: string | null;
  onCite: (sourceId: string) => void;
  defaultOpen: boolean;
  /** 미충족 안내 아래의 다음 행동(로그인 흐름의 로그인·프로필 버튼). */
  renderUnmetAction?: (unmet: Unmet | undefined) => React.ReactNode;
  /** 대응 방안 카드의 단계별 "내 상황 반영" 메모(D71). */
  stepNotes?: StepNote[];
};

/** 접이식 차원 카드 — 그림자 없이 1px 테두리(visual-design §3). */
export function DimensionCard(props: DimensionCardProps) {
  const {
    dimension,
    result,
    unmet,
    sources,
    selected,
    onCite,
    defaultOpen,
    renderUnmetAction,
    stepNotes,
  } = props;
  const status = cardStatus(dimension, result, unmet, sources);
  return (
    <Collapsible
      defaultOpen={defaultOpen}
      className="rounded-lg border border-border bg-card"
    >
      <CollapsibleTrigger className="group flex w-full items-center justify-between px-3.5 py-3 text-left font-bold">
        <span>
          {DIMENSION_TITLES[dimension]}{" "}
          {status && (
            <small
              className={cn(
                "ml-1 font-normal",
                result ? "text-muted-foreground" : "text-amend",
              )}
            >
              {status}
            </small>
          )}
        </span>
        <span
          aria-hidden
          className="text-muted-foreground transition-transform group-data-[state=open]:rotate-180"
        >
          ▾
        </span>
      </CollapsibleTrigger>
      <CollapsibleContent className="px-3.5 pb-3.5">
        {result ? (
          <ImpactBody
            dimension={dimension}
            result={result}
            sources={sources}
            selected={selected}
            onCite={onCite}
            stepNotes={stepNotes}
          />
        ) : (
          <UnmetCard unmet={unmet}>{renderUnmetAction?.(unmet)}</UnmetCard>
        )}
      </CollapsibleContent>
    </Collapsible>
  );
}
