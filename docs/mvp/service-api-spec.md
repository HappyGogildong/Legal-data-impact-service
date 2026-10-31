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
  - "components/plan/QueryPlanner.md"
  - "backend/concurrency-and-reliability.md"
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

> F0(**`LOOKUP`**, D46)은 특정 법령을 모른 채 *코퍼스에서 찾는* 동작이다 — 나머지 넷과 달리 답변 차원이 아니라 **발견**이며, 질문(토글이 켜지면 프로필 라벨 포함)을 임베딩해 `pending` 네임스페이스를 검색한다. **후보만 반환**하고 자동 분석은 하지 않는다 — 사용자가 고르면 원래 질문 + `lawRef` 로 다시 요청한다(D70).
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
| S5 | **트렌드(자주 찾는 법령)** | 조회량 기반 랭킹을 시간창·도메인별로 표출. **집계는 개인 식별 없이 카운트만**(D41) |
| S6 | **시행 임박 알림 구독** | "이 법 시행되면"·"내 프로필 영향 법 D-N" 구독. **인앱 알림함**(연락처 미수집, D49) |

**MVP 제외**(기능은 유지, 구현 후순위): 의안(의원발의·입법예고) 분석, URL/뉴스 입력 해소, 도메인 심화 추론, 알림 등록.

---

## 2. 공개 API 개요

```
BASE  /api/v1
포맷  요청·응답 모두 application/json; charset=utf-8
언어  응답 자연어는 한국어(ko) 고정 (MVP)
인증  프로필·Layer B 분석은 로그인 세션 필요 — 소셜 OAuth2 + 서버 세션 쿠키(D60, SameSite=Lax)
      조회·요약(Layer A)은 익명 허용
```

**두 종류의 리소스로 나뉜다.** 분석(`/analyses`)이 사용자 행위의 중심이고, 법령(`/laws`)은 그것이 *참조*하는 읽기 전용 사실 데이터다. **분석은 법령의 하위 리소스가 아니다** — 사용자는 `lawId`를 모른 채 질문할 수 있고, 해소 자체가 질의의 일부이기 때문이다.

| 메서드 | 경로 | 기능 | 인증 |
|---|---|---|---|
| `POST` | `/analyses` | **자연어 질의 → 그라운딩된 분석**(F1~F4) | 조건부* |
| `POST` | `/analyses/action-revisions` | 대응 방안 상황 확인 — 상황 질문 답으로 **대응 방안만 재생성**(D71) | 필요 |
| `GET` | `/laws` | 곧 시행되는 법령 목록(시행일 범위·커서) — 홈·S06 | — |
| `GET` | `/laws/{lawId}` | 한 법령의 **시행예정본 목록**(D43) | — |
| `GET` | `/laws/{lawId}/{efYd}` | 법령 상세 — 개정 이유·시행 규칙·바뀐 조문(현행 대비)·부칙·위임 조항(S05) | — |
| `GET` | `/laws/trending` | S5 자주 찾는 법령 순위(일·주·월) | — |
| `POST` | `/laws/{lawId}/{efYd}/views` | S05 상세 조회 신호(횟수 집계, CSRF 예외) | — |
| `GET` | `/data-status` | 법령 정보 마지막 갱신 시각·수록 시행예정 법령 수(화면 S15·S05) | — |
| `PUT` | `/subscriptions/{lawId}` | 법령 구독·알림 시점 변경(D74) | 필요 |
| `DELETE` | `/subscriptions/{lawId}` | 구독 해지(멱등) | 필요 |
| `GET` | `/subscriptions` | 내 구독 목록(다음 시행일) | 필요 |
| `GET` | `/notifications` | 인앱 알림함(최근 50개·읽지 않은 수) | 필요 |
| `POST` | `/notifications/{id}/read` · `/notifications/read-all` | 읽음 처리 | 필요 |
| `GET` | `/profile` | S3 내 프로필 조회 | 필요 |
| `PUT` | `/profile` | S3 프로필 속성 수정(현재 버전 동의 필요, 없으면 409) | 필요 |
| `DELETE` | `/profile` | S3 프로필 파기(= 프로필 동의 철회) | 필요 |
| `GET` | `/consents` | 프로필·알림 이메일 동의 상태 | 필요 |
| `PUT` | `/consents/profile` | 프로필 수집 동의·재동의(만 14세 이상 확인) | 필요 |
| `PUT` · `DELETE` | `/consents/notification-email` | 알림 이메일 수신 동의 / 철회 | 필요 |
| `GET` | `/account` | 내 계정(provider·가입일) — 로그인 상태 확인 겸용 | 필요 |
| `DELETE` | `/account` | 계정 삭제(파기, 프로필 포함) | 필요 |
| `GET` | `/bookmarks` | 내 관심 법령 북마크 목록(보여 줄 버전·상태, D72) | 필요 |
| `PUT` | `/bookmarks/{lawId}` | 북마크 추가(멱등, 한도 초과 409) | 필요 |
| `DELETE` | `/bookmarks/{lawId}` | 북마크 해제(멱등) | 필요 |
| `POST` | `/history` | 이력 저장 — 서버가 보관한 분석 결과(`analysisId`)의 스냅샷(D73) | 필요 |
| `GET` | `/history` | 내 이력 목록(법령 갱신됨 표시) | 필요 |
| `GET` | `/history/{id}` | 이력 상세 — 저장 당시 결과·다시 분석할 버전 | 필요 |
| `DELETE` | `/history/{id}` | 이력 삭제(멱등) | 필요 |

\* `/analyses`는 익명도 허용하되 **F3·F4(Layer B)는 인증 세션이 있을 때만** 채워진다(없으면 F1·F2까지).

