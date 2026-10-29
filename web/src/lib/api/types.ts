import type { components } from "./schema";

/** 계약 SSOT 는 백엔드 코드 → api/openapi.json → 이 생성 타입(D64). 중첩 타입은 루트에서 꺼내 이름 변화에 덜 묶인다. */
export type AnalysisResponse = components["schemas"]["AnalysisApiResponse"];
export type AnalyzeRequest = components["schemas"]["AnalyzeApiRequest"];
export type DataStatus = components["schemas"]["DataStatus"];

export type Resolution = NonNullable<AnalysisResponse["resolution"]>;
export type LawHeader = NonNullable<AnalysisResponse["law"]>;
export type LawBrief = NonNullable<AnalysisResponse["alternatives"]>[number];
export type CitedSource = NonNullable<AnalysisResponse["sources"]>[number];
export type ImpactResult = NonNullable<AnalysisResponse["answer"]>[string];
export type Unmet = NonNullable<AnalysisResponse["unmet"]>[string];
export type Claim = NonNullable<ImpactResult["claims"]>[number];
export type Impact = NonNullable<ImpactResult["impacts"]>[number];
export type Action = NonNullable<ImpactResult["actions"]>[number];

/** 분석 대상을 특정할 때 보내는 법령 참조(요청의 lawRef). */
export type LawRefInput = { lawId: string; effectiveDate: string };

export type Account = components["schemas"]["AccountResponse"];
export type Consents = components["schemas"]["ConsentsResponse"];
export type ProfileConsent = components["schemas"]["ProfileConsent"];
export type NotificationEmailConsent =
  components["schemas"]["NotificationEmailConsent"];
export type Profile = components["schemas"]["ProfileResponse"];
export type ProfileInput = components["schemas"]["ProfileApiRequest"];

export type LawListPage = components["schemas"]["LawListPage"];
export type LawListItem = NonNullable<LawListPage["items"]>[number];
export type LawVersions = components["schemas"]["LawVersions"];
export type UpcomingVersion = NonNullable<LawVersions["pending"]>[number];
export type LawDetail = components["schemas"]["LawDetailResponse"];
export type ChangedArticle = NonNullable<LawDetail["changedArticles"]>[number];
export type SourceText = NonNullable<LawDetail["addenda"]>[number];

export type TrendingPage = components["schemas"]["TrendingPage"];
export type TrendingItem = NonNullable<TrendingPage["items"]>[number];
export type LawSummary = NonNullable<LawDetail["summary"]>;

export type Discovery = NonNullable<AnalysisResponse["discovery"]>;
export type DiscoveredLaw = NonNullable<Discovery["candidates"]>[number];

export type Situation = NonNullable<AnalysisResponse["situation"]>;
export type SituationQuestion = NonNullable<Situation["questions"]>[number];
export type StepNote = NonNullable<Situation["stepNotes"]>[number];
export type ActionRevision = components["schemas"]["ActionRevisionApiResponse"];
