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
  - "architecture/v0.8-pending-law-corpus.md"
---

# 서비스 기능·공개 API 명세 (MVP)

**관련:** [[components-io-and-scope|컴포넌트·범위]] · [[component-specs|컴포넌트 스펙]] · [[analysis-prompt-spec|프롬프트 정의서]] · [[v0.8-pending-law-corpus|아키텍처 v0.8]]

이 문서는 **웹앱(및 향후 클라이언트)이 호출하는 공개 REST API `/api/v1/*`** 와 그것이 제공하는 **사용자 기능**을 한곳에 고정한다.

> **범위 경계.** 오케스트레이터↔Analysis Engine 사이의 **내부 계약(`/internal/v1/*`)** 은 [[component-specs]] §3에 있고 여기서 반복하지 않는다. 본 문서는 *바깥 경계*(클라이언트 ↔ 서버)만 다룬다. 온라인 흐름 전체는 [[v0.8-pending-law-corpus]] §3.2.

---

## 1. 서비스가 제공하는 기능

시민이 하는 일은 **"곧 시행될 법이 나에게 뭘 바꾸는지, 언제까지 뭘 해야 하는지"** 를 확인하는 것이다. 4종 분석 커맨드가 이를 네 질문으로 나눈다.

| # | 기능 | 사용자 질문 | 커맨드 | 계층 | 프로필 |
|---|---|---|---|---|---|
| F1 | **쉬운 요약** | "이 법이 대체 뭔데?" | `ImpactSummary` | A | 불필요 |
| F2 | **무엇이 바뀌나** | "현행법 대비 뭐가 달라져?" | `LawDiff` | A | 불필요 |
| F3 | **내게 미치는 영향** | "그래서 나한테 무슨 상관인데?" | `PersonaImpact` | B | **필요** |
| F4 | **대응 행동** | "그럼 나 뭘 해야 해? 언제까지?" | `ActionPlan` | B | **필요** |

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

| 메서드 | 경로 | 기능 | 인증 |
|---|---|---|---|
| `GET` | `/laws` | S1·S2 법령 검색·해소 | — |
| `GET` | `/laws/{lawId}` | 한 법령의 **시행예정본 목록**(D43) | — |
| `GET` | `/laws/{lawId}/{efYd}` | F1·F2 법령 사실 + 조문 대조(Layer A) | — |
| `POST` | `/laws/{lawId}/{efYd}/analysis` | F3·F4 개인화 분석(Layer B) | **필요** |
| `GET` | `/profile` | S3 내 프로필 조회 | 필요 |
| `PUT` | `/profile` | S3 프로필 생성·수정 | 필요 |
| `DELETE` | `/profile` | S3 프로필 파기(개인정보) | 필요 |

`{efYd}` = 시행일자 `YYYY-MM-DD`. **경로에 시행일을 넣는 이유**: 같은 `lawId`에 시행예정본이 여럿일 수 있어(D43) `lawId` 만으로는 대상이 특정되지 않는다.

---

## 3. 엔드포인트 상세

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

> **클라이언트 규칙:** `RESOLVED` → 상세로 진행. `AMBIGUOUS` → 후보(특히 시행일) 선택 UI. `NOT_FOUND_YET`·`UNVERIFIED` → 분석 진입 금지, 안내 문구 그대로 노출. **미등록과 허위는 문구가 다르므로 뭉뚱그리지 말 것**(D23).

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

### 3.4 `POST /laws/{lawId}/{efYd}/analysis` — 개인화 분석 (F3·F4, Layer B)

인증 세션의 **자기신고 프로필**을 주입해 개인화한다. `commands` 로 원하는 것만 선택.

```jsonc
// POST /api/v1/laws/001809/2026-08-04/analysis   (Authorization 필요)
// 요청
{ "commands": ["PersonaImpact", "ActionPlan"] }

// 응답 200
{
  "law_ref": "LAW:001809@2026-08-04",
  "results": {
    "PersonaImpact": {                             // F3
      "impacts": [
        { "aspect": "주거", "direction": "영향 있음",
          "detail": "전세로 거주 중이라면 입주예정자 현장점검 요청권이 새로 생깁니다.",
          "citations": ["LAW:001809@2026-08-04:art:49"] }
      ],
      "uncertainties": []
    },
    "ActionPlan": {                                // F4
      "actions": [
        { "what": "사용검사 전 현장점검을 요청할 수 있는지 관리사무소에 확인",
          "deadline": "2026-08-04 시행 이후 사용검사 신청 건부터",
          "basis": ["LAW:001809@2026-08-04:addenda:3"] }
      ]
    }
  },
  "disclaimer": "법률 자문이 아닌 참고용 정보입니다."
}
```

**동작 규칙:**
- 각 커맨드 결과는 [[component-specs]] §1 `ImpactResult` 스키마를 따른다.
- **인용검증 게이트**를 통과한 결과만 나간다. 근거 부족이면 해당 커맨드는 `422`가 아니라 결과 자리에 `insufficient_grounding` 표식으로 대체(부분 성공 허용).
- 프로필 미설정 상태로 Layer B를 호출하면 `409 profile_required`.
- 캐시 키는 `userId`가 아니라 **프로필 속성 해시**(동일 속성 사용자 간 재사용 + 개인 추적 방지, D41).

### 3.5 프로필 — `GET · PUT · DELETE /profile` (S3)

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
| 401 | `unauthenticated` | 인증 필요한데 세션 없음 |
| 404 | `not_found` | `lawId`/`efYd` 조합이 저장소에 없음 |
| 409 | `profile_required` | Layer B 인데 프로필 미설정 |
| 422 | `insufficient_grounding` | 전 커맨드가 인용검증 실패(부분 성공 시엔 결과 내 표식) |
| 429 | `rate_limited` | 모델·출처 API 한도 |
| 503 | `upstream_error` | 모델 API 장애 |

```jsonc
{ "status": "profile_required",
  "message": "내 영향·대응안을 보려면 프로필을 먼저 입력해 주세요." }
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

- [ ] 인증 방식 확정(세션 쿠키 vs Bearer 토큰) — 웹 구현 단계
- [ ] `AMBIGUOUS` 시행일 복수(D43)의 **기본 선택 정책**(가장 이른 시행일 자동 vs 선택 강제)
- [ ] 부분 성공(`insufficient_grounding` 표식) 렌더링 규약 — 웹 UX
- [ ] 알림 등록(`NOT_FOUND_YET` 법령의 시행 시 통지) — post-MVP
