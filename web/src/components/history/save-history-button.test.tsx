import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readReturnTo } from "@/lib/return-to";
import { json, mockApi } from "@/test/api-mock";
import { SaveHistoryButton } from "./save-history-button";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/ask",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };

function renderButton(
  analysisId: string | undefined,
  routes: Parameters<typeof mockApi>[0],
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    ...routes,
  });
  render(
    <QueryClientProvider client={client}>
      <SaveHistoryButton analysisId={analysisId} />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  sessionStorage.clear();
});

describe("SaveHistoryButton", () => {
  it("누르면 analysisId 로 저장하고 '저장했어요 · 이력 보기'", async () => {
    const requests = renderButton("a-1", {
      "POST /api/v1/history": () => json({ id: 7 }, 201),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );

    expect(await screen.findByText(/저장했어요/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이력 보기" })).toHaveAttribute(
      "href",
      "/me/history/7",
    );
    const body = await requests
      .find((request) => request.method === "POST")!
      .json();
    expect(body).toEqual({ analysisId: "a-1" });
  });

  it("저장하면 이력 목록·상세 캐시를 갱신한다", async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    renderButton(
      "a-1",
      { "POST /api/v1/history": () => json({ id: 7 }, 200) },
      client,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );

    await screen.findByText(/저장했어요/);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["history"] });
  });

  it("409 면 서버 메시지를 보여 준다", async () => {
    renderButton("a-1", {
      "POST /api/v1/history": () =>
        json(
          {
            error: "analysis_expired",
            message: "저장할 결과가 만료됐어요. 다시 분석한 뒤 저장해 주세요.",
          },
          409,
        ),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "저장할 결과가 만료됐어요. 다시 분석한 뒤 저장해 주세요.",
    );
  });

  it("그 밖의 실패는 '저장하지 못했어요'", async () => {
    renderButton("a-1", {
      "POST /api/v1/history": () => json({ error: "x" }, 500),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "저장하지 못했어요",
    );
  });

  it("세션 만료(401)면 다시 로그인 대화상자", async () => {
    renderButton("a-1", {
      "POST /api/v1/history": () => json({ error: "unauthenticated" }, 401),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );
    expect(await screen.findByText("다시 로그인해 주세요")).toBeInTheDocument();
  });

  it("익명이면 돌아올 경로를 두고 로그인으로", async () => {
    renderButton(undefined, {
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );
    expect(router.push).toHaveBeenCalledWith("/login");
    expect(readReturnTo()).toBe("/ask");
  });

  it("로그인인데 analysisId 가 없으면 버튼이 없다", async () => {
    renderButton(undefined, {});
    await vi.waitFor(() =>
      expect(screen.queryByRole("button", { name: "이력에 저장" })).toBeNull(),
    );
    // 세션 조회가 끝난 뒤에도 없다
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByRole("button", { name: "이력에 저장" })).toBeNull();
  });
});
