# 아키텍처 문서 — 색인 (Architecture Index)

이 파일은 **색인**이다. 실제 설계 내용은 버전별 동결 스냅샷에 있다.
설계가 바뀌면 **이전 문서를 지우지 않고** `architecture/`에 새 버전 파일을 추가하고,
이 색인의 표와 변경 이유만 갱신한다. 그래야 버전 간 비교(diff)와 "왜 바꿨나"를 함께 추적할 수 있다.

## 최신본

→ **[architecture/v0.7-offline-online-split.md](architecture/v0.7-offline-online-split.md)** (v0.7, 현행)

> ⚠️ **v0.7 이후 갱신 (D42, 2026-08-01) — 스냅샷 미반영.** 동결 규약상 v0.7 파일은 수정하지 않으므로 여기에 적어 둔다:
> **MVP 분석 대상이 *의안*에서 *공포 후 시행 대기 법령*(국가법령정보 `target=eflaw`)으로 확정**됐다. v0.7 §4.2·§7의 "법안 본문 획득 경로 미확정(D38)" 서술과 "Normalizer 신구조문대비표 파서"는 **더 이상 유효하지 않다** — 본문·개정문·부칙이 이미 확보돼 D38은 해소, 대비표 파싱은 폐기됐다. 현행 계약은 [components/SourceConnector.md](components/SourceConnector.md) §MVP 본문 경로와 [components/component-specs.md](components/component-specs.md) §1.1을 기준으로 한다. 다음 스냅샷(v0.8) 작성 시 반영할 것.

## 버전 이력

| 버전 | 파일 | 날짜 | 상태 | 핵심 |
|---|---|---|---|---|
| v0.7 | [v0.7-offline-online-split.md](architecture/v0.7-offline-online-split.md) | 2026-08-01 | 현행 | 오프라인(배치 적재)·온라인(요청 응답) 실행 모드 분리, Nemotron 군집(Persona Builder)은 그림에서 제외 |
| v0.6 | [v0.6-spring-consolidation.md](architecture/v0.6-spring-consolidation.md) | 2026-07-21 | 대체됨 | 파이프라인을 Spring(Boot 4.0+Spring AI 2.0)으로 통합 — 3→2 런타임, Python 서버 대체 |
| v0.5 | [v0.5-bill-discovery.md](architecture/v0.5-bill-discovery.md) | 2026-06-28 | 대체됨 | 모호 plain text 위한 법안 의미검색(탐색용 임베딩) 추가, 분석용 RAG와 분리 |
| v0.4 | [v0.4-pipeline-refinements.md](architecture/v0.4-pipeline-refinements.md) | 2026-06-28 | 대체됨 | RAG Indexer 분리, 출처 3종 구체화, 커맨드 4종(+LawDiff), BillFacts/2계층, 해소 4상태, triage |
| v0.3 | [v0.3-no-video-internal-mcp.md](architecture/v0.3-no-video-internal-mcp.md) | 2026-06-22 | 대체됨 | 사용자 입력에서 영상 제외, MCP를 사용자 미노출 내부 어댑터로 강등 |
| v0.2 | [v0.2-webapp-primary.md](architecture/v0.2-webapp-primary.md) | 2026-05-31 | 대체됨 | 웹앱을 주 사용자 경로로, MCP는 부가 표면으로 강등 |
| v0.1 | [v0.1-initial.md](architecture/v0.1-initial.md) | 2026-05-31 | 대체됨 | 최초 설계. MCP/BFF를 1급 도구 표면으로 배치 |

## 관련 결정 (ADR)

설계의 **"왜 이렇게 정했나"**는 [`adr/`](adr/README.md)에 ADR로 남긴다. 버전 스냅샷이 *결과 구조*라면 ADR은 *결정·대안·근거*다.

| ADR | 제목 | 기반 버전 | 반영 버전 | 상태 |
|---|---|---|---|---|
| [ADR-001](adr/ADR-001-knowledge-store-sizing.md) | 지식 저장소 구성 — 분리형 vs 통합형(Postgres+pgvector) | v0.3 | v0.4 (단일 Postgres+pgvector 반영됨) | Proposed |

전체 결정 인덱스: [adr/decision-log.md](adr/decision-log.md) (D01~, 최신 D40).

