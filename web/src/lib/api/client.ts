import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const prefix = `${name}=`;
  const found = document.cookie
    .split("; ")
    .find((part) => part.startsWith(prefix));
  return found ? decodeURIComponent(found.slice(prefix.length)) : null;
}

/**
 * 상태 변경 요청에 CSRF 토큰 — 백엔드 csrf.spa(): XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로.
 * rewrites 로 출처가 하나라 프런트 JS 가 쿠키를 읽을 수 있다(frontend-stack §2).
 */
export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) return request;
    const token = readCookie("XSRF-TOKEN");
    if (token) request.headers.set("X-XSRF-TOKEN", token);
    return request;
  },
};

/**
 * 브라우저 전용 클라이언트 — 같은 출처의 /api/* 를 부르고 Next rewrites 가 백엔드로 넘긴다.
 * fetch 는 호출 시점에 찾는다(테스트의 stub 이 먹도록).
 */
export const api = createClient<paths>({
  baseUrl: typeof window === "undefined" ? "" : window.location.origin,
  credentials: "same-origin",
  fetch: (request) => globalThis.fetch(request),
});
api.use(csrfMiddleware);
