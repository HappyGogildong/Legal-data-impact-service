import { expect, test } from "@playwright/test";
import { ARTICLE_18, resolvedResponse } from "../src/test/fixtures";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const QUESTION = "계약을 2026-11-01 이전에 맺었나요?";
const SET_ID = "0123456789abcdef";
const STEP_NOTE = "시행일 이전에 맺은 계약이라 이전 기준이 적용돼요";

/** Layer B 응답 — 대응 방안이 채워지고 상황 질문이 함께 온다(D71). */
const personalizedResponse = {
  ...resolvedResponse,
  answer: {
    ...resolvedResponse.answer,
    action: {
      command: "ACTION",
      claims: [],
      impacts: [],
      actions: [
        {
          what: "관리사무소에 점검 요청 가능 여부 확인",
          deadline: "2026-11-03",
          basis: [ARTICLE_18],
        },
      ],
      uncertainties: [],
      disclaimer: "참고용",
    },
  },
  situation: {
    setId: SET_ID,
    questions: [
      {
        id: "q1",
        text: QUESTION,
        basis: ARTICLE_18,
        answerType: "YES_NO",
        options: [],
      },
    ],
    stepNotes: [],
  },
  unmet: {},
};

/** 대응 방안 재생성 응답 — 단계 설명이 딸린다. */
const revision = {
  action: {
    command: "ACTION",
    claims: [],
    impacts: [],
    actions: [
      {
        what: "갱신 요구 의사 전달",
        deadline: "2026-09 ~ 2027-01",
        basis: [ARTICLE_18],
      },
    ],
    uncertainties: [],
    disclaimer: "참고용",
  },
  stepNotes: [{ index: 0, note: STEP_NOTE }],
};

test("로그인 사용자: 상황 질문에 답하고 '대응 방안 확인하기' → 답만 보내 대응 방안이 '내 상황 반영'으로 바뀐다", async ({
  page,
}) => {
  const revisionBodies: unknown[] = [];
  await page.route("**/api/v1/analyses", async (route) => {
    const body = route.request().postDataJSON() as { query: string };
    await route.fulfill({
      json: body.query.includes("내 영향과 대응 방안")
        ? personalizedResponse
        : { ...resolvedResponse, unmet: {} },
    });
  });
  await page.route("**/api/v1/analyses/action-revisions", async (route) => {
    revisionBodies.push(route.request().postDataJSON());
    await route.fulfill({ json: revision });
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

  // 카드는 접혀 있다 — 대응 방안을 열면 상황 확인 블록이 보인다
  await page.getByRole("button", { name: "대응 방안", exact: true }).click();
  await expect(page.getByText("상황을 알려주시면 더 정확해져요")).toBeVisible();
  await expect(
    page.getByText(
      "답은 이 법령 판단에만 써요. 이력에 저장할 때만 함께 남아요.",
    ),
  ).toBeVisible();
  await expect(page.getByText(STEP_NOTE)).toHaveCount(0);

  const confirm = page.getByRole("button", { name: "대응 방안 확인하기" });
  await expect(confirm).toBeDisabled(); // 답이 있어야 누를 수 있다
  await page
    .getByRole("group", { name: QUESTION })
    .getByRole("button", { name: "예", exact: true })
    .click();
  await confirm.click();

  await expect(page.getByText("내 상황 반영")).toBeVisible();
  await expect(page.getByText(STEP_NOTE)).toBeVisible();
  await expect(page.getByText("갱신 요구 의사 전달")).toBeVisible();
  expect(revisionBodies).toEqual([
    {
      lawRef: {
        lawId: resolvedResponse.law!.lawId,
        effectiveDate: resolvedResponse.law!.effectiveDate,
      },
      setId: SET_ID,
      answers: [{ questionId: "q1", answer: "예" }],
    },
  ]);
});

test("상황 질문이 만료되면 '다시 받기'가 캐시를 쓰지 않고 개인화 분석을 다시 부른다", async ({
  page,
}) => {
  let personalizedCalls = 0;
  await page.route("**/api/v1/analyses", async (route) => {
    const body = route.request().postDataJSON() as { query: string };
    const personalized = body.query.includes("내 영향과 대응 방안");
    if (personalized) personalizedCalls += 1;
    await route.fulfill({
      json: personalized
        ? personalizedResponse
        : { ...resolvedResponse, unmet: {} },
    });
  });
  await page.route("**/api/v1/analyses/action-revisions", (route) =>
    route.fulfill({
      json: { unmet: { reason: "SITUATION_EXPIRED", message: "" } },
    }),
  );
  await page.route("**/api/v1/account", (route) =>
    route.fulfill({ json: ACCOUNT }),
  );

  await page.goto("/");
  await page
    .getByRole("textbox", { name: "질문" })
    .fill("주택법 바뀌면 뭐가 달라져?");
  await page.getByRole("button", { name: "질문 보내기" }).click();
  await page.getByRole("button", { name: "내 영향·대응 방안 보기" }).click();
  await page.getByRole("button", { name: "대응 방안", exact: true }).click();
  await page
    .getByRole("group", { name: QUESTION })
    .getByRole("button", { name: "예", exact: true })
    .click();
  await page.getByRole("button", { name: "대응 방안 확인하기" }).click();
  await expect(page.getByText("상황 질문이 만료됐어요")).toBeVisible();
  expect(personalizedCalls).toBe(1);

  await page.getByRole("button", { name: "다시 받기" }).click();

  await expect.poll(() => personalizedCalls).toBe(2);
  await page.getByRole("button", { name: "대응 방안", exact: true }).click();
  await expect(page.getByText("상황을 알려주시면 더 정확해져요")).toBeVisible();
});
