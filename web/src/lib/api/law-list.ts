import { api } from "./client";
import { unwrap } from "./errors";
import type { LawListPage } from "./types";

/** S06 "더 보기" — 브라우저에서 다음 쪽(같은 출처 /api → rewrites). 첫 쪽은 서버 렌더링(laws.ts). */
export async function fetchLawPage(query: {
  from: string;
  to: string;
  cursor?: string;
  limit: number;
}): Promise<LawListPage> {
  return unwrap(await api.GET("/api/v1/laws", { params: { query } }));
}
