---
title: 서비스 기능·공개 API 명세 (MVP)
status: Draft
version: 0.1
date: 2026-08-02
tags: [mvp, api, spec, rest, features]
related:
  - "mvp/components-io-and-scope.md"
  - "components/component-specs.md"
  - "prompts/analysis-prompt-spec.md"
  - "architecture/v0.9-nl-query-planner.md"
  - "components/QueryPlanner.md"
---

# 서비스 기능·공개 API 명세 (MVP)

**관련:** [[components-io-and-scope|컴포넌트·범위]] · [[component-specs|컴포넌트 스펙]] · [[analysis-prompt-spec|프롬프트 정의서]] · [[v0.9-nl-query-planner|아키텍처 v0.9]] · [[QueryPlanner|Query Planner]]

이 문서는 **웹앱(및 향후 클라이언트)이 호출하는 공개 REST API `/api/v1/*`** 와 그것이 제공하는 **사용자 기능**을 한곳에 고정한다.

> **범위 경계.** 오케스트레이터↔Analysis Engine 사이의 **내부 계약(`/internal/v1/*`)** 은 [[component-specs]] §3에 있고 여기서 반복하지 않는다. 본 문서는 *바깥 경계*(클라이언트 ↔ 서버)만 다룬다. 온라인 흐름 전체는 [[v0.8-pending-law-corpus]] §3.2.

---

## 1. 서비스가 제공하는 기능

**사용자는 자연어로 자유롭게 묻는다** — "주택법 바뀌면 나 전세 계약 어떻게 돼?". 시스템은 이 질문을 **타입으로 분류**하고 답을 **차원으로 구조화**하며, **각 차원마다 조문 인용을 강제**한다. 아래 넷은 *사용자가 고르는 버튼*이 아니라 **답변의 구조이자 그라운딩 가드레일**이다.

| # | 차원 | 대응하는 질문 | 커맨드(내부) | 계층 | 프로필 |
|---|---|---|---|---|---|
| F1 | **쉬운 요약** | "이 법이 대체 뭔데?" | `ImpactSummary` | A | 불필요 |
| F2 | **무엇이 바뀌나** | "현행법 대비 뭐가 달라져?" | `LawDiff` | A | 불필요 |
| F3 | **내게 미치는 영향** | "그래서 나한테 무슨 상관인데?" | `PersonaImpact` | B | **필요** |
| F4 | **대응 행동** | "그럼 나 뭘 해야 해? 언제까지?" | `ActionPlan` | B | **필요** |
| F0 | **법령 발견(검색)** | "나한테 영향 있을 법령 찾아줘" | `LOOKUP` | 발견 | 조건부 |

> F0(**`LOOKUP`**, D46)은 특정 법령을 모른 채 *코퍼스에서 찾는* 동작이다 — 나머지 넷과 달리 답변 차원이 아니라 **발견**이며, 프로필/도메인/조건으로 `pending` 네임스페이스를 검색한다. "찾아서 분석해줘"면 발견 후 top-K를 분석으로 팬아웃한다.
>
> **커맨드 = 내부 분해, 사용자 선택 아님.** 4종은 여전히 `AnalysisCommand`(개방-폐쇄, Layer A/B 구분)로 구현되지만, *사용자가 토글하는 모드*가 아니라 **플래너가 질문에서 골라내는 내부 단위**다. "무엇이 바뀌었는지만 알려줘"는 F2만, 포괄 질문은 넷 다 돌 수 있다. 각 커맨드는 인용 없는 주장을 차단하는 **가드레일**이기도 하다(D08).
>
> **플래너(질의 → 타입 선택)는 [[QueryPlanner|Query Planner]]로 설계 확정**(D46). 자연어를 타입 DTO(`AnalysisQuery`)로 번역하고 타입이 dispatch를 결정한다 — 클라이언트가 커맨드를 지정하던 기존 전제를 대체한다.

이를 둘러싼 지원 기능:

| # | 기능 | 설명 |
|---|---|---|
| S1 | **법령 검색·발견** | 법령명 정확 검색 + **모호한 자연어**("집 구할 때 뭔가 바뀐다던데")로도 후보 탐색 |
| S2 | **해소 안내(4상태)** | 확인 안 되면 분석하지 않고, **미등록**과 **허위 의심**을 구분해 안내(fail-closed) |
| S3 | **자기신고 프로필** | 회원가입 시 이용 목적·나이·직업 등 자기신고. 성명 등 직접식별정보 미수집(D41) |
| S4 | **인용·시행일 표시** | 모든 주장에 조문 근거 링크 + 확정 시행일 + 법률자문 아님 면책 |