`/laws` 는 **읽기 전용 참조 데이터**다 — 적재 배치(D62)가 저장한 정본에서 **결정론으로** 만든다(LLM 없음). 익명·저지연이며 홈 목록·법령 목록(S06)·법령 상세(S05)에 쓴다. 법령명 검색·해소는 `POST /analyses` 안에서 한다(D65). `POST /analyses` 는 같은 정본을 내부에서 끌어와 답을 만든다.

`{efYd}` = 시행일자 `YYYY-MM-DD`. **경로에 시행일을 넣는 이유**: 같은 `lawId`에 시행예정본이 여럿일 수 있어(D43) `lawId` 만으로는 대상이 특정되지 않는다.

---

## 3. 엔드포인트 상세

### 3.0 `POST /analyses` — 자연어 질의 분석 (핵심)

사용자 행위의 중심. **자연어 질문**을 받아 해소→조립→추론→인용검증을 거쳐 **그라운딩된 구조화 답변**을 돌려준다. 로그인 세션이 있으면 서버가 userId 로 프로필을 읽어 Layer B 를 채운다(본문에 프로필을 보내지 않는다). 익명도 허용 — Layer B 는 PROFILE_REQUIRED.

```jsonc
// POST /api/v1/analyses   (로그인 세션 쿠키가 있으면 Layer B까지)
// 요청
{
  "query": "주택법 바뀌면 나 전세 계약 어떻게 돼?",   // ★ 필수 — 자연어
  "lawRef": { "lawId": "001809", "effectiveDate": "2026-08-04" },  // 선택: 검색에서 이미 특정했으면
  "scope": ["impact", "action"],                      // 선택: 특정 차원만. 없으면 플래너가 질문에서 판단
  "profileSearch": false                              // 선택(기본 false): 법령 발견에서 프로필 라벨을 검색어에 붙인다(D70) — Discovery 질문일 때만 쓰임
}
```

**처리 순서**(온라인 경로, [[v0.9-nl-query-planner]] §3.2 · [[QueryPlanner]]):
1. **번역** — `QueryTranslator`(Haiku)가 자연어를 `AnalysisQuery`(타입 집합·엔티티·target)로. `scope`/`lawRef`가 오면 힌트로 병합.
2. **해소/발견** — target이 `Reference`면 해소(4상태, fail-closed), `Discovery`(LOOKUP)면 `LawDiscovery` 가 `pending` ns 코퍼스를 검색해 후보를 돌려준다(D70).
3. **플래닝** — 프로필 유무로 Layer B 차원을 빼지 않는다(D69). 프로필을 못 쓰면 디스패처가 그 차원을 사유(`PROFILE_REQUIRED`·`CONSENT_OUTDATED`)와 함께 `unmet` 으로 표기한다. 단 프로필이 없고 Layer A 차원이 하나도 없으면 `SUMMARY` 를 더해 요약은 보여 준다(선계산본이 있으면 비용 0). 검증된 `AnalysisQuery` 확정.
4. **디스패치·검증** — 타입별 핸들러(SUMMARY=쉬운 요약 저장본 있으면 그것·없으면 Opus, DIFF=Opus, Layer B=개인화 게이트·하루 한도 통과 시 Opus 1회로 IMPACT·ACTION, D67·D69) → 엔진 내부 인용검증(근거 부족은 그 차원만 `INSUFFICIENT_GROUNDING`). 정본은 저장소 정확 조회(벡터 검색 아님). Discovery 는 분석 팬아웃 없이 후보만(D70).

**RESOLVED 응답** — 해소되고 분석까지 성공:
```jsonc
{
  "resolution": "RESOLVED",
  "law": {                                     // 대상 법령 헤더(정본에서 조회). 정본 미적재면 title·lawType·ministry 는 null
    "ref": "LAW:001809@2026-08-04", "lawId": "001809", "title": "주택법",
    "effectiveDate": "2026-08-04", "lawType": "법률", "ministry": "국토교통부"
  },
  "alternatives": [                            // 같은 법령의 다른 시행예정본(D43) — "다른 시행일 보기". 없으면 []
    { "lawId": "001809", "title": "주택법", "effectiveDate": "2027-02-01" }
  ],
  "answer": {                                  // 질문에 필요한 차원만 채워진다(키 순서: summary·diff·impact·action)
    "summary": { "summary": "…", "claims": [ { "statement": "…", "citations": ["LAW:001809@2026-08-04:art:18"], "confidence": 0.9 } ],
                 "uncertainties": ["…"], "disclaimer": "…" }
  },
  "unmet": {                                   // 못 채운 차원 → 사유 코드·설명. 웹은 코드로 안내를 고른다
    "impact": { "reason": "PROFILE_REQUIRED", "message": "프로필 필요 (Layer B)" }
  },                                           // 코드: LAW_NOT_LOADED · BASELINE_NOT_LOADED · PROFILE_REQUIRED · NOT_IMPLEMENTED · CONSENT_OUTDATED · DAILY_LIMIT · INSUFFICIENT_GROUNDING
  "sources": [                                 // answer 안 모든 인용키의 근거(인용 칩·근거 패널용). 찾지 못한 인용은 label=sourceId, text=null
    { "sourceId": "LAW:001809@2026-08-04:art:18", "label": "주택법 제18조(사업계획의 통합심의)",
      "text": "① …(개정)", "previousText": "① …(현행)",   // changeType·previousText 는 찾은 법령 버전이 시행예정일 때만(시행중·과거·철회 버전은 키에 날짜가 있어도 null). previousText: 현행 원문(개정·삭제·이동만, 신설·무변경은 null)
      "changeType": "개정", "effectiveDate": "2026-08-04" }
  ],
  "situation": {                              // 선택(D71) — 대응 방안(ACTION)이 채워지고 상황 질문 또는 단계 설명이 있을 때만. 없으면 생략
    "setId": "0123456789abcdef",             // 질문 묶음 식별자(질문이 있을 때만) — 질문·순서의 SHA-256 앞 16 hex. 재생성 요청이 그대로 돌려보낸다(§3.0.1)
    "questions": [ { "id": "q1", "text": "계약을 2026-11-01 이전에 맺었나요?", "basis": "LAW:001809@2026-08-04:addenda:2",
                    "answerType": "YES_NO", "options": [] } ],   // 0~3개. answerType: YES_NO | DATE | CHOICE(options 2~4개). 모든 형식에 '모름' 답 허용
    "stepNotes": [ { "index": 1, "note": "시행일 이전 계약이라 이전 기준이 적용돼요" } ]   // 대응 방안 단계(0부터)별 상황 설명 — 대개 없음
  },
  // uncertainties 는 차원별 내용이라 각 answer.<차원>(ImpactResult) 안에만 둔다(전역 중복 제거)
  "disclaimer": "법률 자문이 아닌 참고용 정보입니다."
}
```

