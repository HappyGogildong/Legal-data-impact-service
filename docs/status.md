---
title: 프로젝트 현황 · 기술 스펙 · 구현 체크리스트
status: Living
date: 2026-08-14
tags: [status, spec, roadmap]
related: ["ARCHITECTURE.md", "backend/observability.md", "adr/decision-log.md"]
---

# 프로젝트 현황 · 기술 스펙 · 구현 체크리스트

> 개발자용 현황 문서. 사용자용 개요는 [`README.md`](../README.md), 설계 이유는 [`ARCHITECTURE.md`](ARCHITECTURE.md)·[`adr/decision-log.md`](adr/decision-log.md).

---

## 1. 성과 요약 (TL;DR)

> 실측 성과 수치(p99·QPS 등)는 부하 테스트 단계에서 채운다 — 이 프로젝트는 **측정하지 않은 수치를 쓰지 않는다**([D48](backend/observability.md)). 아래는 현재까지의 기술적 하이라이트다.

- ⚡ **비용 인지 설계** — 자연어 질의를 타입 DTO로 번역하는 [Query Planner](components/query/QueryPlanner.md)로 캐시(Layer A)/LLM(Layer B)를 분기. 요약·비교는 LLM 호출 0. `조문변경여부` 플래그로 분석 대상을 **137 → 6 조문(약 20분의 1)** 으로 축소(주택법 실측).
- 🔄 **법령 수집 파이프라인** — 국가법령정보 *시행 대기 법령*을 배치 수집·정규화·조문 diff 선계산(**실측 899건**). 오프라인(적재)·온라인(질의) 실행 모드 분리.
- 📊 **측정 선행 Observability** — Prometheus·Loki·Grafana·Tempo + k6. single-flight 등 동시성 기법은 **지표로 병목을 증명한 뒤** 적용(speculative 최적화 배제).

### 부하 테스트 후 채울 수치 슬롯 (측정 전에는 비워 둔다)

| 항목 | 지표 | 값 |
|---|---|---|
| ⚡ 성능 | 분석 캐시 + single-flight: p99 `___`ms → `___`ms (`__`% 개선) | 미측정 |
| ⚡ 성능 | 중복 LLM 호출 100 → 1 (k6 스탬피드 before/after) | 미측정 |
| 🔄 파이프라인 | 비동기 이벤트 분산 처리로 QPS `____` 안정화 | 미측정 |
| 📊 Observability | k6 부하 시나리오 `__`종 · Grafana 대시보드 `__`개 | 미측정 |

---

## 2. 기술 스펙

| 구분 | 버전 | 비고 |
|---|---|---|
| **JDK** | **Java 21 (LTS)** | Gradle **toolchain으로 고정**. Boot 4.0 최소는 17이나 최신 LTS 권장 |
| Spring Boot | 4.0.0 | GA 2025-11 |
| Spring AI | 2.0.0 | GA 2026-05. **Boot 4 전용** — 3.x와 혼용 불가 |
| Gradle | 9.5.1 (wrapper) | 툴체인 자동 프로비저닝(foojay) 포함 |
| TypeScript | 5.5+ / Node 20+ | 웹 프론트엔드 |
| DB | PostgreSQL 16 + pgvector | `pgvector/pgvector:pg16` |
| 추론 모델 | Claude Opus 4.8 | `claude-opus-4-8`, 입력~32K / 출력 4K |
| 분류·번역 모델 | Claude Haiku 4.5 | `claude-haiku-4-5-20251001`, 질의 번역(티어링) |
| 임베딩 | 외부 API, dim 1536 | OpenAI vs Upstage 벤치 후 확정 |
| Python | 3.12+ | 진단 스크립트 전용(운영 경로 아님) |

### JDK를 21로 고정한 이유

`sourceCompatibility` 대신 **toolchain**을 쓴다. 전자는 *source 레벨*만 낮출 뿐 컴파일러가 상위 JDK의 API에 링크하는 것을 막지 못해, JDK 21 런타임에서 `NoSuchMethodError`가 나는 클래스가 만들어질 수 있다. toolchain은 **JDK 자체를 고정**한다.

```gradle
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
```

JDK 21이 없어도 `settings.gradle`의 foojay 리졸버가 자동으로 받아온다. Java 25 LTS로 올릴 수 있으나(Boot 4.0 지원), 지금 코드가 Java 22~25의 기능을 쓰지 않아 실익이 없다. 올린다면 `languageVersion` 한 줄만 바꾸면 된다.

> ⚠️ Java 22는 **비-LTS이고 이미 EOL**이다(보안 패치 없음). 로컬에 22만 있어도 툴체인이 21을 받아 쓰므로 빌드는 되지만, 개발 JDK도 LTS로 맞추길 권한다.

---

## 3. 구현 체크리스트

