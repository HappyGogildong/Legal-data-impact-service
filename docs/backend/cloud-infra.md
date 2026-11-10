---
title: 클라우드 인프라 — 단일 EC2 + RDS (저비용 운영)
status: Draft
version: 0.2
date: 2026-11-02
tags: [backend, infra, aws, deployment, ops, observability]
related: ["backend/deployment.md", "backend/observability.md", "components/auth/Auth.md", "frontend/frontend-stack.md", "components/batch/IngestJob.md", "adr/decision-log.md", "adr/ADR-001-knowledge-store-sizing.md"]
---

# 클라우드 인프라 (AWS · 저비용 운영)

> 로컬 compose 자립 스택(D59)을 **개인 AWS 계정**에 상시 운영 형태로 올리는 계획. 목표는 **학습용이되 저비용·최소 구성** — Terraform·ALB·EventBridge·ECS 같은 실무 스택은 들이지 않고, 매니지드는 꼭 필요한 곳(DB)에만 쓴다. 근거: [[decision-log]] D63. 관련: [[deployment]] · [[observability]] · [[IngestJob]].

## 1. 전체 구성

```
인터넷 ──443/80──▶ EC2 t4g.medium (arm64, 4GB, Elastic IP)   ── 서울 리전 ap-northeast-2
                    └ docker compose (prod)
                       caddy   ── TLS(Let's Encrypt) → core:8080
                       core    ── API (GHCR 이미지)
                       ingest  ── 배치(같은 이미지, batch 프로파일 · cron이 띄움)
                       prometheus · tempo · grafana(127.0.0.1에만 바인딩)
                         │ 5432 (보안그룹: EC2→RDS만)
                    RDS PostgreSQL 16 db.t4g.micro + pgvector (비공개, 자동 백업 7일)
외부 API: 국가법령정보 · OpenAI 임베딩 · Anthropic
AWS 부가: SSM(접속·Parameter Store) · IAM 역할 1개 · GHCR(GitHub)
```

| 결정 | 선택 | 이유 |
|---|---|---|
| 계정 | **개인 AWS 계정**(신규 가입 무료 혜택 활용) | 학교 혜택(AWS Academy Learner Lab)은 리전 제한·세션 약 4시간 종료·크레딧 소진 시 중단이라 상시 운영·정기 배치가 불가. Learner Lab은 연습용으로만 |
| 컴퓨팅 | **EC2 한 대 + docker compose** | 로컬 compose를 그대로 재사용. ECS·ALB는 비용·구성 대비 이득 없음 |
| DB | **RDS PostgreSQL 16(db.t4g.micro) + pgvector** | 백업·패치·복구를 AWS가 맡는다(D34 유지). 매니지드를 쓰는 유일한 곳 |
| 관측 | **같은 EC2에 Prometheus·Tempo·Grafana** | Grafana가 VPC 안이라 RDS의 실행 기록(`ingest_runs`)을 바로 조회 — 배치 패널·경보의 전제 |
| 리전 | **서울(ap-northeast-2)** | 국가법령정보 등 국내 API와 사용자 지연. us-east-1보다 약 20% 비쌈 |
| 주소 | **무료 주소로 시작**(D78) — 웹 `https://<프로젝트>.vercel.app`, API `https://api.<EIP 점→하이픈>.sslip.io` | 도메인 구매·DNS 설정 없이 시작. 도메인을 사면 `prod.env` 값과 OAuth 콘솔 주소만 바꾼다(아래 §1.1) |

- **arm64(t4g)** — 같은 사양에서 x86보다 약 20% 저렴. 이미지는 CI에서 **빌드 단계만 네이티브(amd64)로 Gradle 실행, 런타임 단계만 arm64**(`FROM --platform=$BUILDPLATFORM`) — jar는 아키텍처 무관이라 빌드가 느려지지 않는다.
- **메모리 예산(4GB)** — OS 0.4 · core JVM 0.8(`-Xmx512m`) · 배치 실행 시 0.6 · Prometheus 0.3 · Tempo 0.3 · Grafana 0.2 · Caddy 0.05 ≈ 2.7GB.

