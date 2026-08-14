# 입법 영향 분석기 (LIA · Legislative Impact Analyzer)

**곧 시행될 법령이 나에게 어떤 변화를 주는지, 무엇을 해야 하는지**를
일반 시민의 언어로 알려주는 웹 서비스.

## 핵심 성과 요약 (TL;DR)

> 실측 성과 수치(p99·QPS 등)는 부하 테스트 단계에서 채운다 — 이 프로젝트는 **측정하지 않은 수치를 쓰지 않는다**([D48](docs/backend/observability.md)). 아래는 현재까지의 기술적 하이라이트다.

- ⚡ **비용 인지 설계** — 자연어 질의를 타입 DTO로 번역하는 **ORM-like 플래너**로 캐시(Layer A)/LLM(Layer B)를 분기. 요약·비교는 LLM 호출 0. `조문변경여부` 플래그로 분석 대상을 **137 → 6 조문(약 20분의 1)** 으로 축소(주택법 실측).
- 🔄 **법령 수집 파이프라인** — 국가법령정보 *시행 대기 법령*을 배치 수집·정규화·조문 diff 선계산(**실측 899건**). 오프라인(적재)·온라인(질의) 실행 모드 분리.
- 📊 **측정 선행 Observability** — Prometheus·Loki·Grafana·Tempo + k6. single-flight 등 동시성 기법은 **지표로 병목을 증명한 뒤** 적용(speculative 최적화 배제).

<!-- 부하 테스트 후 채울 수치 슬롯 (측정 전에는 비워 둔다):
  ⚡ 성능: 분석 캐시 + single-flight 로 p99 ___ms → ___ms (__% 개선) / 중복 LLM 호출 100→1 (k6 스탬피드 before/after)
  🔄 파이프라인: 비동기 이벤트 분산 처리로 QPS ____ 안정화
  📊 Observability: k6 부하 시나리오 __종 · Grafana 대시보드 __개
-->

## 왜 "그냥 Claude에 물어보면" 안 되나

이 서비스는 **Claude의 추론력과 경쟁하지 않는다.** 날것의 LLM을 *법률 정보*에 못 쓰게 만드는 문제 — 지식 공백·환각·검색·검증 — 를 푸는 것이 제품이다. LLM 호출은 파이프라인의 **마지막 단계**이고, 그마저 인용된 근거만 쓰도록 **제약**된다.

| 그냥 Claude에 질문 | LIA |
|---|---|
| **모른다** — 곧 공포될·특정 시행예정 개정은 학습 범위 밖 | 국가법령정보에서 **실제 현행 조문을 검색**해 주입(권위·최신) |
| **지어낸다** — 그럴듯한 조문 번호를 자신 있게 환각 | **인용 없으면 차단**, 미해소 법령은 분석 안 함(fail-closed) |
| **사용자가 못 먹인다** — 어느 개정본? 뭐가 바뀌었나? | 해소·버전 특정·**변경 조문만**(실측 137→6)·현행 대비 diff를 대신 조립 |
| **검증 불가** | 모든 주장에 **조문 링크** → 클릭해 역추적 |

**결정타.** "2026-08-04 시행 주택법 개정으로 전세 세입자한테 뭐가 바뀌어?"
→ 날것의 Claude는 *"그 개정 정보가 없습니다"* 또는 **환각**. LIA는 실제 제49조 신설(현장점검 요청권)을 **인용·확정 시행일·부칙 기한**과 함께 답한다.

> **LLM은 의도적으로 교체 가능한 부품**이다(모델·임베딩 벤더 무관). 모델이 좋아지면 공짜로 올라타고, 해자(데이터 파이프라인·그라운딩·도메인 모델)는 별도로 쌓인다. — 같은 논리로 Harvey AI는 모두가 GPT를 쓰는데도 그라운딩·검증·큐레이션으로 Am Law 100의 97%를 확보했다.

## Overview (EN)

**LIA** tells ordinary citizens how a *soon-to-take-effect* law will affect them and what to do about it — in plain language, **grounded in cited articles**.

- **Cost-aware, ORM-like query planner** — a free-form natural-language question is *compiled* into a typed DTO; cheap dimensions (summary/diff) are served from precomputed cache, only personalized impact/action reach the LLM.
- **Fail-closed grounding** — every claim cites a source article; unverifiable laws are never analyzed, distinguishing *"not yet enacted"* from *"fabricated"*.
- **Measure-first observability** — Prometheus/Loki/Grafana/Tempo + k6; concurrency techniques (single-flight, isolation levels, outbox) are applied **only after metrics prove the bottleneck**.

