import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { MyScreen } from "./my-screen";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/me",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const CONSENTS = {
  profile: {
    policyVersion: "2026-09",
    consentedAt: "2026-10-23T05:01:00Z",
    upToDate: true,
  },
};
const PROFILE = {
  purposes: ["생활·주거"],
  age: 29,
  housingType: "전세",
  regionSido: "서울특별시",
  updatedAt: "2026-10-23T05:02:00Z",
};

function renderMy(routes: Parameters<typeof mockApi>[0] = {}) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    "GET /api/v1/consents": () => json(CONSENTS),
    "GET /api/v1/profile": () => json(PROFILE),
    ...routes,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MyScreen />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
});

describe("MyScreen", () => {
  it("계정·프로필 요약·동의 상태", async () => {
    renderMy();
    expect(await screen.findByText("카카오")).toBeInTheDocument();
    expect(screen.getByText("2026-10-23")).toBeInTheDocument();
    expect(await screen.findByText("7개 중 4개 입력")).toBeInTheDocument();
    expect(screen.getByText("2026-09 버전 · 2026-10-23")).toBeInTheDocument();
    expect(screen.getByText("최신")).toBeInTheDocument();
  });

  it("익명이면 돌아올 경로를 두고 로그인으로", async () => {
    renderMy({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    await vi.waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/login"),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/me");
  });

  it("동의 조회가 실패하면 잘못된 상태 대신 오류를 보인다", async () => {
    renderMy({
      "GET /api/v1/consents": () => json({ error: "internal" }, 500),
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "정보를 불러오지 못했어요.",
    );
    expect(
      screen.queryByRole("switch", { name: "이메일 알림 받기" }),
    ).not.toBeInTheDocument();
  });

  it("이메일 알림 켜기 — 처음엔 동의 대화상자", async () => {
    const requests = renderMy({
      "PUT /api/v1/consents/notification-email": () =>
        json({ email: "me@example.com", consentedAt: "2026-10-24T01:00:00Z" }),
    });
    await userEvent.click(
      await screen.findByRole("switch", { name: "이메일 알림 받기" }),
    );
    await userEvent.click(
      screen.getByRole("checkbox", { name: /알림 이메일 수신에 동의합니다/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 켜기" }),
    );

    expect(
      await screen.findByRole("switch", { name: "이메일 알림 받기" }),
    ).toHaveAttribute("aria-checked", "true");
    expect(requests.some((request) => request.method === "PUT")).toBe(true);
  });

  it("계정 삭제 — 확인 후 삭제하고 홈으로", async () => {
    renderMy({ "DELETE /api/v1/account": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "계정 삭제" }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }),
    );
    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
  });

  it("계정 삭제 실패 — 대화상자에 오류를 보이고 이동하지 않는다", async () => {
    renderMy({
      "DELETE /api/v1/account": () => json({ error: "internal" }, 500),
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "계정 삭제" }),
    );
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "삭제" }));
    expect(
      await within(dialog).findByText(
        "요청을 처리하지 못했어요. 다시 시도해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("로그아웃", async () => {
    document.cookie = "XSRF-TOKEN=t";
    renderMy({ "POST /logout": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "로그아웃" }),
    );
    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
  });
});
