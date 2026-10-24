import type { AnalysisResponse } from "@/lib/api/types";

export const ARTICLE_18 = "LAW:001809@2026-08-04:art:18";

export const resolvedResponse: AnalysisResponse = {
  resolution: "RESOLVED",
  law: {
    ref: "LAW:001809@2026-08-04",
    lawId: "001809",
    title: "주택법",
    effectiveDate: "2026-08-04",
    lawType: "법률",
    ministry: "국토교통부",
  },
  alternatives: [
    { lawId: "001809", title: "주택법", effectiveDate: "2027-02-01" },
  ],
  answer: {
    summary: {
      summary: "사업계획 통합심의 절차가 바뀝니다.",
      claims: [
        {
          statement: "통합심의 대상이 넓어진다.",
          citations: [ARTICLE_18],
          confidence: 0.9,
        },
      ],
      uncertainties: ["시행령 위임 내용은 아직 확정되지 않았어요."],
      disclaimer: "법률 자문이 아닌 참고용 정보입니다.",
    },
    diff: {
      summary: "제18조가 개정됩니다.",
      claims: [
        {
          statement: "제18조 통합심의 범위가 넓어진다.",
          citations: [ARTICLE_18],
          confidence: 0.9,
        },
      ],
      uncertainties: [],
    },
  },
  unmet: {
    impact: { reason: "PROFILE_REQUIRED", message: "프로필 필요 (Layer B)" },
  },
  sources: [
    {
      sourceId: ARTICLE_18,
      label: "주택법 제18조(사업계획의 통합심의)",
      text: "① 사업계획승인권자는 통합하여 심의할 수 있다.",
      previousText: "① 사업계획승인권자는 심의할 수 있다.",
      changeType: "개정",
      effectiveDate: "2026-08-04",
    },
  ],
  disclaimer: "법률 자문이 아닌 참고용 정보입니다.",
};

export const ambiguousResponse: AnalysisResponse = {
  resolution: "AMBIGUOUS",
  message: "여러 후보가 있어요.",
  candidates: [
    {
      lawId: "010513",
      title: "자본시장과 금융투자업에 관한 법률",
      effectiveDate: "2026-10-01",
    },
    {
      lawId: "010513",
      title: "자본시장과 금융투자업에 관한 법률",
      effectiveDate: "2026-11-13",
    },
  ],
};

export const notFoundResponse: AnalysisResponse = {
  resolution: "NOT_FOUND_YET",
  message: "그 법을 아직 못 찾았어요.",
};
