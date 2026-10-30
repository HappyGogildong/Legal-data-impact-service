import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readReturnTo } from "@/lib/return-to";
import { empty, json, mockApi } from "@/test/api-mock";
import { BookmarkToggle } from "./bookmark-toggle";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/laws/001809/2026-11-03",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const SAVED = {
  lawId: "001809",
  title: "주택법",
  status: "시행예정",
  effectiveDate: "2026-11-03",
  bookmarkedAt: "2026-10-30T00:00:00Z",
};

function renderToggle(routes: Parameters<typeof mockApi>[0]) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <BookmarkToggle lawId="001809" />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  sessionStorage.clear();
});

describe("BookmarkToggle", () => {
  it("목록에 있으면 눌린 상태(★)", async () => {
    renderToggle({ "GET /api/v1/bookmarks": () => json({ items: [SAVED] }) });
    expect(
      await screen.findByRole("button", { name: "북마크", pressed: true }),
    ).toBeEnabled();
  });

  it("누르면 PUT 하고 눌린 상태가 된다", async () => {
    let items: unknown[] = [];
    const requests = renderToggle({
      "GET /api/v1/bookmarks": () => json({ items }),
      "PUT /api/v1/bookmarks/001809": () => {
        items = [SAVED];
        return empty(204);
      },
    });
    const button = await screen.findByRole("button", {
      name: "북마크",
      pressed: false,
    });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    expect(
      await screen.findByRole("button", { name: "북마크", pressed: true }),
    ).toBeInTheDocument();
    expect(requests.some((request) => request.method === "PUT")).toBe(true);
  });

  it("눌린 상태에서 누르면 DELETE", async () => {
    let items: unknown[] = [SAVED];
    const requests = renderToggle({
      "GET /api/v1/bookmarks": () => json({ items }),
      "DELETE /api/v1/bookmarks/001809": () => {
        items = [];
        return empty(204);
      },
    });
    const button = await screen.findByRole("button", {
      name: "북마크",
      pressed: true,
    });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    expect(
      await screen.findByRole("button", { name: "북마크", pressed: false }),
    ).toBeInTheDocument();
    expect(requests.some((request) => request.method === "DELETE")).toBe(true);
  });

  it("한도 초과(409)면 되돌리고 서버 메시지를 보여 준다", async () => {
    renderToggle({
      "GET /api/v1/bookmarks": () => json({ items: [] }),
      "PUT /api/v1/bookmarks/001809": () =>
        json(
          {
            error: "bookmark_limit",
            message: "북마크는 최대 100개까지 저장할 수 있어요.",
          },
          409,
        ),
    });
    const button = await screen.findByRole("button", {
      name: "북마크",
      pressed: false,
    });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "북마크는 최대 100개까지 저장할 수 있어요.",
    );
    expect(
      await screen.findByRole("button", { name: "북마크", pressed: false }),
    ).toBeInTheDocument();
  });

  it("그 밖의 실패는 '북마크를 바꾸지 못했어요'", async () => {
    renderToggle({
      "GET /api/v1/bookmarks": () => json({ items: [] }),
      "PUT /api/v1/bookmarks/001809": () => json({ error: "x" }, 500),
    });
    const button = await screen.findByRole("button", { name: "북마크" });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "북마크를 바꾸지 못했어요",
    );
  });

  it("세션 만료(401)면 다시 로그인 대화상자", async () => {
    renderToggle({
      "GET /api/v1/bookmarks": () => json({ items: [] }),
      "PUT /api/v1/bookmarks/001809": () =>
        json({ error: "unauthenticated" }, 401),
    });
    const button = await screen.findByRole("button", { name: "북마크" });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    expect(await screen.findByText("다시 로그인해 주세요")).toBeInTheDocument();
  });

  it("익명이면 목록을 부르지 않고, 누르면 돌아올 경로를 두고 로그인으로", async () => {
    const requests = renderToggle({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    const button = await screen.findByRole("button", { name: "북마크" });
    await vi.waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    expect(router.push).toHaveBeenCalledWith("/login");
    expect(readReturnTo()).toBe("/laws/001809/2026-11-03");
    expect(
      requests.some((request) => request.url.includes("/api/v1/bookmarks")),
    ).toBe(false);
  });
});
