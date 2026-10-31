import { expect, test } from "@playwright/test";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };

test("S05 시행 알림 받기 → 메뉴 알림 배지 → 알림함에서 눌러 읽음 → S05", async ({
  page,
}, testInfo) => {
  let subscribed = false;
  let read = false;
  let effective = ""; // 모의 서버의 주택법 시행일 — /laws/001809 가 보내는 주소에서 읽는다
  await page.route("**/api/v1/account", (route) =>
    route.fulfill({ json: ACCOUNT }),
  );
  await page.route("**/api/v1/subscriptions", (route) =>
    route.fulfill({
      json: {
        items: subscribed
          ? [
              {
                lawId: "001809",
                title: "주택법",
                notifyBefore: 7,
                createdAt: "2026-10-31T00:00:00Z",
              },
            ]
          : [],
      },
    }),
  );
  await page.route("**/api/v1/subscriptions/*", async (route) => {
    subscribed = route.request().method() === "PUT";
    await route.fulfill({ status: 204, body: "" });
  });
  await page.route("**/api/v1/notifications", (route) =>
    route.fulfill({
      json: subscribed
        ? {
            items: [
              {
                id: 1,
                lawId: "001809",
                title: "주택법",
                effectiveDate: effective,
                notifyBefore: 7,
                createdAt: "2026-10-31T00:00:00Z",
                read,
              },
            ],
            unreadCount: read ? 0 : 1,
          }
        : { items: [], unreadCount: 0 },
    }),
  );
  await page.route("**/api/v1/notifications/1/read", async (route) => {
    read = true;
    await route.fulfill({ status: 204, body: "" });
  });

  await page.goto("/laws/001809");
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
  effective = new URL(page.url()).pathname.split("/").pop()!;
  await page.getByRole("button", { name: "시행 알림 받기" }).click();
  const sheet = page.getByRole("dialog", { name: "시행 알림" });
  await sheet.getByRole("button", { name: "알림 받기" }).click();
  await expect(
    page.getByRole("button", { name: "알림 받는 중 · 7일 전" }),
  ).toBeVisible();

  await page.goto("/notifications");
  const nav = testInfo.project.name === "desktop" ? "주 메뉴" : "하단 메뉴";
  await expect(
    page
      .getByRole("navigation", { name: nav })
      .getByRole("link", { name: /읽지 않은 알림 1개/ }),
  ).toBeVisible();
  await page.getByRole("button", { name: /주택법 개정이/ }).click();
  await expect(page).toHaveURL(new RegExp(`/laws/001809/${effective}$`));
  await expect(
    page.getByRole("heading", { level: 1, name: "주택법" }),
  ).toBeVisible();
});
