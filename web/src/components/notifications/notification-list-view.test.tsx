import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import {
  NotificationListView,
  notificationMessage,
} from "./notification-list-view";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/notifications",
}));
vi.mock("@/hooks/use-browser", () => ({ useToday: () => "2026-11-02" }));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const ITEMS = [
  {
    id: 2,
    lawId: "001809",
    title: "주택법",
    effectiveDate: "2026-11-09",
    notifyBefore: 7,
    createdAt: "2026-11-01T19:00:00Z",
    read: false,
  },
  {
    id: 1,
    lawId: "000001",
    title: "근로기준법",
    effectiveDate: "2026-11-02",
    notifyBefore: 0,
    createdAt: "2026-11-01T18:00:00Z",
    read: true,
  },
];

function renderList(routes: Parameters<typeof mockApi>[0] = {}) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    "GET /api/v1/notifications": () => json({ items: ITEMS, unreadCount: 1 }),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <NotificationListView />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
});

describe("notificationMessage", () => {
  it("남은 날·오늘·지남", () => {
    expect(notificationMessage("주택법", "2026-11-09", "2026-11-02")).toBe(
      "주택법 개정이 7일 뒤 시행돼요",
    );
    expect(notificationMessage("주택법", "2026-11-02", "2026-11-02")).toBe(
      "주택법 개정이 오늘 시행돼요",
    );
    expect(notificationMessage("주택법", "2026-11-01", "2026-11-02")).toBe(
      "주택법 개정이 시행됐어요",
    );
  });
});

describe("NotificationListView", () => {
  it("문구·알림 이유, 누르면 읽음 처리 후 그 시행본 S05", async () => {
    const requests = renderList({
      "POST /api/v1/notifications/2/read": () => empty(204),
    });
    const item = await screen.findByRole("button", {
      name: /주택법 개정이 7일 뒤 시행돼요/,
    });
    expect(screen.getAllByText("구독한 법령").length).toBeGreaterThan(0);
    expect(
      screen.getByText("근로기준법 개정이 오늘 시행돼요"),
    ).toBeInTheDocument();
    await userEvent.click(item);
    await vi.waitFor(() =>
      expect(router.push).toHaveBeenCalledWith("/laws/001809/2026-11-09"),
    );
    expect(requests.some((request) => request.method === "POST")).toBe(true);
  });

  it("모두 읽음", async () => {
    const requests = renderList({
      "POST /api/v1/notifications/read-all": () => empty(204),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "모두 읽음" }),
    );
    await vi.waitFor(() =>
      expect(
        requests.some((request) =>
          new URL(request.url).pathname.endsWith("/read-all"),
        ),
      ).toBe(true),
    );
  });

  it("빈 상태·실패·익명", async () => {
    renderList({
      "GET /api/v1/notifications": () => json({ items: [], unreadCount: 0 }),
    });
    expect(await screen.findByText("아직 알림이 없어요")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "곧 시행되는 법령 보기" }),
    ).toHaveAttribute("href", "/laws");
    expect(screen.queryByRole("button", { name: "모두 읽음" })).toBeNull();
  });

  it("못 받으면 안내", async () => {
    renderList({
      "GET /api/v1/notifications": () => json({ error: "x" }, 500),
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "알림을 불러오지 못했어요",
    );
  });

  it("익명이면 로그인으로", async () => {
    renderList({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    await vi.waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/login"),
    );
  });
});
