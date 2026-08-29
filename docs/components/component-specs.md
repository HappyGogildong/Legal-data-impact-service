---
title: 컴포넌트 상세 스펙 (역할·입출력·동작) + 계약 + 정합성 검증
status: Draft
version: 0.2
date: 2026-08-02
tags: [spec, components, contract, profile-schema, consistency]
related:
  - "architecture/v0.8-pending-law-corpus.md"
  - "mvp/components-io-and-scope.md"
  - "prompts/analysis-prompt-spec.md"
  - "adr/decision-log.md"
---

# 컴포넌트 상세 스펙 + 계약 + 정합성 검증 (v0.2 Draft)

**관련:** [[components-io-and-scope|컴포넌트·MVP 범위]] · [[analysis-prompt-spec|프롬프트 정의서]] · [[v0.8-pending-law-corpus|아키텍처 v0.8]] · [[decision-log|결정 로그]]

## 0. 목적·범위·표기법

[[components-io-and-scope]]의 카탈로그를 **개발 가능한 수준의 상세 스펙**으로 확장한다. **MVP 활성 컴포넌트(IN)** 만 다루며, §2 사용자 프로필 스키마·§3 REST 계약을 포함하고, §5에서 **정합성 검증**으로 "스펙대로 개발 시 E2E 동작 가능"함을 확인한다.

