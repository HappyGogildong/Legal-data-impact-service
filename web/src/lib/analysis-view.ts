import type {
  AnalysisResponse,
  CitedSource,
  ImpactResult,
  Unmet,
} from "@/lib/api/types";

/** 결과 화면의 차원 — 순서 고정(screen-plan S02). LOOKUP 은 S04(3단계)라 여기서 다루지 않는다. */
export const DIMENSIONS = ["summary", "diff", "impact", "action"] as const;
export type Dimension = (typeof DIMENSIONS)[number];

export const DIMENSION_TITLES: Record<Dimension, string> = {
  summary: "쉬운 요약",
  diff: "무엇이 바뀌나",
  impact: "내 영향",
  action: "대응 방안",
};

const CHANGED = new Set(["개정", "신설", "삭제", "이동"]);

export function presentDimensions(response: AnalysisResponse): Dimension[] {
  return DIMENSIONS.filter(
    (dimension) => response.answer?.[dimension] || response.unmet?.[dimension],
  );
}

export function sourceIndex(
  response: AnalysisResponse,
): Map<string, CitedSource> {
  const index = new Map<string, CitedSource>();
  for (const source of response.sources ?? []) {
    if (source.sourceId) index.set(source.sourceId, source);
  }
  return index;
}

/** 한 차원이 인용한 키 — 주장·영향의 citations, 대응의 basis(등장 순, 중복 제거). */
export function citationsOf(result: ImpactResult): string[] {
  const ids = [
    ...(result.claims ?? []).flatMap((claim) => claim.citations ?? []),
    ...(result.impacts ?? []).flatMap((impact) => impact.citations ?? []),
    ...(result.actions ?? []).flatMap((action) => action.basis ?? []),
  ];
  return [...new Set(ids)];
}

/** "무엇이 바뀌나" 목록 — 인용한 조문 중 변경 유형이 있는 것. 하위 청크(#n)로 같은 조문이 겹치면 한 번. */
export function changedArticles(
  result: ImpactResult,
  index: Map<string, CitedSource>,
): CitedSource[] {
  const seen = new Set<string>();
  const articles: CitedSource[] = [];
  for (const id of citationsOf(result)) {
    const source = index.get(id);
    if (!source?.changeType || !CHANGED.has(source.changeType)) continue;
    const key = source.label ?? id;
    if (seen.has(key)) continue;
    seen.add(key);
    articles.push(source);
  }
  return articles;
}

/** 인용 칩 글자 — "주택법 제18조(사업계획의 통합심의)" → "주택법 제18조". 못 찾은 근거는 "근거". */
export function citeLabel(
  source: CitedSource | undefined,
  sourceId: string,
): string {
  if (!source?.label || source.label === sourceId) return "근거";
  return source.label.replace(/\(.*\)$/, "");
}

/** 조문 제목 — 법령명을 떼고 "제N조(의M)(제목)"부터. 조문이 아니면 그대로. */
export function articleTitle(label: string): string {
  const match = label.match(/제\d+조(?:의\d+)?(?:\(.*\))?$/);
  return match ? match[0] : label;
}

export type UnmetCopy = { title: string; body: string };

/** 미충족 차원의 안내 — 사유 코드별(계약: service-api-spec §3.0). 프로필 필요(PROFILE_REQUIRED)·재동의(CONSENT_OUTDATED)의 상태별 설명·버튼은 PersonalizeAction. */
export function unmetCopy(unmet: Pick<Unmet, "reason"> | undefined): UnmetCopy {
  switch (unmet?.reason) {
    case "PROFILE_REQUIRED":
      return { title: "내 상황에 맞춘 분석이 필요한 부분이에요", body: "" };
    case "LAW_NOT_LOADED":
      return {
        title: "아직 분석 준비 중인 법령이에요",
        body: "법령 정보를 받아 오는 중이에요. 잠시 후 다시 확인해 주세요.",
      };
    case "BASELINE_NOT_LOADED":
      return {
        title: "현행 법령 정보를 아직 받지 못했어요",
        body: "바뀐 점을 비교하려면 현행 법령이 필요해요. 잠시 후 다시 확인해 주세요.",
      };
    case "CONSENT_OUTDATED":
      return {
        title: "처리방침이 바뀌어 다시 동의가 필요해요",
        body: "다시 동의하면 내 영향과 대응 방안을 볼 수 있어요.",
      };
    case "DAILY_LIMIT":
      return {
        title: "오늘 개인화 분석 한도를 다 썼어요",
        body: "내일 다시 이용해 주세요.",
      };
    case "INSUFFICIENT_GROUNDING":
      return {
        title: "근거가 부족해 답하지 않았어요",
        body: "조문 근거로 확인할 수 있는 내용만 답해요.",
      };
    case "NOT_IMPLEMENTED":
      return { title: "곧 제공할 기능이에요", body: "" };
    default:
      return { title: "이 부분은 지금 답할 수 없어요", body: "" };
  }
}

const UNMET_STATUS: Record<string, string> = {
  PROFILE_REQUIRED: "프로필 필요",
  LAW_NOT_LOADED: "준비 중",
  BASELINE_NOT_LOADED: "준비 중",
  NOT_IMPLEMENTED: "준비 중",
  CONSENT_OUTDATED: "재동의 필요",
  DAILY_LIMIT: "한도 초과",
  INSUFFICIENT_GROUNDING: "근거 부족",
};

/** 접힌 카드 제목 옆 상태 — 변경점은 조문 수, 미충족은 짧은 사유. 없으면 null. */
export function cardStatus(
  dimension: Dimension,
  result: ImpactResult | undefined,
  unmet: Pick<Unmet, "reason"> | undefined,
  index: Map<string, CitedSource>,
): string | null {
  if (!result)
    return unmet?.reason
      ? (UNMET_STATUS[unmet.reason] ?? "답변 없음")
      : "답변 없음";
  if (dimension === "diff")
    return `조문 ${changedArticles(result, index).length}개`;
  return null;
}