### 1.1 주소 (D78)

| 용도 | 첫 배포 | 도메인을 산 뒤 |
|---|---|---|
| 웹(Vercel) | `https://<프로젝트>.vercel.app` | `https://app.<도메인>` |
| API(EC2·Caddy) | `https://api.<EIP 점→하이픈>.sslip.io` — 예: EIP `13.125.1.1` → `api.13-125-1-1.sslip.io` | `https://api.<도메인>` |

- sslip.io 는 이름 안의 IP를 그대로 돌려주는 공개 DNS 서비스다. A 레코드 설정이 필요 없고, Caddy 가 그 이름으로 Let's Encrypt 인증서를 받는다.
- 값이 들어가는 곳: `prod.env` 의 `LIA_DOMAIN`(API 이름) · `FRONTEND_URL` · `OAUTH2_REDIRECT_BASE_URL`(둘 다 웹 주소) · Vercel 환경변수 `LIA_API_ORIGIN`(`https://` + API 이름) · Google OAuth 콘솔의 리다이렉트 URI(`<웹 주소>/login/oauth2/code/google`).
- **위험과 탈출로** — sslip.io 는 제3자 서비스라 장애·인증서 발급 한도에 걸리면 API 주소가 막힌다. 그때는 도메인을 사서 위 표 오른쪽 값으로 바꾼다(코드 변경 없음).

### 비용 (서울 리전 온디맨드 대략치, 2026-10 기준 추정)

| 항목 | 월 |
|---|---|
| EC2 t4g.medium | 약 $30 |
| RDS db.t4g.micro + 스토리지 20GB | 약 $18 + $3 |
| EBS gp3 30GB | 약 $3 |
| 공인 IPv4(Elastic IP) | 약 $4 |
| **합계** | **약 $55~60** (+ 도메인 연 $10~15, 무료 서브도메인이면 $0) |

- 신규 계정 무료 혜택(크레딧)으로 초기 몇 달 상쇄 가능 — **실제 조건은 가입 시 확인**.
- **예산 경보** — AWS Budgets로 월 $60·$80에서 이메일 알림(무료).
- 비용 레버(필요 시): 관측 스택을 빼고 2GB 인스턴스(−$15) · us-east-1 이전(−20%, 국내 API 지연 증가).

## 2. 네트워크·접근·비밀값

**네트워크 (기본 VPC)**

| 보안 그룹 | 인바운드 | 비고 |
|---|---|---|
| `lia-ec2` | 80·443 (0.0.0.0/0) | **22(SSH) 미개방** — 접속은 SSM |
| `lia-rds` | 5432 ← `lia-ec2`만 | RDS 퍼블릭 접근 끔 — 인터넷에서 DB 직접 접근 불가 |

**서버 접근 — SSM Session Manager (키 페어 없음)**
- 셸: `aws ssm start-session --target <instance-id>`.
- Grafana: SSM 포트 포워딩(`AWS-StartPortForwardingSession`)으로 로컬 `localhost:3001`. Grafana는 EC2의 `127.0.0.1`에만 바인딩, 익명 Admin 끔.
- DB 관리(최초 설정·수동 점검): `AWS-StartPortForwardingSessionToRemoteHost`로 RDS 5432를 로컬에 열고 `psql`.
- 계정 보안 기본: 루트 계정은 MFA·미사용, 콘솔 작업은 관리자 IAM 사용자.

**IAM — EC2 인스턴스 역할 하나 (`lia-ec2-role`)**
- `AmazonSSMManagedInstanceCore`(Session Manager).
- `ssm:GetParametersByPath` — `arn:aws:ssm:ap-northeast-2:<account>:parameter/lia/prod/*`만. 암호화는 기본 키(`aws/ssm`), 별도 KMS 키 없음.
- 이후 SSM 방식 CD도 이 역할을 재사용한다.