표기: 타입은 `string|int|enum|T[]|T?`(?=nullable). 모든 스키마는 JSON 직렬화 기준. 컴포넌트 번호(#n)는 [[components-io-and-scope]] §1과 일치.

---

## 1. 공통 데이터 모델 (Single Source of Truth)

모든 컴포넌트가 공유하는 도메인 타입. 변경 시 본 절이 기준.

속성 출처·용도의 전체 카탈로그는 [[law-attributes|법령 속성 카탈로그]] 참고. 아래는 그중 모델에 반영하는 필드.

> **모델 범위.** MVP 분석 대상은 **공포 후 시행 대기 법령**(`target=eflaw`)이며 아래 **`Law`가 유일한 분석 모델**이다. 의안(`Bill`)은 **post-MVP** — eflaw 응답에 `billNo`·발의자·소관위·심사단계가 없어 두 모델을 합치면 절반이 항상 null이 되므로 분리한다(의안 계약은 [[SourceConnector]]).

### 1.1 `Law` — MVP 분석 대상 (공포된 법령)

**시행중본과 시행예정본이 같은 구조**다(`target=law` / `target=eflaw`가 동일 스키마 `법령 > {기본정보, 조문, 부칙, 개정문, 제개정이유}`). `status`로만 구분한다.

```ts
Law {                        // 🟢A(국가법령정보 API) — 전 필드 그라운딩 가능
  id: string                 // 내부 PK
  lawId: string              // ★ 법령ID (버전 불변) — 시행중↔시행예정 연결키
  mst: string                // 법령일련번호 — 버전마다 다름. 연결키로 쓰지 말 것
  title: string              // 법령명_한글
  status: enum("시행중"|"시행예정")     // 현행연혁코드
  amendKind: enum("제정"|"일부개정"|"전부개정"|"타법개정"|"폐지")  // 제개정구분
  lawType: enum("법률"|"대통령령"|"총리령"|"부령"|"기타")          // 법종구분
  ministry: string?          // 소관부처  ⚠️ 응답이 중첩 객체 — 평탄화 필요
  promulgateDate: date       // 공포일자
  promulgateNo: string       // 공포번호 — 이번 개정분 부칙 필터 키
  effectiveDate: date        // ★ 시행일자 (시행예정본은 미래일)
  effectiveRule: string?     // "공포 후 6개월이 경과한 날" 등 — 부칙 제1조에서 추출
  enforcementType: enum("즉시"|"유예"|"단계적")?   // 부칙 단서조항 유무로 판정
  amendReason: string?       // 제개정이유 (= 개정이유 및 주요내용)
  amendText: string?         // 개정문 — 자구 단위 개정 지시문
  addenda: Addendum[]        // 부칙단위 중 promulgateNo 일치분
  articles: Article[]        // 조문단위
  fullText: string           // articles 병합
  baselineLawId: string?     // diff 기준선 = 같은 lawId 의 status="시행중" 버전
  sourceType: enum("LAW")    // MVP는 국가법령정보 단일
  sourceUrl: string?
  revision: string           // 캐시 무효화 해시 (promulgateNo + effectiveDate + 법령키)
  lastSeen: datetime         // 신선도 (revision 해시 비포함)
}
```

> **1:N 주의 (D43 확정).** 한 `lawId`에 **시행 대기 개정이 여러 건 겹칠 수 있다.** 실측: 주택법 현행본은 공포 제21447호(2026-03-05)인데 시행예정본은 제21323호(2026-02-03)로 *나중에 공포된 쪽이 먼저 시행*됐다. **규칙**: 정본 단위 `(lawId, effectiveDate)`, diff 기준선 = **현재 시행중본**, 시행일 미지정 질의는 **가장 이른 미래 시행일본으로 해소**하고 나머지는 안내(`@efYd`로 특정). → [[decision-log|D43]]

### 1.2 `Article` · `Addendum` — 공통 조문·부칙 타입

`Law`가 공유한다. (의안 `Bill` 모델은 post-MVP로 삭제 — 복귀 시 git 이력·[[SourceConnector]] 의안 계약에서 복원, D52)

```ts
Addendum {
  no: string                 // "제1조"
  title: string?             // "시행일" 등
  kind: enum("시행일"|"경과조치"|"적용례"|"특례")
  text: string
  promulgateNo: string       // 소속 개정 공포번호 — 이번 개정분 필터 키
}

Article {
  no: string                 // 조문번호 ("제3조")
  title: string?             // 조문제목
  text: string               // ⚠️ 조문내용 + 항/호/목 재귀 병합 (아래 주의)
  changed: boolean           // ★ 조문변경여부 Y/N — 이번 개정으로 바뀐 조문인가
  changeType: enum("신설"|"개정"|"삭제"|"이동"|"없음")
  movedFrom: string?         // 조문이동이전
  movedTo: string?           // 조문이동이후
  articleEffectiveDate: date?// 조문시행일자 — 조문별로 다를 수 있음(단계적 시행)
  isArticle: boolean         // 조문여부 — "조문"만 실조문, 나머지는 장·절 제목
  diffVsCurrent: string?     // 현행본 같은 조문과의 대조 (baselineLawId로 조회)
}
```

> **`changed` 플래그가 LawDiff의 핵심이다.** 실측(주택법, 2026-08-04 시행): 조문단위 137개 중 `조문변경여부='Y'`는 **6개뿐**(제18·28·46·49·104·106조). 분석 대상을 137→6조문으로 줄이므로 토큰·정확도 양쪽에서 이득이다.
> **`개정문` 정규식 파싱은 하지 말 것.** 실측 법령에서 정규식은 인용된 *타법* 조문번호(제15·27조)를 오탐하고 벌칙·과태료(제104·106조)를 누락했다. **플래그가 정답**이고 `개정문`은 사람이 읽을 근거 텍스트로만 쓴다.
> **`text` 조립 주의.** `조문내용`만 읽으면 본문이 비어 보인다 — 실측에서 제2조(정의)의 `조문내용`은 제목 줄뿐이고 실제 정의는 `항 → 호 → 목` 중첩에 있다. Normalizer가 재귀 병합해야 한다.

### 1.3 `LawFacts` — Layer A 파생 캐시 (MVP 유효)

**프로필과 무관한** 사실 파생층이라 한 번 만들면 모든 사용자가 재사용한다(Layer A 캐시). 참조 키는 `law_ref`.

```ts
LawFacts {                  // 🟡C 파생 — 원본에 저장 X. Layer A 캐시(프로필 무관). D07.
  law_ref: string           // "LAW:{lawId}@{effectiveDate}"
  revision: string           // 기준 revision (캐시 키)
  impactScope: enum("보편"|"도메인특정"|"소수")
  affectedDomains: string[]
  entityTypes: enum("개인"|"사업자"|"법인"|"기관")[]
  obligations: Fact[]        // 신규 의무(신고/등록/허가/납부)
  rights: Fact[]             // 신규 권리/혜택
  penalties: Fact[]          // 벌칙·과태료·제재
  deadlines: Fact[]          // 기한
  thresholds: Fact[]         // 적용 기준(금액·연령·규모)
  moneyEffects: Fact[]       // 비용/세금/지원금 변화
  meta: { model: string, prompt_version: string, derived_at: datetime }
}
Fact { statement: string, citations: string[], confidence: float }  // citations 비면 무효(인용 강제)

ImpactResult {               // 프롬프트 정의서 §4와 동일 스키마
  law_ref: string           // MVP: "LAW:{lawId}@{effectiveDate}"
  command: string
  summary: string
  claims: Claim[]
  affected_profiles: string[]  // 이 법령이 두드러지게 영향 주는 프로필 유형
  impacts: Impact[]
  actions: Action[]
  effective_info: { status: enum("시행중"|"시행예정"), effective_date: date?, enforcement: enum("즉시"|"유예"|"단계적")? }
  uncertainties: string[]
  disclaimer: string
  meta: { model: string, prompt_version: string, layer: enum("A"|"B") }
}
Claim  { statement: string, citations: string[], confidence: float }  // citations 비면 무효
Impact { aspect: string, direction: string, detail: string, citations: string[] }
Action { what: string, deadline: string, basis: string[] }
```

`source_id` 형식(인용 키, 프롬프트 정의서 §2와 동일):

| 대상 | 형식 | 비고 |
|---|---|---|
| 시행예정 법령 조문 | `LAW:{lawId}@{effectiveDate}:art:{no}` | **MVP 주 경로** — 같은 법령ID에 시행예정본이 복수일 수 있어 시행일로 판별(D43) |
| 시행중 법령 조문 | `LAW:{lawId}:art:{no}` | diff 기준선 |
| 부칙 | `LAW:{lawId}@{effectiveDate}:addenda:{no}` | 시행일·경과조치 근거 |
| 개정문 | `LAW:{lawId}@{effectiveDate}:amend` | 자구 변경 근거 |

**`revision` 산출 규칙 (캐시 무효화 키, 갭 4 확정):**
```
revision = sha256( canonical(
   title + amendReason + amendText + fullText
   + articles[]{no,text,changed,changeType}   // 분석 출력에 영향
   + effectiveDate + effectiveRule + promulgateNo + baselineLawId
) )[:16]
```
- *분석 결과에 영향을 주는 필드만* 해시 대상. `sourceUrl`·소관부처 연락처 등 행정 메타는 제외.
- `effectiveDate`·`effectiveRule` 포함 이유: `ActionPlan`(기한)이 의존한다.
- `promulgateNo` 포함 이유: 부칙 필터 키라 이게 바뀌면 적용되는 부칙 자체가 달라진다.
- 사실층(Layer A) 캐시는 `lawId@effectiveDate + revision` 이 같으면 재사용한다. 의안의 `stage`(심사 단계)는 대상이 아니다 — 공포된 법령엔 단계 변동이 없다(D42).
- 최근 관측 시각(`lastSeen`)은 revision과 **별도** 필드로 두어 신선도만 추적(해시 비포함).

### 1.4 `QueryType` · `AnalysisQuery` — 자연어 질의 표현 (D46)

자연어 질의를 실행 가능한 타입 DTO로 표현한다. **상세·시나리오는 [[QueryPlanner]]**, 여기선 계약만.

```ts
QueryType = enum("LOOKUP"|"SUMMARY"|"DIFF"|"IMPACT"|"ACTION")   // LOOKUP=발견, 나머지=분석 차원
Target    = Reference(LawRef) | Discovery(DiscoveryCriteria)     // sealed

AnalysisQuery {                    // QueryTranslator(Haiku)가 번역, QueryPlanner가 검증
  primaryType: QueryType           // FE 주 뷰·주 검색
  types: QueryType[]               // 채울 차원(포괄질문=복수)
  target: Target
  entities: { lawName?, articleNo?, keywords[], conditions[], domains[] }
  intentSummary: string
  filters: { articleScope: enum("CHANGED_ONLY"|"ALL") }
  profileBound: boolean            // Layer B(IMPACT/ACTION) 채울 수 있는지
  options: { language, promptVersion }
}
```

- **번역이 유일한 LLM 자유도.** 이후 dispatch는 결정론(D46, D37 강화). 자연어 입력 ≠ 동적 제어.
- `Reference` 미해소·`Discovery` 무결과 모두 fail-closed — 지어내지 않는다.

---

## 2. 계약 A — 사용자 프로필 (User Profile · 자기신고)

프로필은 **회원가입 시 자기신고**로 받는다. 고정 세그먼트(버킷)는 개인화 해상도가 낮고(같은 버킷 안에서도 상황이 크게 다름) 외부 데이터셋·군집 파이프라인 의존이 붙는다 — 사용자가 직접 답한 속성이 더 정확하고 최신이다.

### 수집 항목 (전부 선택 — 채울수록 개인화 정확도↑)

```ts
UserProfile {
  userId: string                  // 내부 계정 UUID (성명·연락처 아님)
  purposes: Purpose[]             // ★ 이용 목적(다중) — "무엇 때문에 쓰는가"
  age: int?                       // ★ 만 나이(정수). 생년월일 아님 — 구간화하지 않는다
  occupation: string?             // 직업군 대분류(사무·서비스·생산·전문·자영·농림어업·학생·무직)
  employmentType: enum("임금근로"|"자영업"|"프리랜서"|"무직·은퇴"|"학생")?
  householdType: enum("1인"|"부부"|"부부+자녀"|"한부모"|"기타")?
  housingType: enum("자가"|"전세"|"월세"|"기타")?            // 주거 법령 영향에 직결
  regionSido: string?             // 17개 시도까지만 (시군구·상세주소 미수집)
  updatedAt: datetime
}

Purpose = enum("생활·주거"|"세금·재정"|"근로·고용"|"사업·창업"
              |"복지·의료"|"교육·양육"|"관심사 모니터링"|"기타")
```

**`age`를 구간이 아닌 정수로 두는 이유:** 한국 법령의 적용 기준은 **특정 나이로 끊긴다** — 만 19세(성년·청약), 34세(청년 정책 상한), 65세(노인 복지) 등. `"19-29"` 같은 구간으로 뭉개면 *경계에 걸린 사용자에게 정반대 결론*을 줄 수 있다(19세와 29세는 적용 법령이 크게 다르다). `LawFacts.thresholds`(적용 기준: 금액·연령·규모)와 정확히 대조하려면 정수가 필요하다.

**`purposes`가 개인화의 핵심 축이다** — 같은 30세 사무직이라도 *창업 준비 중*인지 *양육 중*인지에 따라 같은 법령에서 볼 조문이 달라진다. 고정 세그먼트로는 잡기 어려운 구분이다. (관심 도메인은 `purposes`와 중복돼 별도 필드로 두지 않는다.)

### 개인정보 최소 수집

| 구분 | 항목 |
|---|---|
| ✅ 수집 | 이용 목적, **만 나이**(정수), 직업군, 고용형태, 가구형태, 주거형태, **시도** |
| ❌ 미수집 | **성명**, 생년월일, 주민등록번호, 연락처, 상세주소(시군구 이하), 소득액, 직장명 |

> **나이 정수화와 프라이버시:** 구간보다 식별력이 조금 높아지지만, 나이 단독으로는 식별자가 되지 않고 **생년월일(월·일)은 받지 않는다**. 법령 적용 정확도라는 실익이 명확하므로 정수를 택했다.

> ⚠️ **"개인정보 아님"이 아니라 "최소 수집"이다.** 직접식별정보는 받지 않지만, 나이·직업·지역·가구형태의 **조합은 재식별 가능성**이 있고 계정 자체가 식별자다 — 개인정보보호법상 "다른 정보와 쉽게 결합하여 알아볼 수 있는 정보"에 해당할 수 있다. 따라서 **개인정보처리방침·수집 동의·파기 절차는 여전히 필요**하다. 시군구 대신 시도까지만 받는 것도 이 때문이다.
>
> 설계 규율: ① 프로필은 **언제든 수정·삭제 가능**해야 한다. ② 분석 결과 캐시 키에는 `userId`가 아니라 **프로필 속성 해시**를 쓴다 — 동일 속성 사용자 간 캐시를 재사용하면서 개인 단위 추적을 피한다.

### 프롬프트 주입 규율 (불변)

- 프로필은 **"수신자 정보"일 뿐 인용 가능한 법적 source가 아니다** — `<profile>` 블록 전용, `<context>`(법령)와 분리.
- 런타임은 `userId`로 **lookup**(벡터 RAG 아님). 주입 시 `userId`는 제외하고 **속성만** 직렬화.
- **정량 인구통계 용도 금지** — 자기신고 표본이라 인구 대표성이 없다. triage 인구 가중치를 쓰려면 별도 근거가 필요하다([[triage-policy]] §6 Open).

> 도메인 특정 법령의 *기업/기관* 영향은 개인 프로필로 미충족 — 별도 엔티티 프로파일은 MVP 범위 밖(후속).

---

## 3. 계약 B — 오케스트레이터 ↔ Analysis Engine (내부 호출)

> 파이프라인이 Spring 단일 런타임이라 아래 계약은 **내부 메서드 호출/DTO**다(필드·오류 의미 동일, `injected_source_ids` 포함). HTTP 스키마 표기는 계약을 명세하기 위한 것이다.

**경계:** 오케스트레이터(#8)가 해소 게이트를 통과한 입력을 Analysis Engine(#11)에 넘긴다. RAG 검색·프롬프트 빌드·foundation API 호출·구조화 응답은 #11이 수행한다. **같은 Spring 애플리케이션 안의 내부 호출**이며(D35), 아래 HTTP 표기는 필드·오류 의미를 보존하기 위한 이력 표기다.

### 3.1 분석 호출

```
analyze(request)                 // 내부 호출(D35). 아래 HTTP 표기는 계약 서술용
Content-Type: application/json
```

요청:
```jsonc
{
  "command": "PersonaImpactCommand",         // 4종 중 1
  "law": { /* Law 부분집합: lawId,title,effectiveDate,effectiveRule,promulgateNo,
              articles[](changed=true 만), amendText, addenda[], revision */ },
  "baseline": { /* 같은 lawId 의 시행중본 — 변경 조문에 대응하는 현행 조문만 */ } | null,
  "profile": { /* UserProfile 속성만 — userId 제외 */ } | null,  // Layer B만 non-null
  "options": {
    "prompt_version": "0.1",
    "layer": "B",                            // "A"|"B"
    "language": "ko",
    "max_tokens": 4000
  }
}
```

응답(200):
```jsonc
{
  "status": "ok",
  "result": { /* ImpactResult (§1) */ },
  "injected_source_ids": ["LAW:001809@20260804:art:18", "LAW:001809:art:18", "..."]  // #12 검증 입력
}
```

오류:
| 코드 | status | 의미 | Spring 처리 |
|---|---|---|---|
| 400 | `bad_request` | 필수 필드 누락/스키마 위반 | 버그 — 로그·중단 |
| 422 | `insufficient_grounding` | 재생성 N회 후에도 인용검증 실패 | "근거 부족" 폴백 표시 |
| 429 | `rate_limited` | foundation API 한도 | 백오프 재시도 |
| 503 | `upstream_error` | 모델 API 장애 | 백오프·서킷브레이커 |

계약 규칙:
- **요청 게이트는 오케스트레이터(#8)가 먼저** 수행한다. Analysis Engine(#11)은 방어적 재검증만.
- 응답 `result`는 **항상 §1 ImpactResult 스키마**. 인용검증은 엔진 내부 1차 + Verification Gate(#12) 2차.
- **답변 캐시 키(완전 동일 질의 전용, §3.4)**: `hash(정규화 질문) + law.lawId + law.effectiveDate + law.revision + (profileHash|"-") + prompt_version`. **질문 해시를 반드시 포함**해야 다른 질의가 같은 답으로 collapse되지 않는다(D51). context 재사용은 이 키가 아니라 **prompt caching**(§3.3·§3.4)이 담당.
  `effectiveDate` 를 넣는 이유: 같은 법령에 시행예정본이 복수일 수 있어 `lawId` 만으로는 캐시가 충돌한다(D43).
  `profileHash` = 주입 대상 프로필 속성의 정규화 해시 — **`userId`를 키에 쓰지 않는다**(동일 속성 사용자 간 캐시 재사용 + 개인 추적 방지, D41).

### 3.2 수집·해소 엔드포인트 (확정)

**Ingest — `POST /internal/v1/ingest`** (LawConnector 트리거, MVP는 배치)
```jsonc
// 요청 — 시행일 범위로 시행예정 법령을 훑는다
{ "source": "LAW", "efFrom": "2026-08-03", "efTo": "2027-08-03", "lawId": null }
// 응답
{ "status": "ok", "ingested": 42, "lawIds": ["001809", "..."] }
```

**Resolve — `POST /internal/v1/resolve`** (SourceAnalyzer) — 결과는 `resolution`(4상태), 모두 HTTP 200
```jsonc
// 요청  (MVP: type ∈ lawName|text; url 은 501 NotImplemented)
{ "type": "lawName", "value": "주택법" }

// RESOLVED — 단일 확정
{ "resolution": "RESOLVED", "resolved": { "lawId": "001809", "effectiveDate": "2026-08-04" } }
// RESOLVED — 같은 lawId 의 시행예정본 복수(D43): 가장 이른 시행일본으로 해소, 나머지는 alternatives 안내
{ "resolution": "RESOLVED",
  "resolved": { "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률", "effectiveDate": "2026-10-01" },
  "alternatives": [ { "lawId": "010513", "effectiveDate": "2026-11-13" },
                    { "lawId": "010513", "effectiveDate": "2027-02-04" } ],
  "message": "이 법은 시행 예정인 개정이 3건입니다. 가장 이른 2026-10-01 기준으로 안내하며, @efYd 로 다른 시행일도 열 수 있습니다." }
// AMBIGUOUS — 서로 다른 법령이 후보(동명 또는 약매칭)
{ "resolution": "AMBIGUOUS",
  "candidates": [ { "lawId": "…", "title": "…", "score": 0.7 } ],
  "message": "여러 법령이 후보로 잡혔습니다. 어느 것을 말씀하시나요?" }
// NOT_FOUND_YET  (출처까지 질의했으나 없음 — 미공포/수집 지연/이미 시행 중)
{ "resolution": "NOT_FOUND_YET", "checkedSource": true, "message": "신뢰 출처에서 확인되지 않습니다(아직 공포되지 않았거나, 이미 시행 중이어서 분석 대상이 아닐 수 있습니다)." }
// UNVERIFIED     (허위 의심; 유사 실재 법령이 있으면 대조용으로 제시)
{ "resolution": "UNVERIFIED", "similar": [], "message": "확인되지 않은 정보입니다." }
```
- **4상태 모두 HTTP 200**(정상 해소 결과). 4xx/5xx는 §3.1 오류표(시스템 오류)와 별개.
- `RESOLVED`만 분석으로 진행. `AMBIGUOUS`는 사용자 확인, `NOT_FOUND_YET`·`UNVERIFIED`는 분석 거부.
- `url` 입력은 MVP에서 `501 not_implemented`(확장점 스텁). `text`(모호 자연어)는 의미검색으로 처리.

### 3.3 모델·토큰 예산 (확정)

| 단계 | 모델 | ID | 단가(in/out $/1M) | 설정 |
|---|---|---|---|---|
| 영향 추론(Layer A·B) | **Claude Opus 4.8** | `claude-opus-4-8` | 5 / 25 | adaptive thinking, `effort:"high"` |
| (후속) triage·추출 | Claude Haiku 4.5 | `claude-haiku-4-5` | 1 / 5 | 저비용 분류 |
| (대안) 비용 압박 시 추론 | Claude Sonnet 4.6 | `claude-sonnet-4-6` | 3 / 15 | — |
| **임베딩(적재·검색)** | **외부 임베딩 API** (자체 호스팅 X) | 벤더 미확정(후보 아래) | ~0.02~0.13 / — | 분석용·탐색용 공유, **dim 1536** |

- **임베딩 모델 = 외부 API 확정(자체 호스팅 제외).** 인프라 예산 없음 → API 호출. ① 공유 `Embedder`(Spring AI `EmbeddingModel` 위임)가 RAG Indexer·Analysis Engine·SourceAnalyzer에 **동일 모델** 제공. 후보: OpenAI `text-embedding-3-small`(1536, 기본·최저가) / Upstage `solar-embedding`(한국어 특화, 4096) / Cohere·Voyage(1024) — 벤치 후 확정. **기본 1536차원**(ADR-001 가정과 일치 → 저장 결정 불변). 추론 모델(Opus)과 별개이며, **모델 변경 시 전 코퍼스 재색인** 필요. 데이터 민감도 낮음(공개 법령)이라 외부 API 적합 — 사용자 프로필은 임베딩 대상이 아니다.
- **MVP 추론 기본 = Opus 4.8** (1M 컨텍스트, 128K 출력). 법적 정확도 우선. 모델 픽은 `prompt_version`/`meta`로 교체 가능.
- **토큰 예산:** 입력 컨텍스트 상한 **~32K**(초과 시 §2 우선순위로 자르기), 출력 `max_tokens=4000`(구조화 JSON엔 충분, 스트리밍 불필요).
- **프롬프트 캐싱:** 안정 프리픽스(시스템 가드레일 + Layer A 사실 블록)에 `cache_control` → 재호출 시 읽기 ~0.1×. 쓰기 1.25×(5분)/2×(1h). **Opus 4.8 최소 캐시 프리픽스 4096토큰** — 그보다 짧으면 캐시 미적용. 캐시 키 안정성 위해 시스템 프롬프트에 날짜·UUID 주입 금지(프로필·대상 법령은 프리픽스 뒤에 배치). → 이것이 **캐싱 모델의 주 수단**(§3.4, D51).

---

### 3.4 캐싱 모델 (3층, D51)

비용을 줄이되 **"완성 답"을 프로필·법령 단위로 캐시하지 않는다** — 그러면 같은 버킷의 서로 다른 질문("구체적으로 더")에 같은 답을 주는 오류가 난다. 대신 세 층:

1. **Layer A 선계산 (오프라인)** — 법령 사실·조문 diff·LawFacts. 프로필 무관·법령 단위·높은 재사용. Law Store 저장 → 온라인에서 **context로 조립**(답이 아니라 *재료*). D07.
2. **Prompt caching (Anthropic prefix)** — 생성 시 안정 프리픽스(시스템 가드레일 + Layer A 법령 사실 블록)에 `cache_control`. 읽기 ~10% 비용·지연↓(§3.3). **context 재사용의 주 수단** — 같은 법령의 여러 질의가 프리픽스를 공유한다. 프로필·실제 질문은 프리픽스 **뒤**(가변부).
3. **답변 캐시 (좁게)** — **완전 동일 질의**만: `hash(정규화 질문) + law_ref + profileHash + prompt_version + revision`. 트렌드·핫 질의의 콜드-중복만 방어([[concurrency-and-reliability]] §1 single-flight). **Semantic 답 캐시는 기본 미사용** — 유사하나 다른 질의에 같은 답을 주는 위험.

**개인화 답 = 캐시된 context + 프로필 + 실제 질문 + 가드레일 → Opus 1콜.** 질문마다 생성하므로 *서로 다른 질문은 다른 답*이다.

**차원(dimension)은 캐시 키가 아니다** — context 라우팅(무엇을 당길지) + 출력 구조 + 그라운딩 가드레일이다.

> **근거.** Anthropic *prompt caching* = context/prefix 재사용, *semantic caching* = 완성 답 재사용(유사 질의) — 다른 층이다. 우리는 전자를 주로, 후자는 완전 반복에만. 도메인 선례 Harvey AI(법률 RAG · Postgres+pgvector · 검색+그라운딩+질의별 생성)도 같은 골격.

---

## 4. 컴포넌트별 상세 (MVP 활성)

각 항목: **역할 / 입력 / 출력 / 동작 / 의존 / 오류·엣지**.

### #1 SourceConnector (Spring) — 국가법령정보 단일 (MVP, D42)
- 역할: 출처 OpenAPI 호출, 인증/페이징/응답 기벽 흡수.
- 구현체:
  | 커넥터 | 출처 | 산출 | 용도 | 상태 |
  |---|---|---|---|---|
  | `LawConnector` | 국가법령정보 `eflaw` | `RawLaw[]` | **시행예정 법령 = 분석 대상** | ✅ 구현 |
  | `LawConnector` | 국가법령정보 `law` | `RawLaw` | 시행중 법령 = **diff 기준선** | ✅ 구현 |
  | `MolegNoticeConnector` | 법제처 입법예고 | `RawBill[]` | 정부입법 의안 | post-MVP |
  | `AssemblyBillsConnector` | 열린국회정보 | `RawBill[]` | 의원발의 의안(통과율 ~20%) | post-MVP |
- 입력: `listPending(from,to,limit)` · `searchPending(query,from,to,limit)` · `fetchPending(mst,efYd)` · `fetchCurrent(lawId)`
- 출력: `RawLaw[]` — `lawId·mst·title·status·시행일·공포일·공포번호` + 원본 블록(`법령 > {기본정보,조문,부칙,개정문,제개정이유}`)
- 동작: API 호출 → 봉투 검사 → 페이지 순회 → 매핑. **연결키는 `법령ID`**(MST는 버전마다 다름).
- 의존: `LAW_OC` 자격증명.
- 오류: 인증 실패도 **HTTP 200 + `{"result":"사용자 정보 검증에 실패..."}`** 로 오므로 봉투 검사가 유일한 방어선. 5xx → 지수 백오프.
- **확장점:** 의안 커넥터를 되살릴 때 `SourceConnector` 인터페이스를 복원한다(계약은 [[SourceConnector]] 에 보존). 하류 무수정.

### #2 SourceAnalyzer (Spring) — 입력 해소 + 해소 상태 판정
- 역할: 사용자 입력 → **시행예정 법령** ref 해소. **신뢰 출처에서 확인되지 않으면 해소 실패(fail-closed).** 기사·입력 *내용*을 사실로 받지 않고 "어떤 법령인가"만 식별(resolver).
- 입력: `{ type: "lawName"|"text"(MVP) | "url"(스텁), value: string }`
- 출력: `{ resolution: ResolutionState, ... }` — 아래 4상태.
- 동작:
  1. 법령명 정확/퍼지 매칭 — `LawLookup` 포트 경유(토큰 유사도).
  2. 매칭 약하면 → **의미검색**(Vector Index `pending` 네임스페이스, 요약·LawFacts 임베딩) → 후보 도출.
  3. 상태 판정. 모호 입력은 보통 `AMBIGUOUS`(후보 명확화).
  4. **의안번호 분기는 없다**(D42) — 법령에 의안번호가 없고 사용자가 법령ID(`001809`)를 입력하지도 않는다.
- 의존: `LawLookup` 포트 → (현재) LawConnector · (예정) Law Store + Vector Index `pending` ns.

**해소 상태 (ResolutionState):**

| 상태 | 조건 | 다음 단계 |
|---|---|---|
| `RESOLVED` | 출처에서 확정 — 1건, 또는 같은 법령의 시행예정본 복수 중 **가장 이른 본**(나머지는 `alternatives` 안내, D43) | 분석 진행 |
| `AMBIGUOUS` | 후보 2건 이상 — **서로 다른 법령**이 동명/약매칭 | 사용자 확인 요청(어느 법령인지). 같은 `법령ID` 복수는 이제 `RESOLVED`+가장 이른 본(D43) |
| `NOT_FOUND_YET` | 법령스러운 표현이나 출처에 없음 — *미공포 / 수집 지연 / 이미 시행 중* | 분석 거부 + 안내, (후속) 알림 등록 제안 |
| `UNVERIFIED` | 신뢰할 매칭 없음, 또는 입력 주장이 원문과 불일치 — *허위 의심* | 분석 거부 + "확인 불가" 안내, 유사 실재 법령 있으면 대조 제시(팩트체크) |

> **`NOT_FOUND_YET`(미등록·지연)와 `UNVERIFIED`(허위 의심)는 구분한다** — 사용자 안내 문구가 다르다(전자 "아직 없음/지연", 후자 "확인 불가"). 둘 다 분석은 거부(fail-closed) — 지어내지 않음. 판별: 입력이 *법령스러운 표현*이면 `NOT_FOUND_YET`, 그렇지 않거나 주장이 원문과 모순이면 `UNVERIFIED`. **불변식은 `ResolutionResult` 생성자가 강제한다** — 미해소 상태에 결과가 딸려나가는 경로를 타입이 막는다.

### #3 Normalizer (Spring)
- 역할: `RawLaw` → 표준 `Law`+`Article[]`+`Addendum[]`. 출처 API의 기벽을 여기서 끝낸다(ACL 안쪽 절반).
- 입력: `RawLaw` (본문 포함)
- 출력: `Law`
- 동작: 헤더 매핑 → **조문 조립(`조문내용` + `항→호→목` 재귀 병합)** → 변경 플래그(`changed`·`movedFrom/To`) 매핑 → **부칙 필터(`부칙공포번호 == promulgateNo`)** → 부칙 제1조에서 `effectiveRule`·`enforcementType` 추출 → 위임조항 감지 → `revision` 해시.
- 의존: 없음.
- 오류: 파싱 실패 조문 → `changeType:"없음"`+원문 보존, 결손 플래그(드롭 금지).
- ⚠️ `조문내용`만 읽으면 본문이 빈다. 부칙은 이력 전체(실측 42개)가 오므로 필터 없이 쓰면 옛 경과조치를 이번 개정으로 오인한다. 상세: [[Normalizer]]

### #4 Law Store (RDB / Postgres+pgvector)
- 역할: 법령 정본(`Law`/`Article`/`Addendum`) + `LawFacts`(Layer A 캐시) + `ImpactResult`(Layer B 캐시). **벡터 chunks는 별도 [[ChunkStore]]**(PgVectorStore)가 소유 — JSONB 정본(JdbcClient)과 벡터(PgVectorStore)를 분리(2026-08-27).
- 입력/출력: Law/Article/LawFacts/ImpactResult CRUD; 검색 쿼리→Law[].
- 동작: upsert. **유니크 키는 `(lawId, effectiveDate)`** — `lawId` 단독은 안 된다. 같은 법령에 시행예정본이 복수일 수 있다(D43). `LawFacts` 캐시 키=`lawId@effectiveDate + revision`(프로필 무관), `ImpactResult` 답변 캐시 키=§3.4(질문 해시 포함·완전 동일 질의 전용, D51).
- 의존: 없음.
- 오류: 제약 위반 → upsert 충돌 해소.
- **저장소 결정 영향:** [[ADR-001-knowledge-store-sizing|ADR-001]] **불변**이며 D42로 여유가 커졌다 — MVP 코퍼스가 *의안 5만 건 가정*에서 **시행예정 899건 실측**으로 줄었다. 부피 주축은 diff 기준선용 시행중 법령 본문(~0.4GB 추정) 안쪽.

### #7 User Profile Store
- 역할: `UserProfile` 보관·조회 (자기신고, D41).
- 입력: `userId`(런타임 조회), `UserProfile`(회원가입·프로필 수정 시 저장).
- 출력: `UserProfile`.
- 동작: 키 lookup. 자유텍스트 매칭 시 임베딩 최근접(선택).
- 의존: 없음 — 사용자가 직접 입력(외부 데이터셋 의존 제거).

### #7b Query Planner (Spring) — 자연어 → `AnalysisQuery` (D46)
- 역할: 자연어 질의를 타입 DTO로 번역·검증. **상세·시나리오 [[QueryPlanner]]**.
- 구성: `QueryTranslator`(Haiku, NL→DTO) → `QueryPlanner`(해소/발견 결정·프로필 게이트) → `QueryDispatcher`(타입별 라우팅).
- 입력: `{ query, lawRef?, scope? }` → 출력: `PlanResult = Planned(AnalysisQuery) | Unresolved(ResolutionResult)`.
- 의존: `ChatClient`(Haiku), #2 SourceAnalyzer(Reference 해소), LawDiscovery(Discovery 검색, 후속).

### #8 QueryDispatcher / Orchestrator (Spring)
- 역할: 검증된 `AnalysisQuery`를 **QueryType별 핸들러로 라우팅**·조립 → 검증 → 캐시.
- 입력: `AnalysisQuery` (해소·발견 완료). 출력: 타입별 결과 묶음.
- 동작:
  1. `types` 순회 — Layer A(SUMMARY·DIFF)=선계산 캐시 조회, Layer B(IMPACT·ACTION)=Analysis Engine(#11) RAG+LLM.
  2. `Target.Discovery`면 검색 후보 top-K에 분석 타입 **팬아웃**.
  3. **완전 동일 질의**면 답변 캐시(§3.4) 히트 반환. 그 외엔 prompt caching으로 context 재사용하며 생성 — 차원은 캐시 키가 아니다(D51).
  4. **Verification Gate(#12)** 통과분만. 근거 부족 차원은 `unmet`으로 부분성공.
- 의존: #4, #7, #7b, #11, #12, `DimensionHandler` 레지스트리.

### #9 DimensionHandler Registry (Spring)
- 역할: `DimensionHandler` 구현체 자동 발견(`@Component`) → `QueryType → Handler`.

### #10 DimensionHandler ×5 (Spring)
각 핸들러 = `type() / needsProfile() / needsRag() / 실행`. 사용자 선택 모드가 아니라 플래너가 고르는 내부 분해(D46).
| 핸들러 | 입력 | kind | 출력 핵심 |
|---|---|---|---|
| `LookupHandler` | DiscoveryCriteria + (프로필) | 발견 | 후보 법령 랭킹 |
| `SummaryHandler` | Law + `제개정이유` | A | summary, claims |
| `DiffHandler` | Law(변경조문) + baseline 시행중본 + 개정문·부칙 | A | claims(조문별 현행→개정), 시행일 |
| `ImpactHandler` | Law(변경조문) + LawFacts + **UserProfile** | B | impacts |
| `ActionHandler` | Law + 부칙(시행일) + **UserProfile** | B | actions(deadline, basis) |

### #11 Analysis Engine (Spring · Spring AI)
- 역할: **정본에서 context 조립**(LawStore 정확 조회 — 벡터 검색 아님, [[AnalysisEngine]] 정합화 2026-08-27) + 프롬프트 빌드 + **foundation API 호출**(Opus) + 1차 인용검증.
- 입력: §3.1 요청(`law`+`baseline`+`profile?`+`options`).
- 출력: §3.1 응답(ImpactResult 또는 오류) + `injected_source_ids`.
- 동작: source_id 부여 → 프롬프트 정의서 §3 템플릿 조립(변경조문·baseline 대응·개정문·부칙·프로필) → API 호출(constrained JSON) → 스키마·인용 존재성 1차 검증 → 실패 시 재생성(≤N).
- 의존: **Law Store**(정본·캐시) · foundation 모델 API(Opus). *벡터 검색(ChunkStore)은 Discovery 전용이라 분석 경로 무관.*

### #12 Verification Gate (Spring)
- 역할: 최종 응답 게이트.
- 입력: `ImpactResult` + 주입 source_id 집합.
- 출력: 통과 `ImpactResult` | "근거 부족" 폴백.
- 동작: ① 스키마 유효 ② 모든 `claims[].citations` 비어있지 않음 ③ 인용 source_id가 주입 집합(`injected_source_ids`, §3.1 응답)에 실재. 실패 시 422 경로.

### #13 Web Frontend (TS)
- 역할: 검색·선택·결과 표시.
- 입력: 사용자 액션.
- 출력: Spring REST 호출 + 렌더(요약/내 영향/대응안 + 인용 표시).
- 동작: 검색→**법령 선택(시행일 포함)**→(프로필 기반)4종 호출→결과·인용·**시행일**·면책 표시. 프로필 미설정 시 안내 후 입력 유도. 같은 법령에 시행예정본이 여럿이면 **가장 이른 시행일 기준으로 표시**하고 다른 시행일 선택 UI 제공(D43).

### #15 Evaluation Harness (오프라인/CI)
- 역할: 합성 프로필 패널로 E2E smoke·회귀(구동·정성), 정답판정 금지.
- 입력: 프로필 패널 + 법령셋 + `prompt_version`.
- 출력: smoke 결과 + UX 비평 + 커버리지 리포트.
- 동작: 프로필별 에이전트가 실제 REST 흐름 호출 → 스키마/인용 누락·UX 이슈 수집. 정답 앵커는 규칙검증+사람 골든셋. **post-MVP 이연(D36)**.

---

## 5. 정합성 검증 (E2E 인터페이스 매칭)

happy-path를 따라 **생산자 출력 ⊇ 소비자 입력 요건**을 점검한다.

| 단계 | 생산자 → 소비자 | 전달 객체 | 정합성 |
|---|---|---|---|
| 1 | #1 → #3 | `RawLaw`(본문 포함) | ✅ Normalizer 입력=RawLaw. 본문 갭(D38) 해소됨 |
| 2 | #3 → #4 | `Law`(+Article, Addendum, revision) | ✅ Store 스키마=공통모델 §1.1. 유니크 키 `(lawId, effectiveDate)` |
| 3 | #13 → #2 | `{type,value}` | ✅ MVP는 lawName/text 활성, url은 스텁 |
| 4 | #2 → #8 | `ResolutionResult`(4상태) | ✅ `analyzable()`만 `lawRef`로 진행; 불변식은 생성자가 강제 |
| 5 | #8 게이트 | command별 requirements | ✅ §4#10 표와 일치(프로필 필수성) |
| 6 | #4,#7 → #8 → #11 | §3.1 요청(Law+baseline+profile+options) | ✅ profile=UserProfile 속성(userId 제외, D41) |
| 7 | #11 → #12 | §3.1 응답(ImpactResult) | ✅ 스키마=§1=프롬프트 정의서 §4 |
| 8 | #12 → #4 → #13 | 검증된 ImpactResult | ✅ 캐시 키=§3.1 키 |
| 9 | #15 → REST | 동일 경로 재사용 | ✅ 런타임과 같은 계약 |

**교차 문서 일관성 점검**
- source_id 형식: 공통모델 §1 = 프롬프트 정의서 §2 ✅
- ImpactResult 필드: 공통모델 §1 = 프롬프트 정의서 §4 ✅
- 커맨드 4종·requirements: §4#10 = [[components-io-and-scope]] §4 = [[decision-log]] D25 ✅
- 프로필 비인용 격리: §2 = 프롬프트 정의서 §3 = D41 ✅
- 캐시 키(Layer A 프로필 제외): §3.1 = 프롬프트 정의서 §2 ✅

**diff 처리 (MVP 포함).** 같은 `lawId`의 시행중본(`target=law&ID=`) ↔ 시행예정본(`target=eflaw&MST=`)이 동일 스키마로 조문 전문을 주므로 직접 대조한다. 대상 조문은 `조문변경여부='Y'`가 지목(주택법 137개 중 6개), 자구 변경 근거는 `개정문`, 시행 시점은 `부칙`이 제공한다. 조문번호가 곧 정렬키다. 복수 시행예정본의 기준 시점은 [[decision-log|D43]].

**결론:** 위 매칭표대로 생산자 출력 ⊇ 소비자 입력이 성립 — 스펙대로 구현 시 happy-path E2E 동작이 보장된다.

---

## 6. 개발 전 확정 — 완료

개발 전 확정이 필요한 설계 항목은 모두 확정됐고 미해결 설계 결정은 없다([[decision-log]] '다음 결정 대기'). 남은 작업은 구현이다.
