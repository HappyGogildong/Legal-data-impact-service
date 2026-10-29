import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ActionRevision, SituationQuestion } from "@/lib/api/types";
import { readSituation, situationKey } from "@/lib/situation-store";
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
const ROUTE = "POST /api/v1/analyses/action-revisions";

function renderSituation() {
  const onRevised = vi.fn();
  const onAsk = vi.fn();
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ActionSituation
        law={law}
        questions={questions}
        sources={new Map()}
        selected={null}
        onCite={vi.fn()}
        onRevised={onRevised}
        onAsk={onAsk}
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

beforeEach(() => router.push.mockReset());

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
      answers: [{ questionId: "q1", answer: "예" }],
    });
    expect(onRevised).toHaveBeenCalledWith(REVISION);
    expect(readSituation(situationKey(law.lawId, law.effectiveDate))).toEqual({
      answers: { q1: "예", q2: "모름" },
      revision: REVISION,
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
});
