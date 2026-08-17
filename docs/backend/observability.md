---
title: 관측성·성능 측정 환경 (Observability)
status: Draft
version: 0.2
date: 2026-08-18
tags: [backend, observability, metrics, tracing, load-test, performance]
related:
  - "backend/concurrency-and-reliability.md"
  - "adr/decision-log.md"
  - "adr/ADR-001-knowledge-store-sizing.md"
---

# 관측성·성능 측정 환경 (Observability)

**관련:** [[concurrency-and-reliability|백엔드 동시성·신뢰성]] · [[ADR-001-knowledge-store-sizing|ADR-001]] (비용 동인) · [[decision-log|D48]]

## 0. 원칙 — 측정이 최적화에 선행한다

동시성·신뢰성 기법(single-flight, 격리수준 상향, outbox)은 **speculative하게 넣지 않는다.** 근거 없이 락을 넣으면 *없는 병목을 만들고* 복잡도만 늘린다. 각 기법은 세 가지를 갖춰야 도입한다:

1. **신호(signal)** — 문제를 드러내는 지표
2. **임계(trigger)** — 도입을 촉발하는 값
3. **재현(reproduce)** — 부하 테스트로 문제를 만들고 수정 전/후를 측정

> 이는 [[concurrency-and-reliability]]의 각 항목이 *문제 → 신호 → 기법 → 트리거* 순서로 서술되는 이유다. 코드보다 **계기판이 먼저**다.

---

## 1. 스택

우리 런타임(Spring Boot 4.0)에 이미 붙는 표준 조합. 자체 호스팅 부담 없이 로컬 도커로 띄운다.

| 층 | 도구 | 비고 |
|---|---|---|
| **지표** | Spring Boot Actuator + **Micrometer**(Boot 내장) → **Prometheus** scrape → **Grafana** | `/actuator/prometheus` 노출 |
| **추적** | **Micrometer Tracing** + **OpenTelemetry**(OTLP) → **Tempo** | 요청당 분산 트레이스. ⚠️ Boot 4.0은 `spring-boot-starter-opentelemetry` + `management.opentelemetry.tracing.export.otlp.*` 필요([[003-boot4-tracing-autoconfig-moved\|003]]) |
| **로그** | 구조화 JSON(Logback) → **Grafana Loki** · trace-id 상관 | §4. AWS 대안 CloudWatch Logs |
| **DB** | **postgres_exporter** | lock wait·deadlock·트랜잭션·커넥션 |
| **부하** | **k6** | 시나리오 재현, before/after |

**로컬 구성:** 기존 `docker-compose.yml`(Postgres) 위에 `docker-compose.observability.yml` **오버레이** — `prometheus`·`grafana`·`tempo`·`postgres-exporter`. core 는 호스트 bootRun, 계기판은 컨테이너:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d db prometheus grafana tempo postgres-exporter
cd core && ./gradlew bootRun --args='--spring.profiles.active=demo'
```

Grafana `http://localhost:3001`(익명 Admin) · Prometheus `:9090` · Tempo `:3200`. 설정: `ops/observability/`.

### 1.1 증분 1 — 구축 범위·상태 (2026-08-18)

**측정·추적 대상을 먼저 정의하고(§2·§3.1) 그에 맞춘 스택만 세운다.** 온라인 답변 경로(dispatch·LLM·RAG)는 미구현이라 도메인 지표는 **hook(배선)** 으로 두고, 지금 코드가 있는 **오프라인 파이프라인**을 실측한다.