**법령 발견 응답**(D70) — 법령명 없는 질문(Discovery)이면 분석 없이 **후보만** 돌려준다. `resolution` 은 `RESOLVED`, `law`·`answer`·`unmet`·`sources`·`disclaimer` 는 없고 `discovery` 에만 담긴다(분석이 아니라 후보 목록이라 면책 문구도 싣지 않는다):
```jsonc
{
  "resolution": "RESOLVED",
  "discovery": {
    "candidates": [                            // 최대 10건, 유사도 순. 실재하는 시행예정 법령만(없으면 [])
      { "lawId": "001809", "title": "주택법", "effectiveDate": "2026-08-04", "ministry": "국토교통부",
        "summary": "전세 계약을 …",              // 쉬운 요약 저장본(D67) 첫 문장. 없으면 개정 이유 앞부분, 둘 다 없으면 null
        "summaryReady": true,                  // false = 요약 준비 중(개정 이유 앞부분으로 대체한 경우)
        "matched": { "sourceId": "LAW:001809@2026-08-04:art:18", "label": "주택법 제18조 사업계획의 통합심의" } }  // 근거 칩 → S05 그 조문
    ],
    "profileApplied": false,                   // 프로필 라벨이 검색어에 실제로 붙었는지
    "profileBlocked": { "reason": "PROFILE_REQUIRED", "message": "프로필 필요" }  // 선택 — 토글이 켜졌는데 프로필을 못 썼을 때만(PROFILE_REQUIRED·CONSENT_OUTDATED)
  }
}
```
- 고르면 클라이언트가 `{ query: <원래 질문>, lawRef }` 로 다시 요청한다 — 분석 차원은 그 재요청에서 같은 질문으로 정해진다.
- 한도(`DAILY_LIMIT`)·조회 집계(D66)는 적용되지 않는다. Opus 호출도 없다(검색어 임베딩 1회뿐).

**미해소 응답** — 해소가 `RESOLVED`가 아니면 **분석하지 않고** 4상태를 그대로 답으로 돌려준다(아래 예시의 `resolution`/`candidates`/`message`). 대화적으로 "그 법을 못 찾았어요"로 노출된다.

```jsonc
{ "resolution": "AMBIGUOUS", "message": "여러 후보가 있어요.",
  "candidates": [ { "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률", "effectiveDate": "2026-10-01" } ] }
```
- 응답 키는 **camelCase**. 값이 없는 최상위 필드는 생략한다(RESOLVED 응답엔 `message`·`candidates` 없음, 미해소 응답엔 `law`·`answer` 등 없음).
- 계약의 기계 판독 원본은 OpenAPI(`/v3/api-docs`, 레포 `api/openapi.json` 스냅샷) — 웹 TS 타입은 여기서 생성한다(D64).

**동작 규칙:**
- 각 차원 결과는 [[component-specs]] §1 `ImpactResult` 스키마를 따른다.
- **인용검증 게이트**를 통과한 차원만 나간다. 근거 부족 차원은 `422`가 아니라 `unmet`에 사유(사유 코드 `INSUFFICIENT_GROUNDING`, D69)로 표기(부분 성공 허용).
- 익명 요청에 F3·F4가 필요하면 그 차원은 생략하고 `unmet: {"impact": {"reason":"PROFILE_REQUIRED", "message":"…"}}`(차원→사유 코드·설명) — 전체를 막지 않는다.
- `CONSENT_OUTDATED` — 개인화 차원(Layer B)인데 프로필 동의가 옛 처리방침 버전이다 → 재동의 안내 (D69)
- `DAILY_LIMIT` — 오늘 개인화 분석 한도(`lia.personalization.daily-limit`, 기본 20)를 다 썼다 (D69)
- `INSUFFICIENT_GROUNDING` — 재생성 후에도 인용 검증을 통과하지 못했다(환각 노출 금지). Layer A·B 공통 (D69)
- `SITUATION_EXPIRED` — `POST /analyses/action-revisions` 전용(§3.0.1): 세션에 그 법령의 상황 질문이 없거나(세션 만료·새 로그인·다른 법령), 요청 `setId` 가 세션의 질문 묶음과 다르다(다른 탭·후속 질문이 같은 법령 질문을 덮어씀) (D71)
- `analysisId` — 로그인 사용자의 법령이 정해진 분석에만. 이력 저장(§3.9)·대응 방안 재생성(§3.0.1)이 돌려보낸다. 서버가 최근 20개를 세션에 보관(D73).
- `situation`(D71) — 질문은 서버가 **HTTP 세션(메모리)에 법령 버전별로 보관**(최근 20개)한다. 클라이언트는 질문 문장을 되돌려 보내지 않고 `setId`(화면이 받은 질문 묶음 식별자)와 `questionId`+답만 `POST /analyses/action-revisions` 로 보낸다. 근거가 주입 집합 밖이거나 형식이 깨진 질문은 서버가 버린다(최대 3개, 질문 문장·단계 설명은 최대 200자 한 줄(줄바꿈 없음), 선택지 최대 40자).
- 캐시 키는 `userId`가 아니라 **프로필 속성 해시 + 법령 참조(law.ref) + 차원**(D41). (Layer B 개인화 답은 D69 로 저장하지 않음 — 답변 캐시는 후속)
- **멀티턴(대화 상태 지속)은 MVP 밖** — 단발 질의만. 멀티턴은 D37 재검토 트리거라 별도 결정 후 도입.

