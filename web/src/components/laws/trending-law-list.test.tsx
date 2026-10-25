import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { TrendingItem } from "@/lib/api/types";
import { TrendingLawList } from "./trending-law-list";

const ITEMS: TrendingItem[] = [
  {
    rank: 1,
    lawId: "001809",
    title: "주택법",
    effectiveDate: "2026-11-03",
    views: 12,
    previousRank: 3,
  },
  {
    rank: 2,
    lawId: "001234",
    title: "근로기준법 시행령",
    effectiveDate: "2026-11-17",
    views: 5,
    previousRank: null,
  },
];

describe("TrendingLawList", () => {
  it("제목·이번 주 표시·순위·법령 링크·변화(글자 병기)·전체 보기 → 자주 찾는 탭", () => {
    render(<TrendingLawList items={ITEMS} />);

    expect(
      screen.getByRole("heading", { name: "자주 찾는 법령" }),
    ).toBeInTheDocument();
    expect(screen.getByText("이번 주")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주택법" })).toHaveAttribute(
      "href",
      "/laws/001809/2026-11-03",
    );
    expect(screen.getByText("▲2")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByText("2계단 올랐어요")).toHaveClass("sr-only");
    expect(screen.getByText("NEW")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "전체 보기" })).toHaveAttribute(
      "href",
      "/laws?tab=trending",
    );
  });

  it("비면 그리지 않는다", () => {
    const { container } = render(<TrendingLawList items={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
