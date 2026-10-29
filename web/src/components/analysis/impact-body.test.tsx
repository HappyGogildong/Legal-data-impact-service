import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ImpactResult } from "@/lib/api/types";
import { ImpactBody } from "./impact-body";

const result: ImpactResult = {
  actions: [
    { what: "계약서를 점검한다", deadline: "2026-08-03" },
    { what: "착공 일정을 확인한다" },
  ],
};

function renderBody(stepNotes?: { index?: number; note?: string }[]) {
  render(
    <ImpactBody
      dimension="action"
      result={result}
      sources={new Map()}
      selected={null}
      onCite={() => {}}
      stepNotes={stepNotes}
    />,
  );
}

describe("ImpactBody — 내 상황 반영(D71)", () => {
  it("stepNotes 가 가리키는 단계에만 배지와 문장", () => {
    renderBody([{ index: 1, note: "시행일 이전 계약이라 이전 기준" }]);

    const [first, second] = screen.getAllByRole("listitem");
    expect(first).not.toHaveTextContent("내 상황 반영");
    expect(second).toHaveTextContent("내 상황 반영");
    expect(second).toHaveTextContent("시행일 이전 계약이라 이전 기준");
    expect(screen.getAllByText("내 상황 반영")).toHaveLength(1);
  });

  it("stepNotes 가 없으면 배지가 없다", () => {
    renderBody();
    expect(screen.queryByText("내 상황 반영")).not.toBeInTheDocument();
  });
});
