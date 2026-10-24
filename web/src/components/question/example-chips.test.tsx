import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setDraft } from "@/lib/ask-store";
import { ExampleChips } from "./example-chips";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/",
}));

beforeEach(() => {
  router.push.mockReset();
});

describe("ExampleChips", () => {
  it("예시를 누르면 남은 '질문 고치기' 초안을 지우고 /ask 로 간다", async () => {
    render(<ExampleChips />);
    act(() => setDraft("x"));
    expect(sessionStorage.getItem("lia.draft")).not.toBeNull();

    await userEvent.click(screen.getAllByRole("button")[0]);

    expect(sessionStorage.getItem("lia.draft")).toBeNull();
    expect(router.push).toHaveBeenCalledWith("/ask");
  });
});
