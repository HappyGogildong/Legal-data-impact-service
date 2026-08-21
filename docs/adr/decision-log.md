---
title: 결정 로그 (Decision Log)
status: Living
date: 2026-06-25
tags: [decisions, log, index]
related:
  - "architecture/v0.3-no-video-internal-mcp.md"
  - "adr/ADR-001-knowledge-store-sizing.md"
  - "prompts/analysis-prompt-spec.md"
  - "mvp/components-io-and-scope.md"
---

# 결정 로그 (Decision Log)

지금까지 합의된 설계 결정을 **한 곳에서 스캔**할 수 있게 정리한다. 깊은 근거·대안은 개별 문서(ADR/스냅샷/스펙)에, 이 로그는 *무엇을·왜·어디에* 의 요약 인덱스다. 살아있는 문서(Living) — 결정이 추가/변경되면 갱신.

> 상세: [[v0.3-no-video-internal-mcp|아키텍처 v0.3]] · [[ADR-001-knowledge-store-sizing|ADR-001]] · [[analysis-prompt-spec|프롬프트 정의서]] · [[components-io-and-scope|컴포넌트·MVP]]

## 요약 표

| ID | 결정 | 상태 | 근거(한 줄) | 상세 |
|---|---|---|---|---|
| D01 | 사용자 입력에서 **영상 제외**(링크·기사·직접입력만) | 확정 | 자막/STT 신뢰도·비용 대비 효용 낮음 | v0.3 §1·§3.3 |
| D02 | **MCP는 내부 전용·사용자 미노출** 어댑터 | 확정 | 일반시민 타겟, 사용자 직접 호출 비현실적 | v0.3 §2·§5 |
| D03 | **3-런타임 폴리글랏**(Python 추론 / Spring 도메인·API / TS 웹) | 확정 | 각 언어 최강 책임 배치 | v0.3 §2 |
| D04 | 지식 저장소 **단일 Postgres+pgvector 통합**(분리형 보류) | Proposed | 워킹셋 수십 GB, 비용 동인은 서빙 인스턴스 | ADR-001 |
| D05 | 분석 엔진 = **외부 foundation API 호출**, 자체/경량 학습·파인튜닝 안 함 | 확정 | 강모델 API로 충분, 운영 단순 | 본 로그 §D05 |
| D06 | **RAG/RDB = 컨텍스트 공급층**(모델 크기와 무관) | 확정 | 모델 학습범위 밖 최신 법령을 호출 시 주입 | 본 로그 §D06 |
| D07 | 분석 엔진 **2계층(사실 A / 해석 B) + 인용검증 게이트 + triage 티어링** | Proposed | 캐시·비용·그라운딩 분리 | 프롬프트 정의서, 본 로그 §D07 |
| D08 | **인용 강제 그라운딩** — 주입 source_id만 인용, 무인용 차단 | 확정 | 환각 통제·역추적 | 프롬프트 정의서 §1·§4·§6 |
| D09 | 응답은 **구조화 JSON**(claims+citations+confidence+actions…) | 확정 | 검증·렌더·캐시 키 | 프롬프트 정의서 §4 |
| D10 | ~~**Nemotron-Personas-Korea** 채택~~ → **D41로 폐기**. 데이터셋 의존은 사라지고 **주입 규율**(비인용·`<persona>` 격리)만 승계 | 개정됨(D41) | 자기신고 프로필이 더 정확·최신 | D41, 본 로그 §D10 |
| D11 | 페르소나 **Store → 런타임 lookup**(벡터 RAG 아님) — *오프라인 사전 분류*는 D41로 폐기, lookup 방식은 유지 | 개정됨(D41) | 법령=벡터RAG, 페르소나=키 lookup 분리 | 컴포넌트·MVP §1 |
| D12 | ~~MVP 입력 = 열린국회만~~ → **D24로 개정** | 개정됨 | — | D24 |
| D13 | ~~MVP 커맨드 3종~~ → **D25로 개정**(LawDiff 추가) | 개정됨 | — | D25 |
| D14 | **Evaluation Harness** — 합성 페르소나 E2E(구동·정성), 정답판정 금지 | Proposed | 커버리지·회귀, 정답은 규칙+사람검수 | 컴포넌트·MVP §5 |
| D15 | **컴포넌트 상세 스펙** 확정 + 정합성 검증 통과(갭 닫음) | 확정 | 스펙대로 개발 시 E2E 동작 보장 | [[component-specs]] |
| D16 | `revision` = 분석영향 필드 content-hash(캐시 무효화 키) | 확정 | 본문·단계·시행일 변동만 무효화 | component-specs §1 |
| D17 | Spring↔Python REST 계약 + ingest/resolve 스키마 확정 | 확정 | 계층 경계·재현 가능 | component-specs §3 |
| D18 | ~~페르소나 6개 세그먼트~~ → **D41로 폐기** | 개정됨 | 6버킷은 개인화 해상도 부족 | D41 |
| D19 | 추론 모델 = **Opus 4.8**(`claude-opus-4-8`), 입력~32K/출력4K, 캐싱 | 확정 | 법적 정확도 우선, 교체 가능 | component-specs §3.3 |
| D20 | ~~현행법 diff MVP 생략~~ → **D26으로 개정**(MVP 포함) | 개정됨 | — | D26 |
| D21 | 법안 속성 확장 + **`LawFacts`(Layer A 파생 캐시)** 신설, `Bill`은 A/B 사실만 | 확정 | 서비스 수준 도메인 모델; C 추론은 Bill과 분리·인용 강제 | [[law-attributes]], component-specs §1 |
| D22 | D21에도 **저장소 결정(ADR-001) 불변** | 확정 | LawFacts ≈0.25GB, 헤드룸 내·트리거 미해당 | law-attributes §저장소 영향 |
| D23 | SourceAnalyzer **해소 4상태**(RESOLVED/AMBIGUOUS/NOT_FOUND_YET/UNVERIFIED), fail-closed | 확정 | 미등록 vs 허위 구분, 소문이 분석으로 둔갑 방지 | component-specs §4 #2·#8, §3.2 |
| D24 | **MVP 입력 = 3개 출처**(열린국회·법제처 입법예고 = 법안, 국가법령정보 = 현행법 기준선) | 확정 | v0.3 §3.1과 정합; 커넥터 추가=확장 패턴 실증(법제처) | component-specs §4 #1, mvp §4 |
| D25 | **MVP 커맨드 = 4종**(+`LawDiff`) | 확정 | "무엇이 바뀌나"가 서비스 핵심 가치 | component-specs §4 #10, mvp §4 |
| D26 | **현행법 diff MVP 포함** — ~~신구조문대비표(1차)~~+국가법령정보(권위 기준선), baselineLawId 채움. **diff 원천은 D42로 개정**(시행중본↔시행예정본 직접 대조) | 개정됨(D42) | diff가 핵심 가치, 대비표로 정렬부담↓; RAG/Vector MVP 활성 | component-specs §5 갭1 |
| D27 | D24/D26에도 **저장소(ADR-001) 불변** — 현행법 ~0.4GB·벡터 ~270K청크, 헤드룸 내 | 확정 | 트리거(5~10M벡터) 미해당; Vector가 MVP에서 비로소 활용됨 | ADR-001 |
| D28 | **Triage 분류 기준·라우팅 정책** 문서화(impactScope 판정 룰), 스테이지는 post-MVP | 확정 | 기준 선고정 → LawFacts 추출 프롬프트 명확화 | [[triage-policy]] |
| D29 | 누적 변경을 **아키텍처 v0.4 스냅샷**으로 반영(RAG Indexer 분리·3출처·4커맨드·LawFacts·해소4상태·triage) | 개정됨→v0.5 | v0.3 그림↔컴포넌트 문서 불일치 해소(RAG Indexer 누락) | [[v0.4-pipeline-refinements]] |
| D30 | **법안 의미검색(LawFacts·요약 임베딩)** 추가 — 모호 plain text 식별 → 후보(AMBIGUOUS). *분석용 RAG(현행법)*와 별개 *탐색용* | 확정 | 법안명·번호 없는 효과/주제 질의 커버; fail-closed 유지 | [[v0.5-bill-discovery]], component-specs §4 #2·#4 |
| D31 | D30에도 **저장소(ADR-001) 불변** — 탐색 임베딩 ~1~3GB(벡터 총 <1M) | 확정 | 헤드룸·트리거(5~10M) 내; 스키마 진화 | ADR-001 |
| D32 | **임베딩은 외부 API**(자체 호스팅 제외), 공유 `Embedder`, dim 1536, 벤더 벤치 후 | 확정 | 인프라 예산 없음; 공개 데이터라 외부 API 적합; 1536=ADR-001 가정 일치 | component-specs §3.3 |
| D33 | **임베딩 벤치 항목 확정** — OpenAI vs Upstage, 시나리오 A(조문→현행법, ~~신구조문대비표~~ **개정문+조문대조=정답**, D42)·B(모호질의→법안), Recall@5·MRR | 확정 | 벤더를 데이터로 확정; 수집 파이프라인 선행 | [[embedding-benchmark]] |
| D34 | **DB 프로비저닝** — 개발=로컬 도커 `pgvector/pgvector:pg16`, 프로덕션=AWS RDS PostgreSQL+pgvector(ADR-001). `CREATE EXTENSION vector`, 임베딩 dim 1536. 스키마 소유권: 관계형(bill/article/impact)=Spring(JPA/Flyway), 임베딩=파이프라인 | 확정 | 지금은 AWS 불필요(로컬 무료); 스키마 이중관리 방지 | docker-compose.yml, db/init.sql |
| D35 | **파이프라인을 Spring으로 통합** — Boot 4.0 + Spring AI 2.0(GA 2026-05-28)으로 Python 서버 대체. 3-런타임 → **2-런타임**(Spring / TS웹). Python↔Spring REST 계약은 내부 호출로 소멸, 도메인 모델 단일화 | 확정 | D05·D19·D32로 Python 선택 근거(무거운 ML 생태계) 소멸 — 실체는 HTTP+파싱+오케스트레이션. v0.2부터 예약된 경로. 기존 Python 코드는 포팅 사양·벤치 도구로 활용 | [[v0.6-spring-consolidation]], [[spring-migration]] |
| D36 | **Evaluation Harness(합성 페르소나 E2E)를 MVP에서 제외** — 수직 슬라이스 완성 후(post-MVP) 착수. MVP 품질 앵커는 컴포넌트 단위 테스트 + 소량 사람검수 골든셋. 역할 규율(D14: 정답판정 금지)은 유지 | 확정 | 하니스는 완성된 E2E 흐름·프롬프트가 있어야 가치(회귀·UX 비평) 발동. 페르소나 패널·REST 전 구간에 걸친 선행 부담이 핵심 경로(#5~#9)를 지연 | mvp §4·§5, 이슈 #16 |
| D37 | **에이전트 프레임워크(LangGraph 등) 미도입** — Analysis Engine은 Spring 빈 + Spring AI `ChatClient`/`VectorStore`로 **명시적 워크플로** 구현(검색→조립→추론→검증→재생성≤N). RAG도 Advisor 자동화 대신 명시적 검색(주입 `source_id` 소유) | 확정 | 실행 경로가 설계 시점에 고정 = 에이전트 아님(H안 기각과 동일 근거). Python 계열 프레임워크는 D35(런타임 통합)와 충돌. 결정성·캐시·인용 감사가 1급 요건 | [[AnalysisEngine]], v0.6 §3.4 |
| D38 | **법안 본문(fullText) 획득 경로 = 미해결 갭** (현행법은 해결 ✓) — 목록 API·상세 페이지·대체 API 3종 모두 본문 미제공(2026-07-21 실측). `RawBill` 필드 정의도 부재하여 명문화. Normalizer는 Phase 1(필드 매핑+revision, 본문 무관)/Phase 2(본문 파서)로 분리 | **해소됨(D42)** | 문서가 본문을 "🔵B 원문 파싱"으로 전제하고 프롬프트 요소4를 필수로 규정했으나, *획득 수단*을 어디에도 규정하지 않은 정합성 결손. 본문 없이는 LawDiff·인용 그라운딩·벤치 정답쌍 모두 불성립 → MVP 필수 관문. **2026-07-31: 국가법령정보(현행법) 본문 제공 확인 → 갭은 *법안(assembly)* 한정. 2026-08-01 D42로 해소 — MVP 대상이 의안이 아니라 *시행예정 법령*(`eflaw`)으로 확정돼 본문이 이미 확보됨. assembly 본문은 참고용 소스의 post-MVP 과제로 강등** | [[SourceConnector]] §본문 획득, [[Normalizer]] |
| D39 | **Java 단일 구조 유지 재확인** (메모리 우려 검토 결과) — Python 분리 재도입 안 함. 대신 **전환 완료**: Spring 자격증명 경로 연결 → 커넥터 Java 이관 → Python은 진단·벤치 도구로만 축소 | 확정 | "RAG가 앱 인스턴스에서 돌아 메모리 한계" 우려는 우리 구조에 미해당 — 벡터 저장·HNSW 검색=Postgres(별도 인스턴스), 임베딩·추론=외부 API(D32·D19). JVM은 HTTP+파싱+top-k 수 KB만 다룸. 분리해도 pgvector 부담은 그대로이고 런타임·인스턴스만 증가(예산 역행, ADR-001 "동인은 띄워 둔 인스턴스") | 본 로그 §D39 |
| D40 | **오프라인/온라인 실행 모드 분리**(v0.7) — 다이어그램·설계 규율을 배치 적재 vs 요청 응답으로 구분. Persona Builder(Nemotron 군집)는 다이어그램에서 제외(post-MVP), `PersonaImpact` 커맨드는 유지하되 세그먼트는 사용자 선택/수작업 정의 | 확정 | 두 모드는 지연 요구·장애 영향·확장 축이 근본적으로 다름. 분리로 "온라인에 넣기 전 오프라인 가능성을 먼저 묻는다"는 규율이 명시됨(LawFacts 선계산·임베딩 적재의 근거) | [[v0.7-offline-online-split]] |
| D41 | **페르소나 = 회원가입 자기신고 프로필**(Nemotron 군집 폐기) — `UserProfile{purposes, age(정수), occupation, employmentType, householdType, housingType, regionSido}`. **성명·생년월일·연락처·상세주소 미수집**, 시도까지만. **나이는 구간이 아닌 정수** — 법령 기준이 만 19/34/65세처럼 특정 나이로 끊기므로 구간화 시 경계 사용자에게 오답. 캐시 키는 `userId`가 아니라 **프로필 속성 해시**. D10 주입 규율(비인용·`<persona>` 격리)은 승계 | 확정 | 고정 6세그먼트는 해상도 부족(같은 버킷 내 상황 상이). 자기신고가 더 정확·최신이고 외부 데이터셋·군집 파이프라인 의존 제거. **주의: 직접식별정보 미수집일 뿐 "개인정보 아님"은 아님** — 조합 재식별·계정 식별자 존재하므로 처리방침·동의·파기 필요 | [[component-specs]] §2 |
| D42 | **MVP 분석 대상 = 공포 후 시행 대기 법령**(국가법령정보 `target=eflaw`), 의안은 post-MVP. 도메인 모델 **`Law` 신설·`Bill` 보류**(C안). 신구조문대비표(HWP) 파싱 **폐기** | 확정 | 서비스 정의가 "적용될 확률이 높거나 적용 예정인 법안"이므로 통과율 ~20%인 의원발의는 참고용에 가깝다. 시행예정 법령은 **적용 확실성 100%**이고 전문·개정문·제개정이유·부칙이 **이미 연동된 출처에 전부 존재**(2026-08-01 실측, 899건). `조문변경여부='Y'` 플래그가 변경 조문을 직접 지목해(주택법 137개 중 6개) LawDiff 대상 선별·토큰 비용까지 해결. eflaw 응답에 `billNo`·발의자·소관위·심사단계가 없어 `Bill`을 쓰면 절반이 null → 모델 분리 | [[v0.8-pending-law-corpus|아키텍처 v0.8]], [[SourceConnector]] §MVP 본문 경로, [[component-specs]] §1.1 |
| D43 | **복수 시행예정본의 기준 규칙 확정** — ① 정본 단위 `(lawId, effectiveDate)`(인용·캐시·Store 키) ② diff 기준선 = **현재 시행중본**(순차 체이닝은 post-MVP) ③ 시행일 미지정 질의는 **가장 이른 미래 시행일본으로 해소**(`RESOLVED`)하고 나머지는 `alternatives`로 안내, `@efYd`로 특정(D45). 동명 *다른* 법령은 `AMBIGUOUS` 유지. 전체 타임라인 병합은 post-MVP | 확정 | 실측: 주택법 현행본 공포 21447(2026-03-05)이 이미 시행 중이고 먼저 공포된 21323(02-03)이 시행 대기 — 공포순≠시행순이라 현행본이 최신 시행분을 반영하므로 기준선=시행중본. "언제까지 뭘 하라"엔 가장 임박한 개정이 가장 actionable → 되묻기 stopgap 대체. 코드: `SourceAnalyzer` 강매칭을 lawId로 그룹핑, `ResolutionResult.resolvedAmong` | [[component-specs]] §1.1, [[SourceAnalyzer]] |
| D44 | **`BillFacts` → `LawFacts` 개명**(참조 키 `bill_ref` → `law_ref`) + **`bill-attributes` → `law-attributes` 문서 개편**(의안 고유 속성 제거, 획득 계층 3→2). v0.4~v0.7 동결 스냅샷은 옛 이름 유지, v0.8·살아있는 문서는 갱신 | 확정 | 이름이 *의안 전용*으로 읽혀 "의안을 post-MVP로 미뤘는데 왜 계속 나오나"라는 오해를 만들었다. 실체는 **분석 대상이 무엇이든 붙는 페르소나 무관 Layer A 파생 캐시**이고 v0.8 오프라인 다이어그램에 MVP로 들어가 있다. 개명 자체는 설계 변화가 없다. 속성 카탈로그는 의안 기준 서술이 남아 있어 본문까지 법령 기준으로 재작성했다 — 특히 **🔵B(원문 파싱) 계층이 소멸**했다(API가 조문·부칙·개정문을 모두 제공). 옛 경로는 동결 스냅샷 v0.4~v0.7의 링크를 살리려 **묘비 문서**로 남긴다 | [[component-specs]] §1.3, [[v0.8-pending-law-corpus]] §4.5 |
| D45 | **공개 API 표면 고정 — 분석 중심 구조** — `POST /api/v1/analyses`(자연어 질의)가 상위 리소스, `/laws/*`(검색·목록·사실)는 그것이 참조하는 **읽기 전용 데이터**. 분석을 law 하위에 두지 않는다(사용자는 lawId를 모른 채 질문). 4종 커맨드는 *사용자 선택 모드가 아니라 답변 구조·그라운딩 가드레일*이고 **Query Planner**(질의→차원)가 고른다. 시행일을 경로에 포함(`/laws/{lawId}/{efYd}`, D43), 해소 4상태는 HTTP 200, 프로필 없으면 부분성공(`unmet`) | 확정 | `/api/v1/...` 가 6개 문서에서 언급만 되고 정의된 적 없었다. 초안은 `/laws/{id}/analysis` 로 분석을 법령 하위에 뒀으나, 이는 '검색→선택→분석' 브라우징 흐름을 API에 박아 **자연어 자유 질의**를 배제한다. 분석이 상위 리소스이고 법령은 그 입력이라는 것이 올바른 구조. Query Planner 는 자연어 질의 모델이 새로 요구하는 미구현 컴포넌트 | [[service-api-spec]] |
| D46 | **Query Planner = NL→타입 DTO→dispatch** (컴파일러/ORM 유비, 명칭은 Query Planner) — 자연어를 `AnalysisQuery`(타입 DTO)로 번역(Haiku), 타입이 검색·실행 전략 결정. QueryType **5종**: `LOOKUP`(발견)+`SUMMARY`·`DIFF`(Layer A)+`IMPACT`·`ACTION`(Layer B). Target 2형: `Reference`(해소)·`Discovery`(코퍼스 검색). 주 타입 1+집합, 자유도 보존 | 확정 | D45가 남긴 Query Planner 공백 해소. **자연어 입력 ≠ 동적 제어** — 번역기가 타입 객체를 뱉으면 이후 경로는 고정이라 결정성·캐시·인용 감사 보존(D37 *강화*, 에이전트 아님). 타입이 검색 전략을 골라 LOOKUP·SUMMARY·DIFF는 캐시/no-RAG(비용 레버). LOOKUP은 '찾아줘' 검색 동작을 대응 | [[QueryPlanner]], [[v0.9-nl-query-planner]] |
| D47 | **아키텍처 v0.9** — 온라인 경로를 자연어 질의 중심으로(Query Planner 신설). 옛 `AnalysisPipeline`+`CommandRegistry`→`QueryDispatcher`, `AnalysisCommand`→`DimensionHandler` | 확정 | 커맨드가 *사용자 선택 모드*가 아니라 답변 구조·그라운딩 가드레일임을 구조에 반영 | [[v0.9-nl-query-planner]] |
| D48 | **백엔드 동시성 기법은 측정 선행** — single-flight·격리수준·outbox는 관측 지표로 병목을 증명한 뒤 적용. 관측 스택 확정: Micrometer→Prometheus→Grafana, Micrometer Tracing→OTel→Tempo, k6 부하, postgres_exporter. 각 기법 = 문제→신호→기법→트리거 | 확정 | speculative한 락은 없는 병목을 만들고 복잡도만 늘린다. "측정 없는 최적화 금지"를 문서 구조로 강제 — k6로 스탬피드 100:100을 먼저 관측하고 single-flight 후 100:1 증명 | [[observability]], [[concurrency-and-reliability]] |
| D49 | **알림은 인앱 알림함 우선**(외부 채널 opt-in) — 연락처 미수집(D41)이라 이메일·푸시 불가. 인앱 알림함은 PII 불필요. Outbox+dedup로 정확히 한 번 | 확정 | D41 최소수집과 알림 기능의 긴장 해소 — 연락처를 받지 않고도 통지 제공. 외부 발송은 명시적 별도 동의 후에만 | [[concurrency-and-reliability]] §3, [[service-api-spec]] §3.6 |
| D50 | **로그 스택 = Grafana Loki**(구조화 JSON + trace-id 상관, Prometheus·Tempo와 Grafana 단일 UI). 운영 로그(휘발 TTL) vs **감사 로그**(append-only 영구) 분리. 로그 PII 규율(userId 대신 프로필 해시, 질의 원문 마스킹) | 확정 | ELK/Elasticsearch는 로그 검색 최강이나 ES 운영 부담이 커 우리 규모엔 과함. 이미 Grafana 진영이라 Loki가 정합(한 UI·경량·라벨 색인). AWS 관리형 대안 CloudWatch. 감사 로그는 법률 서비스 책임성(D08 그라운딩)·D41 로그 뒷문 차단 | [[observability]] §4, [[concurrency-and-reliability]] §4 |
| D51 | **캐싱 모델 3층 확정** — ① Layer A 오프라인 선계산(법령 사실·diff → context 재료) ② **Anthropic prompt caching**(안정 prefix=가드레일+법령 사실, 읽기 ~10%)으로 context 재사용 ③ 답변 캐시는 **완전 동일 질의만**(키에 **질문 해시** 포함). Semantic 답 캐시 기본 미사용. **차원=캐시 키 아님**(라우팅·구조·가드레일). 개인화 답 = 캐시 context + 프로필 + 실제 질문 → Opus 1콜 | 확정 | 이전 "차원별(프로필+법령+dimension) 답 캐시"는 같은 버킷의 다른 질의("구체적으로 더")에 같은 답을 주고, 개인화 답 재사용률이 낮아 선계산 낭비. prompt caching(context)과 semantic caching(완성 답)은 다른 층 — 전자를 주로. 선례: Harvey AI 법률 RAG(Postgres+pgvector·검색+그라운딩·질의별 생성) | [[component-specs]] §3.4, [[concurrency-and-reliability]] §1 |
| D52 | **문서 정리 — MVP 범위 밖 항목 축약·현행화** — ① `Bill` 스키마(§1.2) 삭제(복원은 git·[[SourceConnector]] 계약) ② **프롬프트 정의서 현행 재작성**(Bill·Nemotron·`BILL:` → Law·자기신고 프로필·`LAW:{lawId}@{efYd}`·차원·D51 캐싱) ③ Evaluation Harness 등 post-MVP 블록 한 줄 축약 ④ `ImpactResult` 정렬(`affected_segments`→`affected_profiles`, `stage_info`→`effective_info`) | 확정 | 프롬프트 정의서가 통째로 구 설계라 component-specs가 '동일 스키마'로 참조하며 잘못된 곳을 가리켰다. 결정로그·동결 스냅샷(v0.x)·의도적 post-MVP 경계 마커는 보존(축약 유지) | [[component-specs]] §1·§3, [[analysis-prompt-spec]] |
| D53 | **RAG 평가·회귀 프레임워크 확정** — 3층(Retrieval·Answer·E2E) + **하이브리드 도구**: 결정론 게이트=Java(Recall@K·Hit@K·MRR·**인용존재성 faithfulness**·거부정확도), 리치 답변품질=RAGAS/Python(오프라인 보조). config 통제변인 스윕 + baseline·절대임계(Recall@5≥0.80·faithfulness=1·거부=1) 회귀 게이트. `com.lia.core.eval` 결정론 스캐폴딩 구현(합성 데이터 단위테스트) | 확정 | RAG 노브(chunk·top-k·reranker·prompt) 변경 시 성능 하락을 자동 감지해야 하고, 검색↔생성 오류를 분리해야 한다. **LLM을 게이트 판정자로 쓰지 않음**(D14). 우리 특유: 인용존재성=이미 결정론 faithfulness, fail-closed=Unanswerable 안전 게이트(RAG 없이 실 `SourceAnalyzer`로 가동). **D36 정제** — retrieval·refusal 게이트=근term, answer-quality·persona E2E=post-MVP. D33 검색평가 흡수 | [[rag-evaluation-framework]], [[embedding-benchmark]], [[analysis-prompt-spec]] §6 |
| D54 | **Law Store 저장 모델 = JSONB 정본 + pgvector chunks** — `law_versions`(1행=1버전, `payload jsonb` + 인덱스 `(lawId,efYd)`·revision·status) = **정본 SSOT**, `chunks`(Spring AI `PgVectorStore`) = 조문 임베딩(파생, RAG). 영속 **Spring Data JDBC + Flyway**(Hibernate 불필요), 벡터 `PgVectorStore`. upsert 멱등 `ON CONFLICT (lawId,efYd)` | 확정 | **D34의 "관계형=JPA" 정제** — `Law`는 불변 스냅샷이고 조문 단위 SQL 수요가 낮으며(RAG는 벡터 검색) context는 통본을 쓰므로, 정규화 관계형+ORM 매핑보다 JSONB 문서 1-fetch가 정합적. 검색은 pgvector, 인용의 진실은 정본(둘 다 필요). 정본=SSOT·chunks=재생성 가능 | [[LawStore]], [[law-domain-basics]], [[component-specs]] §1·#4 |

> **D37 재검토 트리거:** ① 대형 옴니버스 법안의 map-reduce + Generator-Critic이 3단 이상 *동적* 분기로 확장 ② 멀티턴 대화형 탐색(상태 지속·중단 재개) 도입. 그때도 `AnalysisEngine` 인터페이스 뒤에 격리해 도입 가능하므로 본 결정은 가역적(JVM 대안: LangGraph4j·Embabel).

---

## 아직 개별 문서가 없는 결정의 상세

### D05 — 외부 foundation API 호출, 비학습
분석 엔진은 잘 만들어진 외부 foundation 모델 API(강모델)를 호출해 응답을 받는다. 자체 모델 학습이나 파인튜닝은 하지 않는다. 모델 티어링(triage용 소형 모델)은 *선택적 비용 최적화*이며 그조차 API(예: Haiku) 조합이지 자체 호스팅이 아니다.

### D06 — RAG/RDB의 위치
RAG·RDB는 "두뇌(모델)를 경량으로 바꾸는 장치"가 아니라, 파운데이션 모델의 **학습범위 밖에 있는 최신·구체 법령 데이터를 호출 시점에 컨텍스트로 공급**하는 층이다. 모델 크기와 직교한다. (법안 1건은 컨텍스트에 통째로 들어가므로 벡터 RAG 대상이 아니고, RAG는 현행법 관련 조문·유사 선례 검색에만 쓴다.)

### D07 — 2계층 분석 엔진
- **Layer A(법안 사실층):** 무엇이 바뀌나·시행일·영향 도메인. 페르소나 무관, 법안당 1회, 강하게 그라운딩·캐시.
- **Layer B(해석층):** 그 위에 페르소나별 영향·대응안. 저렴·다량.
- 전 구간에 **인용검증 게이트**(스키마+인용 존재성), 비용은 **모델 티어링**(triage 소형/추론 강모델), 품질은 보편 법안에 한해 Generator-Critic/Map-Reduce 조건부.

### D10 — Nemotron 페르소나 (D41로 폐기, 규율만 승계)

**폐기:** Nemotron-Personas-Korea 데이터셋 채택·군집·`population_weight`. → **D41 자기신고 프로필**로 대체.

**승계되는 규율(불변):**
- 페르소나/프로필은 "수신자 정보"일 뿐 **인용 가능한 법적 source 아님** — `<persona>`와 `<context>`(법령) 블록 분리.
- (프로필×법안→영향) 합성쌍 **파인튜닝 금지**(학습 안 함 + 그라운딩 원칙 충돌).
- **정량 인구통계 용도 금지** — Nemotron은 독립가정 한계 때문이었고, 자기신고 프로필은 인구 대표성이 없기 때문이다(사유는 다르나 결론 동일).

### D39 — Java 단일 vs Python+Java 재비교 (메모리 관점)

**질문:** Java 단일이면 RAG 동작과 서버가 같은 인스턴스에서 돌아 메모리 한계가 있지 않나?

**검증 — RAG 구성요소의 실행 위치:**

| 구성요소 | 실행 위치 | JVM 메모리 |
|---|---|---|
| 벡터 저장·HNSW 인덱스 | **Postgres/RDS**(별도 인스턴스) | 0 |
| 유사도 검색 연산 | **Postgres**(SQL) | 0 |
| 임베딩 모델 | **외부 API**(OpenAI/Upstage, D32) | 0 |
| LLM 추론 | **외부 API**(Anthropic, D19) | 0 |
| JVM이 하는 일 | HTTP 호출·JSON 파싱·컨텍스트 조립·top-k 5건 | 수십 MB |

→ **"RAG 동작" 중 JVM에 상주하는 것은 없다.** ADR-001의 벡터 24GB는 전부 pgvector 쪽. 우려가 성립하는 경우(자체 임베딩 모델 인프로세스 로드, 인메모리 벡터 인덱스, 로컬 LLM)는 D32·D19·D04에서 이미 배제됨.

**분리해도 이득 없음:** Python을 떼어내도 pgvector·외부 API 부담은 동일하고, 프로세스(JVM+uvicorn)·인스턴스·REST 홉·이중 도메인 모델만 늘어난다. 임베딩 자체 호스팅조차 예산으로 포기한 상황(D32)에서 런타임 증가는 방향이 반대.

**실제 메모리 리스크는 다른 곳:** 대형 법안 원문(수십~수백 KB 문자열) 동시 보유 — Python으로 옮겨도 동일하며 스트리밍·청크 처리로 해결할 문제. 컨테이너 1GB / `-XX:MaxRAMPercentage=75` 수준이면 충분.

**따라서 필요한 것은 구조 재검토가 아니라 전환 완료** — 현재 "설계는 Java 단일, 코드는 Python 다수"인 미완 상태를 해소한다.

---

## 다음 결정 대기 (Open)

- **없음 — 열린 설계 결정이 없다.** 직전까지 유일한 Open이던 **D43**(복수 시행예정본 기준)은 2026-08-14 확정(가장 이른 시행일 해소 + `alternatives` 안내). 남은 것은 구현 세부와 post-MVP뿐이다.

> **post-MVP 대기:**
> - Evaluation Harness 패널 크기·골든셋 규모(D36) — 수직 슬라이스 완성 후.
> - 시행예정본 **순차 체이닝·전체 타임라인 뷰**(D43 확장) — 지금은 가장 이른 본 기준.
> - 답변 **semantic 캐시**(D51 기본 미사용).

> **정리된 항목(더 이상 열려 있지 않음):**
> - ~~세그먼트 군집 알고리즘·검증(구 D18)~~ → **D41로 폐기**(자기신고 프로필로 대체).
> - Proposed였던 D04(저장소)는 D22·D27·D31·D42가 반복 재확인 → 사실상 확정(정식 승격은 실부하 측정 후). D07(2계층 엔진)은 D37·D51이 그 위에 구현을 얹어 채택됨.

> 결정은 **D54까지** 진행됐고 **열린 설계 결정은 없다** — 문서 스펙대로 개발 시 MVP happy-path E2E 동작이 보장된다([[component-specs]] §5 정합성 검증).