| 구성 | 이번 증분 | 비고 |
|---|---|---|
| Actuator+Micrometer→Prometheus | ✅ | `/actuator/prometheus` 라이브 검증 |
| Micrometer Tracing→OTel→**Tempo** | ✅ | Jaeger 대신 Grafana-native |
| postgres-exporter | ✅ | DB lock/tx/conn |
| **오프라인 파이프라인 계측** | ✅ | fetch·normalize·diff·resolve·ingest (§2 live) |
| Grafana 대시보드(`lia-overview`) | ✅ | 파이프라인 지연·JVM·HTTP + hook 패널 |
| 데모 러너(`@Profile("demo")`) | ✅ | 번들 fixture → normalize→diff, 베이스라인 생성 |
| **Loki**(로그)·**k6**(부하) | ⬜ 증분 2 | 이번 "측정·추적 대상"이 아님 — 온라인 경로 landing 후 |
| LLM/RAG/dimension 실측 | ⬜ hook | Embedder·Store·AnalysisEngine landing 시 발화 |

---

## 2. 도메인 지표 (우리가 실제로 봐야 할 것)

프레임워크 기본 지표(JVM·HTTP·HikariCP 풀) 위에, **결정에 쓰이는** 커스텀 지표를 둔다.

**live (증분 1) — 오프라인 파이프라인 단계.** `Observation` 으로 감싸 <b>타이머 + span</b> 동시 생성(코드: `Obs` 상수 · `PipelineConfig` 주입).

| 지표 | 타입 | 태그 | 무엇을 결정하나 |
|---|---|---|---|
| `lia.connector.fetch` | timer | `target` | 외부 API 지연(네트워크+응답+파싱) |
| `lia.normalize` | timer | — | 파싱 순수 CPU — 큰 법령이 병목인가 |
| `lia.diff` | timer | — | 대조 순수 CPU |
| `lia.resolve` | timer | `state` | 해소 지연(명칭매칭+의미검색) |
| `lia.ingest` | timer | — | 적재 1건 end-to-end |

**hook — 온라인 경로 landing 시 발화.**

| 지표 | 타입 | 태그 | 무엇을 결정하나 |
|---|---|---|---|
| `lia.analysis.llm.calls` | counter | `model`,`dimension` | **비용.** Opus vs Haiku 호출량(ADR-001 비용 동인) |
| `lia.analysis.cache.hit` / `.miss` | counter | `layer`,`dimension` | 캐시 적중률 → 스탬피드 판단 |
| `lia.analysis.inflight.duplicate` | counter | `dimension` | **동시 중복 질의 → single-flight 트리거** |
| `lia.query.latency` | timer | `endpoint`,`dimension` | p50/p95/p99 |
| `lia.translate.latency` | timer | — | 번역기(Haiku) 지연 |
| `lia.dimension.latency` | timer | `dimension` | 차원별 지연 — 어디서 시간 쓰나 |
| `lia.rag.retrieve.latency` | timer | `namespace` | 벡터 검색 지연 |
| `lia.ingest.duration` | timer | — | 적재 배치 시간 |
| `lia.ingest.upsert.conflict` | counter | — | **적재 동시성 신호**(feature 2) |
| `lia.notify.delivery.lag` | timer | — | 통지 지연 |
| `lia.notify.duplicate` | counter | — | **정확히 한 번 위반 감지**(feature 3) |

**원칙:** 지표는 *가설을 검증할 수 있게* 태깅한다. "Opus 호출이 많다"가 아니라 "어느 `dimension`의 Opus 호출이 캐시 miss와 함께 늘었나"를 볼 수 있어야 한다.

---

## 3. 트레이싱 — 시간이 어디로 가나

요청 1건의 분산 트레이스:

```
POST /analyses
 └─ translate (Haiku)        ← span
 └─ resolve / discover       ← span
 └─ dispatch
     ├─ SUMMARY (cache)      ← span (짧아야 정상)
     ├─ DIFF (cache)         ← span
     ├─ IMPACT (RAG→Opus)    ← span (여기가 대개 p99 주범)
     └─ verify (인용검증)     ← span
```

p99가 튀면 **어느 span인지** 바로 보인다 — Haiku 번역인지, RAG 검색인지, Opus 추론인지, DB인지. "느리다"를 "무엇이 느리다"로 바꾸는 것이 트레이싱의 값이다.

---

## 3.1 지연 분해 — 추론 시간 vs API 응답 시간

