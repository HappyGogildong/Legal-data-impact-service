import { expect, test } from "@playwright/test";
import { resolvedResponse } from "../src/test/fixtures";

const discoveryResponse = {
  resolution: "RESOLVED",
  discovery: {
    candidates: [
      {
        lawId: resolvedResponse.law!.lawId,
        title: "주택법",
        effectiveDate: resolvedResponse.law!.effectiveDate,
        ministry: "국토교통부",
        summary: "분양 주택의 거주 의무가 바뀌어요.",
        summaryReady: true,
        matched: {
          sourceId: `LAW:${resolvedResponse.law!.lawId}@${resolvedResponse.law!.effectiveDate}:art:18`,
          label: "제18조 통합심의",
        },
      },
    ],
    profileApplied: false,
  },
};

test("법령 발견: 법 이름 없는 질문 → S04 후보 → '이 법령으로 분석' → 같은 질문 + lawRef 로 S02", async ({
  page,
}) => {
  const bodies: { query: string; lawRef?: unknown }[] = [];
  await page.route("**/api/v1/analyses", async (route) => {
    const body = route.request().postDataJSON() as {
      query: string;
      lawRef?: unknown;
    };
    bodies.push(body);
    await route.fulfill({
      json: body.lawRef
        ? { ...resolvedResponse, unmet: {} }
        : discoveryResponse,
    });
  });

  await page.goto("/");
  await page
    .getByRole("textbox", { name: "질문" })
    .fill("전세 관련해서 바뀌는 법 있어?");
  await page.getByRole("button", { name: "질문 보내기" }).click();

  await expect(
    page.getByText("질문과 관련 있어 보이는 시행예정 법령 1건"),
  ).toBeVisible();
  await expect(
    page.getByText("분양 주택의 거주 의무가 바뀌어요."),
  ).toBeVisible();
  await page.getByRole("button", { name: "이 법령으로 분석" }).click();

  await expect.poll(() => bodies.length).toBe(2);
  expect(bodies[1]).toEqual({
    query: "전세 관련해서 바뀌는 법 있어?",
    lawRef: {
      lawId: resolvedResponse.law!.lawId,
      effectiveDate: resolvedResponse.law!.effectiveDate,
    },
  });
  await expect(
    page.getByRole("heading", { level: 1, name: resolvedResponse.law!.title! }),
  ).toBeVisible();
});