**MVP 제외**(기능은 유지, 구현 후순위): 의안(의원발의·입법예고) 분석, URL/뉴스 입력 해소, 도메인 심화 추론, 알림 등록.

---

## 2. 공개 API 개요

```
BASE  /api/v1
포맷  요청·응답 모두 application/json; charset=utf-8
언어  응답 자연어는 한국어(ko) 고정 (MVP)
인증  프로필·Layer B 분석은 인증 세션 필요 (Authorization: Bearer <token>)
      — 인증 방식 확정은 웹 구현 시. 조회·요약(Layer A)은 익명 허용
```

**두 종류의 리소스로 나뉜다.** 분석(`/analyses`)이 사용자 행위의 중심이고, 법령(`/laws`)은 그것이 *참조*하는 읽기 전용 사실 데이터다. **분석은 법령의 하위 리소스가 아니다** — 사용자는 `lawId`를 모른 채 질문할 수 있고, 해소 자체가 질의의 일부이기 때문이다.

| 메서드 | 경로 | 기능 | 인증 |
|---|---|---|---|
| `POST` | `/analyses` | **자연어 질의 → 그라운딩된 분석**(F1~F4) | 조건부* |
| `GET` | `/laws` | S1·S2 법령 검색·해소 | — |
| `GET` | `/laws/{lawId}` | 한 법령의 **시행예정본 목록**(D43) | — |
| `GET` | `/laws/{lawId}/{efYd}` | 이미 분석된 법령 사실 + 조문 대조(Layer A) | — |
| `GET` | `/profile` | S3 내 프로필 조회 | 필요 |
| `PUT` | `/profile` | S3 프로필 생성·수정 | 필요 |
| `DELETE` | `/profile` | S3 프로필 파기(개인정보) | 필요 |

\* `/analyses`는 익명도 허용하되 **F3·F4(Layer B)는 인증 세션이 있을 때만** 채워진다(없으면 F1·F2까지).

`/laws` 는 **읽기 전용 참조 데이터**다 — 오프라인에서 선계산·캐시된 Layer A 사실이라 익명·저지연이며, 검색 자동완성·법령 상세 화면에 쓴다. `POST /analyses` 가 이 데이터를 내부에서 끌어와 개인화 답을 만든다.

`{efYd}` = 시행일자 `YYYY-MM-DD`. **경로에 시행일을 넣는 이유**: 같은 `lawId`에 시행예정본이 여럿일 수 있어(D43) `lawId` 만으로는 대상이 특정되지 않는다.

---

## 3. 엔드포인트 상세

### 3.0 `POST /analyses` — 자연어 질의 분석 (핵심)

사용자 행위의 중심. **자연어 질문**을 받아 해소→조립→추론→인용검증을 거쳐 **그라운딩된 구조화 답변**을 돌려준다.

```jsonc
// POST /api/v1/analyses   (Authorization 있으면 Layer B까지)
// 요청
{
  "query": "주택법 바뀌면 나 전세 계약 어떻게 돼?",   // ★ 필수 — 자연어
  "lawRef": { "lawId": "001809", "effectiveDate": "2026-08-04" },  // 선택: 검색에서 이미 특정했으면
  "scope": ["impact", "action"]                       // 선택: 특정 차원만. 없으면 플래너가 질문에서 판단
}
```

**처리 순서**(온라인 경로, [[v0.9-nl-query-planner]] §3.2 · [[QueryPlanner]]):
1. **번역** — `QueryTranslator`(Haiku)가 자연어를 `AnalysisQuery`(타입 집합·엔티티·target)로. `scope`/`lawRef`가 오면 힌트로 병합.
2. **해소/발견** — target이 `Reference`면 해소(4상태, fail-closed), `Discovery`(LOOKUP)면 `pending` ns 코퍼스 검색.
3. **플래닝** — 프로필 없으면 Layer B 타입 제거(`unmet`). 검증된 `AnalysisQuery` 확정.
4. **디스패치·검증** — 타입별 핸들러(Layer A=캐시 조회, Layer B=RAG+Opus) → 인용검증 게이트. Discovery+분석은 top-K 팬아웃.

