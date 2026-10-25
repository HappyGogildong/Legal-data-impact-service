import type { NextConfig } from "next";
import { apiOrigin } from "./src/lib/api-origin";

/**
 * 백엔드(core) 주소 — 로컬 기본 8080, 운영은 Vercel 환경변수 https://api.<도메인>(D64).
 * rewrites 가 빌드 결과에 고정되므로 빌드 시점에 읽힌다 — 운영 빌드에서 비어 있으면 로컬 주소로 조용히 배포되지 않게 실패시킨다.
 */
if (
  process.env.VERCEL_ENV === "production" &&
  !process.env.LIA_API_ORIGIN?.replace(/\/$/, "")
) {
  throw new Error("LIA_API_ORIGIN 이 필요합니다(운영 빌드) — rewrites 목적지");
}
const origin = apiOrigin();

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  experimental: {
    proxyTimeout: 120_000, // 분석은 수십 초 — rewrites 프록시 기본 30초보다 길다(Vercel 상한과 같게)
  },
  /** 브라우저 입장에서 출처를 하나로 — 세션 쿠키·CSRF 그대로(frontend-stack §2). */
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${origin}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${origin}/oauth2/:path*` },
      {
        source: "/login/oauth2/:path*",
        destination: `${origin}/login/oauth2/:path*`,
      },
      { source: "/logout", destination: `${origin}/logout` },
    ];
  },
};

export default nextConfig;
