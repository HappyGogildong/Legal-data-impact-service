import type { CitedSource, ImpactResult } from "@/lib/api/types";
import { changedArticles, type Dimension } from "@/lib/analysis-view";
import { ChangedArticles } from "./changed-articles";
import { CiteChip } from "./cite-chip";

type ImpactBodyProps = {
  dimension: Dimension;
  result: ImpactResult;
  sources: Map<string, CitedSource>;
  selected: string | null;
  onCite: (sourceId: string) => void;
};

/** 한 차원의 본문 — 요약 문단·주장(+인용 칩)·바뀐 조문·영향·할 일·불확실성. */
export function ImpactBody({
  dimension,
  result,
  sources,
  selected,
  onCite,
}: ImpactBodyProps) {
  const chips = (ids: string[] | undefined) =>
    (ids ?? []).map((id) => (
      <CiteChip
        key={id}
        sourceId={id}
        source={sources.get(id)}
        active={selected === id}
        onCite={onCite}
      />
    ));

  return (
    <div className="flex flex-col gap-3 text-sm">
      {result.summary && <p>{result.summary}</p>}
      {(result.claims ?? []).length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {(result.claims ?? []).map((claim, i) => (
            <li
              key={i}
              data-highlighted={
                selected !== null && (claim.citations ?? []).includes(selected)
              }
              className="rounded-sm px-1 data-[highlighted=true]:bg-chip"
            >
              {claim.statement} {chips(claim.citations)}
            </li>
          ))}
        </ul>
      )}
      {dimension === "diff" && (
        <ChangedArticles articles={changedArticles(result, sources)} />
      )}
      {(result.impacts ?? []).length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {(result.impacts ?? []).map((impact, i) => (
            <li key={i}>
              <b>{impact.aspect}</b> {impact.detail} {chips(impact.citations)}
            </li>
          ))}
        </ul>
      )}
      {(result.actions ?? []).length > 0 && (
        <ol className="flex flex-col gap-2">
          {(result.actions ?? []).map((action, i) => (
            <li key={i} className="flex gap-2.5">
              <span className="flex size-[22px] flex-none items-center justify-center rounded-full bg-navy text-xs font-bold text-white">
                {i + 1}
              </span>
              <span>
                {action.deadline && (
                  <span className="block text-[11.5px] font-bold text-amend">
                    {action.deadline}
                  </span>
                )}
                {action.what} {chips(action.basis)}
              </span>
            </li>
          ))}
        </ol>
      )}
      {(result.uncertainties ?? []).length > 0 && (
        <div className="text-[12.5px] text-amend">
          <p className="font-bold">⚠ 불확실한 부분</p>
          <ul className="list-disc pl-4">
            {(result.uncertainties ?? []).map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
