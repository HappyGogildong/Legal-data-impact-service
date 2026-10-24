import options from "@/lib/profile-options.json";

/** 단일 선택 항목 — 키는 PUT /api/v1/profile 필드, 선택지는 profile-options.json(백엔드 enum 과 동기 테스트). */
export const SINGLE_FIELDS = [
  {
    key: "occupation",
    label: "직업",
    control: "select",
    why: "직업군에 따라 근로·세제 법령 영향 판단에 써요.",
  },
  {
    key: "employmentType",
    label: "고용 형태",
    control: "radio",
    why: "근로자·자영업자 등 적용 대상을 가릴 때 써요.",
  },
  {
    key: "householdType",
    label: "가구 형태",
    control: "radio",
    why: "가구 기준 지원·세제 판단에 써요.",
  },
  {
    key: "housingType",
    label: "주거 형태",
    control: "radio",
    why: "임대차·주택 법령 영향 판단에 써요.",
  },
  {
    key: "regionSido",
    label: "거주 시도",
    control: "select",
    why: "지역에 따라 다른 조례·지원 기준을 가릴 때 써요.",
  },
] as const;

export type SingleFieldKey = (typeof SINGLE_FIELDS)[number]["key"];

export const OPTIONS: Record<SingleFieldKey | "purposes", string[]> = options;

export const PURPOSES_WHY = "어떤 분야의 법령 영향을 먼저 볼지 정할 때 써요.";
export const AGE_WHY =
  "연령 기준이 있는 법령(청년·고령자 지원 등) 판단에 써요.";
export const MIN_AGE = 14;
export const MAX_AGE = 120;