> **멱등성 주의.** `POST`지만 같은 `(query, lawRef, profileHash)`는 캐시로 같은 결과를 준다(Layer B 개인화 답은 D69 로 저장하지 않으므로 답변 캐시 도입 뒤의 동작 — 현재는 매번 생성). 분석 이력 저장은 `POST /analyses` 가 아니라 별도 `/history`(§3.9, D73)가 맡는다 — 분석 응답의 `analysisId` 로 서버가 세션에 보관한 결과를 저장한다.

### 3.0.1 `POST /analyses/action-revisions` — 대응 방안 상황 확인 (D71)

`POST /analyses` 응답의 `situation.questions` 에 사용자가 답하면 **대응 방안만 다시 생성**한다(영향 등 다른 차원은 그대로). 로그인 세션 필수 — 서버가 userId 로 프로필을 읽고, 세션에 보관된 질문과 답을 맞춰 본다. 기존 `anyRequest().authenticated()` 규칙에 걸려 **익명 요청은 401** 이고 유스케이스까지 오지 않는다. **CSRF 보호**가 적용된다(`/analyses` 와 달리 예외로 두지 않는다 — 클라이언트는 CSRF 토큰 헤더를 함께 보내며, 없으면 403). 로그인했지만 개인화할 수 없는 사용자(프로필 없음·동의 구버전)는 오류가 아니라 200 `{ unmet }` 으로 게이트의 사유를 받는다.

```jsonc
// POST /api/v1/analyses/action-revisions
// 요청
{
  "lawRef": { "lawId": "001809", "effectiveDate": "2026-08-04" },   // 질문을 받은 그 법령 버전
  "setId": "0123456789abcdef",                                      // 필수 — 응답 situation.setId 그대로. 없거나 빈 값이면 400
  "answers": [ { "questionId": "q1", "answer": "예" },
               { "questionId": "q2", "answer": "2027-03-01" } ]      // 답: YES_NO 예·아니오·모름 / DATE yyyy-MM-dd·모름 / CHOICE 선택지 하나·모름
}
// 응답 200 — 성공
{
  "action": { "actions": [ { "what": "…", "deadline": "…", "basis": ["LAW:001809@2026-08-04:art:18"] } ], "uncertainties": ["…"], "disclaimer": "…" },
  "stepNotes": [ { "index": 0, "note": "시행일 이전 계약이라 이전 기준" } ],   // 상황이 영향을 준 단계 — 웹이 "내 상황 반영"으로 강조
  "sources": [ /* action 안 인용키의 근거(CitedSource) */ ]
}
// 응답 200 — 못 함
{ "unmet": { "reason": "SITUATION_EXPIRED", "message": "…" } }
```

- `analysisId`(선택) — 분석 응답의 값. 재생성이 성공하고 같은 법령이며 `setId` 가 그 분석의 질문 묶음과 같으면 서버가 그 사용자의 결과에 재생성본·답을 붙여 이력 저장에 쓴다(D73).

**처리 순서**(앞에서 걸리면 뒤는 실행하지 않는다 — [[ActionRevisionUseCase]]):
1. 개인화 게이트 — 로그인 사용자의 프로필 없음·동의 구버전이면 `unmet` `PROFILE_REQUIRED`·`CONSENT_OUTDATED`(익명은 이미 401 로 끊겼다).
2. 세션의 그 법령 질문 조회 — 없거나 그 목록의 `setId` 가 요청 `setId` 와 다르면(답 검증 전에 끊는다 — 한도 차감·추론 없음) `unmet` `SITUATION_EXPIRED`("상황 질문이 만료됐어요" — 웹은 "다시 받기"로 같은 법령 Layer B 를 다시 요청, 한도 1회).
3. 답 검증 — 모르는 `questionId`·중복·형식 위반·빈 목록·전부 `모름` → **400**(정상 사용으론 불가능하므로 위조로 본다). `모름` 답은 모델에 보내지 않는다.
4. 정본·기준선 — 없으면 `LAW_NOT_LOADED`·`BASELINE_NOT_LOADED`.
5. 하루 한도 — 첫 Layer B 와 **같은 한도**를 **호출 직전 1회 차감**, 환불 없음. 초과는 `DAILY_LIMIT`([[cost-policy]]).
6. 엔진(ACTION, Opus 1회·재생성 ≤2) — 재생성 후에도 근거 부족이면 `INSUFFICIENT_GROUNDING`(웹은 기존 대응 방안을 유지).