**RESOLVED 응답** — 해소되고 분석까지 성공:
```jsonc
{
  "resolution": "RESOLVED",
  "law_ref": "LAW:001809@2026-08-04",
  "law": { "lawId": "001809", "title": "주택법", "effectiveDate": "2026-08-04" },
  "answer": {                                  // 질문에 필요한 차원만 채워진다
    "impact": {                                // F3 (프로필 있을 때)
      "impacts": [
        { "aspect": "주거", "direction": "영향 있음",
          "detail": "전세로 거주 중이라면 사용검사 전 현장점검을 요청할 권리가 새로 생깁니다.",
          "citations": ["LAW:001809@2026-08-04:art:49"] }
      ]
    },
    "action": {                                // F4
      "actions": [
        { "what": "관리사무소에 현장점검 요청 가능 여부 확인",
          "deadline": "2026-08-04 시행 이후 사용검사 신청 건부터",
          "basis": ["LAW:001809@2026-08-04:addenda:3"] }
      ]
    }
  },
  "unmet": ["profile"],        // 인증·프로필이 없어 못 채운 차원이 있으면 표기(예: 익명이라 impact 생략 시)
  "uncertainties": ["세부기준 일부가 대통령령에 위임되어 확정 전입니다."],
  "disclaimer": "법률 자문이 아닌 참고용 정보입니다."
}
```

**미해소 응답** — 해소가 `RESOLVED`가 아니면 **분석하지 않고** 4상태를 그대로 답으로 돌려준다(§3.1과 동일 스키마의 `resolution`/`candidates`/`message`). 대화적으로 "그 법을 못 찾았어요"로 노출된다.

**동작 규칙:**
- 각 차원 결과는 [[component-specs]] §1 `ImpactResult` 스키마를 따른다.
- **인용검증 게이트**를 통과한 차원만 나간다. 근거 부족 차원은 `422`가 아니라 `unmet`에 사유(`insufficient_grounding`)로 표기(부분 성공 허용).
- 익명 요청에 F3·F4가 필요하면 그 차원은 생략하고 `unmet: ["profile"]` — 전체를 막지 않는다.
- 캐시 키는 `userId`가 아니라 **프로필 속성 해시 + law_ref + 차원**(D41).
- **멀티턴(대화 상태 지속)은 MVP 밖** — 단발 질의만. 멀티턴은 D37 재검토 트리거라 별도 결정 후 도입.

> **멱등성 주의.** `POST`지만 같은 `(query, lawRef, profileHash)`는 캐시로 같은 결과를 준다. 리소스 신규 생성(분석 이력 저장)은 post-MVP — 그때 `GET /analyses/{id}`(이력·공유)로 확장.

### 3.1 `GET /laws` — 검색·해소 (S1·S2)

법령명 또는 자연어를 받아 **해소 4상태**로 답한다. 해소 결과는 모두 **HTTP 200**이다(4xx/5xx는 시스템 오류 전용).

```
GET /api/v1/laws?q=주택법
GET /api/v1/laws?q=집 구할 때 뭔가 바뀐다던데
```

**RESOLVED** — 정확히 하나로 특정:
```jsonc
{
  "resolution": "RESOLVED",
  "law": { "lawId": "001809", "title": "주택법",
           "effectiveDate": "2026-08-04", "status": "시행예정",
           "amendKind": "일부개정", "ministry": "국토교통부" }
}
```

**AMBIGUOUS** — 후보 다수. **같은 `lawId`의 시행예정본이 여럿이면 시행일 선택을 유도**(D43):
```jsonc
{
  "resolution": "AMBIGUOUS",
  "message": "같은 법령에 시행 예정인 개정이 여러 건입니다. 어느 시행일 기준으로 보시겠습니까?",
  "candidates": [
    { "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률",
      "effectiveDate": "2026-10-01", "score": 0.99 },
    { "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률",
      "effectiveDate": "2026-11-13", "score": 0.99 }
  ]
}
```

