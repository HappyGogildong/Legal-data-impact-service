import { expect, test } from "@playwright/test";
import { resolvedResponse } from "../src/test/fixtures";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const ART = "LAW:001809@2026-11-03:art:18";

/** Layer B 응답 — 내 영향·대응 방안 모두 채움(D69). */
const personalizedResponse = {
  ...resolvedResponse,
  answer: {
    ...resolvedResponse.answer,
    impact: {
      command: "IMPACT",
      summary: "전세 세입자에게 점검 요청 권리가 생겨요.",
      claims: [
        { statement: "점검 요청 권리", citations: [ART], confidence: 0.8 },
      ],
      impacts: [
        {
          aspect: "주거",
          direction: "영향 있음",
          detail: "현장점검을 요청할 수 있어요.",
          citations: [ART],
        },
      ],
      actions: [],
      uncertainties: [],
      disclaimer: "참고용",
    },
    action: {
      command: "ACTION",
      claims: [],
      impacts: [],
      actions: [
        {
          what: "관리사무소에 점검 요청 가능 여부 확인",
          deadline: "2026-11-03",
          basis: [ART],
        },
      ],
      uncertainties: [],
      disclaimer: "참고용",
    },
  },
  unmet: {},
};

test("로그인 사용자: 결과의 '내 영향·대응 방안 보기' → 같은 법령으로 Layer B 요청 → 영향·할 일 표시", async ({
  page,
}) => {
  const queries: string[] = [];
  await page.route("**/api/v1/analyses", async (route) => {
    const body = route.request().postDataJSON() as { query: string };
    queries.push(body.query);
    await route.fulfill({
      json: body.query.includes("내 영향과 대응 방안")
        ? personalizedResponse
        : { ...resolvedResponse, unmet: {} },
    });
  });
  await page.route("**/api/v1/account", (route) =>
    route.fulfill({ json: ACCOUNT }),
  );

  await page.goto("/");
  await page
    .getByRole("textbox", { name: "질문" })
    .fill("주택법 바뀌면 뭐가 달라져?");
  await page.getByRole("button", { name: "질문 보내기" }).click();

  await page.getByRole("button", { name: "내 영향·대응 방안 보기" }).click();

  // 카드는 접혀 있다 — 열어서 본다
  await page.getByRole("button", { name: /^내 영향/ }).click();
  await page.getByRole("button", { name: /^대응 방안/ }).click();
  await expect(page.getByText("현장점검을 요청할 수 있어요.")).toBeVisible();
  await expect(
    page.getByText("관리사무소에 점검 요청 가능 여부 확인"),
  ).toBeVisible();
  expect(queries).toHaveLength(2);
  expect(queries[1]).toBe("주택법 내 영향과 대응 방안");
});
