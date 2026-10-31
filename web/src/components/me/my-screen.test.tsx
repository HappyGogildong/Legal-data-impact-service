import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { reloadTo } from "@/lib/navigation";
import {
  clearSituationMemory,
  readSituation,
  situationKey,
  writeSituation,
} from "@/lib/situation-store";
import { setReturnTo } from "@/lib/return-to";
import { empty, json, mockApi } from "@/test/api-mock";
import { MyScreen } from "./my-screen";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/me",
}));

vi.mock("@/lib/navigation", () => ({ reloadTo: vi.fn() }));

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
  vi.mocked(reloadTo).mockReset();
  sessionStorage.clear();
  clearSituationMemory();
});

describe("MyScreen", () => {
  it("계정·프로필 요약·동의 상태", async () => {
    renderMy();
    expect(await screen.findByText("카카오")).toBeInTheDocument();
    expect(screen.getByText("2026-10-23")).toBeInTheDocument();
    expect(await screen.findByText("7개 중 4개 입력")).toBeInTheDocument();
    expect(screen.getByText("2026-09 버전 · 2026-10-23")).toBeInTheDocument();
    expect(screen.getByText("최신")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /북마크/ })).toHaveAttribute(
      "href",
      "/me/bookmarks",
    );
    expect(screen.getByRole("link", { name: /^이력/ })).toHaveAttribute(
      "href",
      "/me/history",
    );
    expect(screen.getByRole("link", { name: /구독 관리/ })).toHaveAttribute(
      "href",
      "/me/subscriptions",
    );
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
    setReturnTo("/ask");
    renderMy({ "DELETE /api/v1/account": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "계정 삭제" }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }),
    );
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith("/"));
    expect(sessionStorage.getItem("lia.returnTo")).toBeNull();
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
    expect(reloadTo).not.toHaveBeenCalled();
  });

  it("로그아웃", async () => {
    setReturnTo("/ask");
    document.cookie = "XSRF-TOKEN=t";
    renderMy({ "POST /logout": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "로그아웃" }),
    );
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith("/"));
    expect(sessionStorage.getItem("lia.returnTo")).toBeNull();
  });

  it("로그아웃하면 탭에 둔 상황 답(lia.situation.*)을 지운다", async () => {
    const key = situationKey("001809", "2026-08-04");
    writeSituation(key, {
      answers: { q1: "예" },
      identity: "0123456789abcdef",
    });
    document.cookie = "XSRF-TOKEN=t";
    renderMy({ "POST /logout": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "로그아웃" }),
    );
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith("/"));
    expect(sessionStorage.getItem(key)).toBeNull();
    expect(readSituation(key, "0123456789abcdef")).toEqual({ answers: {} });
  });

  it("계정 삭제해도 탭에 둔 상황 답을 지운다", async () => {
    const key = situationKey("001809", "2026-08-04");
    writeSituation(key, {
      answers: { q1: "예" },
      identity: "0123456789abcdef",
    });
    renderMy({ "DELETE /api/v1/account": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "계정 삭제" }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }),
    );
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith("/"));
    expect(sessionStorage.getItem(key)).toBeNull();
    expect(readSituation(key, "0123456789abcdef")).toEqual({ answers: {} });
  });

  it("프로필 삭제 — 확인 후 삭제하면 대화상자가 닫히고 입력으로 바뀐다", async () => {
    renderMy({ "DELETE /api/v1/profile": () => empty(204) });
    await userEvent.click(
      await screen.findByRole("button", { name: "프로필 삭제" }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }),
    );
    await vi.waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(await screen.findByRole("link", { name: "입력" })).toHaveAttribute(
      "href",
      "/profile",
    );
  });

  it("이메일 동의 — 취소하고 다시 열면 체크가 풀려 있다", async () => {
    renderMy();
    const open = async () =>
      userEvent.click(
        await screen.findByRole("switch", { name: "이메일 알림 받기" }),
      );
    await open();
    await userEvent.click(
      screen.getByRole("checkbox", { name: /알림 이메일 수신에 동의합니다/ }),
    );
    await userEvent.click(screen.getByRole("button", { name: "취소" }));
    await open();
    expect(
      screen.getByRole("checkbox", { name: /알림 이메일 수신에 동의합니다/ }),
    ).not.toBeChecked();
  });

  it("이메일 켜기 실패 뒤 성공하면 오류가 다음 열기에 남지 않는다", async () => {
    let attempts = 0;
    renderMy({
      "DELETE /api/v1/consents/notification-email": () => empty(204),
      "PUT /api/v1/consents/notification-email": () =>
        ++attempts === 1
          ? json({ error: "bad_request" }, 400)
          : json({
              email: "me@example.com",
              consentedAt: "2026-10-24T01:00:00Z",
            }),
    });
    const toggle = await screen.findByRole("switch", {
      name: "이메일 알림 받기",
    });
    await userEvent.click(toggle);
    await userEvent.click(
      screen.getByRole("checkbox", { name: /알림 이메일 수신에 동의합니다/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 켜기" }),
    );
    expect(
      await screen.findByText("로그인한 계정에 이메일이 없어 켤 수 없어요."),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 켜기" }),
    );
    await vi.waitFor(() =>
      expect(toggle).toHaveAttribute("aria-checked", "true"),
    );
    await userEvent.click(toggle); // 끄고
    await vi.waitFor(() =>
      expect(toggle).toHaveAttribute("aria-checked", "false"),
    );
    await userEvent.click(toggle); // 다시 열기
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(
      screen.queryByText("로그인한 계정에 이메일이 없어 켤 수 없어요."),
    ).not.toBeInTheDocument();
  });
});
