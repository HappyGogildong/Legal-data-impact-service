import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readReturnTo } from "@/lib/return-to";
import { empty, json, mockApi } from "@/test/api-mock";
import { SubscribeButton } from "./subscribe-button";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/laws/001809/2026-11-09",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const SUBSCRIBED = {
  lawId: "001809",
  title: "주택법",
  notifyBefore: 7,
  createdAt: "2026-10-31T00:00:00Z",
  nextEffectiveDate: "2026-11-09",
};

function renderButton(routes: Parameters<typeof mockApi>[0]) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <SubscribeButton lawId="001809" />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  sessionStorage.clear();
});

describe("SubscribeButton", () => {
  it("미구독 — 시트에서 기본 7일 전으로 알림 받기 → PUT {notifyBefore: 7}", async () => {
    let items: unknown[] = [];
    const requests = renderButton({
      "GET /api/v1/subscriptions": () => json({ items }),
      "PUT /api/v1/subscriptions/001809": () => {
        items = [SUBSCRIBED];
        return empty(204);
      },
    });
    const button = await screen.findByRole("button", {
      name: "시행 알림 받기",
    });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    const sheet = await screen.findByRole("dialog", { name: "시행 알림" });
    expect(
      within(sheet).getByText(
        "이 법령의 시행예정 개정마다 알림함으로 알려 드려요.",
      ),
    ).toBeInTheDocument();
    expect(within(sheet).getByRole("radio", { name: "7일 전" })).toBeChecked();
    await userEvent.click(
      within(sheet).getByRole("button", { name: "알림 받기" }),
    );

    expect(
      await screen.findByRole("button", { name: "알림 받는 중 · 7일 전" }),
    ).toBeInTheDocument();
    const body = await requests
      .find((request) => request.method === "PUT")!
      .json();
    expect(body).toEqual({ notifyBefore: 7 });
  });

  it("구독 중 — 시점을 바꿔 저장하면 PUT, 알림 끄기는 DELETE", async () => {
    let items: unknown[] = [SUBSCRIBED];
    const requests = renderButton({
      "GET /api/v1/subscriptions": () => json({ items }),
      "PUT /api/v1/subscriptions/001809": () => {
        items = [{ ...SUBSCRIBED, notifyBefore: 30 }];
        return empty(204);
      },
      "DELETE /api/v1/subscriptions/001809": () => {
        items = [];
        return empty(204);
      },
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "알림 받는 중 · 7일 전" }),
    );
    let sheet = await screen.findByRole("dialog", { name: "시행 알림" });
    await userEvent.click(
      within(sheet).getByRole("radio", { name: "30일 전" }),
    );
    await userEvent.click(within(sheet).getByRole("button", { name: "저장" }));
    expect(
      await screen.findByRole("button", { name: "알림 받는 중 · 30일 전" }),
    ).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "알림 받는 중 · 30일 전" }),
    );
    sheet = await screen.findByRole("dialog", { name: "시행 알림" });
    await userEvent.click(
      within(sheet).getByRole("button", { name: "알림 끄기" }),
    );
    expect(
      await screen.findByRole("button", { name: "시행 알림 받기" }),
    ).toBeInTheDocument();
    expect(requests.some((request) => request.method === "DELETE")).toBe(true);
  });

  it("409 면 서버 메시지, 그 밖은 '알림 설정을 바꾸지 못했어요'", async () => {
    let status = 409;
    renderButton({
      "GET /api/v1/subscriptions": () => json({ items: [] }),
      "PUT /api/v1/subscriptions/001809": () =>
        status === 409
          ? json(
              {
                error: "subscription_limit",
                message: "구독은 최대 100개까지 할 수 있어요.",
              },
              409,
            )
          : json({ error: "x" }, 500),
    });
    const button = await screen.findByRole("button", {
      name: "시행 알림 받기",
    });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    await userEvent.click(
      within(
        await screen.findByRole("dialog", { name: "시행 알림" }),
      ).getByRole("button", { name: "알림 받기" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "구독은 최대 100개까지 할 수 있어요.",
    );

    status = 500;
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "시행 알림" })).getByRole(
        "button",
        { name: "알림 받기" },
      ),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "알림 설정을 바꾸지 못했어요",
    );
  });

  it("익명이면 목록을 부르지 않고, 누르면 돌아올 경로를 두고 로그인으로", async () => {
    const requests = renderButton({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    const button = await screen.findByRole("button", {
      name: "시행 알림 받기",
    });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    expect(router.push).toHaveBeenCalledWith("/login");
    expect(readReturnTo()).toBe("/laws/001809/2026-11-09");
    expect(
      requests.some((request) => request.url.includes("/api/v1/subscriptions")),
    ).toBe(false);
  });
});
