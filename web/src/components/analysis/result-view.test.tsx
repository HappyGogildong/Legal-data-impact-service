import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { resolvedResponse } from "@/test/fixtures";
import { mockViewport } from "@/test/viewport";
import { ResultView } from "./result-view";

const ask = { query: "주택법 바뀌면 뭐가 달라져?" };

function renderResult(onAsk = vi.fn()) {
  render(<ResultView response={resolvedResponse} ask={ask} onAsk={onAsk} />);
  return onAsk;
}

describe("ResultView — 모바일", () => {
  it("법령 헤더(법령명·D-N·시행일·종류·부처)", () => {
    vi.useFakeTimers({
      toFake: ["Date"],
      now: new Date("2026-07-25T09:00:00+09:00"),
    });
    renderResult();

    expect(
      screen.getByRole("heading", { level: 1, name: "주택법" }),
    ).toBeInTheDocument();
    expect(screen.getByText("D-10")).toBeInTheDocument();
    expect(
      screen.getByText("2026-08-04 시행 · 법률 · 국토교통부"),
    ).toBeInTheDocument();
  });

  it("요약만 펼치고, 나머지 카드는 제목·상태", () => {
    renderResult();

    expect(screen.getByText("통합심의 대상이 넓어진다.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /무엇이 바뀌나\s*조문 1개/ }),
    ).toHaveAttribute("aria-expanded", "false");
    expect(
      screen.getByRole("button", { name: /내 영향\s*프로필 필요/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("시행령 위임 내용은 아직 확정되지 않았어요."),
    ).toBeInTheDocument();
  });

  it("미충족 카드 — 사유별 안내", async () => {
    renderResult();
    await userEvent.click(screen.getByRole("button", { name: /내 영향/ }));
    expect(
      screen.getByText("내 상황에 맞춘 분석이 필요한 부분이에요"),
    ).toBeInTheDocument();
  });

  it("바뀐 조문 펼치면 현행·개정 대비(바뀐 구간 강조)", async () => {
    renderResult();
    await userEvent.click(
      screen.getByRole("button", { name: /무엇이 바뀌나/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: /제18조\(사업계획의 통합심의\)/ }),
    );

    expect(
      screen.getByText("통합하여", { selector: "mark" }),
    ).toBeInTheDocument();
  });

  it("인용 칩 → 아래 시트에 근거 조문", async () => {
    renderResult();
    await userEvent.click(
      screen.getByRole("button", { name: "주택법 제18조" }),
    );

    const sheet = screen.getByRole("dialog", { name: "근거 조문" });
    expect(
      within(sheet).getByText("주택법 제18조(사업계획의 통합심의)"),
    ).toBeInTheDocument();
  });

  it("다른 시행일 → 그 시행본으로 다시 분석", async () => {
    const onAsk = renderResult();
    await userEvent.click(
      screen.getByRole("button", { name: "2027-02-01 시행본으로 보기" }),
    );
    expect(onAsk).toHaveBeenCalledWith({
      query: ask.query,
      lawRef: { lawId: "001809", effectiveDate: "2027-02-01" },
    });
  });

  it("후속 질문 — 법령 맥락을 붙이고, 빼면 붙이지 않는다", async () => {
    const onAsk = renderResult();
    const input = screen.getByRole("textbox", { name: "후속 질문" });

    await userEvent.type(input, "언제부터 적용돼?");
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));
    expect(onAsk).toHaveBeenLastCalledWith({
      query: "언제부터 적용돼?",
      lawRef: { lawId: "001809", effectiveDate: "2026-08-04" },
    });

    await userEvent.click(
      screen.getByRole("button", { name: "법령 맥락 빼기" }),
    );
    await userEvent.type(input, "다른 법은?");
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));
    expect(onAsk).toHaveBeenLastCalledWith({
      query: "다른 법은?",
      lawRef: undefined,
    });
  });
});

describe("ResultView — 법령 헤더 방어", () => {
  it("시행일 없는 다른 시행본은 그리지 않는다", () => {
    render(
      <ResultView
        response={{
          ...resolvedResponse,
          alternatives: [
            { lawId: "001809", title: "주택법" },
            { lawId: "001809", effectiveDate: "2027-02-01" },
          ],
        }}
        ask={ask}
        onAsk={vi.fn()}
      />,
    );

    expect(
      screen.getAllByRole("button", { name: /시행본으로 보기/ }),
    ).toHaveLength(1);
    expect(
      screen.queryByRole("button", { name: /undefined/ }),
    ).not.toBeInTheDocument();
  });

  it("제목·법령 ID 가 없으면 '법령 정보' 제목", () => {
    render(
      <ResultView
        response={{ ...resolvedResponse, law: { ref: "LAW:x" } }}
        ask={ask}
        onAsk={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("heading", { level: 1, name: "법령 정보" }),
    ).toBeInTheDocument();
  });
});

describe("ResultView — 데스크톱", () => {
  it("요약·변경점 기본 펼침, 인용 누르면 오른쪽 패널이 근거로 바뀌고 뒷받침 문장 강조", async () => {
    mockViewport(true);
    renderResult();

    expect(
      screen.getByRole("region", { name: "법령 정보" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /무엇이 바뀌나/ }),
    ).toHaveAttribute("aria-expanded", "true");

    await userEvent.click(
      screen.getAllByRole("button", { name: "주택법 제18조" })[0],
    );

    expect(
      screen.getByRole("region", { name: "근거 조문" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("통합심의 대상이 넓어진다.").closest("li"),
    ).toHaveAttribute("data-highlighted", "true");

    await userEvent.click(
      screen.getByRole("button", { name: "← 법령 정보로" }),
    );
    expect(
      screen.getByRole("region", { name: "법령 정보" }),
    ).toBeInTheDocument();
  });
});
