# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

**LIA (입법 영향 분석기)** — 아직 시행 전인 법안이 시민 개인에게 미칠 영향과 대응 행동을 알려주는 웹앱. 문서·주석·커밋 메시지는 한국어.

처음 온보딩할 때는 `docs/onboarding.md` → `docs/ARCHITECTURE.md`(색인, 최신 스냅샷 v0.5) → `docs/adr/decision-log.md`(결정 D01~) 순서로 읽는다.

## 명령어

```bash
# venv (레포 루트 .venv — 반드시 이걸로 실행)
./.venv/Scripts/python.exe ...

# 테스트 (pytest 없이 단독 실행 가능, 오프라인)
cd pipeline && ../.venv/Scripts/python.exe tests/test_pipeline.py

# 데모 (키 없으면 자동 오프라인 FakeConnector, 키 있으면 실 API)
cd pipeline && ../.venv/Scripts/python.exe -m lia_pipeline.demo

# 열린국회 실 API 스모크 테스트
cd pipeline && ../.venv/Scripts/python.exe scripts/smoke_assembly.py 주택임대차

# 문서 사이트
./.venv/Scripts/python.exe -m mkdocs serve

# 로컬 DB (pgvector 포함 Postgres + db/init.sql 자동 실행)
docker compose up -d db
```

Windows 콘솔(cp949)에서 한글 출력이 깨지면 `export PYTHONIOENCODING=utf-8`.

## 아키텍처 (3-런타임 폴리글랏)

| 디렉터리 | 언어 | 책임 |
|---|---|---|
| `core/` | Java Spring (**Boot 4.0 + Spring AI 2.0**) | **파이프라인(수집·식별·정규화·임베딩·추론) + 도메인·커맨드·REST API** — D35로 Python 파이프라인을 흡수한 단일 애플리케이션 |
| `pipeline/` | Python | **레거시/포팅 사양** — 실 API 검증된 참조 구현 + 임베딩 벤치 도구. 신규 기능은 core에 |
| `mcp/` | TypeScript | 웹 프론트엔드. MCP 어댑터는 내부 전용 — **사용자에게 노출하지 않음** |

Spring 버전 변경점(Boot 3.x→4.0, AI 1.x→2.0)은 `docs/reference/spring-migration.md` 참고 — Jackson 3(`tools.jackson`)·옵션 빌더 강제가 코드에서 주의할 핵심.

데이터 흐름 두 갈래: **(수집·적재)** 신뢰 출처 → Connectors → 법안은 Normalizer→Bill Store, 현행법은 RAG Indexer→Vector Index. **(런타임)** 웹 → Spring REST → AnalysisPipeline(게이트·조립·검증·캐시) → Python Analysis Engine(RAG 검색 + Claude 추론) → 구조화 ImpactResult 반환.

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

열린국회 오류 응답은 최상위 `{"RESULT": {...}}` 형태로 온다(정상 응답의 head[].RESULT와 다름) — `assembly_bills._result_code`가 둘 다 처리한다.

## 설정·비밀 관리

- 비밀값은 **레포 루트 `.env`** (gitignore)에만. `pipeline/config.yaml`(gitignore)은 `${ENV_VAR}` 참조만 두고 비밀을 직접 넣지 않는다.
- `config.example.yaml`·`.env.example`은 커밋되는 견본 — **실제 키 절대 금지**.
- `${VAR}`는 환경변수 참조 문법이다. 리터럴 키를 `${}`로 감싸면 빈 값이 된다(자주 나는 실수).
- 로딩: `pipeline/src/lia_pipeline/config.py`가 config.yaml 파싱 + `.env` 로드(python-dotenv) + pydantic 검증 → 팩토리(`build_assembly_connector`)로 컴포넌트에 주입. 컴포넌트 자체는 설정에 비결합.

## 문서 규약 (중요 — 어기기 쉬움)

- `docs/architecture/vX.Y-*.md`는 **동결 스냅샷 — 절대 수정 금지**. 설계 변경은 새 버전 파일 추가 + `docs/ARCHITECTURE.md` 색인(이력 표·Changelog) 갱신.
- 설계 결정은 `docs/adr/decision-log.md`에 D번호로 추가(개정 시 기존 행을 "개정됨→Dxx"로 표시). ADR 승격 대상은 `docs/adr/`.
- 파이프라인 컴포넌트마다 `docs/components/<Name>.md` 설계 문서(역할/입출력/파라미터/동작/**구조 결정 의도**)를 유지 — 코드 변경 시 함께 갱신.
- **문서(docs/*.md)는 3곳 byte-identical 동기화**: ① 이 레포 `docs/` ② Obsidian 볼트 `D:\rbgusgus\obvsidian\2024-2\프로젝트\입법 영향 분석\` (볼트 루트 = docs/에 대응) ③ `D:\workspace\law-impact-analysis\docs\`. mkdocs.yml은 ①③만. 문서 수정 후 `cp` + `diff`로 맞춘다. 코드·CLAUDE.md·인프라 파일은 동기화 대상 아님.
- **git**: 이 폴더가 메인 작업 폴더이며 `HappyGogildong/Legal-data-impact-service`(PUBLIC)에 연결돼 있다. 커밋은 의미 단위로 나누고, **푸시·이슈 생성 등 외부 반영은 사용자 확인 후** 수행한다. 푸시 전 `.env`·`config.yaml`이 추적되지 않는지 반드시 확인.
- **작업 트래킹**: GitHub Issues(라벨 `area:*`/`type:*`/`priority:*`, 마일스톤 M1~M4). 완료 작업도 이슈로 남기고 close해 이력을 유지한다. 코드 변경 시 관련 이슈 번호를 커밋/PR에 참조.
- mermaid 다이어그램의 엣지 라벨에 괄호 `()`를 쓰면 파싱이 깨진다(노드 라벨은 따옴표라 무관).

## 현재 상태 (2026-07 기준)

설계 문서 v0.6(**Spring 통합, D35**). Python 파이프라인은 열린국회 커넥터·SourceAnalyzer(4상태)까지 실 API 검증 완료 — 이를 **사양 삼아 core(Java)로 포팅 중**. 다음: core 포팅 완료 → Normalizer(신구조문대비표 파서) → Spring AI Embedder/RAGIndexer → 임베딩 벤치(`docs/reference/embedding-benchmark.md`, 벤치 스크립트는 Python 가능). `mcp/`는 아직 PoC 스텁. 포팅 시 Python 테스트 케이스(`pipeline/tests/test_pipeline.py`)와 실 API 지식(AGE 필수, 최상위 RESULT 오류)을 승계할 것.
