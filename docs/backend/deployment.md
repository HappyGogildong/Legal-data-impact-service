---
title: 배포 — 컨테이너화 + docker compose 자립 실행
status: Draft
version: 0.1
date: 2026-09-08
tags: [backend, deployment, docker, ops, ci]
related: ["backend/observability.md", "adr/decision-log.md", "components/web/AnalysisApi.md"]
---

# 배포 (self-host / staging)

> `docker compose up` 한 번으로 **db + core(+관측)** 가 뜨는 자립 실행 스택. 클라우드 매니지드(ECS/RDS/시크릿매니저)·웹 UI 배포는 후속(D59·D34). 근거: [[decision-log]] D59.

## 구성 요소

| 파일 | 역할 |
|---|---|
| `core/Dockerfile` | 멀티스테이지 — build(JDK 21, `./gradlew bootJar -x test`) → runtime(JRE 21, non-root, curl 헬스체크). |
| `docker-compose.yml` | `db`(pgvector, healthcheck) + `core`(build, `depends_on: db healthy`, env 오버라이드, healthcheck). `mcp`는 스텁이라 주석. |
| `docker-compose.observability.yml` | 오버레이 — prometheus·tempo·grafana·postgres-exporter. |
| `db/init.sql` | `CREATE EXTENSION vector`만(보장용). `vector_store`=PgVectorStore, `law_versions`=Flyway 소유. |
| `.github/workflows/ci.yml` | push/PR에 `./gradlew test`(+이미지 빌드 검증). |

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

## 설정 — env-var 오버라이드 (프로파일 최소화)

`application.yml`이 이미 `${VAR}` 주입 지점을 갖고 있어, 컨테이너 실행은 **compose `environment`로 오버라이드**한다(새 프로파일 파일 불필요, Spring relaxed binding):

| 변수 | 값(compose) | 이유 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/lia` | 서비스명으로 DB 접근 |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | `http://tempo:4318/v1/traces` | in-network 트레이싱(기본 `localhost`는 컨테이너에서 무의미) |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.1` | prod 예시(dev 1.0) |

- **비밀값**: 현재 `.env`(env_file)가 단일 소스. **클라우드 배포 시 시크릿 매니저**로 대체(후속).
- **Flyway**: 기동 시 `law_versions` 마이그레이션(DB 필요). Hikari `initialization-fail-timeout:-1`이라 DB 늦게 떠도 컨텍스트는 부팅.

## 헬스체크
- `db`: `pg_isready`. `core`: actuator `/actuator/health`(compose·Dockerfile 양쪽). `core`는 `db` healthy 후 기동.

## CI
- `.github/workflows/ci.yml` — `ubuntu-latest`(Docker 내장) → 단위 + **Testcontainers 통합** 테스트. **라이브 스모크는 OC/`LIA_*_LIVE` 미설정으로 skip**(결정론 오프라인). 이어 `docker build ./core`로 이미지 검증. 레지스트리 push/CD는 후속.

## 후속 (out of scope)
- 클라우드 매니지드(ECS/Fargate · RDS pgvector · 시크릿 매니저 · IaC · CD) — D34 RDS eventual.
- mcp 컨테이너화 · 웹 UI(#14) 배포 · Loki 로그 적재([[observability]] 계획) · 이미지 레지스트리 push.
