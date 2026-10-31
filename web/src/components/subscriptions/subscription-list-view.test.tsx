import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { SubscriptionListView } from "./subscription-list-view";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/me/subscriptions",
}));
vi.mock("@/hooks/use-browser", () => ({ useToday: () => "2026-10-31" }));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const ITEMS = [
  {
    lawId: "001809",
    title: "주택법",
    notifyBefore: 7,
    createdAt: "2026-10-31T00:00:00Z",
    nextEffectiveDate: "2026-11-09",
  },
  {
    lawId: "000001",
    title: "근로기준법",
    notifyBefore: 30,
    createdAt: "2026-10-30T00:00:00Z",
  },
];

function renderList(routes: Parameters<typeof mockApi>[0] = {}) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    "GET /api/v1/subscriptions": () => json({ items: ITEMS }),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <SubscriptionListView />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
});

describe("SubscriptionListView", () => {
  it("다음 시행본 링크·D-day 배지, 다음 개정이 없으면 대기 문구(링크 없음)", async () => {
    renderList();
    expect(await screen.findByRole("link", { name: "주택법" })).toHaveAttribute(
      "href",
      "/laws/001809/2026-11-09",
    );
    expect(screen.getByText("D-9")).toBeInTheDocument();
    expect(screen.getByText("다음 개정을 기다리는 중")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "근로기준법" })).toBeNull();
    expect(screen.getByText("근로기준법")).toBeInTheDocument();
  });

  it("알림 시점을 바꾸면 바로 PUT", async () => {
    const requests = renderList({
      "PUT /api/v1/subscriptions/001809": () => empty(204),
    });
    const row = (await screen.findByRole("link", { name: "주택법" })).closest(
      "li",
    )!;
    await userEvent.selectOptions(
      within(row).getByRole("combobox", { name: "주택법 알림 시점" }),
      "1",
    );
    await vi.waitFor(async () => {
      const put = requests.find((request) => request.method === "PUT");
      expect(put).toBeDefined();
      expect(await put!.clone().json()).toEqual({ notifyBefore: 1 });
    });
  });

  it("PUT 이 끝나기 전에도 고른 시점이 바로 보인다(낙관적 갱신)", async () => {
    let finishPut: (response: Response) => void = () => {};
    const putPending = new Promise<Response>((resolve) => {
      finishPut = resolve;
    });
    renderList({ "PUT /api/v1/subscriptions/001809": () => putPending });
    const select = await screen.findByRole("combobox", {
      name: "주택법 알림 시점",
    });
    expect(select).toHaveValue("7");
    await userEvent.selectOptions(select, "1");
    expect(select).toHaveValue("1");
    finishPut(empty(204));
  });

  it("PUT 이 실패하면 시점이 되돌아가고 안내", async () => {
    renderList({
      "PUT /api/v1/subscriptions/001809": () => json({ error: "x" }, 500),
    });
    const select = await screen.findByRole("combobox", {
      name: "주택법 알림 시점",
    });
    await userEvent.selectOptions(select, "1");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "알림 설정을 바꾸지 못했어요",
    );
    expect(select).toHaveValue("7");
  });

  it("알림 끄기는 확인 없이 DELETE 하고 목록에서 빠진다", async () => {
    let items = ITEMS;
    renderList({
      "GET /api/v1/subscriptions": () => json({ items }),
      "DELETE /api/v1/subscriptions/001809": () => {
        items = ITEMS.slice(1);
        return empty(204);
      },
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "주택법 알림 끄기" }),
    );
    await vi.waitFor(() =>
      expect(screen.queryByRole("link", { name: "주택법" })).toBeNull(),
    );
  });

  it("빈 목록이면 안내", async () => {
    renderList({ "GET /api/v1/subscriptions": () => json({ items: [] }) });
    expect(
      await screen.findByText(
        "법령 화면에서 '시행 알림 받기'를 누르면 여기에 모여요.",
      ),
    ).toBeInTheDocument();
  });

  it("목록을 못 받으면 안내", async () => {
    renderList({
      "GET /api/v1/subscriptions": () => json({ error: "x" }, 500),
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "구독 목록을 불러오지 못했어요",
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
