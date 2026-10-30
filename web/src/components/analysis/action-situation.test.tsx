import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ActionRevision, SituationQuestion } from "@/lib/api/types";
import {
  clearSituationMemory,
  readSituation,
  situationKey,
  writeSituation,
} from "@/lib/situation-store";
import { json, mockApi } from "@/test/api-mock";
import { ActionSituation } from "./action-situation";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/ask",
}));

const law = { lawId: "001809", effectiveDate: "2026-08-04", title: "주택법" };
const lawRef = { lawId: law.lawId, effectiveDate: law.effectiveDate };
const questions: SituationQuestion[] = [
  { id: "q1", text: "계약을 시행일 이전에 맺었나요?", answerType: "YES_NO" },
  { id: "q2", text: "착공 예정일이 언제인가요?", answerType: "DATE" },
];
const REVISION: ActionRevision = {
  action: { actions: [{ what: "이전 기준으로 처리한다" }] },
  sources: [],
  stepNotes: [{ index: 0, note: "시행일 이전 계약이라 이전 기준" }],
};
const SET_ID = "0123456789abcdef";
const ROUTE = "POST /api/v1/analyses/action-revisions";

function renderSituation(
  lawProp: { lawId: string; effectiveDate: string; title?: string } = law,
  analysisId?: string,
) {
  const onRevised = vi.fn();
  const onAsk = vi.fn();
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ActionSituation
        law={lawProp}
        questions={questions}
        setId={SET_ID}
        sources={new Map()}
        selected={null}
        onCite={vi.fn()}
        onRevised={onRevised}
        onAsk={onAsk}
        analysisId={analysisId}
      />
    </QueryClientProvider>,
  );
  return { onRevised, onAsk };
}

/** q1 = 예, q2 = 모름 으로 답하고 확인한다. */
async function answerAndSubmit() {
  await userEvent.click(screen.getAllByRole("button", { name: "예" })[0]);
  await userEvent.click(screen.getAllByRole("button", { name: "모름" })[1]);
  await userEvent.click(
    screen.getByRole("button", { name: "대응 방안 확인하기" }),
  );
}

const KEY = situationKey(law.lawId, law.effectiveDate);

beforeEach(() => {
  router.push.mockReset();
  sessionStorage.clear();
  clearSituationMemory();
});

