import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setReturnTo } from "@/lib/return-to";
import { LoginScreen } from "./login-screen";

const router = { push: vi.fn(), replace: vi.fn() };
let search = "";
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  useSearchParams: () => new URLSearchParams(search),
}));

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  search = "";
});

describe("LoginScreen", () => {
  it("소셜 3종 — 전체 페이지 이동 링크", () => {
    render(<LoginScreen />);
    expect(
      screen.getByRole("link", { name: "카카오로 계속하기" }),
    ).toHaveAttribute("href", "/oauth2/authorization/kakao");
    expect(
      screen.getByRole("link", { name: "네이버로 계속하기" }),
    ).toHaveAttribute("href", "/oauth2/authorization/naver");
    expect(
      screen.getByRole("link", { name: "Google로 계속하기" }),
    ).toHaveAttribute("href", "/oauth2/authorization/google");
  });

  it("취소·실패 안내 → '로그인 없이 계속 보기'는 돌아올 경로로", async () => {
    search = "error=oauth";
    setReturnTo("/ask");
    render(<LoginScreen />);

    expect(
      screen.getByRole("heading", { name: "로그인이 완료되지 않았어요" }),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "로그인 없이 계속 보기" }),
    );
    expect(router.push).toHaveBeenCalledWith("/ask");
  });
});