- `unmet.reason` 은 `/analyses` 의 코드에 **`SITUATION_EXPIRED`** 가 더해진 집합이다. 재생성 응답의 `unmet` 은 차원 맵이 아니라 단일 `{ reason, message }`.
- 프라이버시(D41·D69·D71): 세션엔 질문만 있고 답은 없다 — 답은 요청 처리 중에만 쓰고 버린다. DB 저장 없음. 단 요청에 `analysisId` 가 있고 같은 법령이며 재생성이 성공하면, 서버가 이력 저장용으로 재생성본과 실제로 쓴 답을 그 분석 항목에 붙여 **HTTP 세션(메모리)** 에 둔다(D73) — 로그아웃·세션 만료 때 사라지고, DB 에는 사용자가 "이력에 저장"을 누를 때만 들어간다. 프롬프트엔 `질문: 답` 줄과 프로필 라벨만 가고 userId 는 없다. 답 내용은 로그·트레이스에 남기지 않는다.
- 자유 입력 사실 추출("내 상황 설명하기")은 다음 단계 — 이번엔 구조화 질문만.

### 3.1 `GET /laws` — 곧 시행되는 법령 목록 (홈·S06)

시행예정본을 **시행일이 가까운 순**으로 준다. 해소(4상태)는 이 엔드포인트가 하지 않는다 — 자연어·법령명 해소는 `POST /analyses` 안에서 한다(D65).

```
GET /api/v1/laws?from=2026-10-25&to=2026-11-24&limit=20
GET /api/v1/laws?from=2026-10-25&to=2026-11-24&limit=20&cursor=MjAyNi0xMS0wMXwwMDE4MDk
```
```jsonc
{
  "items": [
    { "lawId": "001809", "title": "주택법", "effectiveDate": "2026-11-01",
      "lawType": "법률", "ministry": "국토교통부", "amendKind": "일부개정" }
  ],
  "nextCursor": "MjAyNi0xMS0wMXwwMDE4MDk"   // 다음 쪽이 없으면 null
}
```

- 조건: 상태 `시행예정` · 시행일 ≥ **오늘(서울)** · `from`(선택) 이후 · `to`(선택) 이전(포함). `from`이 오늘보다 이르면 오늘부터.
- 정렬 `effectiveDate`, `lawId` 오름차순. **키셋 커서** — 마지막 행의 `(effectiveDate, lawId)`를 불투명 문자열로 준다. 클라이언트는 해석하지 않고 그대로 돌려보낸다.
- `limit` 기본 20, 1~50.
- `400`: 날짜 형식 오류(`YYYY-MM-DD` 아님·달력에 없음), `from > to`, 잘못된 커서, `limit` 범위 밖. 본문 `{ "error": "bad_request", "message": "…" }`.
- 분야 필터는 아직 없다(분류 데이터 없음). 법령 발견 #19(D70)도 분야 필터 없이 들어갔고, 분야 필터는 분류 데이터가 생길 때 정한다.

### 3.2 `GET /laws/{lawId}` — 시행예정본 목록 (D43)

한 법령에 걸린 시행 대기 개정을 모두 준다. 클라이언트가 시행일을 고르게 하기 위한 것.

```jsonc
// GET /api/v1/laws/010513
{ "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률",
  "pending": [                                   // 시행예정 · 오늘(서울) 이후 · 시행일 오름차순
    { "effectiveDate": "2026-11-13", "promulgateNo": "21500", "amendKind": "일부개정" },
    { "effectiveDate": "2027-02-04", "promulgateNo": "21611", "amendKind": "일부개정" }
  ],
  "current": { "effectiveDate": "2026-01-02", "promulgateNo": "20900" }  // 시행중본(diff 기준선), 없으면 null
}
```

- `404`: 그 `lawId`의 버전이 하나도 없음. 본문 `{ "error": "not_found", "message": "…" }`.

### 3.3 `GET /laws/{lawId}/{efYd}` — 법령 상세 (S05)

정본에서 결정론으로 만든 사실만 준다(LLM 없음). **상태와 무관하게** 연다 — 배치가 시행·철회된 행도 보존하므로(D62) 예전 인용이 계속 열린다.

```jsonc
// GET /api/v1/laws/001809/2026-11-01
{
  "lawId": "001809", "title": "주택법", "lawType": "법률", "ministry": "국토교통부",
  "promulgateDate": "2026-05-01", "promulgateNo": "21323",
  "effectiveDate": "2026-11-01", "status": "시행예정",      // 시행예정 · 시행중 · 과거 · 철회
  "amendKind": "일부개정", "sourceUrl": "https://www.law.go.kr/…",
  "summary": { "text": "주택 사업계획 심의를 한 번에 묶어 절차가 짧아져요.",
               "claims": [ { "statement": "교육환경·재해영향 평가도 통합심의 대상이 돼요.",
                             "citations": ["LAW:001809@2026-11-01:art:18"] } ],
               "uncertainties": [] },                // 미리 만든 쉬운 요약(D67) — 없으면 생략
  "amendReason": "주택 사업계획 심의 절차를 간소화하고 …",   // 제개정이유 원문
  "enforcementType": "유예",                                 // 즉시 · 유예 · 단계적
  "effectiveRule": "이 법은 공포 후 6개월이 경과한 날부터 시행한다.",
  "effectiveClause": { "sourceId": "LAW:001809@2026-11-01:addenda:제1조",
                       "label": "부칙 제1조(시행일)", "text": "이 법은 공포 후 6개월이 …" },
  "changedArticles": [                                       // 조문변경여부='Y' 조문만
    { "sourceId": "LAW:001809@2026-11-01:art:18", "no": "18",
      "label": "제18조(사업계획의 통합심의)", "changeType": "개정",
      "text": "① … 도시계획·건축·환경·교통·재해 …",
      "previousText": "① … 도시계획·건축·교통 …" }        // 현행 원문 — 아래 규칙
  ],
  "addenda": [ { "sourceId": "LAW:001809@2026-11-01:addenda:제1조", "label": "부칙 제1조(시행일)", "text": "…" } ],
  "delegationClauses": [ "대통령령으로 정하는 사항 …" ],
  "baselineEffectiveDate": "2020-01-01"                      // 대비에 쓴 시행중본, 없으면 생략
}
```

