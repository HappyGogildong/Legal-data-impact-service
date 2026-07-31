# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

**LIA (입법 영향 분석기)** — 아직 시행 전인 법안이 시민 개인에게 미칠 영향과 대응 행동을 알려주는 웹앱. 문서·주석·커밋 메시지는 한국어.

처음 온보딩할 때는 `docs/onboarding.md` → `docs/ARCHITECTURE.md`(색인, 최신 스냅샷 v0.6) → `docs/adr/decision-log.md`(결정 D01~) 순서로 읽는다.

## 명령어

```bash
# Java 테스트 (단위 + 자격증명 바인딩 + 실 API 스모크; 키 없으면 스모크는 자동 스킵)
cd core && ./gradlew test

# 출처 연동 진단 (자격증명·응답 형식·본문 서비스 탐색)
./.venv/Scripts/python.exe tools/probe_sources.py 주택임대차

# 문서 사이트
./.venv/Scripts/python.exe -m mkdocs serve

# 로컬 DB (pgvector 포함 Postgres + db/init.sql 자동 실행)
docker compose up -d db
```

Windows 콘솔(cp949)에서 한글 출력이 깨지면 `export PYTHONIOENCODING=utf-8`.

## 아키텍처 (2-런타임 — Spring + TS)

| 디렉터리 | 언어 | 책임 |
|---|---|---|
| `core/` | Java Spring (**Boot 4.0 + Spring AI 2.0**) | **운영 전체** — 파이프라인(수집·식별·정규화·임베딩·추론) + 도메인·커맨드·REST API |
| `tools/` | Python | 독립 진단 스크립트(`probe_sources.py`) — 운영 코드 아님. `.env`만 읽음 |
| `mcp/` | TypeScript | 웹 프론트엔드. MCP 어댑터는 내부 전용 — **사용자에게 노출하지 않음** |

Spring 버전 변경점(Boot 3.x→4.0, AI 1.x→2.0)은 `docs/reference/spring-migration.md` 참고 — Jackson 3(`tools.jackson`)·옵션 빌더 강제가 코드에서 주의할 핵심.

데이터 흐름 두 갈래: **(수집·적재)** 신뢰 출처 → Connectors → 법안은 Normalizer→Bill Store, 현행법은 RAG Indexer→Vector Index. **(런타임)** 웹 → Spring REST → AnalysisPipeline(게이트·조립·검증·캐시) → Analysis Engine(RAG 검색 + Claude 추론, 내부 호출) → 구조화 ImpactResult 반환.

핵심 설계 원칙 (전 코드에 관철):
- **그라운딩**: 모든 분석 주장은 주입된 조문 `source_id`만 인용 가능. 인용 없는 응답은 게이트에서 차단.
- **fail-closed 해소 4상태**: `RESOLVED / AMBIGUOUS / NOT_FOUND_YET / UNVERIFIED`. 신뢰 출처에서 확인 안 되면 분석하지 않는다. 뉴스·사용자 입력 *내용*은 사실이 아니라 식별 단서.
- **2계층 분석**: Layer A(법안 사실·BillFacts, 페르소나 무관·캐시) → Layer B(세그먼트별 영향·대응안).
- **RAG 두 용도**: 분석용(현행법 조문, namespace=`law`) vs 탐색용(법안 요약·BillFacts, namespace=`bill`). 법안 *전문*은 임베딩하지 않음(컨텍스트에 통째로 들어감). 적재(RAGIndexer)와 검색(AnalysisEngine/SourceAnalyzer)은 **동일 임베딩 모델**(공유 Embedder) 필수.
- 추론=Claude Opus 4.8(외부 API), 임베딩=별도 외부 API(OpenAI vs Upstage 벤치 중, `docs/reference/embedding-benchmark.md`). 자체 모델 학습·파인튜닝 없음.
- 저장소=단일 Postgres+pgvector(ADR-001). 임베딩 테이블은 파이프라인 소유(`db/init.sql`), 관계형 도메인은 Spring 소유.

## 신뢰 출처 — 이름 혼동 주의

법제처(MOLEG)가 입법예고와 국가법령정보센터를 **둘 다 운영**하므로, 설정 키는 운영기관이 아니라 **내용 기준**:

| 키 | 출처 | 내용 | 인증 |
|---|---|---|---|
| `assembly` | 열린국회정보 | 의원발의 법안 | ServiceKey. **`AGE`(국회 대수) 파라미터 필수** — 빠지면 ERROR-300 |
| `moleg` | 법제처 정부입법예고 | 정부제출 법안 | **OC** (회원 이메일의 아이디 부분) |
| `law` | 국가법령정보(law.go.kr) | 현행 법령(diff 기준선) | **OC** |

