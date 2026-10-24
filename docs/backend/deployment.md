---
title: 배포 — 컨테이너화 + docker compose 자립 실행
status: Draft
version: 0.1
date: 2026-09-08
tags: [backend, deployment, docker, ops, ci]
related: ["backend/observability.md", "adr/decision-log.md", "components/web/AnalysisApi.md"]
---

# 배포 (self-host / staging)

> `docker compose up` 한 번으로 **db + core(+관측)** 가 뜨는 자립 실행 스택. 클라우드 운영은 [[cloud-infra]](단일 EC2 + RDS, D63), 웹 UI 배포는 후속. 근거: [[decision-log]] D59.

## 구성 요소

| 파일 | 역할 |
|---|---|
| `core/Dockerfile` | 멀티스테이지 — build(JDK 21, `./gradlew bootJar -x test`) → runtime(JRE 21, non-root, curl 헬스체크). |
| `docker-compose.yml` | `db`(pgvector, healthcheck) + `core`(build, `depends_on: db healthy`, env 오버라이드, healthcheck). `mcp`는 스텁이라 주석. |
| `docker-compose.observability.yml` | 오버레이 — prometheus·tempo·grafana·postgres-exporter. |
| `db/init.sql` | `CREATE EXTENSION vector`만(보장용). `vector_store`=PgVectorStore, `law_versions`=Flyway 소유. |
| `.github/workflows/ci-core.yml` · `ci-web.yml` | 백엔드 — `core/**`·`api/**` 변경 시 `./gradlew test`(+이미지 빌드 검증). 웹 — `web/**`·`api/**` 변경 시 타입 동기·린트·단위·E2E. 경로 필터로 바뀐 쪽만 돈다. |

## 실행

```bash
# 1) 자격증명 준비 — 레포 루트 .env (gitignore, 단일 소스 D39)
cp .env.example .env    # LAW_OC·OPENAI_API_KEY·ANTHROPIC_API_KEY 채우기

# 2) 앱 + DB
docker compose up -d db core
curl -fsS localhost:8080/actuator/health          # {"status":"UP"}
curl -X POST localhost:8080/api/v1/analyses \
  -H 'Content-Type: application/json' -d '{"query":"주택법 뭐가 바뀌어?"}'

# 3) 관측 포함 전체 스택
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
#   Grafana :3001 (anon admin) · Prometheus :9090 · Tempo :3200
```

## 로컬 데모 시드 (선택)

정본이 없으면 분석은 `unmet`(정본 미적재)이다. 번들 주택법을 적재해 실제 answer를 보려면 **`seed` 프로파일**로 한 번 기동한다(임베딩 프리·운영 무영향, `com.lia.core.dev.SeedRunner`):

```bash
# compose core 서비스에 SPRING_PROFILES_ACTIVE=seed 를 준 채 1회 기동 → law_versions 적재
# 이후 explicit lawRef 로 질의:
curl -X POST localhost:8080/api/v1/analyses -H "Content-Type: application/json" \
  -d "{\"query\":\"주택법 뭐가 바뀌어?\",\"lawRef\":{\"lawId\":\"001809\",\"effectiveDate\":\"2026-08-04\"}}"
```
실제 API로 하는 적재는 아래 **적재 배치**를 쓴다.

## 적재 배치 (D62)

법령 수집·RAG 색인은 같은 이미지를 **`batch` 프로파일로 띄워 한 번 돌고 종료**하는 잡이다([[IngestJob]]). 웹 서버 없이 실행되고, 온라인 `core`와 자원이 분리된다. compose에는 `ingest` 서비스(`profiles: [batch]` — 평소 `up`에는 안 뜸)로 둔다.

```bash
# 수동 실행 — 명령은 run · run --full · run --dry-run · sync <lawId> <efYd> [mst] · index · reindex-all · requeue-dead
docker compose run --rm ingest run

# 정기 실행에서 임베딩(색인)만 끄기 — 수집·시행 전환·철회는 계속, INDEX 는 큐에 쌓임
#   compose ingest 서비스 environment 에 LIA_BATCH_INDEX_ENABLED: "false" (cron 줄은 그대로)
# 밀린 색인을 원하는 때 처리 — 설정과 무관하게 동작
docker compose run --rm ingest index
```

