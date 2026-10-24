"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useHydrated } from "@/hooks/use-browser";
import { analyze } from "@/lib/api/analysis";
import { askKey, setAsk, useAsk, type AskRequest } from "@/lib/ask-store";
import { AnalysisProgress } from "./analysis-progress";
import { ErrorCard } from "./error-card";
import { ResolutionView } from "./resolution-view";
import { ResultView } from "./result-view";

/** /ask — 탭에 저장된 질문을 분석해 S02(해소 성공)·S03(해소 실패)·오류로 가른다. 질문이 없으면 홈으로. */
export function AskScreen() {
  const router = useRouter();
  const hydrated = useHydrated();
  const ask = useAsk();
  const key = ask ? askKey(ask) : null;
  const analysis = useQuery({
    queryKey: ["analysis", key],
    queryFn: () => analyze(ask!),
    enabled: ask !== null,
    staleTime: Infinity, // 같은 질문·법령은 같은 답(D51) — 뒤로 가기·다시 시행일 전환이 캐시로
  });

  useEffect(() => {
    if (hydrated && ask === null) router.replace("/");
  }, [hydrated, ask, router]);

  const reask = (next: AskRequest) => {
    setAsk(next);
    window.scrollTo({ top: 0 });
  };

  if (!ask) return null;
  if (analysis.isPending) return <AnalysisProgress key={key} />;
  if (analysis.isError)
    return (
      <ErrorCard error={analysis.error} onRetry={() => analysis.refetch()} />
    );

  const response = analysis.data;
  if (response.resolution !== "RESOLVED") {
    return (
      <ResolutionView
        response={response}
        query={ask.query}
        onPick={(lawRef) => reask({ query: ask.query, lawRef })}
      />
    );
  }
  return <ResultView key={key} response={response} ask={ask} onAsk={reask} />;
}
