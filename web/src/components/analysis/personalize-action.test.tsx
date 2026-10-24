import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { PersonalizeAction } from "./personalize-action";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/ask",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const PROFILE_REQUIRED = {
  reason: "PROFILE_REQUIRED" as const,
  message: "프로필 필요 (Layer B)",
};

function renderAction() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <PersonalizeAction unmet={PROFILE_REQUIRED} />
    </QueryClientProvider>,
  );
}

beforeEach(() => router.push.mockReset());

describe("PersonalizeAction", () => {
  it("익명 → 로그인(돌아올 경로 /ask)", async () => {
    mockApi({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    renderAction();

    await userEvent.click(
      await screen.findByRole("button", { name: "로그인하기" }),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/ask");
    expect(router.push).toHaveBeenCalledWith("/login");
  });

  it("로그인했지만 프로필 없음 → 프로필 입력", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(404),
    });
    renderAction();

    await userEvent.click(
      await screen.findByRole("button", { name: "프로필 입력하기" }),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/ask");
    expect(router.push).toHaveBeenCalledWith("/profile");
  });

  it("프로필 있음 → 곧 제공 안내(지키지 못할 약속을 하지 않는다)", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => json({ housingType: "전세" }),
    });
    renderAction();

    expect(
      await screen.findByText(
        "프로필을 반영한 내 영향·대응 방안은 곧 제공돼요.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });
});
