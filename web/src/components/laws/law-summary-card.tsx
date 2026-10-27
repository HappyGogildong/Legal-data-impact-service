import type { LawSummary } from "@/lib/api/types";
import { citationChip } from "@/lib/law-view";

/** S05 쉬운 요약(D67) — 미리 만든 그라운딩 요약. 칩은 같은 페이지의 그 조문으로(펼침·스크롤은 바뀐 조문 목록이 한다). */
export function LawSummaryCard({
  summary,
  labels,
}: {
  summary: LawSummary;
  labels: Map<string, string>;
}) {
  const claims = summary.claims ?? [];
  const uncertainties = summary.uncertainties ?? [];
  return (
    <div className="flex flex-col gap-2 text-sm">
      {summary.text && <p className="whitespace-pre-line">{summary.text}</p>}
      {claims.length > 0 && (
        <div>
          <h3 className="mb-1 text-xs font-bold text-muted-foreground">
            주요 내용
          </h3>
          <ul className="flex flex-col gap-1.5">
            {claims.map((claim, i) => (
              <li key={i}>
                <span>{claim.statement}</span>
                {(claim.citations ?? []).map((sourceId) => {
                  const chip = citationChip(sourceId, labels);
                  return chip.href ? (
                    <a
                      key={sourceId}
                      href={chip.href}
                      className="ml-1 rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold text-navy"
                    >
                      {chip.text}
                    </a>
                  ) : (
                    <span
                      key={sourceId}
                      className="ml-1 rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold text-navy"
                    >
                      {chip.text}
                    </span>
                  );
                })}
              </li>
            ))}
          </ul>
        </div>
      )}
      {uncertainties.length > 0 && (
        <div>
          <h3 className="mb-1 text-xs font-bold text-muted-foreground">
            불확실한 점
          </h3>
          <ul className="list-disc pl-4">
            {uncertainties.map((text, i) => (
              <li key={i}>{text}</li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-xs text-muted-foreground">
        근거 조문으로 만든 AI 요약이에요. 법률 자문이 아니에요.
      </p>
    </div>
  );
}
