import { expect, test } from "@playwright/test";
import { resolvedResponse } from "../src/test/fixtures";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const CONSENT = {
  policyVersion: "2026-12",
  consentedAt: "2026-10-24T01:00:00Z",
  upToDate: true,
};

test("결과 → 내 영향 '로그인하기' → (OAuth) → 동의 → 프로필 → 결과로 복귀", async ({
  page,
}) => {
  let signedIn = false;
  const analyses: unknown[] = [];
  await page.route("**/api/v1/analyses", async (route) => {
    analyses.push(route.request().postDataJSON());
    await route.fulfill({ json: resolvedResponse });
  });
  await page.route("**/api/v1/account", (route) =>
    signedIn
      ? route.fulfill({ json: ACCOUNT })
      : route.fulfill({ status: 401, json: { error: "unauthenticated" } }),
  );
  await page.route("**/api/v1/consents", (route) =>
    route.fulfill({ json: {} }),
  );
  await page.route("**/api/v1/consents/profile", (route) =>
    route.fulfill({ json: CONSENT }),
  );
  await page.route("**/api/v1/profile", (route) =>
    route.request().method() === "PUT"
      ? route.fulfill({
          json: { housingType: "전세", updatedAt: "2026-10-24T05:02:00Z" },
        })
      : route.fulfill({ status: 404, body: "" }),
  );

  await page.goto("/");
  await page
    .getByRole("textbox", { name: "질문" })
    .fill("주택법 바뀌면 전세 어떻게 돼?");
  await page.getByRole("button", { name: "질문 보내기" }).click();
  await page.getByRole("button", { name: /내 영향/ }).click();
  await page.getByRole("button", { name: "로그인하기" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("link", { name: "카카오로 계속하기" }),
  ).toHaveAttribute("href", "/oauth2/authorization/kakao");

  // 소셜 로그인 성공을 흉내 — 백엔드는 성공 시 {FRONTEND_URL}/login/done 으로 보낸다(계획 1)
  signedIn = true;
  await page.goto("/login/done");
  await expect(page).toHaveURL(/\/consent$/);
  await page.getByRole("checkbox", { name: /만 14세 이상입니다/ }).check();
  await page
    .getByRole("checkbox", { name: /프로필 수집·이용에 동의합니다/ })
    .check();
  await page.getByRole("button", { name: "동의하고 프로필 입력" }).click();

  await expect(page).toHaveURL(/\/profile$/);
  await page.getByRole("radio", { name: "전세" }).check({ force: true });
  await page.getByRole("button", { name: "저장하고 돌아가기" }).click();

  await expect(page).toHaveURL(/\/ask$/);
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  await expect.poll(() => analyses.length).toBe(2); // 돌아온 뒤 다시 분석(전체 이동으로 캐시가 새로 시작)
});

test("익명이 '내 정보'를 누르면 로그인으로", async ({ page }, testInfo) => {
  await page.route("**/api/v1/account", (route) =>
    route.fulfill({ status: 401, json: { error: "unauthenticated" } }),
  );
  await page.goto("/");
  const nav = testInfo.project.name === "desktop" ? "주 메뉴" : "하단 메뉴";
  await page
    .getByRole("navigation", { name: nav })
    .getByRole("link", { name: "내 정보" })
    .click();
  await expect(page).toHaveURL(/\/login$/);
});
