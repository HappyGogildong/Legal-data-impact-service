---
title: 컴포넌트 역할·입출력 & MVP 범위
status: Draft
version: 0.1
date: 2026-06-25
tags: [mvp, components, scope, design]
related:
  - "architecture/v0.3-no-video-internal-mcp.md"
  - "prompts/analysis-prompt-spec.md"
  - "adr/ADR-001-knowledge-store-sizing.md"
---

# 컴포넌트 역할·입출력 & MVP 범위 (v0.1 Draft)

**관련:** [[v0.3-no-video-internal-mcp|아키텍처 v0.3]] · [[analysis-prompt-spec|프롬프트 정의서]] · [[ADR-001-knowledge-store-sizing|ADR-001]]

## 0. 목적

각 컴포넌트의 **역할·입출력 계약**을 고정하고, **MVP 범위(IN/OUT)** 를 확정한다. 결정 사항:
- **입력:** MVP는 *수집 법안 검색/선택*으로 한정. 단, **URL/뉴스 커넥터를 옆에 붙이면 동작**함을 구조(확장점)로 시연(§3).
- **분석:** 시민 코어 4종 — `ImpactSummary`, `LawDiff`, `PersonaImpact`, `ActionPlan`(D25).
- **모델:** 외부 foundation API(강모델). 자체/경량 학습 없음. RAG/RDB는 컨텍스트 공급.
- **페르소나:** 회원가입 시 **자기신고 프로필**(목적·연령대·직업·가구·주거·시도) → 런타임 `userId` **lookup** 주입(D41). 성명 등 직접식별정보 미수집.

---

## 1. 컴포넌트 카탈로그 (역할 · 입력 · 출력)

### 런타임 경로

> **런타임 변경(D35):** 파이프라인 구현 런타임은 Python → **Spring(Boot 4.0+Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]]). 컴포넌트 경계·I/O 계약은 불변.
> 컴포넌트명 링크 = 전용 설계 문서(입출력·파라미터·동작·**구조 결정 의도**). 수집·해석 파이프라인(①)부터 작성 중이며, 미작성분은 단계 개발에 맞춰 추가한다.

