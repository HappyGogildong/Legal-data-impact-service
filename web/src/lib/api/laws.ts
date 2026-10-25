import { cacheLife } from "next/cache";
import createClient from "openapi-fetch";
import { apiOrigin } from "@/lib/api-origin";
import { unwrap } from "./errors";
import type { paths } from "./schema";
import type { DataStatus, LawDetail, LawListPage, LawVersions } from "./types";

/**
 * 서버 렌더링 전용 조회(홈 목록·S05·S06, frontend-stack §5) — Next 서버가 백엔드를 직접 부른다. 공개 데이터라 쿠키를 싣지 않는다.
 * 적재는 하루 한 번이라 몇 분 캐시한다. 브라우저 컴포넌트에서 가져오지 않는다(브라우저는 law-list.ts).
 */
function serverApi() {
  return createClient<paths>({ baseUrl: apiOrigin() });
}

export type LawListQuery = {
  from?: string;
  to?: string;
  cursor?: string;
  limit?: number;
};

export async function fetchUpcomingLaws(
  query: LawListQuery,
): Promise<LawListPage> {
  "use cache";
  cacheLife("minutes");
  return unwrap(await serverApi().GET("/api/v1/laws", { params: { query } }));
}

/** 없는 법령이면 null. */
export async function fetchLawVersions(
  lawId: string,
): Promise<LawVersions | null> {
  "use cache";
  cacheLife("minutes");
  const result = await serverApi().GET("/api/v1/laws/{lawId}", {
    params: { path: { lawId } },
  });
  if (result.response.status === 404) return null;
  return unwrap(result);
}

/** 없는 버전·날짜 형식 오류(404·400)면 null — 화면은 not-found. */
export async function fetchLawDetail(
  lawId: string,
  effectiveDate: string,
): Promise<LawDetail | null> {
  "use cache";
  cacheLife("minutes");
  const result = await serverApi().GET("/api/v1/laws/{lawId}/{effectiveDate}", {
    params: { path: { lawId, effectiveDate } },
  });
  if (result.response.status === 404 || result.response.status === 400) {
    return null;
  }
  return unwrap(result);
}

/** 상세 하단의 갱신 시각 — 실패해도 화면은 그린다(null). */
export async function fetchDataStatusForPage(): Promise<DataStatus | null> {
  "use cache";
  cacheLife("minutes");
  try {
    return unwrap(await serverApi().GET("/api/v1/data-status"));
  } catch {
    return null;
  }
}