describe("ActionSituation", () => {
  it("답을 보내 재생성 — 모름은 빼고, 성공하면 onRevised·탭 저장·확인된 답 칩", async () => {
    const requests = mockApi({ [ROUTE]: () => json(REVISION) });
    const { onRevised } = renderSituation();

    await answerAndSubmit();

    expect(
      await screen.findByText(/계약을 시행일 이전에 맺었나요\?: 예/),
    ).toBeInTheDocument();
    expect(await requests[0].json()).toEqual({
      lawRef,
      setId: SET_ID,
      answers: [{ questionId: "q1", answer: "예" }],
    });
    expect(onRevised).toHaveBeenCalledWith(REVISION);
    expect(
      readSituation(situationKey(law.lawId, law.effectiveDate), SET_ID),
    ).toEqual({
      answers: { q1: "예", q2: "모름" },
      revision: REVISION,
      identity: SET_ID,
    });
  });

  it("analysisId 를 받으면 재생성 요청에 함께 보낸다(D73)", async () => {
    const requests = mockApi({ [ROUTE]: () => json(REVISION) });
    renderSituation(law, "a-1");

    await answerAndSubmit();

    await screen.findByText(/계약을 시행일 이전에 맺었나요\?: 예/);
    expect(await requests[0].json()).toEqual({
      lawRef,
      setId: SET_ID,
      answers: [{ questionId: "q1", answer: "예" }],
      analysisId: "a-1",
    });
  });

  it("SITUATION_EXPIRED — 만료 안내와 다시 받기", async () => {
    mockApi({
      [ROUTE]: () =>
        json({ unmet: { reason: "SITUATION_EXPIRED", message: "" } }),
    });
    const { onRevised, onAsk } = renderSituation();

    await answerAndSubmit();

    expect(
      await screen.findByText("상황 질문이 만료됐어요"),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 받기" }));
    expect(onAsk).toHaveBeenCalledWith({
      query: "주택법 내 영향과 대응 방안",
      lawRef,
    });
    expect(onRevised).not.toHaveBeenCalled();
    // 다시 받기는 이 법령 버전의 저장된 상태(옛 답·재생성 결과)를 비운다
    expect(readSituation(KEY, SET_ID)).toEqual({ answers: {} });
  });

  it("법령 제목이 없어도 다시 받기를 보이고, 제목 없는 개인화 질의로 다시 받는다", async () => {
    mockApi({
      [ROUTE]: () =>
        json({ unmet: { reason: "SITUATION_EXPIRED", message: "" } }),
    });
    const { onAsk } = renderSituation({ ...lawRef });

    await answerAndSubmit();
    await userEvent.click(
      await screen.findByRole("button", { name: "다시 받기" }),
    );

    expect(onAsk).toHaveBeenCalledWith({
      query: "내 영향과 대응 방안",
      lawRef,
    });
  });

  it("INSUFFICIENT_GROUNDING — 다시 만들지 못했다는 안내, onRevised 없음", async () => {
    mockApi({
      [ROUTE]: () =>
        json({ unmet: { reason: "INSUFFICIENT_GROUNDING", message: "" } }),
    });
    const { onRevised } = renderSituation();

    await answerAndSubmit();

    expect(
      await screen.findByText("근거가 부족해 다시 만들지 못했어요"),
    ).toBeInTheDocument();
    expect(onRevised).not.toHaveBeenCalled();
  });

  it("DAILY_LIMIT — 한도 안내", async () => {
    mockApi({
      [ROUTE]: () => json({ unmet: { reason: "DAILY_LIMIT", message: "" } }),
      "GET /api/v1/account": () =>
        json({ provider: "kakao", createdAt: "2026-10-23T05:00:00Z" }),
    });
    renderSituation();

    await answerAndSubmit();

    expect(
      await screen.findByText("오늘 개인화 분석 한도를 다 썼어요"),
    ).toBeInTheDocument();
  });

  it("401 이면 세션 만료 대화상자", async () => {
    mockApi({ [ROUTE]: () => json({ error: "unauthenticated" }, 401) });
    renderSituation();

    await answerAndSubmit();

    expect(
      await screen.findByRole("dialog", { name: "다시 로그인해 주세요" }),
    ).toBeInTheDocument();
  });

  it("401 로 대화상자를 닫은 뒤에도 일반 오류 문구는 띄우지 않는다", async () => {
    mockApi({ [ROUTE]: () => json({ error: "unauthenticated" }, 401) });
    renderSituation();

    await answerAndSubmit();
    await screen.findByRole("dialog", { name: "다시 로그인해 주세요" });
    await userEvent.keyboard("{Escape}");

    expect(
      screen.queryByText("잠시 후 다시 시도해 주세요."),
    ).not.toBeInTheDocument();
  });

  it("서버 오류(500)면 일반 오류 문구", async () => {
    mockApi({ [ROUTE]: () => json({ message: "boom" }, 500) });
    renderSituation();

    await answerAndSubmit();

    expect(
      await screen.findByText("잠시 후 다시 시도해 주세요."),
    ).toBeInTheDocument();
  });

  it("다른 질문 묶음으로 다시 열면 옛 답을 이어받지 않는다", () => {
    writeSituation(KEY, {
      answers: { q1: "예" },
      identity: "fedcba9876543210",
    });
    renderSituation();

    for (const button of screen.getAllByRole("button", { name: "예" })) {
      expect(button).toHaveAttribute("aria-pressed", "false");
    }
  });

  it("같은 질문 묶음이면 저장된 답을 이어받는다", () => {
    writeSituation(KEY, {
      answers: { q1: "예" },
      identity: SET_ID,
    });
    renderSituation();

    expect(screen.getAllByRole("button", { name: "예" })[0]).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("지운 날짜는 보내지 않는다", async () => {
    const requests = mockApi({ [ROUTE]: () => json(REVISION) });
    renderSituation();

    await userEvent.click(screen.getAllByRole("button", { name: "예" })[0]);
    const date = screen.getByLabelText("착공 예정일이 언제인가요?", {
      selector: "input",
    });
    await userEvent.type(date, "2027-03-01");
    await userEvent.clear(date);
    await userEvent.click(
      screen.getByRole("button", { name: "대응 방안 확인하기" }),
    );

    await screen.findByText(/계약을 시행일 이전에 맺었나요\?: 예/);
    expect((await requests[0].json()).answers).toEqual([
      { questionId: "q1", answer: "예" },
    ]);
  });

  it("현재 질문에 없는 id 의 답은 보내지 않는다", async () => {
    writeSituation(KEY, {
      answers: { q1: "예", gone: "예" },
      identity: SET_ID,
    });
    const requests = mockApi({ [ROUTE]: () => json(REVISION) });
    renderSituation();

    await userEvent.click(
      screen.getByRole("button", { name: "대응 방안 확인하기" }),
    );

    await screen.findByText(/계약을 시행일 이전에 맺었나요\?: 예/);
    expect((await requests[0].json()).answers).toEqual([
      { questionId: "q1", answer: "예" },
    ]);
  });
});
