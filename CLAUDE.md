# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

**LIA (입법 영향 분석기)** — 공포됐지만 아직 시행 전인 법령이 시민 개인에게 미칠 영향과 대응 행동을 알려주는 웹앱. 문서·주석·커밋 메시지는 한국어.

처음 온보딩할 때는 `docs/onboarding.md` → `docs/ARCHITECTURE.md`(색인, 최신 스냅샷 v1.0) → `docs/adr/decision-log.md`(결정 D01~) 순서로 읽는다.

## 명령어

```bash
# Java 테스트 (단위 + 자격증명 바인딩 + 실 API 스모크; 키 없으면 스모크는 자동 스킵)
cd core && ./gradlew test

# 웹 (Next.js) — 단위(vitest)·E2E(Playwright, 모의 API)
cd web && pnpm test && pnpm e2e

# 국가법령정보 응답 진단 (시행예정 목록·본문)
./.venv/Scripts/python.exe tools/probe_eflaw.py

# 문서 사이트
./.venv/Scripts/python.exe -m mkdocs serve

# 로컬 DB (pgvector 포함 Postgres + db/init.sql 자동 실행)
docker compose up -d db
```

Windows 콘솔(cp949)에서 한글 출력이 깨지면 `export PYTHONIOENCODING=utf-8`.

## 아키텍처 (2-런타임 — Spring + TS)

| 디렉터리 | 언어 | 책임 |
|---|---|---|
| `core/` | Java Spring (**Boot 4.0 + Spring AI 2.0**) | **운영 전체** — 배치(수집·정규화·diff·색인·요약 선계산) + 질의(번역·해소·발견·분석) + 사용자 기능·REST API |
| `web/` | TypeScript (Next.js 16) | 웹 프론트엔드 — Vercel 배포, API 는 rewrites 로 core 에 프록시(D64) |
| `ops/` | 셸·compose·Grafana | 운영 배포(EC2 compose·`lia` 명령·cron·자동 배포)와 관측 구성(D63·D86) |
| `tools/` | Python | 진단 스크립트(`probe_eflaw.py` 등)·평가 보고서 렌더러(`eval/render_report.py`) — 운영 코드 아님 |
| `mcp/` | TypeScript | PoC 스텁. MCP 어댑터는 내부 전용 — **사용자에게 노출하지 않음** |

Spring 버전 변경점(Boot 3.x→4.0, AI 1.x→2.0)은 `docs/reference/spring-migration.md` 참고 — Jackson 3(`tools.jackson`)·옵션 빌더 강제가 코드에서 주의할 핵심.

데이터 흐름 두 갈래: **(오프라인 배치)** 국가법령정보 → `LawConnector`(ACL) → `Normalizer` → `DiffBuilder` → `LawStore`(정본) · `RAGIndexer` → pgvector 색인 · `SUMMARIZE` 작업이 Layer A 저장본(`law_summaries`)을 만든다(D62·D67). **(온라인 질의)** 웹 → REST → `AnalyzeUseCase` → `QueryPlanner`(번역·해소/발견) → `QueryDispatcher`(차원 핸들러) → Layer A 는 저장본 조회, Layer B 는 `AnalysisEngine`(Claude 추론·인용 검증) → 응답 DTO.

핵심 설계 원칙 (전 코드에 관철):
- **그라운딩**: 모든 분석 주장은 주입된 조문 `source_id`만 인용 가능. 인용 없는 응답은 게이트에서 차단.
- **fail-closed 해소 4상태**: `RESOLVED / AMBIGUOUS / NOT_FOUND_YET / UNVERIFIED`. 신뢰 출처에서 확인 안 되면 분석하지 않는다. 뉴스·사용자 입력 *내용*은 사실이 아니라 식별 단서.
- **2계층 분석**: Layer A(요약·변경점 — 프로필 무관, 배치가 선계산해 저장, 온라인 Opus 호출 0, D67·D80) → Layer B(프로필별 영향·대응안, 요청 시 온라인, D69).
- **RAG는 탐색(Discovery)에만**: 시행예정 변경조문·요약을 단일 `pending` ns에 색인(D55), "어떤 법인지 찾기"에 사용(SourceAnalyzer/LawDiscovery). **분석 경로는 벡터 검색이 아니다** — 대상이 특정된 뒤 정본·baseline을 `(lawId, effectiveDate)`로 LawStore에서 **정확 조회**해 컨텍스트에 통째로 넣는다(전문 임베딩 안 함, AnalysisEngine 정합화 2026-08-27). 색인·검색은 PgVectorStore가 **동일 EmbeddingModel**로 내부 임베딩해 벡터공간 일치를 보장(옛 law ns·공유 Embedder 핫패스 가정은 폐기).
- 추론=Claude Opus 5.5(외부 API, D68), 임베딩=OpenAI `text-embedding-3-small`(dim 1536, D58). 자체 모델 학습·파인튜닝 없음.
- 저장소=단일 Postgres+pgvector(ADR-001). 임베딩 테이블은 파이프라인 소유(`db/init.sql`), 관계형 도메인은 Spring 소유.