| # | 컴포넌트 | 런타임 | 역할 | 입력 | 출력 |
|---|---|---|---|---|---|
| 1 | **[[SourceConnector]]** | Spring | 출처별 fetch·인증/페이징 흡수 — 법안(열린국회·법제처) + 현행법(국가법령정보) | 출처 쿼리 | `RawBill[]`(법안) / `RawLaw[]`(현행법 기준선) |
| 2 | **[[SourceAnalyzer]]** | Spring | 사용자 입력 → 법안 ref 해소(정확매칭 + 법안 의미검색) | 의안번호/법안명/**모호 plain text** · URL(확장) | 해소 4상태(resolved/후보…) |
| 3 | **[[Normalizer]]** | Spring | RawBill → 표준 도메인 모델 | `RawBill` | `Bill` + `Article[]` |
| 4 | **[[RAGIndexer|RAG Indexer]]** | Spring | 임베딩 적재 — 분석용(현행법·선례) + **탐색용(법안 요약·BillFacts)** | 현행법/선례, 법안 요약·BillFacts | Vector Index 엔트리(2 네임스페이스) |
| ✶ | **[[Embedder]]** (공유) | Spring | 텍스트→벡터(외부 API), 적재·검색 공유 — 벤치 대상 | `texts`, `mode(query/passage)` | 벡터(dim 1536) |
| 5 | **Bill Store (RDB)** | — | 법안 정본·결과 캐시 | Bill/Article/ImpactResult | 조회 결과 |
| 6 | **Vector Index** | — | 의미검색 — 분석용(현행법·선례) + 탐색용(법안) | 쿼리 임베딩 | 관련 조문/법안 후보 |
| 7 | **User Profile Store** | — | 자기신고 프로필 보관(D41) | `userId` | `UserProfile` 속성 |
| 8 | **AnalysisPipeline (Orchestrator)** | Spring | 게이트→컨텍스트 조립→엔진 호출→검증→캐시 | `billRef + command + userId` | 검증된 `ImpactResult(JSON)` |
| 9 | **Command Registry** | Spring | `AnalysisCommand` 자동 발견 | `@Component` | 실행 가능 커맨드 집합 |
| 10 | **AnalysisCommand** (×4) | Spring | 한 use-case의 task·requirements 정의 | `CommandContext` | 커맨드 결과 |
| 11 | **[[AnalysisEngine|Analysis Engine]]** | Spring | RAG 검색+프롬프트 빌드+**foundation API 호출** | `{Bill, personaProfile, command, options}` | 구조화 JSON (프롬프트 정의서 §4) |
| 12 | **Verification Gate** | Spring/Py | 스키마·인용 존재성 검사, 실패 시 재생성/폴백 | 엔진 JSON + 주입 source_id 집합 | 통과 JSON 또는 "근거 부족" |
| 13 | **Web Frontend** | TS | 검색·선택·결과 표시 | 사용자 액션 | Spring REST 호출/렌더 |

### 비런타임(오프라인/CI)

| # | 컴포넌트 | 역할 | 입력 | 출력 |
|---|---|---|---|---|
| 15 | **Evaluation Harness** *(post-MVP 이연, D36)* | 합성 페르소나 에이전트로 E2E 구동·회귀(§5) | 세그먼트 패널 + 법안셋 + `prompt_version` | smoke 결과 + UX 비평 + 커버리지 리포트 |

> **계층 매핑(프롬프트 정의서):** Layer A(법안 사실)=`ImpactSummary`·`LawDiff`의 사실부, Layer B(해석)=`PersonaImpact`/`ActionPlan`. 컨텍스트 조립은 #8이 정의서 §1 입력계약대로 수행.
> **#4 RAG Indexer·#6 Vector Index는 MVP에서 활성**(국가법령정보 현행법 임베딩 → LawDiff·영향분석 컨텍스트 검색).

---

## 2. MVP 데이터·제어 흐름 (happy path)

```
[사전 수집·배치]
   SourceConnector(열린국회·법제처) → 법안 → Normalizer → Bill Store
   SourceConnector(국가법령정보) → 현행법 → RAG Indexer → Vector Index  (diff 기준선)

[Web] 법안 검색(이름/의안번호)  ← 열린국회+법제처 수집분
   → [Spring REST] → Bill Store 조회
[Web] 법안 선택  (프로필은 가입 시 저장된 것 사용)
   → [AnalysisPipeline] 해소·requirements 게이트
       · Bill(RDB) + 신구조문대비표 + 현행법 기준선(Vector Index) + UserProfile 조립
   → [Analysis Engine(Spring AI)] 프롬프트 빌드 + foundation API 호출
   → [Verification Gate] 스키마·인용 존재성
   → [Bill Store] ImpactResult 캐시
   → [Web] 4종 결과 표시 (요약 / 무엇이 바뀌나(diff) / 내 영향 / 대응안)
```

---

## 3. 확장점 & "옆에 붙이면 동작" 시연 (입력 확장성)

핵심: *MVP는 3개 출처(열린국회·법제처·국가법령정보) 검색·선택만, URL/뉴스도 커넥터만 추가하면 동일 하류로 합류.* (커넥터 추가가 곧 확장이라는 설계가 MVP에서 이미 입증됨 — 법제처가 그 사례)

**확장점 = #1 SourceConnector / #2 SourceAnalyzer 인터페이스.** 새 입력 추가 절차:
1. `SourceConnector` 구현체 1개 추가(예: `NewsUrlConnector` — fetch+본문추출 → `RawBill`/식별자).
2. `SourceAnalyzer`에 입력 분기 1개 등록(`URL` → 해당 커넥터).
3. **하류 무수정** — Normalizer → Bill Store → 4종 커맨드 → 표면 그대로.

MVP에서는 #2의 **URL/텍스트 분기를 인터페이스만 정의(스텁)** 하고, `의안번호/법안명` 분기만 활성화한다. → "인코딩(커넥터)만 옆에 연결하면 동작"을 코드 구조로 보증(구현은 후순위).

---

## 4. MVP 범위 (IN / OUT)

### IN
- SourceConnector: **법안 2개(열린국회정보·법제처 입법예고) + 현행법 1개(국가법령정보, diff 기준선)**
- SourceAnalyzer: **의안번호/법안명 + 모호 plain text**(정확매칭 + 법안 의미검색) — 해소 4상태(§4 #2). URL/뉴스 = 인터페이스 스텁
- Normalizer → **Bill Store(RDB)**; RAG Indexer → **Vector Index**(현행법 *분석용* + 법안 요약·BillFacts *탐색용*)
- **회원가입 프로필 입력 UI + User Profile Store** (자기신고, D41)
- AnalysisPipeline + Command Registry + **커맨드 4종**(ImpactSummary/**LawDiff**/PersonaImpact/ActionPlan)
- **현행법 diff**: 법안 **신구조문대비표**(1차) + **국가법령정보 현행법**(권위 기준선·보강) → `baselineLawId` 채움
- Analysis Engine: **foundation API 호출** + 현행법 **RAG 검색**, 프롬프트 정의서 v0.1, 구조화 JSON
- Verification: **스키마 + 인용 존재성(규칙)**
- Web: **가입·프로필 입력** → 검색→법안 선택→**4종 표시(요약/diff/내 영향/대응안)**

