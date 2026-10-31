import { api } from "./client";
import { expectOk, unwrap } from "./errors";
import type { HistoryDetail, HistoryItem } from "./types";

/** 이력 목록·상세 쿼리 키 접두 — 상세는 [...HISTORY_KEY, id]. */
export const HISTORY_KEY = ["history"] as const;

/** 서버가 세션에 보관한 결과로 스냅샷 저장(D73). 이미 저장했으면 같은 id(그 기록의 스냅샷을 지금 결과로 바꾼다). 만료·한도는 409 ApiError(메시지를 화면에 그대로). */
export async function saveHistory(analysisId: string): Promise<{ id: number }> {
  const saved = unwrap(
    await api.POST("/api/v1/history", { body: { analysisId } }),
  );
  return { id: Number(saved.id) };
}

/** 저장 최근 순. */
export async function fetchHistory(): Promise<HistoryItem[]> {
  return unwrap(await api.GET("/api/v1/history")).items ?? [];
}

/** 없거나 남의 기록이면 null(404). */
export async function fetchHistoryDetail(
  id: number,
): Promise<HistoryDetail | null> {
  const result = await api.GET("/api/v1/history/{id}", {
    params: { path: { id } },
  });
  if (result.response.status === 404) return null;
  return unwrap(result);
}

/** 멱등 삭제. */
export async function deleteHistory(id: number): Promise<void> {
  expectOk(
    await api.DELETE("/api/v1/history/{id}", { params: { path: { id } } }),
  );
}
