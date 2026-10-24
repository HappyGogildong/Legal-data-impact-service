import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setReturnTo } from "@/lib/return-to";
import { json, mockApi } from "@/test/api-mock";
import { LoginDoneScreen } from "./login-done-screen";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/login/done",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };

function renderDone() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <LoginDoneScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => router.replace.mockReset());

describe("LoginDoneScreen", () => {
  it("동의가 없으면 동의(S08)로", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}),
    });
    renderDone();
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/consent"),
    );
  });

  it("옛 버전 동의면 재동의로", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () =>
        json({
          profile: {
            policyVersion: "2026-09",
            consentedAt: "2026-10-01T00:00:00Z",
            upToDate: false,
          },
        }),
    });
    renderDone();
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/consent"),
    );
  });

  it("최신 동의면 돌아올 경로로", async () => {
    setReturnTo("/ask");
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () =>
        json({
          profile: {
            policyVersion: "2026-09",
            consentedAt: "2026-10-01T00:00:00Z",
            upToDate: true,
          },
        }),
    });
    renderDone();
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/ask"));
  });

  it("세션이 없으면 로그인 오류 안내로", async () => {
    mockApi({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    renderDone();
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/login?error=session"),
    );
  });
});
