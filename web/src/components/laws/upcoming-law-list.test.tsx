import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { LawListItem } from "@/lib/api/types";
import { UpcomingLawList } from "./upcoming-law-list";

const ITEMS: LawListItem[] = [
  {
    lawId: "001809",
    title: "주택법",
    effectiveDate: "2026-11-03",
    lawType: "법률",
    ministry: "국토교통부",
    amendKind: "일부개정",
  },
  {
    lawId: "001234",
    title: "근로기준법 시행령",
    effectiveDate: "2026-11-17",
    lawType: "대통령령",
    ministry: "고용노동부",
    amendKind: "일부개정",
  },
];

describe("UpcomingLawList", () => {
  it("법령명은 상세(S05) 링크, 오늘 기준 D-N, 전체 보기 → /laws", () => {
    render(<UpcomingLawList items={ITEMS} today="2026-10-25" />);

    expect(
      screen.getByRole("heading", { name: "곧 시행되는 법령" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주택법" })).toHaveAttribute(
      "href",
      "/laws/001809/2026-11-03",
    );
    expect(screen.getByText("D-9")).toBeInTheDocument();
    expect(screen.getByText("D-23")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "전체 보기" })).toHaveAttribute(
      "href",
      "/laws",
    );
    expect(screen.queryByText("국토교통부")).not.toBeInTheDocument();
  });

  it("목록이 비면 아무것도 그리지 않는다", () => {
    const { container } = render(
      <UpcomingLawList items={[]} today="2026-10-25" />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
