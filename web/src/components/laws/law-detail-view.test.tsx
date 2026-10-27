import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { LawDetail } from "@/lib/api/types";
import { empty, mockApi } from "@/test/api-mock";
import { LawDetailView } from "./law-detail-view";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const DETAIL: LawDetail = {
  lawId: "001809",
  title: "주택법",
  lawType: "법률",
  ministry: "국토교통부",
  promulgateDate: "2026-05-01",
  promulgateNo: "21323",
  effectiveDate: "2026-11-03",
  status: "시행예정",
  amendKind: "일부개정",
  sourceUrl: "https://www.law.go.kr/lsInfoP.do?lsiSeq=1",
  amendReason: "심의 절차를 간소화한다.",
  enforcementType: "유예",
  effectiveRule: "공포 후 6개월",
  effectiveClause: {
    sourceId: "LAW:001809@2026-11-03:addenda:제1조",
    label: "부칙 제1조(시행일)",
    text: "이 법은 공포 후 6개월이 경과한 날부터 시행한다.",
  },
  changedArticles: [
    {
      sourceId: "LAW:001809@2026-11-03:art:18",
      no: "18",
      label: "제18조(사업계획의 통합심의)",
      changeType: "개정",
      text: "① 통합심의한다.",
      previousText: "① 심의한다.",
    },
  ],
  addenda: [
    {
      sourceId: "LAW:001809@2026-11-03:addenda:제1조",
      label: "부칙 제1조(시행일)",
      text: "이 법은 공포 후 6개월이 경과한 날부터 시행한다.",
    },
  ],
  delegationClauses: ["대통령령으로 정하는 기준에 따라 심의한다."],
  baselineEffectiveDate: "2020-01-01",
};

function renderDetail(
  detail: LawDetail = DETAIL,
  upcoming = [{ effectiveDate: "2026-11-03" }],
) {
  render(
    <LawDetailView
      detail={detail}
      upcoming={upcoming}
      today="2026-10-25"
      lawsUpdatedAt="2026-10-22T19:12:31Z"
    />,
  );
}

beforeEach(() => {
  router.push.mockReset();
  sessionStorage.clear();
  mockApi({ "POST /api/v1/laws/001809/2026-11-03/views": () => empty(204) });
});
afterEach(() => {
  window.location.hash = "";
});

