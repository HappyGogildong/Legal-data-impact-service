import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { json, mockApi } from "@/test/api-mock";
import { UnreadBadge } from "./unread-badge";

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };

function renderBadge(routes: Parameters<typeof mockApi>[0]) {
  const requests = mockApi(routes);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <UnreadBadge />
    </QueryClientProvider>,
  );
  return requests;
}

describe("UnreadBadge", () => {
  it("읽지 않은 수, 9 초과는 9+", async () => {
    renderBadge({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/notifications": () => json({ items: [], unreadCount: 12 }),
    });
    expect(await screen.findByText("9+")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
    expect(screen.getByText("읽지 않은 알림 12개")).toHaveClass("sr-only");
  });

  it("링크 이름은 '알림 읽지 않은 알림 n개' — 숫자는 낭독하지 않는다", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/notifications": () => json({ items: [], unreadCount: 3 }),
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <a href="/notifications">
          알림 <UnreadBadge />
        </a>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByRole("link", { name: "알림 읽지 않은 알림 3개" }),
    ).toBeInTheDocument();
  });

  it("0 이면 없음, 익명이면 알림을 부르지 않음", async () => {
    const requests = renderBadge({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByText(/읽지 않은 알림/)).toBeNull();
    expect(
      requests.some((request) => request.url.includes("/api/v1/notifications")),
    ).toBe(false);
  });
});
