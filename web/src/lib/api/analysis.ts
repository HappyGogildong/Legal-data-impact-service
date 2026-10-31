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

/** 상황 답으로 대응 방안만 다시 만든다(D71) — 로그인 필요. 답은 서버가 다시 만드는 데에만 쓰고 DB 에 저장하지 않는다. setId 는 화면이 보여 준 질문 묶음. analysisId 를 보내면 서버가 그 분석의 세션 항목에 재생성본·답을 붙여 두고(같은 법령·같은 setId 일 때만), 사용자가 "이력에 저장"을 누를 때 함께 저장한다(D73). */
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