## 신뢰 출처 — 이름 혼동 주의

법제처(MOLEG)가 입법예고와 국가법령정보센터를 **둘 다 운영**하므로, 설정 키는 운영기관이 아니라 **내용 기준**이다. MVP 가 쓰는 출처는 `law` 하나(D42) — `assembly`·`moleg` 는 설정만 남은 post-MVP 경로:

| 키 | 출처 | 내용 | 인증 |
|---|---|---|---|
| `assembly` | 열린국회정보 | 의원발의 법안 | ServiceKey. **`AGE`(국회 대수) 파라미터 필수** — 빠지면 ERROR-300 |
| `moleg` | 법제처 정부입법예고 | 정부제출 법안 | **OC** (회원 이메일의 아이디 부분) |
| `law` | 국가법령정보(law.go.kr) | 현행 법령(diff 기준선) | **OC** |

열린국회 API 함정 2가지 (실측 — 의안 경로 재도입 시 참고):
- 오류 응답은 최상위 `{"RESULT": {...}}` 형태(정상 응답의 `head[].RESULT`와 다름) — 삭제된 `AssemblyEnvelope.resultCode`가 둘 다 처리했다.
- (열린국회, post-MVP) `Type=json` 이어도 **`Content-Type: text/html`** 로 응답 → `String`으로 받아 Jackson 3로 직접 파싱. 국가법령정보는 정상 `application/json` 이지만 오류가 200으로 오므로 봉투 검사는 필수(`LawEnvelope.checkError`).

## 패키지 규약 (core — 2026-11-23 개정, D90)

```
com.lia.core
├── domain/            공유 도메인 — 바운디드 컨텍스트별
│   ├── law/           Law · Article · Addendum · ArticleComparison
│   ├── analysis/      ImpactResult · Situation* · StepNote
│   └── user/          UserProfile · 선택지 enum
├── lawdata/           여러 곳이 함께 쓰는 법령 데이터 — LawStore · LawSource · 요약·변경점 저장 · chunk/
├── pipeline/          처리 단계(stage)별 — connector · normalize · diff · resolve · plan · discover ·
│                      dispatch · analyze · embed · index
├── ingest/            오프라인 배치 — job · task · handler · scan
├── feature/           사용자 기능 — 기능 하나 = 폴더 하나(컨트롤러 · 유스케이스 · DTO · 저장소)
│   ├── analysis/      (+ web · usage · event)
│   └── law · history · profile · bookmark · subscription · notification · account
├── web/               ApiExceptionHandler (기능 예외 → HTTP)
└── auth/ · config/ · observability/ · eval/ · dev/
```

축이 둘이다. 데이터가 단계를 차례로 지나는 코드(pipeline·ingest)는 **단계별**, 화면에서 DB까지 한 줄로 이어지는 사용자 기능은 **기능별**.

**규칙:**