| 컴포넌트 | 상태 |
|---|---|
| `LawConnector` — 시행예정 목록·본문·기준선 | ✅ |
| `Normalizer` — 조문 병합·부칙 필터·시행규칙·`revision` | ✅ |
| `SourceAnalyzer` — 해소 4상태, fail-closed | ✅ |
| `domain/law` — `Law`·`Article`·`Addendum` | ✅ |
| `DiffBuilder` — 변경 조문 ↔ 기준선 대조(신설·삭제 확정, `diffVsCurrent`) | ✅ |
| 관측 환경(증분1) — Actuator·Micrometer→Prometheus·Tracing→Tempo·pg-exporter + 파이프라인 계측 | ✅ |
| RAG 평가·회귀 프레임워크 — 결정론 게이트 스캐폴딩(`com.lia.core.eval`), 거부 게이트 가동 | ✅ |
| 컴포넌트 클래스 스펙 규약 + 핵심 4종(DiffBuilder·Normalizer·SourceAnalyzer·LawConnector) | ✅ |
| Law Store — **near-term 구현**(`law_versions` upsert·find·findBaseline, Flyway V1, JdbcClient+JSONB) | ✅ 실 Postgres 통합테스트 통과(Testcontainers 3/3) |
| `IngestService` — **적재 파이프라인 조립**(Normalizer→DiffBuilder→LawStore, `store`/`ingestPending`) | ✅ 실 Postgres 조립 통합테스트 통과(기준선·제정 2/2) |
| Embedder — **포트 + OpenAI 구현체**(Spring AI 위임·정규화·dim 가드·mode 흡수, D32) | ✅ 단위 7 통과(Fake 4·OpenAi 3)·라이브 스모크 옵트인. 벤더 최종확정은 D33 벤치 |
| RAG Indexer — **구현**(변경조문 청킹·`amendReason` 요약·문자수 오버랩 분할·source_id, D55) | ✅ 단위 2(Fake ChunkStore) |
| ChunkStore — **구현**(포트 + `PgVectorChunkStore`, PgVectorStore 래핑·**정본 단위 replace**(재색인=현재 상태)·내부 임베딩) | ✅ 실 pgvector 통합 3(라운드트립·stale제거·스코프격리) |
| IngestService 색인 배선 — `ingestPending`이 `store` 뒤 `index` 호출(store는 임베딩 프리) | ✅ 배선·카나리아 |
| RAG 검색 경로 — `ChunkStoreRetriever`(실물 Retriever) + `SourceAnalyzer.semanticSearch` 배선(ChunkStore) + chunk 메타 title | ✅ 단위 2·실 왕복 스모크 옵트인 |
| RAG 성능 평가(자기검색 기준선) — `SelfRetrievalGold` + `RagEvalLiveTest`(실 적재→Recall@k). 벤더 OpenAI 임시확정 | ✅ 단위 2·실 평가 옵트인(수동). 시나리오 B는 리서치 후 |
| Query Planner — **계획까지**(`QueryTranslator` 포트+Haiku·`QueryPlanner`: NL→PlanResult, 해소·프로필 게이팅·비법령 거부, D46) | ✅ 단위 4(Fake 번역기)·라이브 스모크 옵트인. Dispatcher/핸들러 실행은 후속 |
| Analysis Engine — **Layer A**(SUMMARY·DIFF): `ContextBuilder`(정본→source_id 블록)·`Reasoner` 포트+`SpringAiReasoner`(Opus)·`AnalysisEngine`(조립→추론→인용검증→재생성≤N→폴백) | ✅ 단위 10(ContextBuilder 6·Engine 4, FakeReasoner)·라이브 스모크 옵트인 |
| QueryDispatcher + 차원핸들러 — **Layer A 슬라이스**(D47): `DimensionHandler` 포트+레지스트리·`QueryDispatcher`(Reference 정본 1회조회→차원 라우팅→부분성공 `unmet`)·`Summary`/`Diff` 핸들러(AnalysisEngine 위임)·`LawSource` 포트 | ✅ 단위 11(Dispatcher 7·Handler 2·Registry 2). ImpactHandler·ActionHandler·LookupHandler·캐시·독립 검증게이트는 의존 착지 후 |
| 웹 프론트엔드 · User Profile Store | ⬜ |

단위 테스트 **128개**(+AnalysisEngine 10 · +QueryDispatcher 11) + 통합 **8건**(실 Postgres/pgvector, Testcontainers: Law Store 3 + 적재 조립 2 + ChunkStore 3) 통과. (실 임베딩·번역·해석 스모크/평가 5종은 옵트인·수동)

> ✅ **`[Law]` 해결(D54 · [[004-jejeong-law-no-baseline-english-envelope|troubleshooting/004]]).** `본문 응답에 '법령' 블록이 없다: [Law]`는 **제정 법령 = 현행본 없음**이 원인 — `fetchCurrent`가 `null` 반환(전부 신설)으로 처리. 남은 라이브 스모크의 `빈 응답`은 진단 probe 과다호출로 인한 **국가법령정보 API 일일 쿼터 소진**(쿼터 회복 후 정상, 코드 무관).

### 구현 순서 (큐)

… Query Planner 계획 ✅ · Analysis Engine Layer A ✅ · **QueryDispatcher + 차원핸들러 Layer A ✅**(#10: 계획↔해석 연결, Summary·Diff 라우팅·부분성공) → **UserProfile→Layer B**(IMPACT·ACTION 핸들러) · **LawDiscovery(#19)→LookupHandler** → 수직 슬라이스.

---

## 4. 실행 모드 — 오프라인 / 온라인

같은 애플리케이션이지만 성격이 다르다. **온라인에 새 작업을 넣기 전에 "오프라인으로 미리 할 수 없나"를 먼저 묻는다.**

```
[오프라인 · 배치]  스케줄러 → LawConnector → Normalizer → Diff Builder
                   → LawFacts 파생 → RAG Indexer → Postgres+pgvector

[온라인 · 요청]    사용자 → 웹앱 → REST → Query Planner(번역·해소/검색)
                   → QueryDispatcher(조립) → AnalysisEngine(RAG+LLM) → 인용검증 → 결과
```
