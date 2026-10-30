import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { HistoryListView } from "./history-list-view";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/me/history",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const ITEMS = [
  {
    id: 7,
    query: "주택법 바뀌면 전세 계약 어떻게 돼?",
    lawId: "001809",
    lawTitle: "주택법",
    effectiveDate: "2026-11-08",
    savedAt: "2026-10-30T03:00:00Z",
    lawUpdated: true,
  },
  {
    id: 6,
    query: "근로기준법 시행령 야간 수당",
    lawId: "001234",
    lawTitle: "근로기준법 시행령",
    effectiveDate: "2026-11-22",
    savedAt: "2026-10-29T03:00:00Z",
    lawUpdated: false,
  },
];

function renderList(routes: Parameters<typeof mockApi>[0] = {}) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    "GET /api/v1/history": () => json({ items: ITEMS }),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <HistoryListView />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
});

describe("HistoryListView", () => {
  it("행마다 질문 링크(상세)·법령명·저장일, 갱신된 것만 배지", async () => {
    renderList();
    const link = await screen.findByRole("link", {
      name: /주택법 바뀌면 전세 계약 어떻게 돼\?/,
    });
    expect(link).toHaveAttribute("href", "/me/history/7");
    expect(screen.getByText(/2026-10-30/)).toBeInTheDocument();
    expect(screen.getAllByText("법령 갱신됨")).toHaveLength(1);
  });

  it("삭제는 확인 후 DELETE 하고 목록에서 빠진다", async () => {
    let items = ITEMS;
    const requests = renderList({
      "GET /api/v1/history": () => json({ items }),
      "DELETE /api/v1/history/7": () => {
        items = ITEMS.slice(1);
        return empty(204);
      },
    });
    await userEvent.click(
      await screen.findByRole("button", {
        name: "주택법 바뀌면 전세 계약 어떻게 돼? 이력 삭제",
      }),
    );
    expect(screen.getByText("이 기록을 지울까요?")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "삭제" }));

    await vi.waitFor(() =>
      expect(screen.queryByRole("link", { name: /주택법 바뀌면/ })).toBeNull(),
    );
    expect(requests.some((request) => request.method === "DELETE")).toBe(true);
  });

  it("빈 목록이면 안내", async () => {
    renderList({ "GET /api/v1/history": () => json({ items: [] }) });
    expect(
      await screen.findByText(
        "결과 화면에서 '이력에 저장'을 누르면 여기에 모여요.",
      ),
    ).toBeInTheDocument();
  });

  it("목록을 못 받으면 안내", async () => {
    renderList({ "GET /api/v1/history": () => json({ error: "x" }, 500) });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이력을 불러오지 못했어요",
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