**비밀값 — SSM Parameter Store (SecureString, 기본 등급 무료)**
- 경로 `/lia/prod/` — `LAW_OC` · `OPENAI_API_KEY` · `ANTHROPIC_API_KEY` · `SPRING_DATASOURCE_PASSWORD`(앱 계정) · OAuth 클라이언트 값(`GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET` — 카카오·네이버는 키가 생기면 같은 규칙으로 추가, 없으면 그 provider 는 등록되지 않는다 D78) · `GF_SECURITY_ADMIN_PASSWORD` · `GRAFANA_DB_PASSWORD`(읽기 계정) · `DISCORD_WEBHOOK_URL` · `DB_MASTER_PASSWORD`(최초 설정용). 파라미터 이름의 마지막 부분이 곧 환경변수 이름이다.
- 배포 래퍼 `ops/deploy/lia`가 실행할 때마다 `get-parameters-by-path --with-decryption`으로 읽어 **환경변수로 export한 뒤** `docker compose`를 실행 — **디스크에 비밀값 파일이 남지 않는다.** 정기 배치(cron)도 같은 래퍼를 거친다.
- 비밀이 아닌 설정(도메인·`FRONTEND_URL`·`OAUTH2_REDIRECT_BASE_URL`·`SPRINGDOC_API_DOCS_ENABLED=false`·샘플링 비율·JVM 힙 등)은 레포의 `ops/deploy/prod.env`.
- 로컬 개발은 그대로 레포 루트 `.env` — D39는 "로컬=`.env`, 운영=Parameter Store"로 보완된다.

