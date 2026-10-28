import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setReturnTo } from "@/lib/return-to";
import { json, mockApi } from "@/test/api-mock";
import { ConsentScreen } from "./consent-screen";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/consent",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const CONSENT = {
  policyVersion: "2026-12",
  consentedAt: "2026-10-24T01:00:00Z",
  upToDate: true,
};

function renderConsent() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ConsentScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
});

describe("ConsentScreen", () => {
  it("처음 동의 — 두 항목을 모두 체크해야 보내고, 동의 후 프로필로(돌아올 경로가 없으면 그다음은 홈)", async () => {
    const requests = mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}),
      "PUT /api/v1/consents/profile": () => json(CONSENT),
    });
    renderConsent();

    const submit = await screen.findByRole("button", {
      name: "동의하고 프로필 입력",
    });
    expect(submit).toBeDisabled();
    await userEvent.click(
      screen.getByRole("checkbox", { name: /만 14세 이상입니다/ }),
    );
    await userEvent.click(
      screen.getByRole("checkbox", { name: /프로필 수집·이용에 동의합니다/ }),
    );
    await userEvent.click(submit);

    await vi.waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/profile"),
    );
    const put = requests.find((request) => request.method === "PUT")!;
    expect(await put.json()).toEqual({ over14: true });
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/");
    // 이동 중에는 보조 버튼도 막는다 — 누르면 돌아올 경로가 지워진다
    expect(
      screen.getByRole("button", { name: "동의하지 않고 계속 보기" }),
    ).toBeDisabled();
  });

  it("처음 동의 — 돌아올 경로가 있으면 그대로 둔다(프로필 뒤 그 화면으로)", async () => {
    setReturnTo("/ask");
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}),
      "PUT /api/v1/consents/profile": () => json(CONSENT),
    });
    renderConsent();

    await userEvent.click(
      await screen.findByRole("checkbox", { name: /만 14세 이상입니다/ }),
    );
    await userEvent.click(
      screen.getByRole("checkbox", { name: /프로필 수집·이용에 동의합니다/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 프로필 입력" }),
    );

    await vi.waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/profile"),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/ask");
  });

  it("재동의 — 옛 버전이면 안내하고, 동의 후 돌아올 경로로", async () => {
    setReturnTo("/ask");
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () =>
        json({
          profile: { ...CONSENT, policyVersion: "2026-09", upToDate: false },
        }),
      "PUT /api/v1/consents/profile": () => json(CONSENT),
    });
    renderConsent();

    expect(
      await screen.findByRole("heading", {
        name: "개인정보처리방침이 바뀌었어요",
      }),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("checkbox", { name: /만 14세 이상입니다/ }),
    );
    await userEvent.click(
      screen.getByRole("checkbox", { name: /바뀐 처리방침에 동의합니다/ }),
    );
    await userEvent.click(screen.getByRole("button", { name: "동의하기" }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/ask"));
    expect(sessionStorage.getItem("lia.returnTo")).toBeNull();
  });

  it("만 14세 미만 — 개인화 이용 불가 안내", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}),
    });
    renderConsent();

    await userEvent.click(
      await screen.findByRole("button", { name: "만 14세 미만이에요" }),
    );
    expect(
      screen.getByRole("heading", { name: "개인화 기능을 이용할 수 없어요" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "계정 삭제하기" })).toHaveAttribute(
      "href",
      "/me#delete-account",
    );
  });

  it("만 14세 미만 — 홈으로를 누르면 돌아올 경로를 지운다", async () => {
    setReturnTo("/ask");
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}),
    });
    renderConsent();

    await userEvent.click(
      await screen.findByRole("button", { name: "만 14세 미만이에요" }),
    );
    expect(sessionStorage.getItem("lia.returnTo")).toBe("/ask");
    const home = screen.getByRole("link", { name: "홈으로" });
    home.addEventListener("click", (event) => event.preventDefault()); // jsdom 은 이동을 구현하지 않는다
    await userEvent.click(home);
    expect(sessionStorage.getItem("lia.returnTo")).toBeNull();
  });

  it("동의하지 않고 계속 — 돌아올 경로로", async () => {
    setReturnTo("/ask");
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}),
    });
    renderConsent();

    await userEvent.click(
      await screen.findByRole("button", { name: "동의하지 않고 계속 보기" }),
    );
    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/ask"));
  });

  it("동의 상태 조회가 5xx로 실패하면 멈추지 않고 오류와 다시 시도를 보인다", async () => {
    const requests = mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/consents": () => json({}, 500),
    });
    renderConsent();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "정보를 불러오지 못했어요.",
    );
    const before = requests.filter((request) =>
      request.url.endsWith("/api/v1/consents"),
    ).length;
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await vi.waitFor(() =>
      expect(
        requests.filter((request) => request.url.endsWith("/api/v1/consents"))
          .length,
      ).toBeGreaterThan(before),
    );
  });

  it("세션 조회가 5xx로 실패하면 멈추지 않고 오류와 다시 시도를 보인다", async () => {
    const requests = mockApi({ "GET /api/v1/account": () => json({}, 500) });
    renderConsent();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "정보를 불러오지 못했어요.",
    );
    const before = requests.filter((request) =>
      request.url.endsWith("/api/v1/account"),
    ).length;
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await vi.waitFor(() =>
      expect(
        requests.filter((request) => request.url.endsWith("/api/v1/account"))
          .length,
      ).toBeGreaterThan(before),
    );
  });
});
