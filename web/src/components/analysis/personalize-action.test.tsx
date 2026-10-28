import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Unmet } from "@/lib/api/types";
import { json, mockApi } from "@/test/api-mock";
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

function renderAction(unmet: Unmet = PROFILE_REQUIRED) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <PersonalizeAction unmet={unmet} />
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

  it("로그인했는데 PROFILE_REQUIRED 면(프로필 없음·빈 프로필) 프로필 입력하기", async () => {
    mockApi({ "GET /api/v1/account": () => json(ACCOUNT) });
    renderAction();

    await userEvent.click(
      await screen.findByRole("button", { name: "프로필 입력하기" }),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/ask");
    expect(router.push).toHaveBeenCalledWith("/profile");
    expect(screen.queryByText(/곧 제공/)).not.toBeInTheDocument();
  });

  it("CONSENT_OUTDATED 면 동의하기 → /consent (복귀 주소는 /ask)", async () => {
    mockApi({ "GET /api/v1/account": () => json(ACCOUNT) });
    renderAction({ reason: "CONSENT_OUTDATED", message: "" });

    await userEvent.click(
      await screen.findByRole("button", { name: "동의하기" }),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/ask");
    expect(router.push).toHaveBeenCalledWith("/consent");
  });

  it("한도·근거 부족 사유엔 버튼이 없다(문구만)", async () => {
    mockApi({ "GET /api/v1/account": () => json(ACCOUNT) });
    renderAction({ reason: "DAILY_LIMIT", message: "" });

    await waitFor(() => expect(screen.queryByRole("button")).toBeNull());
  });
});
