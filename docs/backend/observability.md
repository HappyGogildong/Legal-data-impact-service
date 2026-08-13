---
title: 관측성·성능 측정 환경 (Observability)
status: Draft
version: 0.1
date: 2026-08-14
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
| **추적** | **Micrometer Tracing** + **OpenTelemetry**(OTLP) → **Tempo/Jaeger** | 요청당 분산 트레이스 |
| **로그** | 구조화 JSON(Logback) + **trace-id 상관** | 감사 로그와 연결([[concurrency-and-reliability]] §참고) |
| **DB** | **postgres_exporter** | lock wait·deadlock·트랜잭션·커넥션 |
| **부하** | **k6** | 시나리오 재현, before/after |

**로컬 구성:** 기존 `docker-compose.yml`(Postgres) 위에 `docker-compose.observability.yml` **오버레이** — `prometheus`·`grafana`·`tempo`·`postgres-exporter`. 개발자는 `docker compose -f docker-compose.yml -f docker-compose.observability.yml up`으로 계기판까지 한 번에.

---

## 2. 도메인 지표 (우리가 실제로 봐야 할 것)

프레임워크 기본 지표(JVM·HTTP·HikariCP 풀) 위에, **결정에 쓰이는** 커스텀 지표를 둔다.

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

## 4. 부하 테스트 (k6) — before/after로 증명

각 동시성 결정은 **가설 → k6 재현 → 지표 확인 → 기법 적용 → 재측정** 루프를 거친다.

| 시나리오 | 재현 | 측정 | 판정 |
|---|---|---|---|
| **캐시 스탬피드** | M VU가 **동일 질의**를 동시 발사(미캐시 상태) | `llm.calls` vs 요청 수 | 100요청→100호출이면 스탬피드 확정. single-flight 후 100→1이어야 함 |
| **지속 부하** | 목표 RPS 유지 | `query.latency` p99, `llm.calls` 비용 | SLO 내인가 |
| **적재 중 조회** | 배치 실행 중 조회 병행 | 조회 정확성·`upsert.conflict` | 읽기 일관성·경합 |
| **알림 팬아웃** | 시행일 도래 이벤트에 대량 구독 | `notify.duplicate`·`delivery.lag` | 정확히 한 번 지켜지나 |

**핵심:** single-flight를 "넣으면 좋겠지"로 넣지 않는다. 스탬피드 시나리오로 **100:100을 먼저 관측**하고, 적용 후 **100:1을 증명**한다. 그게 이 기법이 정당한 유일한 근거다.

---

## 5. 트리거 임계 (초기값 — 실측으로 조정)

| 기법 | 신호 | 초기 임계 |
|---|---|---|
| **single-flight** | `inflight.duplicate` / `cache.miss` | 동일 키 동시요청이 관측되고 중복 비율 > 5% |
| **격리수준 상향** | `ingest.upsert.conflict` | 경합 > N/일 또는 이상 데이터 1건이라도 |
| **outbox 강화** | `notify.duplicate` | > 0 (정확히 한 번은 타협 없음) |
| **모델 티어 조정** | `llm.calls`(Opus) · 비용 | 예산 초과 추세 |

임계는 **가정이지 규정이 아니다.** 계기판이 서고 실측이 쌓이면 이 표를 갱신한다.

---

## 6. 단계

1. Actuator + Micrometer + Prometheus 지표 노출(Boot 기본 + 커스텀 4~5개).
2. `docker-compose.observability.yml` — Prometheus·Grafana·postgres-exporter.
3. Grafana 대시보드: 비용(LLM 호출)·지연(p99)·캐시 적중률.
4. Micrometer Tracing + OTLP → Tempo(요청 트레이스).
5. k6 스탬피드 시나리오 — 스탬피드 **관측**(기법 적용은 그 다음).

> 관측이 서기 전에는 [[concurrency-and-reliability]]의 기법을 **넣지 않는다.** 이 문서가 그 순서를 강제한다.