**Not a wrapper.** LIA does not compete with Claude's reasoning; it solves what makes a raw LLM unusable for *legal* info — knowledge gaps, hallucination, retrieval, verification. The LLM is the last, *constrained* step (cite-only-injected sources). Ask raw Claude about a specific pending amendment and it says "I don't have that" or fabricates; LIA retrieves the actual article, cites it, and gives the effective date. The model is a swappable commodity; the moat is the data pipeline + grounding.

Stack: Java 21 · Spring Boot 4.0 · Spring AI 2.0 · PostgreSQL + pgvector · Claude (Opus/Haiku). Design docs live in [`docs/`](docs/) (Korean); see [`docs/troubleshooting/`](docs/troubleshooting) for debugging write-ups.

---

> 참고한 `korean-law-mcp` 류는 *이미 시행 중인* 법령을 조회한다.
> LIA의 차별점은 **아직 시행되지 않은 법령**을 다루고, 그것을 *나에게 미칠 영향*과
> *언제까지 무엇을 해야 하는지*로 번역한다는 점이다.

설계 상세: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · 처음이라면 [`docs/onboarding.md`](docs/onboarding.md)

---

## 분석 대상 — 공포 후 시행 대기 법령

| | 의원발의 의안 | 정부 입법예고 | **공포 후 시행 대기 법령** |
|---|---|---|---|
| 적용 확실성 | ~20% | 높음 | **100% 확정** |
| 시행일 | 미정 | 미정 | **확정일** |
| MVP 지위 | 참고용 | post-MVP | **분석 대상** |

통과 여부가 불확실한 의안을 분석하면 사용자에게 *일어나지 않을 일*을 알리게 된다.
시행 대기 법령은 불확실성이 0이고 시행일이 확정돼 있어 "언제까지 무엇을 하라"가
비로소 단정적으로 성립한다. → [결정 D42](docs/adr/decision-log.md)

**현재 코퍼스: 시행예정 899건** (2026-08-02~2027-12-31 실측)

---

## 기술 스펙

| 구분 | 버전 | 비고 |
|---|---|---|
| **JDK** | **Java 21 (LTS)** | Gradle **toolchain으로 고정**. Boot 4.0 최소는 17이나 최신 LTS 권장 |
| Spring Boot | 4.0.0 | GA 2025-11 |
| Spring AI | 2.0.0 | GA 2026-05. **Boot 4 전용** — 3.x와 혼용 불가 |
| Gradle | 9.5.1 (wrapper) | 툴체인 자동 프로비저닝(foojay) 포함 |
| TypeScript | 5.5+ / Node 20+ | 웹 프론트엔드 |
| DB | PostgreSQL 16 + pgvector | `pgvector/pgvector:pg16` |
| 추론 모델 | Claude Opus 4.8 | `claude-opus-4-8`, 입력~32K / 출력 4K |
| 임베딩 | 외부 API, dim 1536 | OpenAI vs Upstage 벤치 후 확정 |
| Python | 3.12+ | 진단 스크립트 전용(운영 경로 아님) |

### JDK를 21로 고정한 이유

`sourceCompatibility` 대신 **toolchain**을 쓴다. 전자는 *source 레벨*만 낮출 뿐
컴파일러가 상위 JDK의 API에 링크하는 것을 막지 못해, JDK 21 런타임에서
`NoSuchMethodError`가 나는 클래스가 만들어질 수 있다. toolchain은 **JDK 자체를 고정**한다.

```gradle
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
```

JDK 21이 없어도 `settings.gradle`의 foojay 리졸버가 자동으로 받아온다.
Java 25 LTS로 올릴 수 있으나(Boot 4.0 지원), 지금 코드가 Java 22~25의 기능을 쓰지 않아
실익이 없다. 올린다면 `languageVersion` 한 줄만 바꾸면 된다.

> ⚠️ Java 22는 **비-LTS이고 이미 EOL**이다(보안 패치 없음). 로컬에 22만 있어도
> 툴체인이 21을 받아 쓰므로 빌드는 되지만, 개발 JDK도 LTS로 맞추길 권한다.

---

## 구성 (2-런타임)

| 디렉터리 | 언어 | 책임 |
|---|---|---|
| [`core/`](core) | Java Spring | 수집·식별·정규화·대조·RAG·LLM 추론 + 도메인 + REST API |
| [`mcp/`](mcp) | TypeScript | 웹 프론트엔드(주 경로) + 내부 전용 MCP 어댑터(미노출) |
| [`tools/`](tools) | Python | 출처 연동 진단 스크립트 (운영 코드 아님) |
| [`docs/`](docs) | Markdown | 설계·결정 이력 (mkdocs 서빙) |

