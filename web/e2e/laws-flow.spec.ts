import { expect, test } from "@playwright/test";

test("홈 곧 시행 → S06 기간 → S05 상세 → 쉬운 요약(기본)·칩 → 조문", async ({
  page,
}) => {
  await page.goto("/");
  const upcoming = page.getByRole("region", { name: "곧 시행되는 법령" });
  await expect(upcoming.getByRole("link", { name: "주택법" })).toBeVisible();
  await expect(upcoming.getByText("D-9")).toBeVisible();
  await upcoming.getByRole("link", { name: "전체 보기" }).click();

  await expect(page).toHaveURL(/\/laws$/);
  await expect(
    page.getByRole("link", { name: "근로기준법 시행령" }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "소득세법" })).toHaveCount(0);
  await page.getByRole("link", { name: "3개월" }).click();
  await expect(page).toHaveURL(/range=quarter/);
  await expect(page.getByRole("link", { name: "소득세법" })).toBeVisible();

  await page.getByRole("link", { name: "주택법" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  await expect(
    page.getByText("주택 사업계획 심의를 한 번에 묶어 절차가 짧아져요."),
  ).toBeVisible();
  await page.getByRole("link", { name: "제18조" }).click();
  await expect(page).toHaveURL(/#art-18$/);
  await expect(page.getByText("현행", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "쉬운 요약 보기" }),
  ).toHaveCount(0);
});

test("없는 시행일·잘못된 날짜 → 찾는 페이지가 없어요", async ({ page }) => {
  await page.goto("/laws/001809/2099-01-01");
  await expect(page.getByText("찾는 페이지가 없어요")).toBeVisible();
  await page.goto("/laws/001809/2026-13-01");
  await expect(page.getByText("찾는 페이지가 없어요")).toBeVisible();
});

test("/laws/{lawId} → 가장 이른 시행예정본으로 이동", async ({ page }) => {
  await page.goto("/laws/001809");
  await expect(page).toHaveURL(/\/laws\/001809\/\d{4}-\d{2}-\d{2}$/);
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
});
