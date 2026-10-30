import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { empty, json, mockApi } from "@/test/api-mock";
import { resolvedResponse } from "@/test/fixtures";
import { HistoryDetailView } from "./history-detail-view";

const router = { push: vi.fn(), replace: vi.fn() };
let params: { id: string } = { id: "7" };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/me/history/7",
  useParams: () => params,
}));

const ACCOUNT = { provider: "kakao", createdAt: "2026-10-23T05:00:00Z" };
const DETAIL = {
  id: 7,
  query: "주택법 바뀌면 전세 계약 어떻게 돼?",
  lawId: "001809",
  lawTitle: "주택법",
  effectiveDate: "2026-08-04",
  savedAt: "2026-10-30T03:00:00Z",
  lawUpdated: false,
  body: { ...resolvedResponse, unmet: {} },
  answers: [] as { question: string; answer: string }[],
};

function renderDetail(
  detail: unknown,
  extra: Parameters<typeof mockApi>[0] = {},
) {
  const requests = mockApi({
    "GET /api/v1/account": () => json(ACCOUNT),
    "GET /api/v1/bookmarks": () => json({ items: [] }),
    "GET /api/v1/history/7": () =>
      detail === null ? json({ error: "not_found" }, 404) : json(detail),
    ...extra,
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <HistoryDetailView />
    </QueryClientProvider>,
  );
  return requests;
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
  params = { id: "7" };
});

describe("HistoryDetailView", () => {
  it("저장 당시 배너와 결과 본문(읽기 전용)", async () => {
    renderDetail(DETAIL);
    expect(
      await screen.findByText("저장 당시(2026-10-30) 기준이에요"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("사업계획 통합심의 절차가 바뀝니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "후속 질문" })).toBeNull();
    expect(
      screen.queryByRole("button", { name: "내 영향·대응 방안 보기" }),
    ).toBeNull();
    expect(screen.queryByText("그 뒤 법령이 바뀌었어요")).toBeNull();
  });

  it("갱신됐고 시행예정본이 있으면 '최신 내용으로 다시 분석' → 같은 질문 + 그 버전으로 S02", async () => {
    renderDetail({
      ...DETAIL,
      lawUpdated: true,
      latestEffectiveDate: "2027-02-01",
    });
    expect(
      await screen.findByText(/그 뒤 법령이 바뀌었어요/),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "최신 내용으로 다시 분석" }),
    );
    expect(router.push).toHaveBeenCalledWith("/ask");
    expect(JSON.parse(sessionStorage.getItem("lia.ask")!)).toEqual({
      query: DETAIL.query,
      lawRef: { lawId: "001809", effectiveDate: "2027-02-01" },
    });
  });

  it("갱신됐고 시행예정본이 없으면 '법령 상세 보기'(저장한 버전 S05)", async () => {
    renderDetail({ ...DETAIL, lawUpdated: true });
    expect(
      await screen.findByRole("link", { name: "법령 상세 보기" }),
    ).toHaveAttribute("href", "/laws/001809/2026-08-04");
  });

  it("재생성본이 있으면 대응 방안에 '내 상황 반영'과 확인한 상황 칩", async () => {
    renderDetail({
      ...DETAIL,
      // 실제 스냅샷의 본문엔 대응 방안이 unmet 자리로 있고, 재생성본이 그 카드를 채운다
      body: {
        ...resolvedResponse,
        unmet: { action: { reason: "PROFILE_REQUIRED", message: "x" } },
      },
      revision: {
        action: {
          command: "ACTION",
          claims: [],
          impacts: [],
          actions: [
            {
              what: "갱신 요구 의사 전달",
              deadline: "2027-01-01",
              basis: [resolvedResponse.sources![0].sourceId!],
            },
          ],
          uncertainties: [],
          disclaimer: "참고용",
        },
        stepNotes: [{ index: 0, note: "이전 기준이 적용돼요" }],
      },
      answers: [{ question: "계약을 이미 맺었나요?", answer: "예" }],
    });
    await userEvent.click(
      await screen.findByRole("button", { name: /^대응 방안/ }),
    );
    expect(screen.getByText("갱신 요구 의사 전달")).toBeInTheDocument();
    expect(screen.getByText("내 상황 반영")).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "확인한 상황" }),
    ).toHaveTextContent("계약을 이미 맺었나요?: 예");
  });

  it("삭제는 확인 후 DELETE 하고 목록으로", async () => {
    const requests = renderDetail(DETAIL, {
      "DELETE /api/v1/history/7": () => empty(204),
    });
    await userEvent.click(await screen.findByRole("button", { name: "삭제" }));
    await userEvent.click(
      screen.getAllByRole("button", { name: "삭제" }).at(-1)!,
    );
    await vi.waitFor(() =>
      expect(router.push).toHaveBeenCalledWith("/me/history"),
    );
    expect(requests.some((request) => request.method === "DELETE")).toBe(true);
  });

  it("삭제 직후엔 상세를 다시 받지 않아 '찾는 기록이 없어요'가 깜빡이지 않는다", async () => {
    let deleted = false;
    renderDetail(DETAIL, {
      "GET /api/v1/history/7": () =>
        deleted ? json({ error: "not_found" }, 404) : json(DETAIL),
      "DELETE /api/v1/history/7": () => {
        deleted = true;
        return empty(204);
      },
    });
    await userEvent.click(await screen.findByRole("button", { name: "삭제" }));
    await userEvent.click(
      screen.getAllByRole("button", { name: "삭제" }).at(-1)!,
    );
    await vi.waitFor(() =>
      expect(router.push).toHaveBeenCalledWith("/me/history"),
    );
    expect(screen.queryByText("찾는 기록이 없어요")).toBeNull();
  });

  it("숫자가 아닌 id 면 요청 없이 '찾는 기록이 없어요'", async () => {
    params = { id: "abc" };
    const requests = renderDetail(DETAIL);
    expect(await screen.findByText("찾는 기록이 없어요")).toBeInTheDocument();
    expect(
      requests.some((request) => request.url.includes("/api/v1/history/")),
    ).toBe(false);
  });

  it("없거나 남의 기록(404)이면 '찾는 기록이 없어요'", async () => {
    renderDetail(null);
    expect(await screen.findByText("찾는 기록이 없어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이력 목록" })).toHaveAttribute(
      "href",
      "/me/history",
    );
  });
});