"느리다"의 층을 나누지 않으면 반드시 오독한다. 지연은 **세 층**이며, 두 층까지는 우리가 직접 측정하지만 **순수 추론(모델 compute)은 프로바이더 내부라 직접 측정할 수 없다.**

```
[ 총 API 응답 지연 ]  ← 사용자 체감
   = 우리 오버헤드 + [ LLM 호출 구간 ]
                       = 네트워크 + 프로바이더 큐 + [ 순수 추론 ]  ← Anthropic 내부
```

| 층 | 무엇 | 측정 |
|---|---|---|
| **총 API 응답 지연** | 웹→REST→파이프라인 전체 왕복 | ✅ request span |
| **LLM 호출 구간** | `ChatClient` 호출을 감싼 span (네트워크+큐+추론) | ✅ 별도 span — **"추론 계열"을 나머지와 분리** |
| **순수 추론**(서버 compute) | 모델이 토큰을 계산하는 시간만 | ❌ 직접 불가 — Anthropic 응답에 server compute 필드 없음 |

**순수 추론 근사 — 스트리밍.** `.stream()`(Spring AI `ChatClient` Flux)을 쓰면 LLM 호출 구간을 둘로 쪼갠다. 최종 응답은 버퍼링해 구조화 JSON으로 돌려주더라도 **내부 스트리밍으로 TTFT를 계측**한다.

- **TTFT**(첫 토큰까지) ≈ 네트워크 + 큐 + **prefill**(프롬프트 처리)
- **생성 시간**(첫~마지막 토큰) ≈ 실제 토큰 생성. `생성시간 / output_tokens` = **토큰/초 처리량**

**우리 오버헤드는 자동으로 갈린다:** `총 − LLM span = RAG 검색 + 프롬프트 조립 + 인용검증`. `dimension` 태그로 Layer A(캐시, **추론 0**) vs Layer B(Opus) 구분. 포괄 질문은 `추론 계열 = Σ(llm.call span)`(IMPACT+ACTION).

> **⚠️ 대기시간 ≠ 추론시간.** single-flight로 99요청이 1계산을 기다리면 그 99개의 *API 지연*은 크지만 *추론은 0*이다 — 그냥 기다린 것. 집계 지표(`query.latency`)만 보면 "느리다"로 오독한다. **`llm.singleflight.wait` span/지표를 `llm.call`과 분리**해야 "기다린 거지 추론이 느린 게 아니다"가 보인다.

**분해용 지표:**

| 지표 | 의미 |
|---|---|
| `query.latency{endpoint,dimension}` | 총 API 지연(사용자 체감) |
| `llm.call.latency{model,dimension}` | LLM 호출 구간(추론 계열) |
| `llm.ttft{model}` | 스트리밍 시 TTFT — prefill+큐 근사 |
| `llm.singleflight.wait` | 병합 대기(추론 아님, 별도) |
| `rag.retrieve.latency{namespace}` | 벡터 검색(우리 오버헤드) |

항상 `query.latency = 우리 오버헤드(RAG+조립+검증+대기) + Σ llm.call.latency` 로 분해된다.

---

## 4. 로그 (Logs) — Grafana Loki

세 기둥에서 로그의 역할은 다르다. **지표**=집계 숫자, **추적**=요청 경로, **로그**=이벤트 원본("무슨 일이 있었나"의 상세). 셋을 **Grafana 한 UI**에서 `trace-id`로 오간다 — Prometheus(지표) · Loki(로그) · Tempo(추적).

**스택.** 구조화 JSON 로그(Logback) → **Loki** 적재 → Grafana 조회. Loki는 로그 *본문*을 색인하지 않고 **라벨만 색인**해 경량이다(ELK/Elasticsearch 대비 운영 부담↓ — 우리 규모엔 이게 맞다). AWS 배포 시 관리형 대안은 CloudWatch Logs.

**상관(correlation).** MDC에 `trace-id`·`span-id`를 주입해 로그 라인이 추적과 연결된다. Grafana에서 느린 span → 그 요청의 로그로 원클릭.