- `changedArticles[].previousText` — **시행예정본**의 개정·삭제·이동 조문일 때만 기준선(시행중본)의 현행 원문(이동은 옛 번호로 찾는다). 신설·무변경·시행된 버전은 없다. 근거 패널(`POST /analyses`의 `sources`)과 같은 규칙이다.
- `sourceId`는 인용키 형식 그대로라 결과 화면의 인용 칩이 상세의 해당 조문(`#art-18`)으로 이어진다.
- `summary` — **적재 배치가 미리 만든 쉬운 요약**(D67): `{ "text": "…", "claims": [{ "statement": "…", "citations": ["LAW:…:art:18"] }], "uncertainties": ["…"] }`. 분석과 같은 엔진·인용 검증을 통과한 것만 저장되고, 정본 revision·프롬프트 버전이 지금과 같을 때만, **시행예정본일 때만** 싣는다. 없으면 필드를 생략 — 화면은 "쉬운 요약 보기"가 이 법령 `lawRef`로 `POST /analyses`를 부르는 대체로 간다. 개인화 분석은 `POST /analyses`로만 한다.
- `404`: 그 버전이 없음. `400`: `efYd` 형식 오류.

### 3.4 프로필·동의·계정 — `/profile` · `/consents` · `/account` (S3)

**동의가 먼저, 프로필 수정은 그 다음**(D61). 동의는 처리방침 버전당 한 번 하는 행위라 프로필 수정과 분리한다.

```jsonc
// 1) PUT /api/v1/consents/profile   → 프로필 수집 동의(행이 없으면 빈 프로필 생성). 재동의도 같은 호출
{ "over14": true }
// → 200 { "policyVersion": "draft-2026-09", "consentedAt": "…", "upToDate": true }

// 2) PUT /api/v1/profile   (전부 선택 입력, 값은 라벨 문자열, 전체 교체)
{ "purposes": ["생활·주거", "관심사 모니터링"],
  "age": 29, "occupation": "사무",
  "employmentType": "임금근로", "householdType": "1인",
  "housingType": "전세", "regionSido": "서울특별시" }
// → 200 위 속성 + updatedAt   /  409 { "error": "consent_required" } (동의 없음·옛 버전)

// GET /api/v1/profile → 위 속성 + updatedAt (userId 는 응답에 포함하지 않는다) / 404
// DELETE /api/v1/profile → 204, 파기 = 프로필 동의 철회

// GET /api/v1/consents → { "profile": {policyVersion, consentedAt, upToDate} | null,
//                          "notificationEmail": {email, consentedAt} | null }
// PUT /api/v1/consents/notification-email → 200 { email, consentedAt }  (세션의 IdP 이메일 저장, 없으면 400)
// DELETE /api/v1/consents/notification-email → 204

// GET /api/v1/account → { provider, createdAt }   (userId·email 없음)
// DELETE /api/v1/account → 204, 계정 파기(프로필 cascade) + 세션 무효화
```

- 스키마는 [[component-specs]] §2 `UserProfile`. **`age` 는 정수(14~120)**(구간화 안 함), 모든 필드가 닫힌 값(자유 문자열 없음), 성명·생년월일·주민번호·연락처·상세주소·소득 **미수집**.
- **재동의 흐름**: 서버 처리방침 버전이 올라가면 `upToDate: false` → `PUT /profile` 409 → UI가 처리방침 표시 → `PUT /consents/profile` → 재시도. 클라이언트는 버전을 보내지 않는다(서버가 동의 시점 버전을 기록).
- **이메일은 로그인만으로 저장하지 않는다** — 알림 수신 동의 시에만 세션 principal의 IdP 이메일을 저장(D60·D61).
- ⚠️ 직접식별정보를 안 받아도 조합 재식별 소지가 있어 **개인정보처리방침·수집 동의·파기 절차 필요**(D41). 실제 처리방침 문서는 법무 산출물로 별도 준비. 컴포넌트: [[ProfileApi]] · [[ConsentApi]] · [[AccountApi]].

---

### 3.5 자주 찾는 법령 — `GET /laws/trending` · `POST /laws/{lawId}/{efYd}/views` (S5)

조회 횟수 기준 순위. **읽기 전용·익명**이고 화면은 몇 분 캐시한다(순위는 초 단위로 안 변함).

```
GET /api/v1/laws/trending?window=week&limit=10
```
```jsonc
{
  "window": "week",                       // day(오늘) | week(오늘 포함 7일) | month(30일)
  "items": [
    { "rank": 1, "lawId": "001809", "title": "주택법",
      "effectiveDate": "2026-11-01", "views": 1240, "previousRank": 3 },   // 직전 같은 길이 구간 순위
    { "rank": 2, "lawId": "010513", "title": "자본시장과 금융투자업에 관한 법률",
      "effectiveDate": "2026-11-13", "views": 980, "previousRank": null }  // null = 직전 구간 순위에 없음(신규)
  ]
}
```

- 기본 `window=week`·`limit=10`, `limit` 1~50. 그 밖의 값은 `400` `{ "error": "bad_request" }`.
- 순위 단위는 **`(lawId, effectiveDate)`**(D43). **시행예정·오늘(서울) 이후** 버전만 오른다. 동점은 `lawId`·`effectiveDate` 오름차순.
- 분야(`domain`) 필터는 분류 데이터가 생길 때까지 없다(D65).