파이프라인은 D35로 Spring에 통합됐다(3-런타임 → 2-런타임).

### 실행 모드 — 오프라인 / 온라인

같은 애플리케이션이지만 성격이 다르다. **온라인에 새 작업을 넣기 전에
"오프라인으로 미리 할 수 없나"를 먼저 묻는다.**

```
[오프라인 · 배치]  스케줄러 → LawConnector → Normalizer → Diff Builder
                   → LawFacts 파생 → RAG Indexer → Postgres+pgvector

[온라인 · 요청]    사용자 → 웹앱 → REST → SourceAnalyzer(해소 4상태)
                   → Pipeline(조립) → AnalysisEngine(RAG+LLM) → 인용검증 → 결과
```

---

## 설계 원칙 (불변)

1. **신뢰 출처 그라운딩** — 모든 주장에 조문 `source_id` 인용, 없으면 차단
2. **수집과 해석의 분리** — 출처 API의 기벽은 Normalizer에서 끝난다
3. **확장 = 커넥터·커맨드 추가** (개방-폐쇄)
4. **fail-closed** — 신뢰 출처에서 확인되지 않으면 분석하지 않는다.
   "아직 없는 법"(`NOT_FOUND_YET`)과 "지어낸 법"(`UNVERIFIED`)은 안내가 다르다

---

## 빠른 시작

```bash
# 1) 자격증명 — 레포 루트 .env 가 단일 소스 (gitignore)
cp .env.example .env
```

```bash
# 2) 로컬 DB (Postgres 16 + pgvector, db/init.sql 자동 실행)
docker compose up -d db
```

```bash
# 3) 코어 — 단위 테스트 + 실 API 스모크 (키 없으면 스모크는 자동 스킵)
cd core && ./gradlew test
```

```bash
# 4) 코어 실행
cd core && ./gradlew bootRun
```

```bash
# 5) 웹 (TypeScript)
cd mcp && npm install && npm run dev
```

**출처 연동 진단** — 자격증명·응답 형식·본문 구조를 한 번에 실측한다:

```bash
python tools/probe_eflaw.py
```

**문서 사이트**:

```bash
mkdocs serve
```

### 자격증명

`.env` **하나가 단일 소스**다. `application.yml`은 `${ENV_VAR}` 참조만 둔다.

| 키 | 용도 | 발급 |
|---|---|---|
| `LAW_OC` | 국가법령정보 — **MVP 필수** | 회원 이메일의 아이디 부분 |
| `ANTHROPIC_API_KEY` | 추론 | console.anthropic.com |
| `OPENAI_API_KEY` | 임베딩 | platform.openai.com |
| `MOLEG_OC` · `ASSEMBLY_API_KEY` | post-MVP 출처 | — |

---

## 구현 상태

| 컴포넌트 | 상태 |
|---|---|
| `LawConnector` — 시행예정 목록·본문·기준선 | ✅ |
| `Normalizer` — 조문 병합·부칙 필터·시행규칙·`revision` | ✅ |
| `SourceAnalyzer` — 해소 4상태, fail-closed | ✅ |
| `domain/law` — `Law`·`Article`·`Addendum` | ✅ |
| Diff Builder — 변경 조문 ↔ 기준선 대조 | ⬜ 다음 |
| Law Store(RDB) · Embedder · RAG Indexer | ⬜ |
| Analysis Engine · 커맨드 4종 · 인용검증 게이트 | ⬜ |
| 웹 프론트엔드 · User Profile Store | ⬜ |

테스트 **60개** 통과(단위 + 실 API 라이브 스모크).

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 버전 색인 + 변경 이유. 현행 **v0.8** |
| [`docs/adr/decision-log.md`](docs/adr/decision-log.md) | 결정 로그 D01~D44 |
| [`docs/components/`](docs/components) | 컴포넌트별 입출력·동작·**구조 결정 의도** |
| [`docs/reference/law-attributes.md`](docs/reference/law-attributes.md) | 법령 속성 카탈로그 |
| [`CLAUDE.md`](CLAUDE.md) | 개발 규약·실측 함정·패키지 규칙 |

> `docs/architecture/vX.Y-*.md`는 **동결 스냅샷**이다 — 수정하지 않고 새 버전을 만든다.

---

## 면책

법률 자문이 아닌 **참고용 정보 제공** 서비스다. 모든 결과는 법령 조문·부칙·개정문 등
일차 출처로 역추적 가능하도록 설계했고, 근거가 부족하면 답을 만들지 않고 차단한다.
