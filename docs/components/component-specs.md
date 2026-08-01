---
title: 컴포넌트 상세 스펙 (역할·입출력·동작) + 계약 + 정합성 검증
status: Draft
version: 0.1
date: 2026-06-25
tags: [spec, components, contract, segment-schema, rest, consistency]
related:
  - "architecture/v0.3-no-video-internal-mcp.md"
  - "mvp/components-io-and-scope.md"
  - "prompts/analysis-prompt-spec.md"
  - "adr/decision-log.md"
---

# 컴포넌트 상세 스펙 + 계약 + 정합성 검증 (v0.1 Draft)

**관련:** [[components-io-and-scope|컴포넌트·MVP 범위]] · [[analysis-prompt-spec|프롬프트 정의서]] · [[v0.3-no-video-internal-mcp|아키텍처 v0.3]] · [[decision-log|결정 로그]]

## 0. 목적·범위·표기법

[[components-io-and-scope]]의 카탈로그를 **개발 가능한 수준의 상세 스펙**으로 확장한다. **MVP 활성 컴포넌트(IN)** 만 다루며, §2 사용자 프로필 스키마·§3 REST 계약을 포함하고, §5에서 **정합성 검증**으로 "스펙대로 개발 시 E2E 동작 가능"함을 확인한다.