**조회 1회**(D66) = ① S05 상세 방문 — 브라우저가 `POST /api/v1/laws/{lawId}/{efYd}/views`(본문 없음 → `204`, 없는 버전 `404`, 날짜 형식 오류 `400`)를 **탭당 법령별 1회** 보낸다(서버 렌더링 캐시와 무관하게 실제 방문을 센다) ② 분석 — `POST /analyses`가 그 법령으로 해소되고 정본이 있어 답을 냈을 때 서버가 센다(요청마다 — 후속 질문·시행일 전환·새로고침도 각 1회, 중복 제거 없음).

> **구현 노트 — 집계.**
> - **개인정보(D41):** 서울 날짜별 **횟수만** 남긴다(`law_views_daily`). *누가* 봤는지(userId·IP·세션)는 저장하지 않는다.
> - **증가:** `INSERT … ON CONFLICT DO UPDATE SET views = views + 1` 한 문장. 인기 법령 한 행에 잠금이 몰릴 수 있으나 측정 전엔 기법을 더하지 않는다(D48).
> - **CSRF 예외:** 조회 신호는 세션을 읽지 않고 개인 데이터가 없어 CSRF 공격 대상이 아니다(익명은 XSRF 쿠키도 없다). 같은 사람의 반복 호출은 탭 단위 중복 제거만 — 요청 빈도 제한은 운영 과제.
> - 분석 경로의 기록 실패는 분석 응답을 막지 않는다(로그만).

---

### 3.6 법령 구독·알림함 — `/subscriptions` · `/notifications` (S11·S12, D74)

로그인 필요(익명 401). 쓰기는 CSRF 필수(403). 구독 단위는 **법령(lawId)** — 그 법령의 모든 시행예정본에 알림. 알림은 매일 배치 `notify` 가 만든다(LLM 없음).

| 요청 | 응답 |
|---|---|
| `PUT /subscriptions/{lawId}` `{ "notifyBefore": 7 }` | 204(없으면 만들고 있으면 시점 변경) · 없는 법령 404 `not_found` · `notifyBefore` 가 0·1·7·30 이 아니거나 없음 400 · 한도 409 `subscription_limit` |
| `DELETE /subscriptions/{lawId}` | 204(멱등) |
| `GET /subscriptions` | 200 `{ "items": [SubscriptionItem] }` — 다음 시행일 가까운 순, 없는 것은 뒤 |
| `GET /notifications` | 200 `{ "items": [NotificationItem], "unreadCount": 3 }` — 최근 50개 |
| `POST /notifications/{id}/read` · `POST /notifications/read-all` | 204(남의 알림은 건드리지 않음) |

```json
// SubscriptionItem — nextEffectiveDate: 가장 이른 시행예정본(없으면 생략)
{ "lawId": "001809", "title": "주택법", "notifyBefore": 7, "createdAt": "2026-10-31T00:00:00Z", "nextEffectiveDate": "2026-11-09" }
// NotificationItem
{ "id": 12, "lawId": "001809", "title": "주택법", "effectiveDate": "2026-11-09", "notifyBefore": 7, "createdAt": "2026-11-02T19:00:00Z", "read": false }
```

- **알림 생성**: 구독 법령의 시행예정 버전 중 `시행일 − notifyBefore ≤ 오늘(서울) ≤ 시행일` → `INSERT … ON CONFLICT DO NOTHING`. 배치가 하루 빠져도 다음 실행에서 한 번.
- **정확히 한 번**: `UNIQUE(user_id, law_id, effective_date, notify_before)` — 이 테이블이 Outbox 역할([[concurrency-and-reliability]] §3).
- **채널은 인앱 알림함만**(D49). 이메일(동의한 경우)은 ⑦c, 내 프로필 관련 구독(`type: impact`)은 ⑦b.
- 한도 `lia.subscriptions.max-per-user`(기본 100, `LIA_SUBSCRIPTIONS_MAX_PER_USER`), 메시지 "구독은 최대 {N}개까지 할 수 있어요."

---

### 3.7 `GET /data-status` — 데이터 현황

사용자에게 **데이터가 얼마나 최신인지** 보여 준다(화면 S15 안내·S05 하단). 익명·읽기 전용. 컴포넌트: [[DataStatusApi]].

```jsonc
// GET /api/v1/data-status
{ "lawsUpdatedAt": "2026-10-23T04:12:31+09:00",   // 목록 전체를 받아 갱신을 마친 마지막 배치 시각. 한 번도 없으면 null
  "pendingLawCount": 912 }                         // 수록된 시행예정 법령 버전 수
```

- **시각·개수만** 공개한다 — 격리 작업 수·오류 같은 운영 세부는 Grafana([[cloud-infra]] §4).

---

### 3.8 관심 법령 북마크 — `/bookmarks` (S14, D72)

로그인 필요(익명 401). 쓰기는 CSRF 필수(403). 북마크 단위는 **법령(lawId)** — 버전이 아니다.

| 요청 | 응답 |
|---|---|
| `PUT /bookmarks/{lawId}` | 204(이미 있어도 204) · 저장된 버전이 하나도 없는 법령 404 `not_found` · 한도 초과 409 `bookmark_limit` |
| `DELETE /bookmarks/{lawId}` | 204(없어도 204) |
| `GET /bookmarks` | 200 `{ "items": [BookmarkItem] }` — 추가한 시각 최근 순 |

```json
{ "lawId": "001809", "title": "주택법", "status": "시행예정",
  "effectiveDate": "2026-11-08", "bookmarkedAt": "2026-10-30T00:12:00Z" }
```

- `status`·`effectiveDate` 는 **보여 줄 버전**의 것: 시행예정본이 있으면 가장 이른 시행예정본(`시행예정`), 없으면 시행일이 가장 늦은 행(`시행중`·`과거` → `시행됨`, `철회` → `철회`). S05 경로는 `/laws/{lawId}/{effectiveDate}`.
- 한도는 `lia.bookmarks.max-per-user`(기본 100, 환경 변수 `LIA_BOOKMARKS_MAX_PER_USER`). 초과 시 409 `bookmark_limit`, 메시지는 "북마크는 최대 {N}개까지 저장할 수 있어요.". 알림은 북마크와 별개(구독 §3.6).

