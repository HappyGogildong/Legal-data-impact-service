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
});
