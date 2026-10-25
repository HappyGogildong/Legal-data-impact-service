import { api } from "./client";
import { expectOk } from "./errors";

/** S05 조회 신호(D66) — 횟수만 +1. 본문 없음, CSRF 예외(익명은 XSRF 쿠키가 없다). */
export async function recordLawView(
  lawId: string,
  effectiveDate: string,
): Promise<void> {
  expectOk(
    await api.POST("/api/v1/laws/{lawId}/{effectiveDate}/views", {
      params: { path: { lawId, effectiveDate } },
    }),
  );
}