1. **의존은 안쪽으로만: `domain ← lawdata ← pipeline ← ingest·feature`.** `domain`은 core 의 다른 패키지를 import하지 않고, `lawdata`는 `domain`만 쓴다. `pipeline`은 기능·배치를 모른다. 도메인 타입은 천천히 변하고 단계·기능은 자주 변하므로, 섞으면 안정된 것이 불안정한 것에 딸려 움직인다. (남은 예외: `AnalysisEngine → eval.FaithfulnessGate`, `LawConnector → config.LiaSourceProperties`, `observability ↔ pipeline`)
2. **기능끼리는 `history → analysis → {law, profile}`, `profile → account` 방향만.** 새로 기능 간 의존이 필요하면 공용 부분을 `domain`·`lawdata`로 내릴 수 있는지 먼저 본다(예: `ArticleComparison`). 기능이 포트를 구현할 때는 기능 → 포트 방향(`NotificationStore implements ingest.job.DueNotifications`).
3. **기능 폴더가 커지면 그 안에서 관심사로 나눈다**(`feature.analysis`의 `web`·`usage`·`event`). 하위 패키지끼리도 한 방향 — `web → 루트 → usage·event`.
4. **`Raw*`는 도메인이 아니다.** `RawLaw`는 출처 원형을 담은 커넥터 지역 DTO(`Map raw` 보유)이고 `connector/`에 산다. `RawLaw → Law` 변환이 **Anti-Corruption Layer**이며, 출처 API의 기벽은 전부 이 층에 가둔다(원칙 ② 수집과 해석의 분리).
5. **포트는 교체가 실제로 예정된 것만.** `LawLookup`은 커넥터 → Law Store 로 구현을 실제로 갈아끼웠다(D87, `LawStoreLookup` — 포트 옆 `pipeline.resolve`). 저장소는 Postgres 하나로 고정(D04)이라 Repository 추상화를 따로 두지 않는다.
6. **`domain`은 빈약(anemic)해도 된다 — 단, 우리가 만든 판정은 예외.** `Law`·`Article`은 우리가 저작하지 않는 외부 권위 사실이라 강제할 불변식이 없다. 반면 **우리가 산출한 판정에는 있다**: `ResolutionResult`의 fail-closed 4상태(D23)는 생성자에서 강제한다. 인용 없는 주장 차단(D08)도 타입이 알아야 한다.
7. **주석 링크만을 위한 상향 import 는 만들지 않는다** — `{@link com.lia.core.feature...}`처럼 정규화된 이름으로 적는다.

> 도입하지 않는 것(현 단계에서 순수 비용): Aggregate Root(`Law`는 통째로 읽고 쓰는 불변 스냅샷), Repository 추상화, 헥사고날식 저장소별 포트, Domain Event, Spring Modulith, Value Object 남발.

## 설정·비밀 관리

- 비밀값은 **레포 루트 `.env`**(gitignore) 하나가 단일 소스. `core/src/main/resources/application.yml`은 `${ENV_VAR}` 참조만 둔다.
- `.env.example`은 커밋되는 견본 — **실제 키 절대 금지**.
- `${VAR}`는 환경변수 참조 문법이다. 리터럴 키를 `${}`로 감싸면 빈 값이 된다(자주 나는 실수).
- 로딩: `application.yml` → `LiaSourceProperties`(`@ConfigurationProperties`) 타입 바인딩 → `PipelineConfig`가 커넥터에 주입. `.env` 주입 경로는 Gradle(test/bootRun 환경변수) + compose(`env_file`). 컴포넌트 자체는 설정에 비결합.

## 문서 규약 (중요 — 어기기 쉬움)

