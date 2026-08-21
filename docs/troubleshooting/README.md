---
title: 트러블슈팅 사례
status: Living
date: 2026-08-14
tags: [troubleshooting, debugging, postmortem]
related:
  - "backend/observability.md"
  - "adr/decision-log.md"
---

# 트러블슈팅 (Troubleshooting)

실제로 부딪힌 문제를 **[문제 정의 → 가설 → 메트릭/실측 기반 원인 확인 → 해결 방안 비교 → 최종 적용·검증]** 5단계로 기록한다. 각 사례는 **재현·근거·검증**이 있어야 한다 — 추정이나 각색은 쓰지 않는다([[observability|측정 선행]] 원칙, D48).

형식: [`_template.md`](_template.md).

## 사례

| # | 사례 | 유형 | 핵심 |
|---|---|---|---|
| [001](001-lawapi-display1-single-object.md) | 국가법령정보 `display=1` 단일 객체 함정 | API 통합 | 예외 없이 **조용한 0건** → 응답 구조가 건수에 따라 달라짐 |
| [002](002-delegation-regex-korean-morphology.md) | 위임조항 정규식 한글 형태소 누락 | 파싱 | `정한다` ≠ `정하는` — 단위 테스트가 포착 |
| [003](003-boot4-tracing-autoconfig-moved.md) | Boot 4.0 트레이스가 Tempo에 안 뜸 | 관측·Spring 4 | 자동설정이 starter-actuator에서 분리 → `starter-opentelemetry` 필요 |
| [004](004-jejeong-law-no-baseline-english-envelope.md) | 제정 법령 현행본 부재 → 영문 `Law` 봉투 | 커넥터·도메인 | "환경 차이"로 보인 간헐 실패가 실은 **날짜 의존 데이터** |

## 부하 테스트 시 추가 예정 (감지 신호 매핑)

아직 런타임 부하를 걸지 않아 아래 성능·동시성 유형은 **미발생**이다. 발생하면 이 폴더에 5단계로 기록한다. 각 유형의 1차 감지 신호는 이미 [[observability]]·[[concurrency-and-reliability]]에 정의돼 있다 — *어떻게 진단할지*는 이미 준비됐고, *무엇이 일어났는지*만 실측으로 채운다.

| 유형 | 1차 감지 신호(계획) | 관련 |
|---|---|---|
| 캐시 스탬피드 (중복 LLM 호출) | `inflight.duplicate` / `cache.miss` + k6 100:100 | concurrency §1 |
| Connection Pool starvation | HikariCP `active`/`pending`, `query.latency` p99 | observability §2 |
| Lock / Deadlock | postgres_exporter `deadlocks`, `ingest.upsert.conflict` | concurrency §2 |
| GC Pause / Memory | JVM gc pause, heap, `query.latency` p99 | observability §2 |

> **측정 선행(D48).** 신호 없이 기법을 넣지 않듯, 사례도 재현·근거 없이 쓰지 않는다. 이 표는 "예상 진단 경로"이지 "발생 기록"이 아니다.
