import { expect, test } from "@playwright/test";
import { resolvedResponse } from "../src/test/fixtures";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const QUERY = "주택법 바뀌면 뭐가 달라져?";

test("S02 이력에 저장 → 상세(저장 당시 배너) → 목록 → 삭제 → 빈 상태", async ({
  page,
}) => {
  const saved = new Map<number, unknown>();
  await page.route("**/api/v1/account", (route) =>
    route.fulfill({ json: ACCOUNT }),
  );
  await page.route("**/api/v1/analyses", (route) =>
    route.fulfill({
      json: { ...resolvedResponse, unmet: {}, analysisId: "a-1" },
    }),
  );
  await page.route("**/api/v1/history", async (route) => {
    if (route.request().method() === "POST") {
      saved.set(7, {
        id: 7,
        query: QUERY,
        lawId: "001809",
        lawTitle: "주택법",
        effectiveDate: resolvedResponse.law!.effectiveDate,
        savedAt: "2026-10-30T03:00:00Z",
        lawUpdated: false,
      });
      await route.fulfill({ status: 201, json: { id: 7 } });
    } else {
      await route.fulfill({ json: { items: [...saved.values()] } });
    }
  });
  await page.route("**/api/v1/history/7", async (route) => {
    if (route.request().method() === "DELETE") {
      saved.delete(7);
      await route.fulfill({ status: 204, body: "" });
    } else {
      await route.fulfill({
        json: {
          ...(saved.get(7) as object),
          body: { ...resolvedResponse, unmet: {} },
          answers: [],
        },
      });
    }
  });

  await page.goto("/");
  await page.getByRole("textbox", { name: "질문" }).fill(QUERY);
  await page.getByRole("button", { name: "질문 보내기" }).click();
  await page.getByRole("button", { name: "이력에 저장" }).click();
  await expect(page.getByText(/저장했어요/)).toBeVisible();

  await page.getByRole("link", { name: "이력 보기" }).click();
  await expect(
    page.getByText("저장 당시(2026-10-30) 기준이에요"),
  ).toBeVisible();

  await page.goto("/me/history");
  await expect(page.getByRole("link", { name: QUERY })).toBeVisible();
  await page.getByRole("button", { name: `${QUERY} 이력 삭제` }).click();
  await page.getByRole("button", { name: "삭제" }).click();
  await expect(
    page.getByText("결과 화면에서 '이력에 저장'을 누르면 여기에 모여요."),
  ).toBeVisible();
});
