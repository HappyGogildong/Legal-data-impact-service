import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setDraft } from "@/lib/ask-store";
import { QuestionBox } from "./question-box";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/",
}));

beforeEach(() => {
  router.push.mockReset();
});

describe("QuestionBox", () => {
  it("보내면 앞뒤 공백을 지운 질문을 저장하고 /ask 로 간다(URL 에 질문 없음)", async () => {
    render(<QuestionBox />);

    await userEvent.type(
      screen.getByRole("textbox", { name: "질문" }),
      "  주택법 바뀌면 전세 어떻게 돼?  ",
    );
    await userEvent.click(screen.getByRole("button", { name: "질문 보내기" }));

    expect(JSON.parse(sessionStorage.getItem("lia.ask")!)).toEqual({
      query: "주택법 바뀌면 전세 어떻게 돼?",
    });
    expect(router.push).toHaveBeenCalledWith("/ask");
  });

  it("빈 질문은 보내지 않는다", async () => {
    render(<QuestionBox />);
    await userEvent.click(screen.getByRole("button", { name: "질문 보내기" }));
    expect(router.push).not.toHaveBeenCalled();
  });

  it("'질문 고치기' 초안을 채운다", () => {
    render(<QuestionBox />);
    act(() => setDraft("자본시장법 뭐가 바뀌어?"));
    expect(screen.getByRole("textbox", { name: "질문" })).toHaveValue(
      "자본시장법 뭐가 바뀌어?",
    );
  });
});