표기: 타입은 `string|int|enum|T[]|T?`(?=nullable). 모든 스키마는 JSON 직렬화 기준. 컴포넌트 번호(#n)는 [[components-io-and-scope]] §1과 일치.

---

## 1. 공통 데이터 모델 (Single Source of Truth)

모든 컴포넌트가 공유하는 도메인 타입. 변경 시 본 절이 기준.

속성 출처·용도의 전체 카탈로그는 [[bill-attributes|법안 속성 카탈로그]] 참고. 아래는 그중 모델에 반영하는 필드.

```ts
Bill {                       // 🟢A(API) + 🔵B(원문) 원천 사실 — 그라운딩 대상
  id: string                 // 내부 PK
  billNo: string             // 의안번호 (출처 식별자)
  title: string
  billKind: enum("제정"|"일부개정"|"전부개정"|"폐지")     // A
  lawType: enum("법률"|"시행령"|"시행규칙"|"조례"|"기타")   // A
  summary: string?           // 제안이유+주요내용 (B)
  proposalReason: string?    // B
  mainContents: string?      // B
  proposerType: enum("의원"|"정부"|"위원장")              // A
  proposers: string[]
  committee: string?
  age: string?               // 대수·회기 (A)
  proposeDate: date
  stage: Stage
  stageHistory: StageEvent[] // 단계별 일자 (A)
  procResult: string?        // 처리결과 (A)
  effectiveDate: date?       // 시행(예정)일 — 부칙 우선 (B→A)
  effectiveRule: string?     // "공포 후 6개월" 등 (B 부칙)
  enforcementType: enum("즉시"|"유예"|"단계적")?
  addenda: Addendum[]        // 부칙 (B)
  delegationClauses: string[]// 하위법령 위임 조항 (B)
  fullText: string
  articles: Article[]
  baselineLawId: string?     // 현행법 diff 기준선 (MVP: 국가법령정보로 채움)
  sourceType: enum("ASSEMBLY"|"MOLEG"|"LAW"|"URL"|"TEXT")
  sourceUrl: string?
  revision: string           // 캐시 무효화용 해시 (분석영향 필드 기준)
  lastSeen: datetime         // 신선도 (revision 해시 비포함)
}

StageEvent { stage: Stage, date: date }
Addendum   { no: string, kind: enum("시행일"|"경과조치"|"적용례"|"특례"), text: string }

Article {
  no: string                 // 조문 번호 ("제3조")
  title: string?
  text: string
  changeType: enum("신설"|"개정"|"삭제"|"이동"|"없음")
  oldNewTable: string?       // 신구조문대비표(개정 전후) — MVP diff 원천 (B)
  diffVsCurrent: string?     // 현행법 정밀 대조 (MVP: 신구조문대비표/국가법령정보 기반)
}

BillFacts {                  // 🟡C 파생 — Bill에 저장 X. Layer A 캐시(페르소나 무관). D07.
  bill_ref: string           // "BILL:{billNo}"
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

Stage = enum("발의"|"위원회심사"|"본회의"|"정부이송"|"공포"|"시행")

ImpactResult {               // 프롬프트 정의서 §4와 동일 스키마
  bill_ref: string           // "BILL:{billNo}"
  command: string
  summary: string
  claims: Claim[]
  affected_segments: string[]
  impacts: Impact[]
  actions: Action[]
  stage_info: { stage: string, effective_date: date?, passage_note: string? }
  uncertainties: string[]
  disclaimer: string
  meta: { model: string, prompt_version: string, layer: enum("A"|"B") }
}
Claim  { statement: string, citations: string[], confidence: float }  // citations 비면 무효
Impact { aspect: string, direction: string, detail: string, citations: string[] }
Action { what: string, deadline: string, basis: string[] }
```

`source_id` 형식(인용 키, 프롬프트 정의서 §2와 동일): `BILL:{billNo}:art:{no}` · `BILL:{billNo}:addenda:{no}` · `LAW:{lawId}:art:{no}` · `PREC:{billNo}`.

**`revision` 산출 규칙 (캐시 무효화 키, 갭 4 확정):**
```
revision = sha256( canonical(
   title + summary + fullText
   + articles[]{no,text,changeType}     // 분석 출력에 영향
   + effectiveDate + stage + baselineLawId
) )[:16]
```
- *분석 결과에 영향을 주는 필드만* 해시 대상. `sourceUrl`·`proposers` 등 행정 메타는 제외.
- `stage`·`effectiveDate` 포함 이유: `ActionPlan`(기한)·`stage_info`가 의존.
- 단계만 바뀌고 본문 동일하면 revision 변경 → 보수적 무효화(ActionPlan 재생성 필요). 본문 무변경 시 사실층(Layer A) 캐시는 `billNo+revision`이 같아 재사용 가능하나, 단계 변동은 revision을 바꾸므로 재실행됨(정확성 우선).
- 최근 관측 시각(`lastSeen`)은 revision과 **별도** 필드로 두어 신선도만 추적(해시 비포함).

---

## 2. 계약 A — 사용자 프로필 (User Profile · 자기신고)

**D41로 개정.** ~~Nemotron 군집 6개 세그먼트~~ → **회원가입 시 자기신고 프로필**. 고정 6버킷은 개인화 해상도가 낮고(같은 버킷 안에서 상황이 크게 다름), 외부 데이터셋·군집 파이프라인 의존이 붙었다. 사용자가 직접 답한 속성이 더 정확하고 최신이며 Nemotron 의존이 사라진다.

### 수집 항목 (전부 선택 — 채울수록 개인화 정확도↑)

```ts
UserProfile {
  userId: string                  // 내부 계정 UUID (성명·연락처 아님)
  purposes: Purpose[]             // ★ 이용 목적(다중) — "무엇 때문에 쓰는가"
  ageBand: enum("19-29"|"30-39"|"40-49"|"50-64"|"65+")?    // 생년월일 아님
  occupation: string?             // 직업군 대분류(사무·서비스·생산·전문·자영·농림어업·학생·무직)
  employmentType: enum("임금근로"|"자영업"|"프리랜서"|"무직·은퇴"|"학생")?
  householdType: enum("1인"|"부부"|"부부+자녀"|"한부모"|"기타")?
  housingType: enum("자가"|"전세"|"월세"|"기타")?            // 주거 법안 영향에 직결
  regionSido: string?             // 17개 시도까지만 (시군구·상세주소 미수집)
  interests: string[]?            // 관심 도메인(주거·세제·근로·복지·교육·창업…)
  updatedAt: datetime
}

Purpose = enum("생활·주거"|"세금·재정"|"근로·고용"|"사업·창업"
              |"복지·의료"|"교육·양육"|"관심사 모니터링"|"기타")
```

`purposes`가 핵심이다 — 같은 30대 직장인이라도 *창업 준비 중*인지 *양육 중*인지에 따라 같은 법안의 관심 조문이 달라진다. 고정 세그먼트로는 잡히지 않던 축이다.

### 개인정보 최소 수집

| 구분 | 항목 |
|---|---|
| ✅ 수집 | 이용 목적, **연령대**(구간), 직업군, 고용형태, 가구형태, 주거형태, **시도**, 관심 도메인 |
| ❌ 미수집 | **성명**, 생년월일, 주민등록번호, 연락처, 상세주소(시군구 이하), 소득액, 직장명 |

> ⚠️ **"개인정보 아님"이 아니라 "최소 수집"이다.** 직접식별정보는 받지 않지만, 연령대·직업·지역·가구형태의 **조합은 재식별 가능성**이 있고 계정 자체가 식별자다 — 개인정보보호법상 "다른 정보와 쉽게 결합하여 알아볼 수 있는 정보"에 해당할 수 있다. 따라서 **개인정보처리방침·수집 동의·파기 절차는 여전히 필요**하다. 시군구 대신 시도까지만 받는 것도 이 때문이다.
>
> 설계 규율: ① 프로필은 **언제든 수정·삭제 가능**해야 한다. ② 분석 결과 캐시 키에는 `userId`가 아니라 **프로필 속성 해시**를 쓴다 — 동일 속성 사용자 간 캐시를 재사용하면서 개인 단위 추적을 피한다.

### 프롬프트 주입 규율 (D10에서 승계 — 불변)

- 프로필은 **"수신자 정보"일 뿐 인용 가능한 법적 source가 아니다** — `<persona>` 블록 전용, `<context>`(법령)와 분리.
- 런타임은 `userId`로 **lookup**(벡터 RAG 아님). 주입 시 `userId`는 제외하고 **속성만** 직렬화.
- **정량 인구통계 용도 금지** — 자기신고 표본이라 인구 대표성이 없다. ~~`population_weight`~~ 제거(Nemotron 분포 기반이었음); triage 인구 가중치는 별도 근거가 필요하다([[triage-policy]] §6 Open).

> 도메인 특정 법안의 *기업/기관* 영향은 개인 프로필로 미충족 — 별도 엔티티 프로파일은 MVP 범위 밖(후속).

---

## 3. 계약 B — 오케스트레이터 ↔ Analysis Engine (D35로 REST→내부 호출)

> **D35:** 파이프라인이 Spring으로 통합되어 아래 REST 계약은 **내부 메서드 호출/DTO**로 승계된다(필드·오류 의미는 동일, `injected_source_ids` 포함). HTTP 스키마는 이력·참고용으로 보존.

**경계:** Spring(#8)이 게이트 통과 후 정규화된 입력을 Python(#11)에 넘긴다. RAG 검색·프롬프트 빌드·foundation API 호출·구조화 응답은 Python이 수행. (MVP는 현행법 diff 생략/best-effort라 RAG 비중 낮음.)

### 3.1 분석 호출

```
POST /internal/v1/analyze        (Python Analysis Engine)
Content-Type: application/json
```

요청:
```jsonc
{
  "command": "PersonaImpactCommand",         // 3종 중 1
  "bill": { /* Bill 부분집합: billNo,title,stage,effectiveDate,articles[],fullText,revision */ },
  "persona": { /* UserProfile 속성만 — userId 제외 */ } | null,  // Layer B만 non-null
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
  "injected_source_ids": ["BILL:2210001:art:3", "..."]  // #12 인용 존재성 검증 입력
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
- **요청 게이트는 Spring이 먼저** 수행(아래 §4 #8). Python은 방어적 재검증만.
- 응답 `result`는 **항상 §1 ImpactResult 스키마**. 인용검증은 Python이 1차(엔진 내부)+Spring이 2차(#12) 수행.
- 멱등/캐시 키: `command + bill.billNo + bill.revision + (profileHash|"-") + prompt_version` (Layer A는 프로필 제외).
  `profileHash` = 주입 대상 프로필 속성의 정규화 해시 — **`userId`를 키에 쓰지 않는다**(동일 속성 사용자 간 캐시 재사용 + 개인 추적 방지, D41).

### 3.2 수집·해소 엔드포인트 (확정)

**Ingest — `POST /internal/v1/ingest`** (SourceConnector 트리거, MVP는 배치)
```jsonc
// 요청
{ "source": "ASSEMBLY", "since": "2026-01-01", "billNo": null, "keyword": null }
// 응답
{ "status": "ok", "ingested": 42, "billNos": ["2210001", "..."] }
```

**Resolve — `POST /internal/v1/resolve`** (SourceAnalyzer) — 결과는 `resolution`(4상태), 모두 HTTP 200
```jsonc
// 요청  (MVP: type ∈ billNo|title; url|text 는 501 NotImplemented)
{ "type": "title", "value": "주택임대차보호법 일부개정법률안" }

// RESOLVED
{ "resolution": "RESOLVED", "resolved": "2210001" }
// AMBIGUOUS
{ "resolution": "AMBIGUOUS", "candidates": [ { "billNo": "2210001", "title": "...", "score": 0.93 } ] }
// NOT_FOUND_YET  (출처까지 질의했으나 없음 — 미등록/지연)
{ "resolution": "NOT_FOUND_YET", "checkedSource": true, "message": "신뢰 출처에서 확인되지 않습니다(아직 발의 전이거나 미등록일 수 있음)." }
// UNVERIFIED     (허위 의심; 유사 실제 법안 있으면 대조용으로 제시)
{ "resolution": "UNVERIFIED", "similar": [ { "billNo": "2209888", "title": "...", "score": 0.41 } ], "message": "확인되지 않은 정보입니다." }
```
- **4상태 모두 HTTP 200**(정상 해소 결과). 4xx/5xx는 §3.1 오류표(시스템 오류)와 별개.
- `RESOLVED`만 분석으로 진행. `AMBIGUOUS`는 사용자 확인, `NOT_FOUND_YET`·`UNVERIFIED`는 분석 거부.
- `url`/`text` 입력은 MVP에서 `501 not_implemented`(확장점 스텁).

### 3.3 모델·토큰 예산 (확정)

| 단계 | 모델 | ID | 단가(in/out $/1M) | 설정 |
|---|---|---|---|---|
| 영향 추론(Layer A·B) | **Claude Opus 4.8** | `claude-opus-4-8` | 5 / 25 | adaptive thinking, `effort:"high"` |
| (후속) triage·추출 | Claude Haiku 4.5 | `claude-haiku-4-5` | 1 / 5 | 저비용 분류 |
| (대안) 비용 압박 시 추론 | Claude Sonnet 4.6 | `claude-sonnet-4-6` | 3 / 15 | — |
| **임베딩(적재·검색)** | **외부 임베딩 API** (자체 호스팅 X) | 벤더 미확정(후보 아래) | ~0.02~0.13 / — | 분석용·탐색용 공유, **dim 1536** |

- **임베딩 모델 = 외부 API 확정(자체 호스팅 제외).** 인프라 예산 없음 → API 호출. ① Python 파이프라인의 공유 `Embedder`가 RAG Indexer·Analysis Engine·SourceAnalyzer에 **동일 모델** 제공. 후보: OpenAI `text-embedding-3-small`(1536, 기본·최저가) / Upstage `solar-embedding`(한국어 특화, 4096) / Cohere·Voyage(1024) — 벤치 후 확정. **기본 1536차원**(ADR-001 가정과 일치 → 저장 결정 불변). 추론 모델(Opus)과 별개이며, **모델 변경 시 전 코퍼스 재색인** 필요. 데이터 민감도 낮음(공개 법령·합성 페르소나)이라 외부 API 적합.
- **MVP 추론 기본 = Opus 4.8** (1M 컨텍스트, 128K 출력). 법적 정확도 우선. 모델 픽은 `prompt_version`/`meta`로 교체 가능.
- **토큰 예산:** 입력 컨텍스트 상한 **~32K**(초과 시 §2 우선순위로 자르기), 출력 `max_tokens=4000`(구조화 JSON엔 충분, 스트리밍 불필요).
- **프롬프트 캐싱:** 안정 프리픽스(시스템 가드레일 + Layer A 사실 블록)에 `cache_control` → 재호출 시 읽기 ~0.1×. 쓰기 1.25×(5분)/2×(1h). **Opus 4.8 최소 캐시 프리픽스 4096토큰** — 그보다 짧으면 캐시 미적용. 캐시 키 안정성 위해 시스템 프롬프트에 날짜·UUID 주입 금지(페르소나·법안은 프리픽스 뒤에 배치).

---

## 4. 컴포넌트별 상세 (MVP 활성)

각 항목: **역할 / 입력 / 출력 / 동작 / 의존 / 오류·엣지**.

### #1 SourceConnector (Spring) — 3개 출처 (MVP)
- 역할: 출처 OpenAPI 호출, 인증/페이징/필드명 차이 흡수.
- 구현체:
  | 커넥터 | 출처 | 산출 | 용도 |
  |---|---|---|---|
  | `AssemblyConnector` | 열린국회정보 | `RawBill[]` | 의원발의 법안 |
  | `MolegConnector` | 법제처 입법예고 | `RawBill[]` | 정부입법 법안 |
  | `LawConnector` | 국가법령정보 | `RawLaw[]` | 현행법 기준선(diff·RAG) |
- 입력: `{ since: date?, billNo?: string, keyword?: string }` (법안) / `{ lawId|lawName }` (현행법)
- 출력: `RawBill[]` 또는 `RawLaw[]`(현행 조문)
- 동작: API 호출 → 페이지 순회 → 매핑. 레이트리밋 준수. 법안 커넥터는 Normalizer로, `LawConnector`는 RAG Indexer/Bill Store(기준선)로.
- 의존: 각 출처 API 키.
- 오류: 키 만료/4xx → 로그+스킵, 5xx → 재시도.
- **확장점:** `SourceConnector` 인터페이스 구현체 추가로 URL/뉴스·조례 등 확장(하류 무수정). *법제처 커넥터가 이 패턴의 MVP 내 실증 사례.*

### #2 SourceAnalyzer (Spring) — 입력 해소 + 해소 상태 판정
- 역할: 사용자 입력 → 법안 ref 해소. **신뢰 출처에서 확인되지 않으면 해소 실패(fail-closed).** 기사·입력 *내용*을 사실로 받지 않고 "어떤 법안인가"만 식별(resolver).
- 입력: `{ type: "billNo"|"title"(MVP) | "url"|"text"(스텁), value: string }`
- 출력: `{ resolution: ResolutionState, ... }` — 아래 4상태.
- 동작:
  1. 엔티티 추출(의안번호/법안명/키워드/**주제·효과**). `url`은 본문 추출 후, `text`(모호 자연어)는 그대로.
  2. Bill Store 검색(정확/퍼지).
  3. 매칭 약하면 → **법안 의미검색**(Vector Index 법안 네임스페이스, BillFacts·요약 임베딩) → 후보 도출.
  4. Store/출처 miss → **on-demand 신뢰 출처 질의** → 미등록(지연) vs 부재 판별.
  5. 매칭 결과로 상태 판정. 모호 입력은 보통 `AMBIGUOUS`(후보 명확화).
- 의존: Bill Store, **Vector Index(법안 탐색)**, (확장 시) SourceConnector.

**해소 상태 (ResolutionState):**

| 상태 | 조건 | 다음 단계 |
|---|---|---|
| `RESOLVED` | 출처에서 정확히 1건 확인 | 분석 진행 |
| `AMBIGUOUS` | 후보 2건 이상 | 사용자 확인 요청(후보 제시) |
| `NOT_FOUND_YET` | 잘 형성된 식별자/법안명이나 출처에 없음 — *아직 발의 전 또는 수집 지연* | 분석 거부 + "확인되지 않음(미등록 가능)" 안내, (후속) 알림 등록 제안 |
| `UNVERIFIED` | 신뢰할 매칭 없음, 또는 입력 주장이 원문과 불일치 — *허위 의심* | 분석 거부 + "확인 불가" 안내, 유사 실제 법안 있으면 대조 제시(팩트체크) |

> **`NOT_FOUND_YET`(미등록·지연)와 `UNVERIFIED`(허위 의심)는 구분한다** — 사용자 안내 문구가 다르다(전자 "아직 없음/지연", 후자 "확인 불가"). 둘 다 분석은 거부(fail-closed) — 지어내지 않음. 판별: 입력이 *형식상 유효한 법안 식별자/명*이면 `NOT_FOUND_YET`, 신뢰할 엔티티가 안 잡히거나 주장이 원문과 모순이면 `UNVERIFIED`.

### #3 Normalizer (Spring)
- 역할: RawBill → 표준 `Bill`+`Article[]`.
- 입력: `RawBill`
- 출력: `Bill`
- 동작: 필드 매핑, 조문 파싱(조/항/호 + 부칙), `changeType` 판정, `revision` 해시 계산.
- 의존: 없음.
- 오류: 파싱 실패 조문 → `changeType:"없음"`+원문 보존, 결손 플래그.

### #4 Bill Store (RDB / Postgres+pgvector)
- 역할: 법안 정본(`Bill`/`Article`) + `BillFacts`(Layer A 캐시) + `ImpactResult`(Layer B 캐시) + (선택) 벡터.
- 입력/출력: Bill/Article/BillFacts/ImpactResult CRUD; 검색 쿼리→Bill[].
- 동작: upsert(billNo 유니크). `BillFacts` 캐시 키=`billNo+revision`(페르소나 무관), `ImpactResult` 캐시 키=§3.1.
- 의존: 없음.
- 오류: 제약 위반 → upsert 충돌 해소.
- **저장소 결정 영향:** `BillFacts`+확장 필드 추가는 [[ADR-001-knowledge-store-sizing|ADR-001]] **불변**(≈0.25GB, 헤드룸 내, 스키마 진화이지 사이징·기술 변경 아님).

### #7 User Profile Store
- 역할: `UserProfile` 보관·조회 (자기신고, D41).
- 입력: `userId`(런타임 조회), `UserProfile`(회원가입·프로필 수정 시 저장).
- 출력: `UserProfile`.
- 동작: 키 lookup. 자유텍스트 매칭 시 임베딩 최근접(선택).
- 의존: 없음 — 사용자가 직접 입력(외부 데이터셋 의존 제거).

### #8 AnalysisPipeline / Orchestrator (Spring)
- 역할: 게이트 → 컨텍스트 조립 → Python 호출 → 검증 → 캐시.
- 입력: `{ billRef, command, userId? }`
- 출력: 검증된 `ImpactResult`.
- 동작:
  0. **해소 상태 게이트**: `RESOLVED`만 진행. `AMBIGUOUS`→사용자 확인 반환, `NOT_FOUND_YET`/`UNVERIFIED`→분석 거부 + 안내 문구 반환(분석 단계 미진입).
  1. `supports/requirements` **게이트**: PersonaImpact/ActionPlan은 `userId`(프로필) 필수, 모든 분석은 Bill(#3·#4) 필수. 미충족 시 즉시 거부.
  2. Bill(RDB) + UserProfile(Store) 로드 → §3.1 요청 구성.
  3. 캐시 조회(키) → 히트면 반환.
  4. Python `/analyze` 호출.
  5. **Verification Gate(#12)** 통과분만 캐시·반환, 실패 시 폴백.
- 의존: #4, #7, #11, #12, Command Registry.
- 오류: §3.1 오류표 처리.

### #9 Command Registry (Spring)
- 역할: `AnalysisCommand` 구현체 자동 발견(`@Component`).
- 출력: `name → AnalysisCommand`.

### #10 AnalysisCommand ×4 (Spring)
각 커맨드 = `name() / supports() / requirements() / 출력 핵심필드`.
| 커맨드 | requirements | layer | 출력 핵심 |
|---|---|---|---|
| `ImpactSummaryCommand` | Bill | A→B경계 | summary, claims |
| `LawDiffCommand` | Bill, baseline(신구조문대비표 또는 현행법) | A | claims(조문별 현행→개정), impacts |
| `PersonaImpactCommand` | Bill, segment | B | affected_segments, impacts |
| `ActionPlanCommand` | Bill, segment | B | actions(deadline,basis) |

### #11 Analysis Engine (Spring · Spring AI)
- 역할: 현행법 **RAG 검색**(MVP 활성) + 프롬프트 빌드 + **foundation API 호출** + 1차 인용검증.
- 입력: §3.1 요청.
- 출력: §3.1 응답(ImpactResult 또는 오류).
- 동작: source_id 부여 → 프롬프트 정의서 §3 템플릿 조립 → API 호출(constrained JSON) → 스키마·인용 존재성 1차 검증 → 실패 시 재생성(≤N).
- 의존: foundation 모델 API.

### #12 Verification Gate (Spring/Py)
- 역할: 최종 응답 게이트.
- 입력: `ImpactResult` + 주입 source_id 집합.
- 출력: 통과 `ImpactResult` | "근거 부족" 폴백.
- 동작: ① 스키마 유효 ② 모든 `claims[].citations` 비어있지 않음 ③ 인용 source_id가 주입 집합(`injected_source_ids`, §3.1 응답)에 실재. 실패 시 422 경로.

### #13 Web Frontend (TS)
- 역할: 검색·선택·결과 표시.
- 입력: 사용자 액션.
- 출력: Spring REST 호출 + 렌더(요약/내 영향/대응안 + 인용 표시).
- 동작: 검색→법안 선택→(프로필 기반)4종 호출→결과·인용·면책 표시. 프로필 미설정 시 안내 후 입력 유도.

### #15 Evaluation Harness (오프라인/CI)
- 역할: 합성 페르소나 패널로 E2E smoke·회귀(구동·정성), 정답판정 금지.
- 입력: 세그먼트 패널 + 법안셋 + `prompt_version`.
- 출력: smoke 결과 + UX 비평 + 커버리지 리포트.
- 동작: 세그먼트별 에이전트가 실제 REST 흐름 호출 → 스키마/인용 누락·UX 이슈 수집. 정답 앵커는 규칙검증+사람 골든셋.

---

## 5. 정합성 검증 (E2E 인터페이스 매칭)

happy-path를 따라 **생산자 출력 ⊇ 소비자 입력 요건**을 점검한다.

| 단계 | 생산자 → 소비자 | 전달 객체 | 정합성 |
|---|---|---|---|
| 1 | #1 → #3 | RawBill | ✅ Normalizer 입력=RawBill |
| 2 | #3 → #4 | Bill(+Article,revision) | ✅ Store 스키마=공통모델 §1 |
| 3 | #13 → #2 | `{type,value}` | ✅ MVP는 billNo/title만 활성 |
| 4 | #2 → #8 | `resolution`(4상태) | ✅ `RESOLVED`만 billRef로 진행; 그 외는 게이트에서 안내·거부 |
| 5 | #8 게이트 | command별 requirements | ✅ §4#10 표와 일치(persona 필수성) |
| 6 | #4,#7 → #8 → #11 | §3.1 요청(Bill+persona+options) | ✅ persona=UserProfile 속성(userId 제외, D41) |
| 7 | #11 → #12 | §3.1 응답(ImpactResult) | ✅ 스키마=§1=프롬프트 정의서 §4 |
| 8 | #12 → #4 → #13 | 검증된 ImpactResult | ✅ 캐시 키=§3.1 키 |
| 9 | #15 → REST | 동일 경로 재사용 | ✅ 런타임과 같은 계약 |

**교차 문서 일관성 점검**
- source_id 형식: 공통모델 §1 = 프롬프트 정의서 §2 ✅
- ImpactResult 필드: 공통모델 §1 = 프롬프트 정의서 §4 ✅
- 커맨드 3종·requirements: §4#10 = [[components-io-and-scope]] §4 = [[decision-log]] D13 ✅
- persona 비인용 격리: §2 = 프롬프트 정의서 §3 = D10 ✅
- 캐시 키(Layer A persona 제외): §3.1 = 프롬프트 정의서 §2 ✅

**발견된 갭 / 개발 전 닫을 것**
1. **현행법 diff 처리 수준 (확정·MVP 포함):** MVP에 `LawDiff` 포함. **신구조문대비표**(법안 원문, 항상 정렬·인용 가능)를 *1차 diff*로, **국가법령정보 현행법**(`LawConnector`→Vector Index)을 *권위 기준선·보강*으로 사용 → `baselineLawId` 채움. 남은 과제는 *조문 정렬(alignment)* 정밀도(§6) — 신구조문대비표가 없는/불완전한 법안에서 현행법 자동매칭 품질. 이때만 confidence↓ + uncertainties 표기.
2. **resolve/ingest 스키마 미확정** — §3.2는 후속(§6 Open). MVP 검색 경로엔 영향 없음.
3. ~~인용 존재성 검증의 source_id 집합 전달~~ — **닫힘**: §3.1 응답에 `injected_source_ids: string[]` 추가, #12가 이를 검증 입력으로 사용.
4. **revision 산출 규칙** — 단계변동만? 본문변동 포함? 캐시 정확성 좌우 → 규칙 명문화 필요.

**결론:** 갭 3은 본 문서에서 닫았다. 남은 갭 2·4(스키마 보강, 소규모)와 갭 1(정확도 면책의 *정책 결정*)을 처리하면 **스펙대로 구현 시 happy-path E2E 동작이 보장**된다. 갭 1은 동작 자체를 막지 않는다.

---

## 6. Open (개발 전 확정) — 전부 닫힘

- [x] §3.1 응답에 `injected_source_ids` 추가(정합성 갭 3)
- [x] `revision` 산출 규칙(갭 4) → §1 확정
- [x] resolve/ingest 엔드포인트 스키마 → §3.2 확정
- [x] 세그먼트 군집 개수·기준 → §2 확정(6개)
- [x] foundation 모델 픽 + 토큰 예산 → §3.3 확정(Opus 4.8)
- [x] 임베딩 모델 배치 → §3.3 **외부 API 확정**(자체 호스팅 제외, dim 1536), 벤더는 벤치 후
- [x] 현행법 diff MVP 처리 수준 → §5 갭1 **MVP 포함**(신구조문대비표+국가법령정보)
- [ ] 조문 정렬(alignment) 정밀도 — 신구조문대비표 파싱 vs 현행법 자동매칭 (구현 세부)

> 남은 작업은 대부분 *구현*. 본 스펙대로 개발 시 happy-path E2E 동작 보장.