### 3.9 질의·분석 이력 — `/history` (S13, D73)

로그인 필요(익명 401). 쓰기는 CSRF 필수(403). **명시적 저장만** — 서버가 세션에 보관한 분석 결과로 스냅샷을 만든다.

| 요청 | 응답 |
|---|---|
| `POST /history` `{ "analysisId": "…" }` | 201 `{ "id": 12 }`(같은 analysisId 를 이미 저장했으면 200 같은 id — 스냅샷을 지금 결과로 바꾼다) · `analysisId` 없음·빈 값 400 · 세션에 결과 없음 409 `analysis_expired`("저장할 결과가 만료됐어요. 다시 분석한 뒤 저장해 주세요.") · 한도 초과 409 `history_limit` |
| `GET /history` | 200 `{ "items": [HistoryItem] }` — 저장 최근 순 |
| `GET /history/{id}` | 200 `HistoryDetail` · 없거나 남의 기록 404 `not_found` |
| `DELETE /history/{id}` | 204(멱등, 남의 기록은 지우지 않음) |

```json
// HistoryItem
{ "id": 12, "query": "주택법 바뀌면 전세 계약 어떻게 돼?", "lawId": "001809", "lawTitle": "주택법",
  "effectiveDate": "2026-11-08", "savedAt": "2026-10-30T00:12:00Z", "lawUpdated": false }
// HistoryDetail = HistoryItem + body(분석 응답 §3.0) + revision(재생성 응답 §3.0.1, 있으면) + answers + latestEffectiveDate(있으면)
```

- `lawUpdated` — 저장한 버전의 지금 revision·상태가 저장 당시와 다르거나 그 버전이 없어짐.
- `latestEffectiveDate` — 그 법령의 가장 이른 시행예정본(다시 분석할 버전). 없으면 생략.
- `unreadable` — 저장한 스냅샷을 읽지 못했을 때만 `true`(그 밖엔 키 생략). 이때 `body`·`revision` 없이 메타데이터만, `answers` 는 빈 목록. 스냅샷은 `version`(현재 1)을 갖고 알 수 없는 필드·모르는 enum 은 무시하며 읽는다.
- 한도 `lia.history.max-per-user`(기본 100, `LIA_HISTORY_MAX_PER_USER`), 메시지 "이력은 최대 {N}개까지 저장할 수 있어요."

---

## 4. 공통 규약

### 4.1 오류 (시스템 오류만 4xx/5xx)

해소 4상태는 **정상 결과(200)** 다. 아래는 시스템 오류.

| 코드 | status | 의미 |
|---|---|---|
| 400 | `bad_request` | 스키마 위반·필수 파라미터 누락 · 상황 재생성의 위조·형식 위반 답(D71) |
| 401 | `unauthenticated` | 인증 전용 엔드포인트(`/profile`·`/consents`·`/account`·`/bookmarks`·`/history`·`/subscriptions`·`/notifications`·`/analyses/action-revisions`)에 세션 없이 접근 |
| 404 | `not_found` | `lawId`/`efYd` 조합이 저장소에 없음 · 프로필 미생성 · 북마크할 법령이 저장소에 없음(`PUT /bookmarks/{lawId}`) · 이력이 없거나 남의 기록(`GET /history/{id}`) · 구독할 법령이 저장소에 없음(`PUT /subscriptions/{lawId}`) |
| 409 | `consent_required` · `bookmark_limit` · `analysis_expired` · `history_limit` · `subscription_limit` | 현재 처리방침 버전의 프로필 동의 없이 `PUT /profile` (D61) · 사용자당 북마크 한도 초과 `PUT /bookmarks/{lawId}` (D72) · 저장할 분석 결과가 세션에 없음 `POST /history` (D73) · 이력 한도 초과 `POST /history` (D73) · 구독 한도 초과 `PUT /subscriptions/{lawId}` (D74) |
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
- 관심 법령 북마크는 법령 ID·추가 시각만 저장하고 계정 삭제 시 함께 지운다(D72). 처리방침 저장 항목(#35)에 추가한다.
- 이력은 사용자가 저장을 누를 때만 질문·결과·상황 답 스냅샷을 저장하고, 언제든 지울 수 있으며 계정 삭제 시 함께 지운다(D73). 처리방침 저장 항목(#35)에 추가한다.
- 구독한 법령과 알림 기록을 저장하고, 해지·계정 삭제 시 지운다(D74). 처리방침 저장 항목(#35)에 추가한다.

---

## 5. Open

- [x] **Query Planner 설계 확정(D46)** — 자연어 → `AnalysisQuery`(타입 DTO) → dispatch. 5 QueryType(LOOKUP 포함), Reference/Discovery target. 상세 [[QueryPlanner]]
- [x] LOOKUP 팬아웃 정책 — 후보만 반환(D70). 고르면 원래 질문 + `lawRef` 로 재요청
- [x] 인증 방식 확정 — 소셜 OAuth2 + 서버 세션 쿠키(D60)
- [ ] `AMBIGUOUS` 시행일 복수(D43)의 **기본 선택 정책**(가장 이른 시행일 자동 vs 선택 강제)
- [ ] 부분 성공(`unmet` 차원) 렌더링 규약 — 웹 UX
- [ ] 멀티턴 대화(상태 지속) — **D37 재검토 트리거**. 단발 질의로 MVP 한정
- [ ] 알림 등록(`NOT_FOUND_YET` 법령의 시행 시 통지) — post-MVP
