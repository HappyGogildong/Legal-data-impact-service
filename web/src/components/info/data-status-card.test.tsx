import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DataStatusCard } from "./data-status-card";

function renderCard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <DataStatusCard />
    </QueryClientProvider>,
  );
}

function json(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

describe("DataStatusCard", () => {
  it("갱신 시각(서울)·시행예정 법령 수", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ lawsUpdatedAt: "2026-10-22T19:12:31Z", pendingLawCount: 912 }),
    );
    renderCard();

    expect(await screen.findByText("2026-10-23 04:12")).toBeInTheDocument();
    expect(screen.getByText("912건")).toBeInTheDocument();
  });

  it("갱신 기록이 없으면 그렇게 말한다", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json({ lawsUpdatedAt: null, pendingLawCount: 0 }),
    );
    renderCard();
    expect(
      await screen.findByText("아직 갱신 기록이 없어요"),
    ).toBeInTheDocument();
  });
});
