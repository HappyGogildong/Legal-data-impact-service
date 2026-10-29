"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useHydrated } from "@/hooks/use-browser";
import { analyze } from "@/lib/api/analysis";
import {
  analysisQueryKey,
  askKey,
  setAsk,
  useAsk,
  type AskRequest,
} from "@/lib/ask-store";
import { AnalysisProgress } from "./analysis-progress";
import { DiscoveryView } from "./discovery-view";
import { ErrorCard } from "./error-card";
import { PersonalizeAction } from "./personalize-action";
import { ResolutionView } from "./resolution-view";
import { ResultView } from "./result-view";

/** /ask — 탭에 저장된 질문을 분석해 S02(해소 성공)·S03(해소 실패)·S04(법령 발견)·오류로 가른다. 질문이 없으면 홈으로. */
export function AskScreen() {
  const router = useRouter();
  const hydrated = useHydrated();
  const ask = useAsk();
  const key = ask ? askKey(ask) : null;
  const analysis = useQuery({
    queryKey: analysisQueryKey(ask),
    // signal 을 넘기지 않는다 — 동기 백엔드·답 캐시 없음이라, 화면을 떠날 때 중단하면 돌아왔을 때 유료 분석이 한 번 더 나간다
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
  // 오류 뒤 다시 시도하는 동안에도 진행 표시만 — 오류 카드가 남아 다시 눌리면 유료 분석이 겹친다
  if (analysis.isPending || analysis.isFetching)
    return <AnalysisProgress key={key} />;
  if (analysis.isError)
    return (
      <ErrorCard
        error={analysis.error}
        query={ask.query}
        onRetry={() => analysis.refetch()}
      />
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
  if (response.discovery) {
    return (
      <DiscoveryView discovery={response.discovery} ask={ask} onAsk={reask} />
    );
  }
  return (
    <ResultView
      key={key}
      response={response}
      ask={ask}
      onAsk={reask}
      renderUnmetAction={(unmet) => <PersonalizeAction unmet={unmet} />}
    />
  );
}