describe("LawDetailView", () => {
  it("헤더 — 법령명·D-N·종류·부처·공포·원문 링크, 하단 갱신 시각", () => {
    renderDetail();

    expect(
      screen.getByRole("heading", { level: 1, name: "주택법" }),
    ).toBeInTheDocument();
    expect(screen.getByText("D-9")).toBeInTheDocument();
    expect(
      screen.getByText("법률 · 국토교통부 · 공포 2026-05-01 제21323호"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "국가법령정보센터 원문 ↗" }),
    ).toHaveAttribute("href", "https://www.law.go.kr/lsInfoP.do?lsiSeq=1");
    expect(screen.getByText(/2026-10-23 04:12 갱신/)).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("쉬운 요약 보기 — 질문을 탭 저장소에 두고 /ask (URL 에 질문 없음)", async () => {
    renderDetail();

    await userEvent.click(
      screen.getByRole("button", { name: "쉬운 요약 보기" }),
    );

    expect(router.push).toHaveBeenCalledWith("/ask");
    expect(JSON.parse(sessionStorage.getItem("lia.ask") ?? "null")).toEqual({
      query: "주택법 쉬운 요약",
      lawRef: { lawId: "001809", effectiveDate: "2026-11-03" },
    });
  });

  it("개정 이유·시행 규칙·위임 조항 안내", async () => {
    renderDetail();

    expect(screen.getByText("심의 절차를 간소화한다.")).toBeInTheDocument();
    expect(screen.getByText("유예")).toBeInTheDocument();
    expect(
      screen.getAllByText("이 법은 공포 후 6개월이 경과한 날부터 시행한다.")
        .length,
    ).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole("button", { name: /위임 조항/ }));
    expect(
      screen.getByText("대통령령으로 정하는 기준에 따라 심의한다."),
    ).toBeInTheDocument();
  });

  it("시행 조항 본문이 비어 있으면 시행 규칙 문장으로 채운다", () => {
    const emptyClause = { ...DETAIL.effectiveClause!, text: "" };
    renderDetail({ ...DETAIL, effectiveClause: emptyClause });
    expect(screen.getByText("공포 후 6개월")).toBeInTheDocument();
  });

  it("시행 조항과 시행 규칙이 모두 비어 있으면 안내 문구를 보인다", () => {
    const emptyClause = { ...DETAIL.effectiveClause!, text: "" };
    renderDetail({
      ...DETAIL,
      effectiveClause: emptyClause,
      effectiveRule: "",
    });
    expect(
      screen.getByText("시행 규칙이 제공되지 않았어요."),
    ).toBeInTheDocument();
  });

  it("바뀐 조문 — 고유 주소, 펼치면 현행↔개정 대비", async () => {
    renderDetail();

    const item = document.getElementById("art-18");
    expect(item).not.toBeNull();
    await userEvent.click(
      within(item as HTMLElement).getByRole("button", {
        name: /제18조\(사업계획의 통합심의\)/,
      }),
    );
    expect(screen.getByText("현행")).toBeInTheDocument();
  });

  it("주소가 #art-18 이면 그 조문이 펼쳐져 있고 그 위치로 스크롤한다", () => {
    // 본문이 스트리밍으로 늦게 오면 브라우저의 기본 앵커 이동이 지나가 버린다 — 직접 스크롤해야 한다
    const scrolled: Element[] = [];
    Element.prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this);
    };
    window.location.hash = "#art-18";
    renderDetail();
    expect(screen.getByText("현행")).toBeInTheDocument();
    expect(scrolled).toEqual([document.getElementById("art-18")]);
    delete (Element.prototype as Partial<Element>).scrollIntoView; // jsdom 엔 원래 없다
  });

  it("시행예정본이 여럿이면 시행일 선택 링크", () => {
    renderDetail(DETAIL, [
      { effectiveDate: "2026-11-03" },
      { effectiveDate: "2027-05-01" },
    ]);
    const nav = screen.getByRole("navigation", { name: "시행일" });
    expect(
      within(nav).getByRole("link", { name: "2027-05-01" }),
    ).toHaveAttribute("href", "/laws/001809/2027-05-01");
    expect(
      within(nav).getByRole("link", { name: "2026-11-03" }),
    ).toHaveAttribute("aria-current", "page");
  });

  it("철회된 버전 — 상태 배너, 요약 버튼·질문창·현행 대비 없음", async () => {
    const requests = mockApi({});
    renderDetail({ ...DETAIL, status: "철회" }, []);

    expect(screen.getByRole("status")).toHaveTextContent(
      "이 시행예정본은 철회되었어요",
    );
    expect(
      screen.queryByRole("button", { name: "쉬운 요약 보기" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: "후속 질문" }),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: /제18조\(사업계획의 통합심의\)/ }),
    );
    expect(screen.queryByText("현행")).not.toBeInTheDocument();
    expect(screen.getByText("① 통합심의한다.")).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(requests).toHaveLength(0);
  });

  it("원문 주소가 http(s)가 아니면 링크를 두지 않는다", () => {
    renderDetail({ ...DETAIL, sourceUrl: "javascript:alert(1)" });
    expect(
      screen.queryByRole("link", { name: "국가법령정보센터 원문 ↗" }),
    ).not.toBeInTheDocument();
  });

  it("요약이 있으면 요약이 기본, 개정 이유 원문은 접힘, 쉬운 요약 보기 버튼 없음", async () => {
    renderDetail({
      ...DETAIL,
      summary: {
        text: "절차가 짧아져요.",
        claims: [
          {
            statement: "통합심의가 넓어진다",
            citations: ["LAW:001809@2026-11-03:art:18"],
          },
        ],
        uncertainties: [],
      },
    });

    expect(screen.getByText("절차가 짧아져요.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "제18조" })).toHaveAttribute(
      "href",
      "#art-18",
    );
    expect(
      screen.queryByRole("button", { name: "쉬운 요약 보기" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("심의 절차를 간소화한다."),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: /개정 이유 원문 보기/ }),
    );
    expect(screen.getByText("심의 절차를 간소화한다.")).toBeInTheDocument();
  });
});
