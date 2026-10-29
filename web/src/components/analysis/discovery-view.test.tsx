import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Discovery } from "@/lib/api/types";
import { mockApi, empty } from "@/test/api-mock";
import { DiscoveryView } from "./discovery-view";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/ask",
}));

const CARD = {
  lawId: "001809",
  title: "주택법",
  effectiveDate: "2026-11-03",
  ministry: "국토교통부",
  summary: "분양 주택의 거주 의무가 바뀌어요.",
  summaryReady: true,
  matched: {
    sourceId: "LAW:001809@2026-11-03:art:46",
    label: "제46조 입주자의 거주의무",
  },
};
const PENDING_SUMMARY = {
  ...CARD,
  lawId: "002000",
  title: "건축법",
  summary: "건축물 안전 기준을 정비하려는 것임…",
  summaryReady: false,
  matched: { sourceId: "LAW:002000@2026-11-03", label: "개정 이유" },
};

function renderView(discovery: Discovery, onAsk = vi.fn()) {
  mockApi({ "GET /api/v1/account": () => empty(401) });
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <DiscoveryView
        discovery={discovery}
        ask={{ query: "전세 관련 바뀌는 법" }}
        onAsk={onAsk}
      />
    </QueryClientProvider>,
  );
  return onAsk;
}

beforeEach(() => {
  router.push.mockReset();
});

describe("DiscoveryView", () => {
  it("후보 카드 — 법령명·쉬운 요약·근거 칩(S05 조문 주소)·상세 링크", () => {
    renderView({ candidates: [CARD], profileApplied: false });

    expect(
      screen.getByText("질문과 관련 있어 보이는 시행예정 법령 1건"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("분양 주택의 거주 의무가 바뀌어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "제46조 입주자의 거주의무" }),
    ).toHaveAttribute("href", "/laws/001809/2026-11-03#art-46");
    expect(screen.getByRole("link", { name: "상세 보기" })).toHaveAttribute(
      "href",
      "/laws/001809/2026-11-03",
    );
  });

  it("요약이 없으면 개정 이유 앞부분과 요약 준비 중, 개정 이유 칩은 상세 맨 위", () => {
    renderView({ candidates: [PENDING_SUMMARY], profileApplied: false });

    expect(screen.getByText("요약 준비 중")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "개정 이유" })).toHaveAttribute(
      "href",
      "/laws/002000/2026-11-03",
    );
  });

  it("이 법령으로 분석 — 원래 질문 + lawRef 로 다시 묻는다(토글 없이)", async () => {
    const onAsk = renderView({ candidates: [CARD], profileApplied: false });

    await userEvent.click(
      screen.getByRole("button", { name: "이 법령으로 분석" }),
    );

    expect(onAsk).toHaveBeenCalledWith({
      query: "전세 관련 바뀌는 법",
      lawRef: { lawId: "001809", effectiveDate: "2026-11-03" },
    });
  });

  it("토글을 켜면 같은 질문을 profileSearch 로 다시 묻는다", async () => {
    const onAsk = renderView({ candidates: [CARD], profileApplied: false });

    await userEvent.click(
      screen.getByRole("switch", { name: "내 프로필 기준으로 찾기" }),
    );

    expect(onAsk).toHaveBeenCalledWith({
      query: "전세 관련 바뀌는 법",
      profileSearch: true,
    });
    expect(
      screen.getByText(
        "켜면 프로필 항목이 검색 처리를 위해 외부 서비스로 전송돼요",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: "내 프로필 기준으로 찾기" }),
    ).toHaveAccessibleDescription(
      "켜면 프로필 항목이 검색 처리를 위해 외부 서비스로 전송돼요",
    );
  });

  it("프로필을 못 쓴 사유가 오면 개인화 안내를 보여 주고 목록은 그대로", async () => {
    renderView({
      candidates: [CARD],
      profileApplied: false,
      profileBlocked: { reason: "PROFILE_REQUIRED", message: "x" },
    });

    expect(
      await screen.findByRole("button", { name: "로그인하기" }),
    ).toBeInTheDocument();
    expect(screen.getByText("주택법")).toBeInTheDocument();
  });

  it("결과 없음 — 안내와 질문 고치기(질문창 초안으로 홈)", async () => {
    renderView({ candidates: [], profileApplied: false });

    expect(
      screen.getByText("관련 시행예정 법령을 찾지 못했어요"),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "질문 고치기" }));
    expect(sessionStorage.getItem("lia.draft")).toBe("전세 관련 바뀌는 법");
    expect(router.push).toHaveBeenCalledWith("/");
  });
});