**NOT_FOUND_YET** — 법령스러우나 신뢰 출처에 없음(미공포/수집 지연/이미 시행 중):
```jsonc
{ "resolution": "NOT_FOUND_YET", "checkedSource": true,
  "message": "신뢰 출처에서 확인되지 않습니다(아직 공포되지 않았거나, 이미 시행 중이어서 분석 대상이 아닐 수 있습니다)." }
```

**UNVERIFIED** — 허위 의심(신뢰할 매칭 없음):
```jsonc
{ "resolution": "UNVERIFIED", "similar": [],
  "message": "확인되지 않은 정보입니다. 실재하는 법령과 매칭되지 않습니다." }
```

> **두 경로가 이 엔드포인트를 쓴다.** ① *브라우징* — 자동완성·목록에서 법령을 고른 뒤 `GET /laws/{lawId}/{efYd}`로 상세를 보거나, 그 `lawRef`를 `POST /analyses`에 실어 질문. ② *직접 질의* — 사용자가 `POST /analyses`에 자연어만 넣으면 서버가 내부에서 같은 해소를 수행하므로 이 엔드포인트를 거치지 않아도 된다.
> **클라이언트 규칙:** `RESOLVED` → 진행. `AMBIGUOUS` → 후보(특히 시행일) 선택 UI. `NOT_FOUND_YET`·`UNVERIFIED` → 분석 진입 금지, 안내 문구 그대로 노출. **미등록과 허위는 문구가 다르므로 뭉뚱그리지 말 것**(D23).

### 3.2 `GET /laws/{lawId}` — 시행예정본 목록 (D43)

한 법령에 걸린 시행 대기 개정을 모두 준다. 클라이언트가 시행일을 고르게 하기 위한 것.

```jsonc
// GET /api/v1/laws/010513
{ "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률",
  "pending": [
    { "effectiveDate": "2026-10-01", "promulgateNo": "21324", "amendKind": "일부개정" },
    { "effectiveDate": "2026-11-13", "promulgateNo": "21500", "amendKind": "일부개정" },
    { "effectiveDate": "2027-02-04", "promulgateNo": "21611", "amendKind": "일부개정" }
  ],
  "current": { "effectiveDate": "2026-01-02", "promulgateNo": "20900" }  // diff 기준선
}
```

### 3.3 `GET /laws/{lawId}/{efYd}` — 법령 사실 + 조문 대조 (F1·F2, Layer A)

**프로필과 무관한** 사실층. 오프라인 선계산·캐시되므로 익명·저지연이다.

```
GET /api/v1/laws/001809/2026-08-04
```
```jsonc
{
  "law": {
    "lawId": "001809", "title": "주택법", "effectiveDate": "2026-08-04",
    "amendKind": "일부개정", "ministry": "국토교통부",
    "effectiveRule": "이 법은 공포 후 6개월이 경과한 날부터 시행한다. 다만, …",
    "enforcementType": "단계적"
  },
  "summary": {                                   // F1 ImpactSummary
    "text": "주택 사업계획 심의를 통합해 절차를 줄이고, 감리·현장점검 절차를 강화합니다.",
    "claims": [
      { "statement": "교육환경·재해영향 평가를 통합심의 대상에 추가한다.",
        "citations": ["LAW:001809@2026-08-04:art:18"], "confidence": 0.94 }
    ]
  },
  "diff": {                                       // F2 LawDiff — 변경 조문만
    "changedArticles": [
      { "no": "18", "title": "사업계획의 통합심의 등", "changeType": "개정",
        "current": "…도시계획·건축·교통…",
        "revised": "…도시계획·건축·환경·교통·재해…",
        "citations": ["LAW:001809:art:18", "LAW:001809@2026-08-04:art:18"] }
    ],
    "baseline": { "lawId": "001809", "effectiveDate": "2026-07-01" }
  },
  "uncertainties": ["일부 세부기준은 대통령령에 위임되어 있어 확정 전입니다."],
  "disclaimer": "법률 자문이 아닌 참고용 정보입니다."
}
```

> `diff.changedArticles` 는 **`조문변경여부='Y'` 조문만**(실측 137개 중 6개). 자구 변경 근거는 `개정문`, 대조 기준선은 같은 `lawId`의 시행중본이다.

> `/laws/{lawId}/{efYd}` 는 **읽기 전용**이다 — F1·F2에 해당하는 Layer A 사실을 담지만, 이것은 *브라우징·검색 상세용 조회*이지 분석 요청이 아니다. 개인화 분석은 `POST /analyses` 로만 한다.