> ADR이 `Accepted`되어 구조에 반영되면, 그 변경을 담은 새 버전 파일을 추가하고 위 "반영 버전"을 확정한다(예: v0.4). 동결 스냅샷에는 ADR 링크를 넣지 않으므로, 버전↔ADR 매핑은 **이 표가 단일 출처**다.

## 변경 이유 (Changelog)

### v0.6 → v0.7 — "오프라인·온라인 실행 모드 분리"

**무엇이 바뀌었나**
- §2 신설: **실행 모드 대조표** — 계기·지연 요구·실패 처리·외부 의존·상태(read/write)·확장 축·장애 영향을 오프라인 vs 온라인으로 구분.
- §3 다이어그램을 **둘로 분리**: §3.1 오프라인(스케줄러 → 커넥터 → Normalizer/BillFacts 파생 → RAG Indexer → 저장소 write), §3.2 온라인(사용자 → REST → 해소 게이트 → 커맨드 → Engine+LLM → 인용검증 → 응답).
- **Persona Builder(Nemotron 군집) 다이어그램에서 제외** — post-MVP. `PersonaImpact` 커맨드는 유지하되 세그먼트는 *사용자 선택/수작업 정의*로 단순화.
- §4.4: RAG 두 용도를 **적재(오프라인) / 검색(온라인) 시점**과 함께 표로 정리.
- §4.1·§7에 **D38**(법안 본문 경로 미확정, 현행법은 확보) 실측 상태 반영.

**왜 바꿨나**
- v0.6까지 한 그림에 섞여 있어 **"무엇이 사용자를 기다리게 하는가"** 가 드러나지 않았다. 두 모드는 지연 요구(분~시간 vs 초)·장애 영향(신선도 저하 vs 사용자 직접)·확장 축(코퍼스 vs 동시 사용자)이 근본적으로 다르다.
- 분리하면 설계 규율이 명시된다 — **"온라인에 넣기 전에 오프라인으로 미리 할 수 없는지 먼저 묻는다."** BillFacts 선계산·임베딩 적재가 오프라인인 이유, 온라인이 *검색+개인화 추론*만 남는 이유가 그림으로 설명된다.
- 페르소나 군집(Nemotron)은 후순위인데 다이어그램에 있어 MVP 범위를 오해하게 만들었다.

### v0.5 → v0.6 — "파이프라인 Spring 통합 (Python 서버 대체)"

**무엇이 바뀌었나**
- §2: 3-런타임(Python/Spring/TS) → **2-런타임(Spring/TS)**. 파이프라인 컴포넌트(커넥터·SourceAnalyzer·Normalizer·RAG Indexer·Analysis Engine)가 **Boot 4.0 + Spring AI 2.0** 기반으로 코어와 한 애플리케이션에 통합.
- Python↔Spring **REST 계약 소멸** → 내부 메서드 호출(스키마는 DTO로 승계).
- Spring AI 매핑: Embedder→`EmbeddingModel`, Vector→`VectorStore`(PgVector), 추론→`ChatClient`(Anthropic). 버전 변경점은 [reference/spring-migration.md](reference/spring-migration.md).
- 설계 개념(4상태·2계층·그라운딩·2 네임스페이스·커맨드)은 **전부 불변** — 런타임 표기만 변경.

**왜 바꿨나**
- Python 채택 근거였던 "무거운 NLP·LLM·RAG 생태계"가 결정 누적으로 소멸(D05 비학습·D19 추론 API·D32 임베딩 API·D04 pgvector). 파이프라인 실체는 HTTP+파싱+오케스트레이션.
- v0.2 스택 결정 박스가 예약해 둔 "Spring AI로 JVM 흡수" 경로의 실행. 운영(배포 3→2)·계약(REST 경계 소멸)·모델(이중 정의 해소) 단순화. 팀 스택(Gradle/Spring)과 일치. (결정: [adr/decision-log.md](adr/decision-log.md) D35)

### v0.4 → v0.5 — "모호 plain text 위한 법안 의미검색 추가"