열린국회 API 함정 2가지 (실측·해결됨):
- 오류 응답은 최상위 `{"RESULT": {...}}` 형태(정상 응답의 `head[].RESULT`와 다름) — `AssemblyEnvelope.resultCode`가 둘 다 처리.
- `Type=json` 이어도 **`Content-Type: text/html`** 로 응답 → `RestClient` 컨버터가 거부하므로 `String`으로 받아 Jackson 3로 직접 파싱(`AssemblyBillsConnector.parseJson`).

## 설정·비밀 관리

- 비밀값은 **레포 루트 `.env`**(gitignore) 하나가 단일 소스. `core/src/main/resources/application.yml`은 `${ENV_VAR}` 참조만 둔다.
- `.env.example`은 커밋되는 견본 — **실제 키 절대 금지**.
- `${VAR}`는 환경변수 참조 문법이다. 리터럴 키를 `${}`로 감싸면 빈 값이 된다(자주 나는 실수).
- 로딩: `application.yml` → `LiaSourceProperties`(`@ConfigurationProperties`) 타입 바인딩 → `PipelineConfig`가 커넥터에 주입. `.env` 주입 경로는 Gradle(test/bootRun 환경변수) + compose(`env_file`). 컴포넌트 자체는 설정에 비결합.

## 문서 규약 (중요 — 어기기 쉬움)

- `docs/architecture/vX.Y-*.md`는 **동결 스냅샷 — 절대 수정 금지**. 설계 변경은 새 버전 파일 추가 + `docs/ARCHITECTURE.md` 색인(이력 표·Changelog) 갱신.
- 설계 결정은 `docs/adr/decision-log.md`에 D번호로 추가(개정 시 기존 행을 "개정됨→Dxx"로 표시). ADR 승격 대상은 `docs/adr/`.
- 파이프라인 컴포넌트마다 `docs/components/<Name>.md` 설계 문서(역할/입출력/파라미터/동작/**구조 결정 의도**)를 유지 — 코드 변경 시 함께 갱신.
- **작업은 이 레포에서만 한다.** 문서(docs/*.md)는 **2곳 byte-identical 동기화**: ① 이 레포 `docs/` — **단일 소스, 편집은 여기서만** ② Obsidian 볼트 `D:\rbgusgus\obvsidian\2024-2\프로젝트\입법 영향 분석\` (볼트 루트 = docs/에 대응, 아카이빙 미러). 문서 수정 후 `cp` + `diff`로 맞춘다. 코드·CLAUDE.md·인프라·mkdocs.yml은 동기화 대상 아님.
  - ~~`D:\workspace\law-impact-analysis`~~ — 2026-07-21 동기화 대상에서 제외. 더 이상 갱신·참조하지 않는다(기존 사본은 stale).
- **git**: 이 폴더가 메인 작업 폴더이며 `HappyGogildong/Legal-data-impact-service`(PUBLIC)에 연결돼 있다. 커밋은 의미 단위로 나누고, **푸시·이슈 생성 등 외부 반영은 사용자 확인 후** 수행한다. 푸시 전 `.env`가 추적되지 않는지 반드시 확인.
- **작업 트래킹**: GitHub Issues(라벨 `area:*`/`type:*`/`priority:*`, 마일스톤 M1~M4). 완료 작업도 이슈로 남기고 close해 이력을 유지한다. 코드 변경 시 관련 이슈 번호를 커밋/PR에 참조.
- mermaid 다이어그램의 엣지 라벨에 괄호 `()`를 쓰면 파싱이 깨진다(노드 라벨은 따옴표라 무관).

## 현재 상태 (2026-07 기준)

설계 문서 v0.6(**Spring 통합, D35·D39**). **Python 파이프라인 제거 완료** — 커넥터·SourceAnalyzer(4상태)·설정이 Java로 이관돼 실 API 검증까지 통과(테스트 15건). 다음: Normalizer(#5, 신구조문대비표 파서) → LawConnector/MolegConnector(#11) → Spring AI Embedder(#6)/RAGIndexer(#7) → 임베딩 벤치(#8). `mcp/`는 아직 PoC 스텁.

**미해결 갭(D38):** 법안 본문(조문·부칙·신구조문대비표) 획득 경로 미확정 — 열린국회 API 5종 모두 메타데이터만 제공. 현행법(국가법령정보)은 조문 42개·부칙 제공 확인됨. `docs/components/SourceConnector.md` §본문 획득 참고.
