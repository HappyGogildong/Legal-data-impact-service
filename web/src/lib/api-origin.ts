/**
 * 백엔드(core) 주소 — rewrites(빌드 시점, next.config.ts)와 서버 렌더링 조회(실행 시점, lib/api/laws.ts) 공용.
 * 끝의 / 는 뗀다. 비어 있으면 로컬 기본값(frontend-stack §5 환경변수).
 */
export function apiOrigin(): string {
  return (
    process.env.LIA_API_ORIGIN?.replace(/\/$/, "") || "http://localhost:8080"
  );
}
