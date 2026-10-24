import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setAsk } from "@/lib/ask-store";
import {
  ambiguousResponse,
  notFoundResponse,
  resolvedResponse,
} from "@/test/fixtures";
import { AskScreen } from "./ask-screen";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/ask",
}));

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderScreen() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <AskScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
});

describe("AskScreen", () => {
  it("질문이 없으면 홈으로", () => {
    renderScreen();
    expect(router.replace).toHaveBeenCalledWith("/");
  });

  it("대기 상태를 보이고, 해소되면 결과(S02)", async () => {
    setAsk({ query: "주택법 바뀌면?" });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(resolvedResponse));
    renderScreen();

    expect(screen.getByRole("status")).toHaveTextContent("법령 확인 중");
    expect(
      await screen.findByRole("heading", { level: 1, name: "주택법" }),
    ).toBeInTheDocument();
  });

  it("모호함(S03) → 후보를 고르면 lawRef 를 붙여 다시 분석", async () => {
    setAsk({ query: "자본시장법" });
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json(ambiguousResponse))
      .mockResolvedValueOnce(json(resolvedResponse));
    renderScreen();

    expect(
      await screen.findByRole("heading", { name: "어떤 법령인지 골라 주세요" }),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: /2026-11-13 시행/ }),
    );

    expect(
      await screen.findByRole("heading", { level: 1, name: "주택법" }),
    ).toBeInTheDocument();
    const second = fetchSpy.mock.calls[1][0] as Request;
    expect(await second.json()).toEqual({
      query: "자본시장법",
      lawRef: { lawId: "010513", effectiveDate: "2026-11-13" },
    });
  });

  it("미등록(S03) → '질문 고치기'는 초안을 두고 홈으로", async () => {
    setAsk({ query: "이상한 법" });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(notFoundResponse));
    renderScreen();

    await userEvent.click(
      await screen.findByRole("button", { name: "질문 고치기" }),
    );

    expect(sessionStorage.getItem("lia.draft")).toBe("이상한 법");
    expect(router.push).toHaveBeenCalledWith("/");
  });

  it("서버 오류 → '다시 시도'로 다시 부른다", async () => {
    setAsk({ query: "주택법" });
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ error: "internal", message: "오류" }, 500))
      .mockResolvedValueOnce(json(resolvedResponse));
    renderScreen();

    await userEvent.click(
      await screen.findByRole("button", { name: "다시 시도" }),
    );
    expect(
      await screen.findByRole("heading", { level: 1, name: "주택법" }),
    ).toBeInTheDocument();
  });

  it("다시 시도 중에는 진행 표시만 — 다시 누를 수 없어 요청이 겹치지 않는다", async () => {
    setAsk({ query: "주택법" });
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ error: "internal", message: "오류" }, 500))
      .mockReturnValueOnce(new Promise<Response>(() => {}));
    renderScreen();

    await userEvent.click(
      await screen.findByRole("button", { name: "다시 시도" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent("법령 확인 중");
    expect(screen.queryByRole("button", { name: "다시 시도" })).toBeNull();
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it("화면을 떠나면 진행 중인 분석 요청을 중단한다", async () => {
    setAsk({ query: "주택법" });
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockReturnValueOnce(new Promise<Response>(() => {}));
    const { unmount } = renderScreen();
    await screen.findByRole("status");

    unmount();

    expect((fetchSpy.mock.calls[0][0] as Request).signal.aborted).toBe(true);
  });

  it("400 → '질문 고치기'는 질문을 초안으로 두고 홈으로", async () => {
    setAsk({ query: "   " });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ error: "bad_request", message: "질문을 입력해 주세요" }, 400),
    );
    renderScreen();

    await userEvent.click(
      await screen.findByRole("button", { name: "질문 고치기" }),
    );

    expect(sessionStorage.getItem("lia.draft")).toBe("   ");
    expect(router.push).toHaveBeenCalledWith("/");
  });

  it("법령 없는 결과 → '질문 고치기'도 질문을 초안으로 두고 홈으로", async () => {
    setAsk({ query: "전세 계약 어떻게 돼?" });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ ...resolvedResponse, law: undefined }),
    );
    renderScreen();

    await userEvent.click(
      await screen.findByRole("button", { name: "질문 고치기" }),
    );

    expect(sessionStorage.getItem("lia.draft")).toBe("전세 계약 어떻게 돼?");
    expect(router.push).toHaveBeenCalledWith("/");
  });
});