**무엇이 바뀌었나**
- §1 원칙 넷째·§2 USRC: **모호한 자연어(plain text)** 를 명시적 입력 유형으로 추가(U2).
- §2 다이어그램: **법안 요약·BillFacts 임베딩(탐색용)** 을 RAG Indexer 범위·Vector Index 내용에 추가. `SourceAnalyzer -. 법안 의미검색 .-> Vector Index` 엣지 신설.
- §3.3: SourceAnalyzer 해소를 *정확/퍼지 매칭 + 의미검색(매칭 약할 때)* 2단계로. 모호 입력 → 보통 `AMBIGUOUS`(후보 명확화).
- §3.4: RAG의 **두 용도 분리** 명문화 — *분석용(현행법·선례)* vs *탐색용(법안 요약·BillFacts)*. 법안 전문은 임베딩 안 함(컨텍스트에 통째로).
- §6: 탐색 임베딩 추가가 ADR-001 불변임 명시(~1~3GB, 헤드룸·트리거 내).

**왜 바꿨나**
- 사용자 입력에 *법안명·번호 없이 주제·효과만 묘사하는 모호한 plain text*("…사람들이 서로 경계하게 만드는 법안")가 들어온다. 기존 RAG(현행법)·DB(정확매칭)로는 *법안 식별*이 안 됨 → **법안 자체의 의미검색**이 필요.
- "분석을 위한 RAG(알려진 법안에 현행법 컨텍스트 제공)"와 "탐색을 위한 의미검색(모호 입력 → 후보 법안)"은 목적이 다른 별개 용도라 구분.
- 그래도 **fail-closed 유지** — 실재 법안이 없으면 후보 없음/미등록/허위로 처리, 지어내지 않음.

### v0.3 → v0.4 — "파이프라인 정제: RAG Indexer 분리·출처 3종·diff·BillFacts"

**무엇이 바뀌었나**
- §2 다이어그램: **`RAG Indexer` 노드 신설** — v0.3에서 Analysis Engine(C4)이 겸하던 임베딩 적재를 분리. Analysis Engine은 *쿼리 임베딩+검색+추론*만. RAG 대상도 *법안* → **현행법·선례**로(법안 한 건은 컨텍스트에 통째로).
- §2/§3.1: Connectors를 **법안 2종(열린국회·법제처) + 현행법(국가법령정보=`LawConnector`)** 으로 구체화. 저장소를 **단일 Postgres+pgvector**(`Bill Store`·`Vector Index`·`Persona Store`)로 명시.
- §3.2: 도메인 모델에 **`BillFacts`(Layer A 파생 캐시)** + Bill/Article 확장 필드(신구조문대비표·부칙·위임조항 등) 반영.
- §3.3: 소스 입력 흐름에 **해소 4상태**(RESOLVED/AMBIGUOUS/NOT_FOUND_YET/UNVERIFIED, fail-closed) 추가.
- §3.4: **2계층 엔진**(사실 A / 해석 B) + **인용검증 게이트** + **임베딩 모델은 추론 모델과 별개** 명문화.
- §4: MVP 커맨드 **4종(+`LawDiff`)**, **Triage** 분류 기준 고정(라우팅은 post-MVP), 페르소나 lookup.
- §6/§7: 임베딩 모델 공유 요건, 다음 단계를 3출처·4커맨드·diff·임베딩 모델 확정으로 갱신.

**왜 바꿨나**
- v0.3 다이어그램이 임베딩 적재를 Analysis Engine에 묶어 그려, 이후 컴포넌트 문서에서 분리한 **`RAG Indexer`가 그림에 없던 불일치**를 해소(이 버전의 직접 계기).
- MVP를 v0.3 §3.1(3개 출처)과 일치시키고, 핵심 가치인 **현행법 diff를 MVP에 포함**(신구조문대비표 1차 + 국가법령정보 보강).
- 누적 결정(2계층 엔진·BillFacts·해소 4상태·triage·foundation API 비학습)을 스냅샷에 반영해 그림과 컴포넌트 문서를 정합화. (상세: [adr/decision-log.md](adr/decision-log.md) D04·D07·D10·D14·D18~D28)

### v0.2 → v0.3 — "영상 입력 제외, MCP 사용자 미노출"