- `docs/architecture/vX.Y-*.md`는 **동결 스냅샷 — 절대 수정 금지**. 설계 변경은 새 버전 파일 추가 + `docs/ARCHITECTURE.md` 색인(이력 표·Changelog) 갱신.
- 설계 결정은 `docs/adr/decision-log.md`에 D번호로 추가(개정 시 기존 행을 "개정됨→Dxx"로 표시). ADR 승격 대상은 `docs/adr/`.
- **컴포넌트는 구현 전 `docs/components/<Name>.md` spec-first가 필수다.** 새 클래스(서비스·오케스트레이터·커넥터·스토어·파이프라인 단계 등 행위·계약이 있는 것)의 코드를 쓰기 전에 먼저 스펙 문서를 만들어 계약을 못 박고, 그 계약이 구현을 이끈다. 규약·템플릿은 [`_class-spec-template.md`](docs/components/_class-spec-template.md). 문서는 **코드가 못 말하는 것만**(역할 경계·계약·불변식·부작용·구조 결정 의도) 담고 필드/시그니처는 미러링하지 않는다. 코드 변경 시 함께 갱신.
- **작업은 이 레포에서만 한다.** 문서(docs/*.md)는 **2곳 byte-identical 동기화**: ① 이 레포 `docs/` — **단일 소스, 편집은 여기서만** ② Obsidian 볼트 `D:\rbgusgus\obvsidian\2024-2\프로젝트\입법 영향 분석\` (볼트 루트 = docs/에 대응, 아카이빙 미러). 문서 수정 후 `cp` + `diff`로 맞춘다. 코드·CLAUDE.md·인프라·mkdocs.yml은 동기화 대상 아님.
  - ~~`D:\workspace\law-impact-analysis`~~ — 2026-07-21 동기화 대상에서 제외. 더 이상 갱신·참조하지 않는다(기존 사본은 stale).
- **git**: 이 폴더가 메인 작업 폴더이며 `HappyGogildong/Legal-data-impact-service`(PUBLIC)에 연결돼 있다. 커밋은 의미 단위로 나누고, **푸시·PR·이슈 생성 등 외부 반영은 사용자 확인 후** 수행한다. 푸시 전 `.env`가 추적되지 않는지 반드시 확인.
  - **브랜치 전략(2026-08-27 채택) — feature 브랜치 + PR + 머지 커밋.** 컴포넌트/기능마다 `feat/<name>` 브랜치를 파고 TDD RED/GREEN 커밋을 세밀히 남긴다. 완성 후 PR을 열어 관련 이슈에 연결(`Closes #N`)하고 **merge commit(`--no-ff`)으로 병합**해 세부 커밋을 보존한다. `main`은 항상 통합가능 상태로 유지. PR은 `/code-review ultra` 대상이자 기능 단위 기록이 된다.
- **작업 트래킹**: GitHub Issues(라벨 `area:*`/`type:*`/`priority:*`, 마일스톤 M1~M4). 완료 작업도 이슈로 남기고 close해 이력을 유지한다. 코드 변경 시 관련 이슈 번호를 커밋/PR에 참조.
- mermaid 다이어그램의 엣지 라벨에 괄호 `()`를 쓰면 파싱이 깨진다(노드 라벨은 따옴표라 무관).

## 현재 상태 (2026-11-24 기준)

MVP 기능 구현과 **AWS 운영 투입 완료**(EC2+RDS+Vercel, [`docs/backend/cloud-infra.md`](docs/backend/cloud-infra.md)), **main 머지 시 자동 배포**(D86 — OIDC → SSM 문서 `LIA-Deploy`, 롤백은 Actions `Deploy` 수동 실행) — 시행예정 법령 수집·정규화·diff·색인 배치(D62, cron 매일 04:00·일요일 `--full`, 같은 시행일 공포본 병합 D85), 분석 Layer A 선계산(운영 일괄 요약은 꺼 둠)·Layer B 개인화(D69), 법령 발견(D70)·상황 확인(D71)·북마크·이력·구독 알림함(D72~D74), 웹(Next.js), RAG 오프라인 평가와 운영 보고서(D83, `lia eval` → `docs/eval/reports/`), 법령 이름 해소 개선(D87~D89), 패키지 재배치·ArchUnit 의존 규칙(D90). 세부 진척은 [`docs/status.md`](docs/status.md), 결정은 decision-log(D01~D90), 열린 일은 `gh issue list`. `mcp/`는 아직 PoC 스텁.

**MVP 수집 경로(D42, 2026-08-02):** 분석 대상은 의안이 아니라 **공포 후 시행 대기 법령**(`target=eflaw`). 전문·개정문·제개정이유·부칙이 모두 제공되므로 D38(법안 본문 갭)은 해소됐고, 의안 경로는 post-MVP로 강등돼 **코드에서 삭제**했다(`SourceConnector`·`RawBill`·`Assembly*`). 계약은 `docs/components/connector/SourceConnector.md`에 보존.

**실측 함정(국가법령정보):** ① `display=1` 이면 `law`가 배열이 아니라 **단일 객체** ② 인증 실패도 **HTTP 200** + `{"result":"사용자 정보 검증에 실패..."}` ③ `조문내용`만 읽으면 본문이 빔(`항→호→목` 재귀 병합 필수) ④ 부칙은 이력 전체 → `부칙공포번호`로 필터 ⑤ 연결키는 **`법령ID`**(MST는 버전마다 다름). 재현: `python tools/probe_eflaw.py`
