import { defineConfig, devices } from "@playwright/test";

const MOCK_API = "http://localhost:3199";

/**
 * 주요 흐름 E2E — 백엔드 없이. 브라우저 조회는 page.route 로, 서버 컴포넌트 조회(법령 화면)는
 * 모의 API 서버(e2e/mock-api/server.mjs)로 모의한다(frontend-stack §5).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  use: { baseURL: "http://localhost:3100", trace: "retain-on-failure" },
  projects: [
    { name: "mobile", use: { ...devices["Pixel 7"] } },
    {
      name: "desktop",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1280, height: 900 },
      },
    },
  ],
  webServer: [
    {
      command: "node e2e/mock-api/server.mjs",
      url: `${MOCK_API}/health`,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: "pnpm build && pnpm start -p 3100",
      url: "http://localhost:3100",
      env: { LIA_API_ORIGIN: MOCK_API },
      reuseExistingServer: !process.env.CI,
      timeout: 240_000,
    },
  ],
});
