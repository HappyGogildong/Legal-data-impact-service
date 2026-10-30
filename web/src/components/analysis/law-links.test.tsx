import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { json, mockApi } from "@/test/api-mock";
import { LawHeaderView } from "./law-header-view";
import { SourceBody } from "./source-panel";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/ask",
}));

describe("S02 → 법령 상세(S05) 링크", () => {
  it("결과 상단 — 법령 상세 보기", () => {
    // 헤더의 북마크 ☆ 가 세션을 묻는다 — 익명이면 북마크 목록은 부르지 않는다
    mockApi({
      "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <LawHeaderView
          law={{
            lawId: "001809",
            title: "주택법",
            effectiveDate: "2026-11-03",
          }}
          alternatives={[]}
          onPickAlternative={vi.fn()}
        />
      </QueryClientProvider>,
    );
    expect(
      screen.getByRole("link", { name: "법령 상세 보기 →" }),
    ).toHaveAttribute("href", "/laws/001809/2026-11-03");
  });

  it("근거 — 조문 고유 주소로, 시행일 없는 인용키는 링크 없음", () => {
    const { rerender } = render(
      <SourceBody
        source={{
          sourceId: "LAW:001809@2026-11-03:art:18",
          label: "주택법 제18조",
          text: "① 통합심의한다.",
        }}
      />,
    );
    expect(
      screen.getByRole("link", { name: "법령 상세에서 보기 →" }),
    ).toHaveAttribute("href", "/laws/001809/2026-11-03#art-18");

    rerender(
      <SourceBody
        source={{
          sourceId: "LAW:001809:art:18",
          label: "주택법 제18조",
          text: "① 심의한다.",
        }}
      />,
    );
    expect(
      screen.queryByRole("link", { name: "법령 상세에서 보기 →" }),
    ).not.toBeInTheDocument();
  });
});
