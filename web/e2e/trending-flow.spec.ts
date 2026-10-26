import { expect, test } from "@playwright/test";

test("홈 자주 찾는 → S06 자주 찾는 탭(구간) → S05", async ({ page }) => {
  await page.goto("/");
  const trending = page.getByRole("region", { name: "자주 찾는 법령" });
  await expect(trending.getByRole("link", { name: "주택법" })).toBeVisible();
  await expect(trending.getByText("▲2")).toBeVisible();
  await expect(trending.getByText("NEW")).toBeVisible();
  await trending.getByRole("link", { name: "전체 보기" }).click();

  await expect(page).toHaveURL(/\/laws\?tab=trending$/);
  await expect(page.getByRole("link", { name: "자주 찾는" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await expect(
    page.getByRole("link", { name: "근로기준법 시행령" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "오늘" }).click();
  await expect(page).toHaveURL(/window=day/);
  await expect(
    page.getByRole("link", { name: "근로기준법 시행령" }),
  ).toHaveCount(0);

  await page.getByRole("link", { name: "주택법" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
});

test("S05 를 열면 조회 신호를 한 번만 보낸다(새로고침해도)", async ({
  page,
}) => {
  const views: string[] = [];
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/views"))
      views.push(request.url());
  });

  await page.goto("/laws/001809"); // 가장 이른 시행예정본으로 이동
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  await expect.poll(() => views.length).toBe(1);
  await page.reload();
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  await page.waitForTimeout(500);
  expect(views).toHaveLength(1);
});
