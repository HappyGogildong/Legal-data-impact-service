import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { CitedSource, SituationQuestion } from "@/lib/api/types";
import { SituationCheck } from "./situation-check";

const SOURCE_ID = "LAW:001809@2026-08-04:art:18";
const questions: SituationQuestion[] = [
  {
    id: "q1",
    text: "계약을 시행일 이전에 맺었나요?",
    basis: SOURCE_ID,
    answerType: "YES_NO",
  },
  { id: "q2", text: "착공 예정일이 언제인가요?", answerType: "DATE" },
  {
    id: "q3",
    text: "어떤 유형인가요?",
    answerType: "CHOICE",
    options: ["아파트", "오피스텔"],
  },
];
const sources = new Map<string, CitedSource>([
  [SOURCE_ID, { sourceId: SOURCE_ID, label: "주택법 제18조(통합심의)" }],
]);

function renderCheck(
  props: Partial<React.ComponentProps<typeof SituationCheck>> = {},
) {
  const onAnswer = vi.fn();
  const onSubmit = vi.fn();
  const onCite = vi.fn();
  render(
    <SituationCheck
      questions={questions}
      answers={{}}
      onAnswer={onAnswer}
      onSubmit={onSubmit}
      pending={false}
      sources={sources}
      selected={null}
      onCite={onCite}
      {...props}
    />,
  );
  return { onAnswer, onSubmit, onCite };
}

describe("SituationCheck", () => {
  it("제목과 저장 안내", () => {
    renderCheck();
    expect(
      screen.getByRole("heading", { name: "상황을 알려주시면 더 정확해져요" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "답은 이 법령 판단에만 써요. 이력에 저장할 때만 함께 남아요.",
      ),
    ).toBeInTheDocument();
  });

  it("예/아니오 질문 — 예·아니오·모름 버튼", async () => {
    const { onAnswer } = renderCheck();
    const item = screen
      .getByText(/계약을 시행일 이전에 맺었나요/)
      .closest("li")!;
    const buttons = [...item.querySelectorAll("button")];
    expect(buttons.map((button) => button.textContent)).toEqual([
      "주택법 제18조",
      "예",
      "아니오",
      "모름",
    ]);

    await userEvent.click(buttons[1]);
    expect(onAnswer).toHaveBeenCalledWith("q1", "예");
  });

  it("날짜 질문 — 날짜 입력", async () => {
    const { onAnswer } = renderCheck();
    const input = screen.getByLabelText("착공 예정일이 언제인가요?", {
      selector: "input",
    });
    await userEvent.type(input, "2027-03-01");
    expect(onAnswer).toHaveBeenLastCalledWith("q2", "2027-03-01");
  });

  it("선택지 질문 — 선택지와 모름", async () => {
    const { onAnswer } = renderCheck();
    const item = screen.getByText("어떤 유형인가요?").closest("li")!;
    expect(
      [...item.querySelectorAll("button")].map((button) => button.textContent),
    ).toEqual(["아파트", "오피스텔", "모름"]);

    await userEvent.click(screen.getByRole("button", { name: "오피스텔" }));
    expect(onAnswer).toHaveBeenCalledWith("q3", "오피스텔");
  });

  it("근거 칩을 누르면 onCite", async () => {
    const { onCite } = renderCheck();
    await userEvent.click(
      screen.getByRole("button", { name: "주택법 제18조" }),
    );
    expect(onCite).toHaveBeenCalledWith(SOURCE_ID);
  });

  it("모름 아닌 답이 없으면 확인 버튼 비활성", () => {
    renderCheck({ answers: { q1: "모름" } });
    expect(
      screen.getByRole("button", { name: "대응 방안 확인하기" }),
    ).toBeDisabled();
  });

  it("모름 아닌 답이 있으면 활성이고, 누르면 onSubmit", async () => {
    const { onSubmit } = renderCheck({ answers: { q1: "예" } });
    const button = screen.getByRole("button", { name: "대응 방안 확인하기" });
    expect(button).toBeEnabled();
    await userEvent.click(button);
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it("질문마다 선택 버튼을 묶어 질문 문장으로 이름 붙인다(모름 포함, 날짜도)", () => {
    renderCheck();

    const yesNo = screen.getByRole("group", {
      name: /계약을 시행일 이전에 맺었나요/,
    });
    expect(
      [...yesNo.querySelectorAll("button")].map((b) => b.textContent),
    ).toEqual(["예", "아니오", "모름"]);

    const date = screen.getByRole("group", {
      name: "착공 예정일이 언제인가요?",
    });
    expect(date).toContainElement(
      screen.getByLabelText("착공 예정일이 언제인가요?", { selector: "input" }),
    );
    expect(
      within(date).getByRole("button", { name: "모름" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("group", { name: "어떤 유형인가요?" }),
    ).toBeInTheDocument();
  });
});
