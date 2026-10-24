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
