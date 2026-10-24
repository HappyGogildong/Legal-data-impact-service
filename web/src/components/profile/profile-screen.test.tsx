import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setReturnTo } from "@/lib/return-to";
import { empty, json, mockApi } from "@/test/api-mock";
import { ProfileScreen } from "./profile-screen";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/profile",
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const SAVED = {
  purposes: ["생활·주거"],
  housingType: "전세",
  updatedAt: "2026-10-24T05:02:00Z",
};

function renderProfile() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ProfileScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
});

describe("ProfileScreen", () => {
  it("선택한 항목만 보내고(전체 교체) 돌아올 경로로", async () => {
    setReturnTo("/ask");
    const requests = mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(404),
      "PUT /api/v1/profile": () => json(SAVED),
    });
    renderProfile();

    await userEvent.click(
      await screen.findByRole("button", { name: "생활·주거" }),
    );
    await userEvent.click(screen.getByRole("radio", { name: "전세" }));
    await userEvent.click(
      screen.getByRole("button", { name: "저장하고 돌아가기" }),
    );

    expect(router.push).toHaveBeenCalledWith("/ask");
    const put = requests.find((request) => request.method === "PUT")!;
    expect(await put.json()).toEqual({
      purposes: ["생활·주거"],
      housingType: "전세",
    });
  });

  it("돌아올 경로가 지금 화면(/profile)이면 무시하고 저장했어요를 보인다", async () => {
    setReturnTo("/profile");
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(404),
      "PUT /api/v1/profile": () => json(SAVED),
    });
    renderProfile();

    await userEvent.click(await screen.findByRole("radio", { name: "전세" }));
    await userEvent.click(
      screen.getByRole("button", { name: "저장하고 돌아가기" }),
    );

    expect(
      await screen.findByRole("heading", { name: "저장했어요" }),
    ).toBeInTheDocument();
    expect(router.push).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("lia.returnTo")).toBeNull();
  });

  it("나이 범위 밖이면 보내지 않는다", async () => {
    const requests = mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(404),
    });
    renderProfile();

    await userEvent.type(
      await screen.findByRole("spinbutton", { name: "나이" }),
      "9",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "저장하고 돌아가기" }),
    );
    expect(
      screen.getByText("나이는 14~120 사이로 입력해 주세요."),
    ).toBeInTheDocument();
    expect(requests.some((request) => request.method === "PUT")).toBe(false);
  });

  it("409 → 재동의 대화상자 → 동의 후 같은 값으로 다시 저장", async () => {
    let attempts = 0;
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(404),
      "GET /api/v1/consents": () => json({}),
      "PUT /api/v1/consents/profile": () =>
        json({
          policyVersion: "2026-12",
          consentedAt: "2026-10-24T01:00:00Z",
          upToDate: true,
        }),
      "PUT /api/v1/profile": () =>
        ++attempts === 1
          ? json({ error: "consent_required", message: "동의 필요" }, 409)
          : json(SAVED),
    });
    renderProfile();

    await userEvent.click(await screen.findByRole("radio", { name: "전세" }));
    await userEvent.click(
      screen.getByRole("button", { name: "저장하고 돌아가기" }),
    );

    const dialog = await screen.findByRole("dialog", {
      name: "프로필을 저장하려면 동의가 필요해요",
    });
    await userEvent.click(
      screen.getByRole("checkbox", { name: /만 14세 이상입니다/ }),
    );
    await userEvent.click(
      screen.getByRole("checkbox", { name: /프로필 수집·이용에 동의합니다/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 저장" }),
    );

    expect(
      await screen.findByRole("heading", { name: "저장했어요" }),
    ).toBeInTheDocument();
    expect(attempts).toBe(2);
    expect(dialog).not.toBeInTheDocument();
  });

  it("재동의 뒤 재저장이 실패하면 대화상자를 닫고 저장 실패 문구를 보인다", async () => {
    let attempts = 0;
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(404),
      "GET /api/v1/consents": () => json({}),
      "PUT /api/v1/consents/profile": () =>
        json({
          policyVersion: "2026-12",
          consentedAt: "2026-10-24T01:00:00Z",
          upToDate: true,
        }),
      "PUT /api/v1/profile": () =>
        ++attempts === 1
          ? json({ error: "consent_required", message: "동의 필요" }, 409)
          : empty(500),
    });
    renderProfile();

    await userEvent.click(await screen.findByRole("radio", { name: "전세" }));
    await userEvent.click(
      screen.getByRole("button", { name: "저장하고 돌아가기" }),
    );

    await screen.findByRole("dialog", {
      name: "프로필을 저장하려면 동의가 필요해요",
    });
    await userEvent.click(
      screen.getByRole("checkbox", { name: /만 14세 이상입니다/ }),
    );
    await userEvent.click(
      screen.getByRole("checkbox", { name: /프로필 수집·이용에 동의합니다/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "동의하고 저장" }),
    );

    expect(
      await screen.findByText("저장하지 못했어요. 다시 시도해 주세요."),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(attempts).toBe(2);
  });

  it("수정 — 현재 값을 채우고 마지막 수정 시각", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => json(SAVED),
    });
    renderProfile();

    expect(await screen.findByRole("radio", { name: "전세" })).toBeChecked();
    expect(screen.getByRole("button", { name: "생활·주거" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      screen.getByText("마지막 수정 2026-10-24 14:02"),
    ).toBeInTheDocument();
  });

  it("프로필 조회가 실패하면 빈 폼 대신 오류 — 저장 버튼이 없어 저장된 값을 덮어쓰지 않는다", async () => {
    mockApi({
      "GET /api/v1/account": () => json(ACCOUNT),
      "GET /api/v1/profile": () => empty(500),
    });
    renderProfile();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "정보를 불러오지 못했어요.",
    );
    expect(
      screen.queryByRole("button", { name: "저장하고 돌아가기" }),
    ).not.toBeInTheDocument();
  });
});
