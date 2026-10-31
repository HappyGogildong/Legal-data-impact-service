import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import InfoPage from "./page";

describe("InfoPage", () => {
  it("문의처 — 이메일 링크(mailto)", () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          lawsUpdatedAt: "2026-10-22T19:12:31Z",
          pendingLawCount: 912,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <InfoPage />
      </QueryClientProvider>,
    );

    expect(
      screen.getByRole("link", { name: "rbgus630@gmail.com" }),
    ).toHaveAttribute("href", "mailto:rbgus630@gmail.com");
  });

  it("처리방침 요약 — 처리 위탁(Anthropic·OpenAI)", () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          lawsUpdatedAt: "2026-10-22T19:12:31Z",
          pendingLawCount: 912,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <InfoPage />
      </QueryClientProvider>,
    );

    expect(screen.getByText(/처리 위탁/)).toBeInTheDocument();
    expect(screen.getByText(/Anthropic/)).toBeInTheDocument();
    expect(screen.getByText(/OpenAI/)).toBeInTheDocument();
  });

  it("처리방침 요약 — 수집하는 항목에 북마크·이력", () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          lawsUpdatedAt: "2026-10-22T19:12:31Z",
          pendingLawCount: 912,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <InfoPage />
      </QueryClientProvider>,
    );

    const sentence =
      "직접 저장한 관심 법령 북마크와 이력(질문·결과·내 영향·대응 방안·상황 답) — 이력·북마크 화면에서 언제든 지울 수 있고 계정을 삭제하면 함께 지워져요.";
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName === "P" &&
          (element.textContent ?? "").replace(/\s+/g, " ").includes(sentence),
      ),
    ).toBeInTheDocument();
  });

  it("처리방침 요약 — 수집하는 항목에 구독·알림 기록", () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          lawsUpdatedAt: "2026-10-22T19:12:31Z",
          pendingLawCount: 912,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <InfoPage />
      </QueryClientProvider>,
    );

    const sentence =
      "구독한 법령과 알림 기록도 저장해요 — 구독 관리·알림함에서 지울 수 있고 계정을 삭제하면 함께 지워져요.";
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName === "P" &&
          (element.textContent ?? "").replace(/\s+/g, " ").includes(sentence),
      ),
    ).toBeInTheDocument();
  });
});
