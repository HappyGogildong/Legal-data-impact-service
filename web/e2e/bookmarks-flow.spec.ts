import { expect, test } from "@playwright/test";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };

test("S05 ☆ → 내 북마크에 보임 → 해제 → 되돌리기 자리 → 다시 열면 빈 목록", async ({
  page,
}) => {
  const saved = new Map<string, unknown>();
  await page.route("**/api/v1/account", (route) =>
    route.fulfill({ json: ACCOUNT }),
  );
  await page.route("**/api/v1/bookmarks", (route) =>
    route.fulfill({ json: { items: [...saved.values()] } }),
  );
  await page.route("**/api/v1/bookmarks/*", async (route) => {
    const lawId = new URL(route.request().url()).pathname.split("/").pop()!;
    if (route.request().method() === "PUT")
      saved.set(lawId, {
        lawId,
        title: "주택법",
        status: "시행예정",
        effectiveDate: "2026-11-08",
        bookmarkedAt: "2026-10-30T00:00:00Z",
      });
    else saved.delete(lawId);
    await route.fulfill({ status: 204, body: "" });
  });

  await page.goto("/laws/001809");
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  const star = page.getByRole("button", { name: "북마크" });
  await expect(star).toHaveAttribute("aria-pressed", "false");
  await star.click();
  await expect(star).toHaveAttribute("aria-pressed", "true");
  // 낙관적 갱신 — PUT 과 재조회가 끝나야 다시 활성화된다
  await expect(star).toBeEnabled();

  await page.goto("/me/bookmarks");
  await expect(page.getByRole("link", { name: "주택법" })).toBeVisible();
  await page.getByRole("button", { name: "주택법 북마크 해제" }).click();
  await expect(page.getByText("북마크를 해제했어요")).toBeVisible();
  await expect(page.getByRole("button", { name: "되돌리기" })).toBeVisible();

  await page.reload();
  await expect(
    page.getByText("관심 법령의 ☆를 누르면 여기에 모여요."),
  ).toBeVisible();
});
