import { expect, test, type Page } from "@playwright/test";
import { ambiguousResponse, resolvedResponse } from "../src/test/fixtures";

async function mockAnalyses(page: Page, ...bodies: unknown[]) {
  const queue = [...bodies];
  const requests: unknown[] = [];
  await page.route("**/api/v1/analyses", async (route) => {
    requests.push(route.request().postDataJSON());
    await route.fulfill({ json: queue.shift() ?? resolvedResponse });
  });
  return requests;
}

test("익명 질문 → 결과(S02) → 인용 근거 → 후속 질문", async ({
  page,
}, testInfo) => {
  const requests = await mockAnalyses(page, resolvedResponse, resolvedResponse);
  await page.goto("/");
  await page
    .getByRole("textbox", { name: "질문" })
    .fill("주택법 바뀌면 뭐가 달라져?");
  await page.getByRole("button", { name: "질문 보내기" }).click();

  await expect(page).toHaveURL(/\/ask$/); // 질문은 URL 에 없다
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "주택법 제18조" }).first().click();
  const evidence =
    testInfo.project.name === "desktop"
      ? page.getByRole("region", { name: "근거 조문" })
      : page.getByRole("dialog", { name: "근거 조문" });
  await expect(evidence.getByText("통합하여")).toBeVisible();
  if (testInfo.project.name !== "desktop") await page.keyboard.press("Escape");

  await page
    .getByRole("textbox", { name: "후속 질문" })
    .fill("언제부터 적용돼?");
  await page.getByRole("button", { name: "보내기", exact: true }).click();
  await expect.poll(() => requests.length).toBe(2);
  expect(requests[1]).toEqual({
    query: "언제부터 적용돼?",
    lawRef: { lawId: "001809", effectiveDate: "2026-08-04" },
  });
});

test("모호함(S03) → 후보 선택 → 결과", async ({ page }) => {
  const requests = await mockAnalyses(
    page,
    ambiguousResponse,
    resolvedResponse,
  );
  await page.goto("/");
  await page.getByRole("textbox", { name: "질문" }).fill("자본시장법");
  await page.getByRole("button", { name: "질문 보내기" }).click();

  await page.getByRole("button", { name: /2026-11-13 시행/ }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  expect(requests[1]).toEqual({
    query: "자본시장법",
    lawRef: { lawId: "010513", effectiveDate: "2026-11-13" },
  });
});

test("안내(S15) — 데이터 현황", async ({ page }) => {
  await page.route("**/api/v1/data-status", (route) =>
    route.fulfill({
      json: { lawsUpdatedAt: "2026-10-22T19:12:31Z", pendingLawCount: 912 },
    }),
  );
  await page.goto("/info");
  await expect(page.getByText("912건")).toBeVisible();
});
