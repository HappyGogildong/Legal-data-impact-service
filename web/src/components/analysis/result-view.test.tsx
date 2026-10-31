import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderUi, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AnalysisResponse } from "@/lib/api/types";
import {
  clearSituationMemory,
  situationKey,
  writeSituation,
} from "@/lib/situation-store";
import { json, mockApi } from "@/test/api-mock";
import { resolvedResponse } from "@/test/fixtures";
import { mockViewport } from "@/test/viewport";
import { ResultView } from "./result-view";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/ask",
}));

const ask = { query: "주택법 바뀌면 뭐가 달라져?" };

// 헤더의 북마크 ☆ 가 세션을 묻는다 — 익명이면 북마크 목록은 부르지 않는다
beforeEach(() => {
  mockApi({
    "GET /api/v1/account": () => json({ error: "unauthenticated" }, 401),
  });
});

function render(ui: ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return renderUi(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

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

  it("응답에 내 영향·대응 방안이 없으면 보기 버튼, 누르면 같은 법령으로 다시 묻는다", async () => {
    const onAsk = vi.fn();
    const response = { ...resolvedResponse, unmet: {} }; // impact·action 없음
    render(<ResultView response={response} ask={ask} onAsk={onAsk} />);

    await userEvent.click(
      screen.getByRole("button", { name: "내 영향·대응 방안 보기" }),
    );

    expect(onAsk).toHaveBeenCalledWith({
      query: `${resolvedResponse.law!.title} 내 영향과 대응 방안`,
      lawRef: {
        lawId: resolvedResponse.law!.lawId,
        effectiveDate: resolvedResponse.law!.effectiveDate,
      },
    });
  });

  it("법령명이 없으면 보기 버튼을 두지 않는다(앞 공백만 있는 질문 방지)", () => {
    const lawWithoutTitle = { ...resolvedResponse.law!, title: undefined };
    render(
      <ResultView
        response={{ ...resolvedResponse, law: lawWithoutTitle, unmet: {} }}
        ask={ask}
        onAsk={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "내 영향·대응 방안 보기" }),
    ).not.toBeInTheDocument();
  });

  it("내 영향 미충족 카드가 있으면 보기 버튼을 따로 두지 않는다", () => {
    renderResult();
    expect(
      screen.queryByRole("button", { name: "내 영향·대응 방안 보기" }),
    ).not.toBeInTheDocument();
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

describe("ResultView — 대응 방안 상황 확인(D71)", () => {
  const withSituation: AnalysisResponse = {
    ...resolvedResponse,
    answer: {
      ...resolvedResponse.answer,
      impact: { summary: "내 영향 요약" },
      action: { actions: [{ what: "기본 대응 단계" }] },
    },
    unmet: {},
    situation: {
      setId: "0123456789abcdef",
      questions: [
        {
          id: "q1",
          text: "계약을 시행일 이전에 맺었나요?",
          answerType: "YES_NO",
        },
      ],
      stepNotes: [{ index: 0, note: "기본 상황 메모" }],
    },
  };

  function renderWithClient(response: AnalysisResponse) {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <ResultView response={response} ask={ask} onAsk={vi.fn()} />
      </QueryClientProvider>,
    );
  }

  it("situation.questions 가 있으면 대응 방안 카드 아래에 상황 확인 블록", () => {
    renderWithClient(withSituation);

    const actionCard = screen.getByRole("button", { name: "대응 방안" });
    const block = screen.getByRole("heading", {
      name: "상황을 알려주시면 더 정확해져요",
    });
    expect(
      actionCard.compareDocumentPosition(block) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("situation 이 없으면 상황 확인 블록이 없다", () => {
    renderWithClient({ ...withSituation, situation: undefined });
    expect(
      screen.queryByText("상황을 알려주시면 더 정확해져요"),
    ).not.toBeInTheDocument();
  });

  it("질문 묶음 식별자(setId)가 없으면 재생성을 보낼 수 없어 상황 확인 블록을 내지 않는다", () => {
    renderWithClient({
      ...withSituation,
      situation: { ...withSituation.situation, setId: undefined },
    });
    expect(
      screen.queryByText("상황을 알려주시면 더 정확해져요"),
    ).not.toBeInTheDocument();
  });

  beforeEach(() => {
    sessionStorage.clear();
    clearSituationMemory();
  });

  const revisionState = (extra = {}) => ({
    answers: { q1: "예" },
    identity: withSituation.situation!.setId!,
    revision: {
      action: { actions: [{ what: "재생성된 대응 단계" }] },
      sources: [],
      stepNotes: [{ index: 0, note: "시행일 이전 계약이라 이전 기준" }],
      ...extra,
    },
  });

  it("다른 질문 묶음으로 저장된 옛 revision 은 보여 주지 않는다", async () => {
    writeSituation(situationKey("001809", "2026-08-04"), {
      ...revisionState(),
      identity: "fedcba9876543210",
    });
    renderWithClient(withSituation);

    await userEvent.click(screen.getByRole("button", { name: "대응 방안" }));

    expect(screen.getByText(/기본 대응 단계/)).toBeInTheDocument();
    expect(screen.queryByText(/재생성된 대응 단계/)).not.toBeInTheDocument();
    expect(
      screen.queryByText("시행일 이전 계약이라 이전 기준"),
    ).not.toBeInTheDocument();
  });

  it("질문 묶음(setId) 없는 새 응답은 저장된 옛 revision 을 덮어쓰지 않는다 — 응답 자신의 대응 방안", async () => {
    writeSituation(situationKey("001809", "2026-08-04"), revisionState());
    renderWithClient({ ...withSituation, situation: undefined });

    await userEvent.click(screen.getByRole("button", { name: "대응 방안" }));

    expect(screen.getByText(/기본 대응 단계/)).toBeInTheDocument();
    expect(screen.queryByText(/재생성된 대응 단계/)).not.toBeInTheDocument();
    expect(screen.queryByText("내 상황 반영")).not.toBeInTheDocument();
  });

  it("대응 방안 unmet 응답도 저장된 옛 revision 을 되살리지 않는다 — 미충족 안내", async () => {
    writeSituation(situationKey("001809", "2026-08-04"), revisionState());
    const { action: _unused, ...answerWithoutAction } = withSituation.answer!;
    void _unused;
    renderWithClient({
      ...withSituation,
      answer: answerWithoutAction,
      situation: undefined,
      unmet: { action: { reason: "DAILY_LIMIT", message: "" } },
    });

    await userEvent.click(screen.getByRole("button", { name: /대응 방안/ }));

    expect(
      await screen.findByText("오늘 개인화 분석 한도를 다 썼어요"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/재생성된 대응 단계/)).not.toBeInTheDocument();
    expect(screen.queryByText("내 상황 반영")).not.toBeInTheDocument();
  });

  it("revision 에 stepNotes 가 없으면 기본 응답의 메모를 붙이지 않는다", async () => {
    writeSituation(
      situationKey("001809", "2026-08-04"),
      revisionState({ stepNotes: undefined }),
    );
    renderWithClient(withSituation);

    await userEvent.click(screen.getByRole("button", { name: "대응 방안" }));

    expect(screen.getByText(/재생성된 대응 단계/)).toBeInTheDocument();
    expect(screen.queryByText("내 상황 반영")).not.toBeInTheDocument();
    expect(screen.queryByText("기본 상황 메모")).not.toBeInTheDocument();
  });

  it("이력에 저장한 뒤 상황 답으로 대응 방안을 다시 만들면 '이력에 저장' 이 다시 나온다(D73)", async () => {
    mockApi({
      "GET /api/v1/account": () =>
        json({ provider: "kakao", createdAt: "2026-10-23T05:00:00Z" }),
      "POST /api/v1/history": () => json({ id: 7 }, 201),
      "POST /api/v1/analyses/action-revisions": () =>
        json({
          action: { actions: [{ what: "재생성된 대응 단계" }] },
          sources: [],
          stepNotes: [],
        }),
    });
    renderWithClient({ ...withSituation, analysisId: "a-1" });

    await userEvent.click(
      await screen.findByRole("button", { name: "이력에 저장" }),
    );
    expect(await screen.findByText(/저장했어요/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "이력에 저장" }),
    ).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "예" }));
    await userEvent.click(
      screen.getByRole("button", { name: "대응 방안 확인하기" }),
    );

    expect(
      await screen.findByRole("button", { name: "이력에 저장" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/저장했어요/)).not.toBeInTheDocument();
  });

  it("탭 저장소에 revision 이 있으면 대응 방안 카드가 그 action 을 보여 준다(내 영향은 그대로)", async () => {
    writeSituation(situationKey("001809", "2026-08-04"), revisionState());
    renderWithClient(withSituation);

    await userEvent.click(screen.getByRole("button", { name: /내 영향/ }));
    await userEvent.click(screen.getByRole("button", { name: "대응 방안" }));

    expect(screen.getByText(/재생성된 대응 단계/)).toBeInTheDocument();
    expect(screen.queryByText(/기본 대응 단계/)).not.toBeInTheDocument();
    expect(screen.getByText("내 상황 반영")).toBeInTheDocument();
    expect(
      screen.getByText("시행일 이전 계약이라 이전 기준"),
    ).toBeInTheDocument();
    expect(screen.getByText("내 영향 요약")).toBeInTheDocument();
  });
});