**무엇이 바뀌었나**
- §1 원칙 넷째: 입력 소스 예시에서 `영상`을 제거(`뉴스 링크·기사 텍스트`만 유지).
- §2 TypeScript의 역할: `웹 프론트엔드 + (선택) MCP 어댑터` → `웹 프론트엔드(주 경로)`. MCP는 "내부 구현용으로 둘 수 있으나 사용자 인터페이스로는 미제공"으로 명문화. 스택 결정 박스도 동일 취지로 갱신.
- 다이어그램: `사용자 입력 소스`에서 `영상(자막·STT)` 노드 제거. ④ `사용자 표면`에는 웹앱만 두고, `MCP 어댑터`는 별도 `(내부) 비노출 어댑터` 서브그래프로 이동. `파워유저/LLM 클라이언트` 액터와 그 연결선 삭제.
- §3.3 소스 입력 분석 흐름: `영상` 분기(자막 API·STT) 제거, 제목을 `(링크 / 뉴스)`로 변경.
- §4.2: `SourceResolveCommand` 설명을 `링크/영상` → `링크/기사`로.
- §5 제목·내용: `웹앱 주 경로 + MCP 부가 표면` → `웹앱 단일 주 경로 · MCP는 내부 전용`. 사용자 표면 표는 웹앱만, MCP 도구 표는 "내부 참고(비노출)"로 강등.
- §6·§7: 신규 클라이언트 축에서 MCP를 "내부 전용"으로 표기, 다음 단계의 MCP 항목을 "내부 한정·사용자 노출은 별도 결정"으로 변경.

**왜 바꿨나**
- 영상은 자막/STT 의존으로 식별 신뢰도·운영비용 대비 효용이 낮아 MVP 입력 소스에서 제외. 텍스트(링크·기사)만으로도 "법안 식별" 정규 흐름은 동일하게 성립.
- MCP는 내부 구현·테스트·향후 B2B 연동에 유용할 수 있으나, 일반 시민 타겟 제품에서 **사용자에게 노출할 계획이 아직 없으므로** 사용자 표면이 아닌 내부 어댑터로 위치를 분명히 함. (세 축 독립 확장 구조는 그대로 유지.)

### v0.1 → v0.2 — "웹앱 우선, MCP 부가화"

**무엇이 바뀌었나**
- §2 TypeScript의 역할: `MCP 서버 & 도구 표면(BFF)` → `웹 프론트엔드(주 경로) + 선택적 MCP 어댑터`.
- 다이어그램 ④ 계층: `MCP Server / Web BFF`가 사용자에게 직결되던 구조 → `웹앱 프론트엔드(★주 경로)` + `MCP 어댑터(선택)`로 분리. 사용자도 `일반 시민`과 `파워유저/LLM 클라이언트`로 구분.
- §5 제목·내용: `도구 표면(MCP/BFF)` → `웹앱 주 경로 + MCP 부가 표면`. MCP 도구 표를 "선택 활성화"로 표기.
- §2에 **스택 결정 박스** 신설 (Python+Spring 분리 유지 근거).
- §6·§7: 폴리글랏 운영비용 명시, 다음 단계의 종착점을 `MCP 응답` → `웹 화면 표시`로 변경, MCP는 MVP 이후로 미룸.

**왜 바꿨나**
- 타겟이 **일반 시민**이라, 사용자가 Claude·Cursor 같은 LLM 클라이언트로 MCP를 직접 호출하는 시나리오가 비현실적. 실제 사용 경로는 웹앱이다.
- 참고 리포(`korean-law-mcp`)는 "MCP가 곧 제품"인 형태지만, 우리 제품은 "서버가 LLM 오케스트레이션을 소유하는 웹 서비스"다 — 두 아키텍처를 명확히 분리할 필요.
- Spring 채택의 타당성: 무거운 NLP·LLM·RAG는 Python이 맡고 Spring엔 도메인·트랜잭션·API만 두므로, "LLM 서비스에 Spring 부적합"이라는 통념이 이 구조엔 적용되지 않음. (Spring AI 1.x로 향후 JVM 통합 여지도 있음.)

---

## 문서 작성 규약

- 버전 파일명: `architecture/vMAJOR.MINOR-짧은설명.md`
- 동결 스냅샷은 **수정 금지**. 변경은 항상 새 버전 파일로.
- 새 버전 추가 시: ① 새 파일 생성 → ② 이 색인의 이력 표 한 줄 추가 → ③ Changelog에 "무엇이/왜" 항목 추가.
- 본문 상단에 `상태: 현행/대체됨`을 표기.
- 결정 기록(ADR)은 [`adr/`](adr/README.md)에 둔다. ADR ↔ 버전 매핑은 위 **"관련 결정(ADR)"** 표에서 관리하고(동결 스냅샷은 수정 금지이므로 스냅샷 본문에 ADR을 링크하지 않는다), ADR 본문에서는 frontmatter `related` + 위키링크로 기반 버전을 가리킨다.
