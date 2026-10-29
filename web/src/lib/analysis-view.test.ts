import { describe, expect, it } from "vitest";
import { ARTICLE_18, resolvedResponse } from "@/test/fixtures";
import {
  articleTitle,
  cardStatus,
  changedArticles,
  citeLabel,
  citationsOf,
  presentDimensions,
  sourceIndex,
  unmetCopy,
} from "./analysis-view";

const index = sourceIndex(resolvedResponse);
const summary = resolvedResponse.answer!.summary;
const diff = resolvedResponse.answer!.diff;

describe("analysis-view", () => {
  it("응답에 온 차원만 화면 순서대로(채움·미충족 모두)", () => {
    expect(presentDimensions(resolvedResponse)).toEqual([
      "summary",
      "diff",
      "impact",
    ]);
  });

  it("인용 — 주장·영향 citations 와 대응 basis, 중복 제거", () => {
    expect(
      citationsOf({
        claims: [{ citations: ["A", "B"] }],
        impacts: [{ citations: ["B", "C"] }],
        actions: [{ basis: ["D"] }],
      }),
    ).toEqual(["A", "B", "C", "D"]);
  });

  it("바뀐 조문 — 인용 중 변경 유형이 있는 조문만", () => {
    expect(
      changedArticles(diff, index).map((source) => source.sourceId),
    ).toEqual([ARTICLE_18]);
    expect(
      changedArticles({ claims: [{ citations: ["모름"] }] }, index),
    ).toEqual([]);
  });

  it("인용 칩 라벨 — 괄호 제목을 떼고, 못 찾은 근거는 '근거'", () => {
    expect(citeLabel(index.get(ARTICLE_18), ARTICLE_18)).toBe("주택법 제18조");
    expect(citeLabel(undefined, "LAW:x")).toBe("근거");
  });

  it("조문 제목 — 법령명을 떼고 조 번호부터", () => {
    expect(
      articleTitle("자본시장과 금융투자업에 관한 법률 제6조의3(계약갱신 요구)"),
    ).toBe("제6조의3(계약갱신 요구)");
    expect(articleTitle("주택법 개정 이유")).toBe("주택법 개정 이유");
  });

  it("미충족 안내 문구 — 사유 코드별, 모르는 코드는 일반 문구", () => {
    expect(unmetCopy({ reason: "PROFILE_REQUIRED" }).title).toContain(
      "내 상황",
    );
    expect(unmetCopy({ reason: "LAW_NOT_LOADED" }).title).toContain("준비 중");
    expect(unmetCopy(undefined).title).toBe("이 부분은 지금 답할 수 없어요");
  });

  it("카드 상태 — 변경점은 조문 수, 미충족은 짧은 사유", () => {
    expect(cardStatus("diff", diff, undefined, index)).toBe("조문 1개");
    expect(cardStatus("summary", summary, undefined, index)).toBeNull();
    expect(
      cardStatus("impact", undefined, { reason: "PROFILE_REQUIRED" }, index),
    ).toBe("프로필 필요");
  });
});

describe("unmetCopy — Layer B 사유(D69)", () => {
  it("재동의·한도·근거 부족 문구", () => {
    expect(unmetCopy({ reason: "CONSENT_OUTDATED" })).toEqual({
      title: "처리방침이 바뀌어 다시 동의가 필요해요",
      body: "다시 동의하면 내 영향과 대응 방안을 볼 수 있어요.",
    });
    expect(unmetCopy({ reason: "DAILY_LIMIT" })).toEqual({
      title: "오늘 개인화 분석 한도를 다 썼어요",
      body: "내일 다시 이용해 주세요.",
    });
    expect(unmetCopy({ reason: "INSUFFICIENT_GROUNDING" })).toEqual({
      title: "근거가 부족해 답하지 않았어요",
      body: "조문 근거로 확인할 수 있는 내용만 답해요.",
    });
  });

  it("접힌 카드 상태", () => {
    expect(
      cardStatus("impact", undefined, { reason: "CONSENT_OUTDATED" }, index),
    ).toBe("재동의 필요");
    expect(
      cardStatus("action", undefined, { reason: "DAILY_LIMIT" }, index),
    ).toBe("한도 초과");
    expect(
      cardStatus(
        "summary",
        undefined,
        { reason: "INSUFFICIENT_GROUNDING" },
        index,
      ),
    ).toBe("근거 부족");
  });
});

describe("unmetCopy — 상황 확인(D71)", () => {
  it("상황 질문 만료 문구와 카드 상태", () => {
    expect(unmetCopy({ reason: "SITUATION_EXPIRED" }).title).toBe(
      "상황 질문이 만료됐어요",
    );
    expect(
      cardStatus("action", undefined, { reason: "SITUATION_EXPIRED" }, index),
    ).toBe("만료");
  });
});
