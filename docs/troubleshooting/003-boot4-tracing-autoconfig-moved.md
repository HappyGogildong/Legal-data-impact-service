---
title: "003 · Boot 4.0 트레이스가 Tempo에 안 뜸 — 자동설정 모듈 이동"
status: Resolved
date: 2026-08-18
tags: [troubleshooting, observability, tracing, spring-boot-4, otlp]
---

# 003 · Boot 4.0 트레이스가 Tempo에 안 뜸 — 자동설정 모듈 이동

| | |
|---|---|
| 유형 | 관측성 · 트레이싱 · Spring Boot 4.0 |
| 발생 시점 | 2026-08-18 (관측 환경 증분1) |
| 관련 | `core/build.gradle` · `application.yml` · [[observability]] |

## 1. 문제 정의

지표(Prometheus)는 정상인데 **트레이스가 Tempo에 하나도 안 들어왔다.** Tempo 검색 `inspected_traces=0`, core 로그에 OTLP 오류조차 없음. 데모 ingest 로그의 상관ID 자리가 `[lia-core]`로 **traceId가 비어 있었다** → span 자체가 안 만들어짐.

## 2. 가설

1. OTLP export 실패(네트워크) — 하지만 오류 로그 0건이라 배제.
2. **트레이싱 자동설정이 아예 활성화 안 됨**(Tracer/exporter 빈 미생성).

## 3. 원인 확인 (근거)

- `micrometer-tracing-bridge-otel`·`opentelemetry-exporter-otlp`는 classpath에 **해결됨**(의존성 트리 확인).
- 그런데 `spring-boot-actuator-autoconfigure-4.0.0.jar`의 `AutoConfiguration.imports`에 **tracing·otlp 항목이 없다.** Boot 4.0은 관측 자동설정을 모듈로 쪼갰다(`spring-boot-micrometer-observation`은 별도 모듈).
- 즉 **트레이싱 자동설정(`OtlpTracingAutoConfiguration`)이 `starter-actuator`에 없고**, bridge만으론 Tracer가 안 뜬다 → 가설 2 확정.
- 부수 확인: 속성 네임스페이스도 Boot 3.x의 `management.otlp.tracing.*` → Boot 4.0 `management.opentelemetry.tracing.export.otlp.*`로 이동.

## 4. 해결 방안

| 안 | 내용 | 평가 |
|---|---|---|
| A | bridge·exporter를 개별 추가(현행) | Boot 4.0에선 자동설정 모듈이 빠져 **작동 안 함** |
| B | **`spring-boot-starter-opentelemetry`** 추가 | 트레이싱 자동설정 + bridge 번들. Boot 4.0 표준 |

## 5. 최종 적용·검증

- `build.gradle`: `micrometer-tracing-bridge-otel` → **`org.springframework.boot:spring-boot-starter-opentelemetry`** (+ `opentelemetry-exporter-otlp` sender 유지).
- `application.yml`: `management.otlp.tracing.endpoint` → **`management.opentelemetry.tracing.export.otlp.endpoint`**.
- 검증: 재기동 후 로그 상관ID가 `[<traceId>-<spanId>]`로 채워짐. Tempo에서 `lia.ingest → {lia.normalize, lia.diff}` **3-span 트리** 조회 성공.

**교훈:** Boot 메이저 업글 시 **자동설정은 스타터에 있다고 가정하지 말 것** — 4.0은 관측 자동설정을 모듈로 분리했다. "의존성은 있는데 빈이 안 뜬다"면 해당 `AutoConfiguration.imports`에 실제로 등록됐는지부터 확인한다. 속성 네임스페이스 이동도 함께 본다.