### OUT (확장점은 유지, 구현 후순위)
- URL/뉴스 해소 **구현** (인터페이스만)
- `StageTracker` 통과확률, `Precedent` 비교
- 추가 출처(지자체 **조례**, 국세청 해석례 등)
- 대형법안 Map-Reduce, Generator-Critic, 모델 티어링
- LLM-judge 인용 뒷받침(MVP는 규칙 검증만)
- MCP 어댑터(내부 전용·미노출 — 기정)
- 인구분포 triage 가중치
- **Evaluation Harness(합성 페르소나 E2E)** — 수직 슬라이스 완성 후 착수(**D36**). MVP 품질 앵커는 컴포넌트 단위 테스트 + 소량 사람검수 골든셋

---

## 5. Evaluation Harness — 합성 페르소나 E2E *(post-MVP 이연, D36)*

> **이연(D36):** 완성된 E2E 흐름·프롬프트가 있어야 회귀·UX 비평 가치가 발동하므로 **수직 슬라이스(#13) 완성 후 착수**한다. 아래 역할 규율은 착수 시점에 그대로 적용.

비런타임 CI 컴포넌트. **두 역할을 분리**한다.

- **채택(구동·정성):** 흐름 smoke/회귀, 스키마·인용 누락 탐지, 세그먼트별 UX 비평("전문용어 과다/내 관심사 누락"), 취약 케이스(고령·농촌·저문해) 발굴, 현실적 자유텍스트 입력 생성.
- **금지(정답 판정):** 법적 정확성의 최종 오라클로 쓰지 않음. → 정답 앵커는 **규칙 검증 + 소규모 사람검수 골든셋**.
- 7M 전수 아님 — **대표 패널 수십 세그먼트** 샘플(비용).
- 학습 루프 없음(파인튜닝 미사용 방침 유지).

---

## 6. MVP 수용 기준 (vertical slice)

1. **열린국회·법제처** 법안 각 1건 + 해당 **현행법 기준선**이 수집·정규화되어 RDB/Vector Index에 존재.
2. 사용자 프로필 1건이 저장돼 있고 수정·삭제가 가능하다.
3. 한 법안 × 한 프로필 → **4종 커맨드**(요약/diff/내 영향/대응안)가 **인용 포함 구조화 JSON**을 반환.
4. `LawDiff`가 신구조문대비표·현행법 기준선을 인용해 조문별 현행→개정 변화를 제시.
5. Verification Gate가 *인용 없는/허위 source_id* 응답을 차단(재생성/폴백).
6. Web에서 검색→선택→4종 결과 표시까지 동작.

---

## 7. 결정 필요 (Open)

- [x] 페르소나 획득 방식 → **자기신고 프로필**(D41, [[component-specs]] §2). ~~Nemotron 6세그먼트~~ 폐기
- [x] Spring↔Python REST 계약 → [[component-specs]] §3
- [x] 현행법 diff MVP 처리 → **MVP 포함**(신구조문대비표+국가법령정보, §4 IN)
- [x] foundation 모델 픽 + 토큰 예산 → Opus 4.8 ([[component-specs]] §3.3)
- [ ] 현행법↔법안 조문 **정렬(alignment)** 방식 — 신구조문대비표 파싱 vs baseline 자동매칭
- [ ] Evaluation Harness 패널 크기·골든셋 규모
