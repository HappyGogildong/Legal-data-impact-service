import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LawSummaryCard } from "./law-summary-card";

describe("LawSummaryCard", () => {
  it("문단·주요 내용(칩 → 조문 주소)·불확실한 점·AI 요약 안내", () => {
    render(
      <LawSummaryCard
        summary={{
          text: "절차가 짧아져요.",
          claims: [
            {
              statement: "통합심의가 넓어진다",
              citations: ["LAW:001809@2026-11-03:art:18"],
            },
          ],
          uncertainties: ["세부 기준은 대통령령"],
        }}
        labels={new Map([["LAW:001809@2026-11-03:art:18", "제18조"]])}
      />,
    );

    expect(screen.getByText("절차가 짧아져요.")).toBeInTheDocument();
    expect(screen.getByText("통합심의가 넓어진다")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "제18조" })).toHaveAttribute(
      "href",
      "#art-18",
    );
    expect(screen.getByText("세부 기준은 대통령령")).toBeInTheDocument();
    expect(
      screen.getByText(/근거 조문으로 만든 AI 요약이에요/),
    ).toBeInTheDocument();
  });

  it("주요 내용·불확실한 점이 없으면 그 칸을 그리지 않는다", () => {
    render(
      <LawSummaryCard
        summary={{ text: "짧은 요약", claims: [], uncertainties: [] }}
        labels={new Map()}
      />,
    );
    expect(screen.queryByText("주요 내용")).not.toBeInTheDocument();
    expect(screen.queryByText("불확실한 점")).not.toBeInTheDocument();
  });
});