### 3.4 프로필 — `GET · PUT · DELETE /profile` (S3)

```jsonc
// PUT /api/v1/profile   (전부 선택 입력)
{ "purposes": ["생활·주거", "관심사 모니터링"],
  "age": 29, "occupation": "사무",
  "employmentType": "임금근로", "householdType": "1인",
  "housingType": "전세", "regionSido": "서울특별시" }

// GET /api/v1/profile → 위 속성 + updatedAt (userId 는 응답에 포함하지 않는다)
// DELETE /api/v1/profile → 204, 개인정보 파기
```

- 스키마는 [[component-specs]] §2 `UserProfile`. **`age` 는 정수**(구간화 안 함), 성명·생년월일·주민번호·연락처·상세주소·소득 **미수집**.
- ⚠️ 직접식별정보를 안 받아도 조합 재식별 소지가 있어 **개인정보처리방침·수집 동의·파기 절차 필요**(D41). `DELETE` 는 파기 요건 충족용.

---

## 4. 공통 규약

### 4.1 오류 (시스템 오류만 4xx/5xx)

해소 4상태는 **정상 결과(200)** 다. 아래는 시스템 오류.

| 코드 | status | 의미 |
|---|---|---|
| 400 | `bad_request` | 스키마 위반·필수 파라미터 누락 |
| 401 | `unauthenticated` | 인증 전용 엔드포인트(`/profile`)에 세션 없이 접근 |
| 404 | `not_found` | `lawId`/`efYd` 조합이 저장소에 없음 |
| 422 | `insufficient_grounding` | 요청한 **모든** 차원이 인용검증 실패(일부만 실패면 200 + `unmet`) |
| 429 | `rate_limited` | 모델·출처 API 한도 |
| 503 | `upstream_error` | 모델 API 장애 |

> **프로필 미설정은 오류가 아니다.** `/analyses`는 익명·프로필 없음도 정상 처리하고 채울 수 없는 차원을 `unmet`으로 표기한다(부분 성공). 전면 차단(`409`)하지 않는 이유는 자연어 질의 모델에서 "일단 답을 주되 개인화는 프로필 입력을 유도"가 자연스럽기 때문이다.

```jsonc
{ "status": "insufficient_grounding",
  "message": "요청하신 내용을 뒷받침할 조문 근거를 찾지 못했습니다." }
```

### 4.2 그라운딩·인용 (불변)

- 모든 `claims`/`impacts`/`diff` 항목은 **비어 있지 않은 `citations`** 를 가진다. 빈 인용은 서버에서 차단된다(D08).
- `source_id` 형식은 [[component-specs]] §1 표. 시행예정 조문은 **시행일 포함**: `LAW:{lawId}@{effectiveDate}:art:{no}`.
- 클라이언트는 각 인용을 원문 조문으로 링크해 **역추적 가능**하게 표시한다.

### 4.3 개인정보·프라이버시

- 개인정보(프로필)를 **URL·쿼리스트링에 넣지 않는다** — 프로필은 인증 컨텍스트로만 전달.
- 응답에 `userId` 를 싣지 않는다. 로그·캐시 키에도 원본 `userId` 대신 속성 해시.
- 프로필 관련 엔드포인트는 HTTPS 전제.

---

## 5. Open

- [x] **Query Planner 설계 확정(D46)** — 자연어 → `AnalysisQuery`(타입 DTO) → dispatch. 5 QueryType(LOOKUP 포함), Reference/Discovery target. 상세 [[QueryPlanner]]
- [ ] LOOKUP 팬아웃 정책 — top-K 자동 분석 vs 후보만 반환 후 사용자 선택
- [ ] 인증 방식 확정(세션 쿠키 vs Bearer 토큰) — 웹 구현 단계
- [ ] `AMBIGUOUS` 시행일 복수(D43)의 **기본 선택 정책**(가장 이른 시행일 자동 vs 선택 강제)
- [ ] 부분 성공(`unmet` 차원) 렌더링 규약 — 웹 UX
- [ ] 멀티턴 대화(상태 지속) — **D37 재검토 트리거**. 단발 질의로 MVP 한정
- [ ] 알림 등록(`NOT_FOUND_YET` 법령의 시행 시 통지) — post-MVP