정기 실행은 **외부 스케줄러**가 띄운다(로컬은 호스트 cron, 클라우드는 ECS 예약 작업 등):

```cron
# 매일 04:00 KST — 증분 적재  /  매주 일요일 05:00 — 전체 재검증(기준선 갱신)
0 4 * * *  cd /opt/lia && docker compose run --rm ingest run
0 5 * * 0  cd /opt/lia && docker compose run --rm ingest run --full
```

- 종료 코드 0=성공, 1=실행 실패, 2=부분 실패(작업 실패·격리, 목록 미완주, 종료 신호로 중단) — 스케줄러 알림에 쓴다. 판정 기준은 [[IngestJob]] §실행 모델.
- 강제 종료(SIGKILL·OOM)된 실행은 다음 실행이 시작할 때 `FAILED`로 닫는다.
- 이미 다른 실행이 돌고 있으면 잠금을 못 잡고 바로 종료한다(코드 0).
- 실행 결과는 `ingest_runs` 테이블이 기준 출처다(1회성 잡이라 Prometheus가 긁지 못함 — Grafana는 Postgres를 직접 조회). 트레이스는 OTLP push라 Tempo에 그대로 남는다.
- 비밀값은 `core`와 같은 `.env`(D39) — `LAW_OC`·`OPENAI_API_KEY` 필요.

## 설정 — env-var 오버라이드 (프로파일 최소화)

`application.yml`이 이미 `${VAR}` 주입 지점을 갖고 있어, 컨테이너 실행은 **compose `environment`로 오버라이드**한다(새 프로파일 파일 불필요, Spring relaxed binding):

| 변수 | 값(compose) | 이유 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/lia` | 서비스명으로 DB 접근 |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | `http://tempo:4318/v1/traces` | in-network 트레이싱(기본 `localhost`는 컨테이너에서 무의미) |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.1` | prod 예시(dev 1.0) |

- **비밀값**: 현재 `.env`(env_file)가 단일 소스. **클라우드 배포 시 시크릿 매니저**로 대체(후속).
- **Flyway**: 기동 시 `law_versions` 마이그레이션(DB 필요). Hikari `initialization-fail-timeout:-1`이라 DB 늦게 떠도 컨텍스트는 부팅.
  - ⚠️ **Boot 4.0 함정**: 오토컨피그가 모듈로 분리돼 `org.flywaydb:flyway-core`만으론 기동 시 migrate가 **안 걸린다** — `org.springframework.boot:spring-boot-flyway`(오토컨피그 모듈)가 있어야 `spring.flyway.enabled`가 실제로 동작한다. (통합테스트는 `Flyway.configure().migrate()` 수동 실행이라 이 누락을 못 잡았고, `docker compose up` 실측으로 발견.)

## 헬스체크
- `db`: `pg_isready`. `core`: actuator `/actuator/health`(compose·Dockerfile 양쪽). `core`는 `db` healthy 후 기동.

## CI
- 경로 필터로 나눈 두 워크플로 — 바뀐 쪽만 돈다. 계약 파일 `api/**`가 바뀌면 둘 다 돈다(백엔드 스냅샷 테스트 + 웹 타입 동기). 문서만 바뀐 PR은 CI 없음.
- `.github/workflows/ci-core.yml` — `ubuntu-latest`(Docker 내장) → 단위 + **Testcontainers 통합** 테스트. **라이브 스모크는 OC/`LIA_*_LIVE` 미설정으로 skip**(결정론 오프라인). 이어 `docker build ./core`로 이미지 검증. 레지스트리 push/CD는 후속.

## 후속 (out of scope)
- 클라우드 운영 — [[cloud-infra]](D63): 단일 EC2 + RDS pgvector + Caddy + SSM Parameter Store, GHCR 이미지 수동 배포. 그 너머(SSM 방식 CD·IaC·ALB·ECS)는 그 문서의 범위 밖 절.
- mcp 컨테이너화 · 웹 UI(#14) 배포 · Loki 로그 적재([[observability]] 계획) · 이미지 레지스트리 push.