**레벨·이벤트 정책.** 도메인 사건을 *구조화 이벤트*로 남긴다:
- `resolution=NOT_FOUND_YET/UNVERIFIED`(fail-closed) — WARN
- `grounding.blocked`(인용 없는 주장 차단)·`regenerate`(재생성) — INFO/WARN
- `ingest.upsert.conflict`·`notify.duplicate` — WARN/ERROR → Grafana 알림 연동

**운영 로그 vs 감사 로그 — 분리한다.**
- **운영 로그**(Loki): 디버깅·성능. 보존 TTL(예: 30일), 휘발.
- **감사 로그**: 법률 서비스 책임성 — 답변의 인용·모델·프롬프트 버전을 **append-only 영구** 기록. 상세: [[concurrency-and-reliability]] §4 감사 로그.

> **⚠️ D41 — 로그가 최소수집의 뒷문이 되지 않게.** ① 로그에 `userId`·프로필 원본을 남기지 않는다 — **프로필 해시·trace-id만**. ② 자연어 질의 원문에 개인정보가 섞일 수 있다 → 질의 전문을 기본 로깅하지 않거나 마스킹, 보존을 짧게. ③ 감사 로그도 근거·인용만, 개인 식별 최소.

---

## 5. 부하 테스트 (k6) — before/after로 증명

각 동시성 결정은 **가설 → k6 재현 → 지표 확인 → 기법 적용 → 재측정** 루프를 거친다.

| 시나리오 | 재현 | 측정 | 판정 |
|---|---|---|---|
| **캐시 스탬피드** | M VU가 **동일 질의**를 동시 발사(미캐시 상태) | `llm.calls` vs 요청 수 | 100요청→100호출이면 스탬피드 확정. single-flight 후 100→1이어야 함 |
| **지속 부하** | 목표 RPS 유지 | `query.latency` p99, `llm.calls` 비용 | SLO 내인가 |
| **적재 중 조회** | 배치 실행 중 조회 병행 | 조회 정확성·`upsert.conflict` | 읽기 일관성·경합 |
| **알림 팬아웃** | 시행일 도래 이벤트에 대량 구독 | `notify.duplicate`·`delivery.lag` | 정확히 한 번 지켜지나 |

**핵심:** single-flight를 "넣으면 좋겠지"로 넣지 않는다. 스탬피드 시나리오로 **100:100을 먼저 관측**하고, 적용 후 **100:1을 증명**한다. 그게 이 기법이 정당한 유일한 근거다.

---

## 6. 트리거 임계 (초기값 — 실측으로 조정)

| 기법 | 신호 | 초기 임계 |
|---|---|---|
| **single-flight** | `inflight.duplicate` / `cache.miss` | 동일 키 동시요청이 관측되고 중복 비율 > 5% |
| **격리수준 상향** | `ingest.upsert.conflict` | 경합 > N/일 또는 이상 데이터 1건이라도 |
| **outbox 강화** | `notify.duplicate` | > 0 (정확히 한 번은 타협 없음) |
| **모델 티어 조정** | `llm.calls`(Opus) · 비용 | 예산 초과 추세 |

임계는 **가정이지 규정이 아니다.** 계기판이 서고 실측이 쌓이면 이 표를 갱신한다.

---

## 7. 단계

1. Actuator + Micrometer + Prometheus 지표 노출(Boot 기본 + 커스텀 4~5개).
2. `docker-compose.observability.yml` — Prometheus·Grafana·postgres-exporter.
3. Grafana 대시보드: 비용(LLM 호출)·지연(p99)·캐시 적중률.
4. Micrometer Tracing + OTLP → Tempo(요청 트레이스). 구조화 JSON 로그 → **Loki**(trace-id 상관).
5. k6 스탬피드 시나리오 — 스탬피드 **관측**(기법 적용은 그 다음).

> 관측이 서기 전에는 [[concurrency-and-reliability]]의 기법을 **넣지 않는다.** 이 문서가 그 순서를 강제한다.
