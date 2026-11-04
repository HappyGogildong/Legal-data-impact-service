import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setReturnTo } from "@/lib/return-to";
import { LoginScreen } from "./login-screen";

const router = { push: vi.fn(), replace: vi.fn() };
let search = "";
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  useSearchParams: () => new URLSearchParams(search),
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
  render(
    <QueryClientProvider client={client}>
      <LoginScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  search = "";
});
afterEach(() => vi.restoreAllMocks());

describe("LoginScreen", () => {
  it("켜진 provider 만 — 구글만 켜져 있으면 구글 버튼 하나(전체 페이지 이동 링크)", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ providers: ["google"] }),
    );
    renderScreen();

    expect(
      await screen.findByRole("link", { name: "Google로 계속하기" }),
    ).toHaveAttribute("href", "/oauth2/authorization/google");
    expect(
      screen.queryByRole("link", { name: "카카오로 계속하기" }),
    ).toBeNull();
    expect(
      screen.queryByRole("link", { name: "네이버로 계속하기" }),
    ).toBeNull();
  });

  it("셋 다 켜져 있으면 카카오·네이버·구글 순서", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ providers: ["google", "kakao", "naver"] }),
    );
    renderScreen();

    await screen.findByRole("link", { name: "카카오로 계속하기" });
    expect(
      screen.getAllByRole("link").map((link) => link.textContent),
    ).toEqual(["카카오로 계속하기", "네이버로 계속하기", "Google로 계속하기"]);
  });

  it("켜진 provider 가 없으면 준비 중 안내", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json({ providers: [] }));
    renderScreen();

    expect(
      await screen.findByText("지금은 로그인을 준비 중이에요."),
    ).toBeInTheDocument();
    expect(screen.queryAllByRole("link")).toHaveLength(0);
  });

  it("목록을 못 받으면 오류 안내와 다시 시도", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ error: "x" }, 500))
      .mockResolvedValue(json({ providers: ["google"] }));
    renderScreen();

    expect(
      await screen.findByText("로그인 방법을 불러오지 못했어요."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(
      await screen.findByRole("link", { name: "Google로 계속하기" }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("취소·실패 안내 → '로그인 없이 계속 보기'는 돌아올 경로로", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ providers: ["google"] }),
    );
    search = "error=oauth";
    setReturnTo("/ask");
    renderScreen();

    expect(
      screen.getByRole("heading", { name: "로그인이 완료되지 않았어요" }),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "로그인 없이 계속 보기" }),
    );
    expect(router.push).toHaveBeenCalledWith("/ask");
    expect(sessionStorage.getItem("lia.returnTo")).toBeNull();
  });

  it("알 수 없는 오류 값이 객체 상속 속성 이름이어도 취소·실패 안내를 보인다", () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ providers: ["google"] }),
    );
    search = "error=constructor";
    renderScreen();
    expect(
      screen.getByText("취소했거나 잠시 문제가 있었어요."),
    ).toBeInTheDocument();
  });
});
