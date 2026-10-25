import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { LawHeaderView } from "./law-header-view";
import { SourceBody } from "./source-panel";

describe("S02 → 법령 상세(S05) 링크", () => {
  it("결과 상단 — 법령 상세 보기", () => {
    render(
      <LawHeaderView
        law={{ lawId: "001809", title: "주택법", effectiveDate: "2026-11-03" }}
        alternatives={[]}
        onPickAlternative={vi.fn()}
      />,
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
