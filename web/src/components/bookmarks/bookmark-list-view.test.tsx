import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { BookmarkListView } from "./bookmark-list-view";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/me/bookmarks",
}));
vi.mock("@/hooks/use-browser", () => ({ useToday: () => "2026-10-30" }));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const ITEMS = [
  {
    lawId: "001809",
    title: "주택임대차보호법",
    status: "시행예정",
    effectiveDate: "2026-11-08",
    bookmarkedAt: "2026-10-30T03:00:00Z",
  },
  {
    lawId: "000001",
    title: "주택법",
    status: "시행됨",
    effectiveDate: "2026-08-04",
    bookmarkedAt: "2026-10-30T02:00:00Z",
  },
  {
    lawId: "000002",
    title: "가상법",
    status: "철회",
    effectiveDate: "2026-12-01",
    bookmarkedAt: "2026-10-30T01:00:00Z",
  },
];

function renderList(routes: Parameters<typeof mockApi>[0] = {}) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    "GET /api/v1/bookmarks": () => json({ items: ITEMS }),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const { unmount } = render(
    <QueryClientProvider client={client}>
      <BookmarkListView />
    </QueryClientProvider>,
  );
  return { requests, client, unmount };
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
});

describe("BookmarkListView", () => {
  it("행마다 법령명 링크(그 버전의 S05)와 상태 배지", async () => {
    renderList();
    const link = await screen.findByRole("link", { name: "주택임대차보호법" });
    expect(link).toHaveAttribute("href", "/laws/001809/2026-11-08");
    expect(screen.getByText("D-9")).toBeInTheDocument();
    expect(screen.getByText("시행예정")).toBeInTheDocument();
    expect(screen.getByText("시행됨 2026-08-04")).toBeInTheDocument();
    expect(screen.getByText("시행 예정이던 개정 철회")).toBeInTheDocument();
    expect(screen.queryByText(/알림/)).not.toBeInTheDocument();
  });

  it("해제하면 DELETE 하고 그 자리에 되돌리기 — 누르면 PUT 으로 복원", async () => {
    const { requests } = renderList({
      "DELETE /api/v1/bookmarks/000001": () => empty(204),
      "PUT /api/v1/bookmarks/000001": () => empty(204),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "주택법 북마크 해제" }),
    );

    expect(await screen.findByText("북마크를 해제했어요")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "주택법" }),
    ).not.toBeInTheDocument();
    expect(requests.some((request) => request.method === "DELETE")).toBe(true);

    await userEvent.click(screen.getByRole("button", { name: "되돌리기" }));
    expect(
      await screen.findByRole("link", { name: "주택법" }),
    ).toBeInTheDocument();
    expect(requests.some((request) => request.method === "PUT")).toBe(true);
  });

  it("해제 안내는 role=status 로 낭독되고, 되돌리기 버튼이 포커스를 받아 법령명을 설명으로 갖는다", async () => {
    renderList({
      "DELETE /api/v1/bookmarks/000001": () => empty(204),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "주택법 북마크 해제" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "북마크를 해제했어요",
    );
    const undo = screen.getByRole("button", { name: "되돌리기" });
    expect(undo).toHaveFocus();
    expect(undo).toHaveAccessibleDescription("주택법");
  });

  it("해제 실패면 행을 그대로 두고 안내", async () => {
    renderList({
      "DELETE /api/v1/bookmarks/000001": () => json({ error: "x" }, 500),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "주택법 북마크 해제" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "북마크를 바꾸지 못했어요",
    );
    expect(screen.getByRole("link", { name: "주택법" })).toBeInTheDocument();
  });

  it("빈 목록이면 안내와 홈 링크", async () => {
    renderList({ "GET /api/v1/bookmarks": () => json({ items: [] }) });
    expect(
      await screen.findByText("관심 법령의 ☆를 누르면 여기에 모여요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "홈으로" })).toHaveAttribute(
      "href",
      "/",
    );
  });

  it("목록을 못 받으면 안내", async () => {
    renderList({ "GET /api/v1/bookmarks": () => json({ error: "x" }, 500) });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "북마크를 불러오지 못했어요",
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

  it("목록 행 구성 — ul 안에 세 행", async () => {
    renderList();
    const list = await screen.findByRole("list", { name: "북마크한 법령" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
  });

  it("떠날 때 여기서 해제한 행을 캐시에서 빼고 다시 불러오게 한다", async () => {
    const { client, unmount } = renderList({
      "DELETE /api/v1/bookmarks/000001": () => empty(204),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "주택법 북마크 해제" }),
    );
    expect(await screen.findByText("북마크를 해제했어요")).toBeInTheDocument();

    const invalidate = vi.spyOn(client, "invalidateQueries");
    unmount();

    const cached = client.getQueryData<{ lawId?: string }[]>(["bookmarks"]);
    expect(cached?.map((item) => item.lawId)).toEqual(["001809", "000002"]);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["bookmarks"] });
  });

  it("해제한 게 없으면 떠나도 캐시를 건드리지 않는다", async () => {
    const { client, unmount } = renderList();
    await screen.findByRole("list", { name: "북마크한 법령" });

    const invalidate = vi.spyOn(client, "invalidateQueries");
    unmount();

    expect(client.getQueryData<unknown[]>(["bookmarks"])).toHaveLength(3);
    expect(invalidate).not.toHaveBeenCalled();
  });
});
