import type { AskRequest } from "@/lib/ask-store";
import { api } from "./client";
import { unwrap } from "./errors";
import type {
  ActionRevision,
  AnalysisResponse,
  DataStatus,
  LawRefInput,
} from "./types";

export async function analyze(
  request: AskRequest,
  signal?: AbortSignal,
): Promise<AnalysisResponse> {
  return unwrap(
    await api.POST("/api/v1/analyses", {
      body: {
        query: request.query,
        lawRef: request.lawRef,
        profileSearch: request.profileSearch,
      },
      signal,
    }),
  );
}

export async function fetchDataStatus(): Promise<DataStatus> {
  return unwrap(await api.GET("/api/v1/data-status"));
}

/** 상황 답으로 대응 방안만 다시 만든다(D71) — 로그인 필요, 답은 이 요청에만 쓰이고 저장되지 않는다. setId 는 화면이 보여 준 질문 묶음. analysisId — 있으면 서버가 그 결과에 재생성본·답을 붙여 이력 저장에 쓴다(D73). */
export async function reviseAction(
  lawRef: LawRefInput,
  setId: string,
  answers: { questionId: string; answer: string }[],
  analysisId?: string,
): Promise<ActionRevision> {
  return unwrap(
    await api.POST("/api/v1/analyses/action-revisions", {
      body: {
        lawRef,
        setId,
        answers,
        ...(analysisId ? { analysisId } : {}),
      },
    }),
  );
}
