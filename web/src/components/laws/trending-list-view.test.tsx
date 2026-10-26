import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { TrendingItem } from "@/lib/api/types";
import { LawTabs } from "./law-tabs";
import { TrendingListView } from "./trending-list-view";

const ITEM: TrendingItem = {
  rank: 1,
  lawId: "001809",
  title: "주택법",
  effectiveDate: "2026-11-03",
  views: 12,
  previousRank: 2,
};

describe("S06 자주 찾는 탭", () => {
  it("탭 — 곧 시행·자주 찾는 링크, 고른 탭은 aria-current", () => {
    render(<LawTabs tab="trending" />);
    expect(screen.getByRole("link", { name: "곧 시행" })).toHaveAttribute(
      "href",
      "/laws",
    );
    expect(screen.getByRole("link", { name: "자주 찾는" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("구간 필터 링크·순위 행(D-N 포함)·집계 안내", () => {
    render(
      <TrendingListView window="week" today="2026-10-25" items={[ITEM]} />,
    );

    expect(screen.getByRole("link", { name: "오늘" })).toHaveAttribute(
      "href",
      "/laws?tab=trending&window=day",
    );
    expect(screen.getByRole("link", { name: "이번 주" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "주택법" })).toHaveAttribute(
      "href",
      "/laws/001809/2026-11-03",
    );
    expect(screen.getByText("D-9")).toBeInTheDocument();
    expect(screen.getByText("▲1")).toBeInTheDocument();
    expect(
      screen.getByText(
        "순위는 조회 횟수만 집계해요. 누가 봤는지는 남기지 않아요.",
      ),
    ).toBeInTheDocument();
  });

  it("집계가 없으면 그렇게 말한다", () => {
    render(<TrendingListView window="day" today="2026-10-25" items={[]} />);
    expect(screen.getByText("아직 집계된 조회가 없어요.")).toBeInTheDocument();
  });
});
