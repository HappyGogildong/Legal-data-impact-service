import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { LawListItem, LawListPage } from "@/lib/api/types";
import { json, mockApi } from "@/test/api-mock";
import { LawListView } from "./law-list-view";

const BOUNDS = { from: "2026-10-25", to: "2026-11-24" };

function item(
  lawId: string,
  title: string,
  effectiveDate: string,
): LawListItem {
  return {
    lawId,
    title,
    effectiveDate,
    lawType: "법률",
    ministry: "국토교통부",
    amendKind: "일부개정",
  };
}

function renderView(firstPage: LawListPage) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <LawListView
        range="month"
        bounds={BOUNDS}
        today="2026-10-25"
        firstPage={firstPage}
      />
    </QueryClientProvider>,
  );
}

describe("LawListView", () => {
  it("기간 필터는 링크, 고른 기간은 aria-current · 행에 부처·시행일·D-N", () => {
    renderView({ items: [item("001809", "주택법", "2026-11-03")] });

    expect(screen.getByRole("link", { name: "이번 주" })).toHaveAttribute(
      "href",
      "/laws?range=week",
    );
    expect(screen.getByRole("link", { name: "이번 달" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "주택법" })).toHaveAttribute(
      "href",
      "/laws/001809/2026-11-03",
    );
    expect(screen.getByText("국토교통부")).toBeInTheDocument();
    expect(screen.getByText("11-03")).toBeInTheDocument();
    expect(screen.getByText("D-9")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "더 보기" }),
    ).not.toBeInTheDocument();
  });

  it("더 보기 — 같은 기간·커서로 다음 쪽을 이어 붙이고, 마지막 쪽이면 버튼이 사라진다", async () => {
    const requests = mockApi({
      "GET /api/v1/laws": () =>
        json({
          items: [item("002000", "소득세법", "2026-11-20")],
          nextCursor: null,
        }),
    });
    renderView({
      items: [item("001809", "주택법", "2026-11-03")],
      nextCursor: "CURSOR-1",
    });

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(
      await screen.findByRole("link", { name: "소득세법" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주택법" })).toBeInTheDocument();
    const url = new URL(requests[0].url);
    expect(url.searchParams.get("from")).toBe("2026-10-25");
    expect(url.searchParams.get("to")).toBe("2026-11-24");
    expect(url.searchParams.get("cursor")).toBe("CURSOR-1");
    expect(url.searchParams.get("limit")).toBe("20");
    expect(
      screen.queryByRole("button", { name: "더 보기" }),
    ).not.toBeInTheDocument();
  });

  it("더 불러오기 실패 — 안내와 다시 시도", async () => {
    mockApi({
      "GET /api/v1/laws": () => json({ error: "x", message: "실패" }, 500),
    });
    renderView({
      items: [item("001809", "주택법", "2026-11-03")],
      nextCursor: "CURSOR-1",
    });

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "더 불러오지 못했어요",
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("기간에 법령이 없으면 그렇게 말한다", () => {
    renderView({ items: [] });
    expect(
      screen.getByText("이 기간에 시행되는 법령이 없어요."),
    ).toBeInTheDocument();
  });
});