**DB 계정 — 최소 권한**
- **관리자(master)** — 최초 설정에만 사용(비밀번호는 Parameter Store; Secrets Manager 연동은 비밀값당 월 $0.4라 쓰지 않음):
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;                 -- 확장 생성은 rds_superuser 필요
  CREATE ROLE lia_app LOGIN PASSWORD '<SSM: SPRING_DATASOURCE_PASSWORD>';
  ALTER DATABASE lia OWNER TO lia_app;
  ALTER SCHEMA public OWNER TO lia_app;                  -- Flyway·PgVectorStore 가 lia_app 으로 테이블 생성
  CREATE ROLE grafana_reader LOGIN PASSWORD '<SSM: GRAFANA_DB_PASSWORD>';
  GRANT CONNECT ON DATABASE lia TO grafana_reader;
  GRANT USAGE ON SCHEMA public TO grafana_reader;
  ```
- **`grafana_reader`에는 `ingest_runs`·`ingest_tasks`·`ingest_task_attempts`와 호출 기록 `llm_calls`·`llm_prices`·`llm_call_costs`, 질의 이벤트 `query_events`(D83)의 SELECT만** — 계정·프로필 테이블(알림 이메일 포함)은 읽지 못한다. 관측 경로가 최소수집(D41)의 뒷문이 되지 않게(호출 기록·질의 이벤트에는 사용자 식별값·내용이 없다). 테이블이 생긴 뒤(첫 배포 후) 런북 단계로:
  ```sql
  GRANT SELECT ON ingest_runs, ingest_tasks, ingest_task_attempts, llm_calls, llm_prices, llm_call_costs, query_events TO grafana_reader;
  ```
- 앱의 `CREATE EXTENSION IF NOT EXISTS vector`(PgVectorStore `initialize-schema`)는 확장이 이미 있으면 통과한다.

**프록시 뒤 HTTPS 인식** — `server.forward-headers-strategy=native`. 앱이 Caddy의 `X-Forwarded-Proto`를 믿어 `Secure` 쿠키·OAuth 리다이렉트 URI가 https로 만들어진다(Tomcat은 기본적으로 사설망 대역 프록시만 신뢰 — 도커 네트워크 172.x 포함).

## 3. 배포·배치 실행

**이미지 — CI가 GHCR에 push (배포는 수동)**
- main 병합 시 백엔드 CI(`.github/workflows/ci-core.yml`)가 테스트 후 `docker buildx`로 `linux/arm64`·`linux/amd64` 이미지를 빌드해 `ghcr.io/happygogildong/lia-core`에 push — 태그 `:<커밋 SHA>`·`:main`.
- Dockerfile: 빌드 단계를 `FROM --platform=$BUILDPLATFORM`으로 — Gradle은 네이티브로, 런타임 단계만 대상 아키텍처.
- 이미지에 비밀값이 없으므로 GHCR 패키지를 **공개**로 둬 EC2가 인증 없이 pull. 비공개여야 하면 읽기 전용 토큰을 Parameter Store에.

**운영 compose — `ops/deploy/docker-compose.prod.yml`**

| 서비스 | 요점 |
|---|---|
| `caddy` | 80·443 공개, Let's Encrypt 인증서는 볼륨에 보존. **`/actuator/*`는 외부 차단, `/actuator/health`만 허용** — 앱 보안 설정이 actuator 전체를 열어 두어 막지 않으면 Prometheus 지표가 인터넷에 노출된다 |
| `core` | GHCR 이미지(`${LIA_IMAGE_TAG}`) · RDS 주소·`lia_app` · `JAVA_TOOL_OPTIONS=-Xmx512m` · `SERVER_FORWARD_HEADERS_STRATEGY=native` · `restart: unless-stopped` |
| `ingest` | 같은 이미지, `batch` 프로파일, `profiles: [batch]`(로컬과 같음) |
| `prometheus`·`tempo`·`grafana` | 로컬 관측 설정 재사용. Grafana는 `127.0.0.1:3001`만, 익명 접근 끔. DB 컨테이너·postgres-exporter 없음(RDS 기본 지표는 CloudWatch) |

**EC2 준비 (최초 1회) — `ops/deploy/bootstrap.sh`**
- Amazon Linux 2023 arm64 AMI(SSM 에이전트·AWS CLI 기본 포함).
- docker·compose 플러그인·git·cronie 설치, 시간대 `Asia/Seoul`, 스왑 2GB(메모리 안전판), `/opt/lia`에 레포 clone.

**배포 (수동, SSM 접속 후)**
```bash
cd /opt/lia && git pull            # 운영 compose·Caddyfile·대시보드 등 설정 갱신
ops/deploy/lia up <커밋 SHA>        # Parameter Store → 환경변수 → pull → up -d
```
- 롤백: `ops/deploy/lia up <이전 SHA>`. **Flyway는 앞으로만** — 스키마가 바뀐 배포는 DB를 되돌리지 못하므로 그때는 RDS 스냅샷 복구.
- 배치 시간대(04:00~05:00)에는 배포하지 않는다 — 실행 잠금으로 겹치진 않지만 배치 도중 마이그레이션 변경을 피한다.
- **다음 단계 — SSM 방식 CD**: GitHub OIDC → IAM 역할 → `ssm send-command`로 위 두 줄을 Actions가 실행. 22번 포트를 열지 않는다.

**정기 배치 — 호스트 cron (D62)**
```cron
0 4 * * *  /opt/lia/ops/deploy/lia ingest run        >> /var/log/lia/ingest.log 2>&1
0 5 * * 0  /opt/lia/ops/deploy/lia ingest run --full >> /var/log/lia/ingest.log 2>&1
```
- 래퍼가 Parameter Store를 읽고 `docker compose run --rm ingest …` 실행. 로그 파일은 logrotate 주 단위 정리 — 실행 결과의 기준은 `ingest_runs`.

**소셜 로그인 (#36, D78)** — 웹은 Vercel 이 API·로그인 경로를 rewrites로 이 EC2 에 전달한다(D64, [[frontend-stack]] §2). provider 콘솔과 앱의 OAuth `redirect-uri`는 `<웹 주소>/login/oauth2/code/{google|kakao|naver}`, `FRONTEND_URL` = 웹 주소. 운영은 `oauth` 프로파일을 켜고(`SPRING_PROFILES_ACTIVE=oauth`), **`client-id` 가 있는 provider 만 등록**된다 — 첫 배포는 구글만. 웹 로그인 화면은 `GET /api/v1/auth/providers` 의 목록만 버튼으로 그린다.

**운영 첫 설정(`prod.env`)** — Layer A(요약·변경점) 배치 **꺼짐**(`LIA_BATCH_SUMMARY_ENABLED=false`, 유료 Opus — 백필 뒤 `summarize --dry-run` 으로 규모를 보고 따로 결정, D67·D78·D80. 돌리기 전까지 분석 화면의 요약·변경점은 준비 중으로 나온다), 익명 분석 하루 상한 1,000(`LIA_ANALYSIS_ANONYMOUS_DAILY_LIMIT`, 잠정, D81), 색인 켜짐(`LIA_BATCH_INDEX_ENABLED=true`, 임베딩 비용은 백필 전체가 1달러 미만 추정).

## 4. 관측·배치 패널·경보

**데이터 소스 `lia-postgres`** — 프로비저닝 파일 하나에 환경변수(`$LIA_DB_HOST`·`$LIA_DB_USER`·`$GRAFANA_DB_PASSWORD`)로 두고 로컬·운영이 값만 달리 준다.
- 로컬: `db:5432` / `lia` (관측 오버레이에서도 같은 패널 동작).
- 운영: RDS 엔드포인트 / `grafana_reader`(§2 — `ingest_*` 세 테이블, `llm_*` 호출 기록, `query_events` SELECT만).

**대시보드 `LIA · Batch`** (`ops/observability/grafana/provisioning/dashboards/lia-batch.json`, Overview와 별도)

| 패널 | 내용 | 출처 |
|---|---|---|
| 마지막 실행 | 명령·결과·시작/종료·소요 시간 | `ingest_runs` 최신 행 |
| 마지막 성공 이후 경과 | 시간 단위, 26시간 기준선 | `max(finished_at) WHERE outcome='SUCCESS'` |
| 최근 실행 20건 | 결과·`list_complete`·`done`·`failed`·`dead`·`deferred.INDEX`·`malformedRows`·`duplicateKeys` | `ingest_runs.counts` |
| 작업 상태 | READY·RUNNING·DEAD 개수 | `ingest_tasks` |
| DEAD 목록 | 종류·법령·시행일·시도 횟수·마지막 오류·시각 | `ingest_tasks WHERE status='DEAD'` |
| 색인 지연 | READY인 INDEX 수 · 가장 오래된 READY의 나이 | `ingest_tasks` |
| 일별 추이 | 결과별 실행 수·처리 건수 | `ingest_runs` |

**경보 3개** (Grafana 관리, `ops/observability/grafana/alerting/lia-batch-alerts.yaml`)

| 경보 | 조건 | 의미 |
|---|---|---|
| 격리 작업 발생 | `DEAD ≥ 1` | 사람이 원인을 보고 `requeue-dead` 여부 판단 |
| 완료된 정기 실행 없음 | 끝까지 돈 `run`(SUCCESS·PARTIAL) 후 **26시간** 초과, 한 번도 없으면 발동 | 배치 실패·cron 미동작·잠금이 쥔 채 멈춤을 모두 잡는다. 격리 1건으로 PARTIAL 이 이어져도 이 경보까지 겹쳐 울리지 않게 PARTIAL 도 완료로 센다 |
| 목록 미완주 연속 | 최근 `run` 2회 연속 `list_complete=false` | 국가법령정보 API 이상, 사라짐 판정이 계속 미뤄짐 |

- 알림 채널은 **Discord 웹훅** 하나(`$DISCORD_WEBHOOK_URL`). 경보 파일은 `provisioning` 폴더 **밖**에 두고 **운영 compose에서만** 마운트한다 — 로컬은 `provisioning`을 통째로 마운트하는데 웹훅이 없어 계속 실패로 뜨기 때문. 운영 compose는 `provisioning`의 하위 폴더를 따로 마운트한다(통째로 읽기 전용 마운트하면 그 안에 `alerting` 마운트 지점을 만들지 못해 Grafana 컨테이너가 뜨지 않는다).
- **한계** — Grafana 자체가 죽으면 경보도 울리지 않는다(같은 EC2에 둔 대가).

**운영 관측 설정**
- 운영용 `prometheus.yml`(수집 대상 `core:8080` — 로컬은 `host.docker.internal`).
- 30GB 디스크 안에서: Prometheus 보존 15일, Tempo 72시간.
- CloudWatch는 EC2·RDS 기본 지표만, 추가 경보 없음. 비용 경보는 §1의 AWS Budgets.

## 5. 산출물·런북·검증

### 레포 산출물

| 영역 | 파일 |
|---|---|
| 이미지·CI | `core/Dockerfile`(`--platform=$BUILDPLATFORM`) · `.github/workflows/ci-core.yml`(main 병합 시 buildx arm64·amd64 → GHCR push, PR은 빌드만) |
| 운영 배포 | `ops/deploy/`: `docker-compose.prod.yml` · `Caddyfile` · `prod.env` · `lia`(래퍼 — `up <tag>`·`ingest <args>`·`ps`·`logs <서비스>`, 현재 태그는 `.image-tag`에 기록·커밋 안 함) · `bootstrap.sh` · `prometheus.prod.yml` · `tempo.prod.yaml` · `crontab` · `logrotate.conf` · `README.md` |
| 관측 | 데이터 소스 `lia-postgres` · `lia-batch.json` · `ops/observability/grafana/alerting/`(Discord 연락처·정책·규칙 3개, 운영에서만 마운트) · 로컬 관측 compose의 Grafana DB 환경변수 |
| 소셜 로그인 | `OAuthClientRegistrationConfig`(`lia.oauth.*` → client-id 가 있는 provider 만) · `GET /api/v1/auth/providers` · 웹 S07 — D78 |
| 문서 | 이 문서 · [[deployment]] 후속 절 연결 · [[observability]] 배치 대시보드 · D63 · status · mkdocs 목차 |

- **인프라 때문에 앱 비즈니스 코드를 바꾸지 않는다** — 프록시 헤더는 운영 compose 환경변수(`SERVER_FORWARD_HEADERS_STRATEGY=native`), actuator 차단은 Caddy. 앱 변경은 소셜 로그인 등록(D78)뿐.

### AWS 구축 런북 (수동, 1회)

AWS 리소스는 계정 소유자가 직접 만든다(자격증명·비용). 운영 투입(#44)의 전제.

1. **계정 보안** — 루트 MFA, 관리자 IAM 사용자 생성 후 루트 미사용. 리전 `ap-northeast-2`.
2. **예산 경보** — AWS Budgets: 월 $60·$80 이메일.
   - 2-1. **GHCR 패키지 공개** — 이 작업이 main 에 병합되면 CI 가 `lia-core` 패키지를 만든다. 기본이 비공개이므로 GitHub 패키지 설정에서 Public 으로 바꾼다(이미지에 비밀값 없음, EC2 가 인증 없이 pull).
3. **IAM 역할** `lia-ec2-role` — 신뢰 주체 EC2, `AmazonSSMManagedInstanceCore` + `/lia/prod/*` `ssm:GetParametersByPath` 인라인 정책. 인스턴스 프로파일로 연결.
4. **보안 그룹** — `lia-ec2`(80·443), `lia-rds`(5432 ← `lia-ec2`).
5. **RDS** — PostgreSQL 16, `db.t4g.micro`, gp3 20GB, 퍼블릭 접근 끔, `lia-rds`, 초기 DB `lia`, 자동 백업 7일, 관리자 비밀번호는 직접 지정.
6. **Parameter Store** — `/lia/prod/` 아래 §2의 값들을 SecureString으로 등록.
7. **EC2** — Amazon Linux 2023 arm64, `t4g.medium`, gp3 30GB, `lia-ec2`, `lia-ec2-role`, 키 페어 없음. 접속(SSM) 후 `ops/deploy/bootstrap.sh`.
8. **Elastic IP + 주소** — Elastic IP 를 만들어 EC2 에 연결하고, §1.1 규칙으로 API 이름(`api.<EIP 점→하이픈>.sslip.io`)을 정한다. 웹 주소는 9-1의 Vercel 프로젝트 주소. `prod.env` 의 `LIA_DOMAIN`·`FRONTEND_URL`·`OAUTH2_REDIRECT_BASE_URL` 을 채워 커밋한다(비밀값 아님). 도메인을 샀다면 A 레코드를 EIP 로 두고 그 이름을 쓴다.
9. **최초 SQL** — SSM 원격 포트 포워딩으로 RDS 접속, §2의 확장·계정 SQL 실행(관리자 계정).
   - 9-1. **Vercel·Google·Discord** — Vercel 에 `web/` 프로젝트를 만들고(루트 디렉터리 `web`) 환경변수 `LIA_API_ORIGIN` = `https://<API 이름>` 을 넣는다. Google Cloud 콘솔 OAuth 클라이언트에 리다이렉트 URI `<웹 주소>/login/oauth2/code/google` 을 등록하고 ID·비밀값을 Parameter Store 에. Discord 채널 웹훅을 만들어 `DISCORD_WEBHOOK_URL` 로.
10. **첫 배포** — `ops/deploy/lia up <커밋 SHA>` → Flyway·PgVectorStore가 스키마 생성.
11. **읽기 권한** — `GRANT SELECT ON ingest_runs, ingest_tasks, ingest_task_attempts, llm_calls, llm_prices, llm_call_costs, query_events TO grafana_reader;` 그리고 `llm_prices` 에 단가를 넣는다([[cost-policy]] "단가 넣는 법").
12. **cron** — `ops/deploy/crontab` 설치, logrotate 설정.
13. **확인**
    - `https://<도메인>/actuator/health` → `{"status":"UP"}`, `https://<도메인>/actuator/prometheus` → 차단.
    - SSM 포트 포워딩으로 Grafana 접속 → `LIA · Overview`·`LIA · Batch`·`LIA · Token & Cost`·`LIA · Requests` 표시.
    - 경보 연락처 시험 발송 → Discord 수신.
    - `ops/deploy/lia ingest run --dry-run` → `ingest_runs`에 기록, 배치 패널 반영.
    - `https://<API 이름>/api/v1/auth/providers` → `{"providers":["google"]}`, 웹에서 구글 로그인 왕복 → 동의 화면(S08).
14. **백필** — `ops/deploy/lia ingest run`(시행예정 전체 수집·정규화·색인, D75·D76·D77 반영). 소요 시간·임베딩 비용·`DEAD` 건수를 #44 에 기록. 쉬운 요약은 `ops/deploy/lia ingest summarize --dry-run` 결과를 보고 따로 결정.

15. **평가 기준선**(D83, 유료: 정답 세트 문항마다 Haiku 1회 + 주제 질문마다 임베딩 1회) — 백필로 전체 색인이 끝난 뒤 `ops/deploy/lia eval 2>&1 | tee eval.log`. 보고서는 `grep '^{"config"' eval.log` 한 줄이다. 처음엔 기준선이 없어 비교 없이 통과(0)한다. 이 줄을 `core/src/main/resources/eval/offline-baseline.json` 으로 PR 에 올려 커밋하면 다음 배포부터 회귀 비교(3 = 회귀)가 켜진다. 색인 법령 수·문항 수가 크게 바뀌면 다시 잰다([[rag-evaluation-framework]] §5.2).

### 검증 (구현 단계)
- **로컬 관측 스택** — Grafana API로 `lia-postgres` 연결 상태, `LIA · Batch` 로드, 패널 쿼리가 로컬 DB의 실행 기록(샘플 데이터)에 결과를 내는지.
- **경보 프로비저닝** — 가짜 웹훅으로 Grafana를 띄워 규칙 3개 로드 확인.
- **형식** — 운영 compose `docker compose config`(더미 환경변수) · `caddy validate` · 스크립트 `bash -n`.
- **CI** — 병합 후 GHCR에 arm64·amd64 매니페스트 확인.

### 범위 밖 (필요해지면 다음 단계)
- **SSM 방식 CD**(다음 단계로 명시) · Terraform/IaC · ALB · ECS · EventBridge · 오토스케일링·멀티 AZ · 웹 UI 호스팅(→ Vercel, D64 [[frontend-stack]]) · 로그 수집(Loki) · WAF.
