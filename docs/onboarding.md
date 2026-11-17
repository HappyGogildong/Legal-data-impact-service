---
title: 시작하기 — 온보딩 가이드
status: Living
date: 2026-11-18
tags: [onboarding, start-here, guide]
---

# 🚀 시작하기 — 어떤 문서부터 볼까

새로 합류한 팀원(또는 새 컨텍스트에서 시작하는 AI 세션)을 위한 **소개 + 문서 내비게이션**. 5분 안에 현재 상태를 파악하고 필요한 문서로 바로 가도록 돕는다.

## 이 프로젝트 한 줄
**LIA(입법 영향 분석기)** — 아직 시행 전인(발의·심사·공포 단계) 법안이 *나에게 어떤 변화를 주는지, 무엇을 해야 하는지*를 일반 시민 언어로 알려주는 **웹앱**.

**스택(v0.6, D35):** Java Spring **Boot 4.0 + Spring AI 2.0** 단일 애플리케이션(수집·해석 파이프라인+도메인+REST) + TypeScript 웹. 저장소는 단일 Postgres+pgvector. 추론=Claude Opus 5.5 API(D68), 임베딩=OpenAI `text-embedding-3-small`(D32). 운영은 AWS EC2+RDS(D63)와 Vercel 웹(D64).

## 현재 스냅샷 (2026-11-18)

| 상태 | 내용 |
|---|---|
| ✅ 완료 | 수집·정규화·diff·색인 배치(D42·D62, 같은 시행일 공포본 병합 D85) · 분석 Layer A 선계산·Layer B 개인화(D67·D69) · 법령 발견(D70) · 대응 방안 상황 확인(D71) · 북마크·이력·구독 알림함(D72~D74) · 웹(Next.js) · 관측(Grafana 배치·호출 기록, D83) · **AWS 운영 투입**(EC2+RDS+Vercel, 백필·cron, 2026-11-16 — [[cloud-infra]]) · **자동 배포**(main 머지 → OIDC → SSM, D86, 2026-11-18) |
| 🔜 다음 | 평가 기준선(`lia eval`)·`llm_prices` 단가 → ⑦b 내 프로필 관련 구독 · ⑦c 이메일 발송(#32) · 개인정보처리방침(#35) |
| 📋 트래킹 | 진척 상세는 [[status]], 열린 일은 `gh issue list`(마일스톤 M1~M4) |

레포: **github.com/HappyGogildong/Legal-data-impact-service** (이 폴더가 메인 작업 폴더)

## 권장 읽기 순서
1. **이 문서** (지금)
2. `CLAUDE.md` (레포 루트) — 개발 규약·명령어·함정 모음 (AI 세션은 자동 로드)
3. [[ARCHITECTURE|아키텍처 색인]] → 최신 스냅샷 [[v0.6-spring-consolidation|v0.6]] — 전체 그림·데이터 흐름
4. [[decision-log|결정 로그]] (D01~D86) — "왜 이렇게 됐나"를 한눈에
5. [[component-specs|컴포넌트 상세 스펙]] — 도메인 모델·계약·정합성
6. [[analysis-prompt-spec|프롬프트 정의서]] — LLM 입력계약·응답 JSON·인용검증
7. 참조: [[law-attributes|법령 속성]] · [[triage-policy|Triage]] · [[embedding-benchmark|임베딩 벤치]] · [[spring-migration|Spring 버전 변경점]]

## 역할별 빠른 경로
| 역할 | 먼저 볼 것 |
|---|---|
| **백엔드(Spring — 메인)** | [[spring-migration]](Jackson 3·빌더 주의) → component-specs §1(도메인)·§3(내부 계약)·§4 → `core/src/` 코드 + 열린 이슈 #5·#9·#10 |
| **프론트엔드(TS)** | component-specs #13, prompts §4(ImpactResult 스키마), mvp §2(흐름), 이슈 #14 |
| **기획·PM** | v0.6 §1·§4·§5, decision-log, mvp §4(범위), GitHub 마일스톤 |
| **새 AI 세션** | CLAUDE.md(자동) → 이 문서 '현재 스냅샷' → `gh issue list` → decision-log 하단(최신 결정) |

## 문서 지도 (docs/)
| 위치 | 무엇 | 성격 |
|---|---|---|
| `ARCHITECTURE.md` | 버전 색인 + ADR 매핑 + Changelog | living 색인 |
| `architecture/` | 버전별 **동결 스냅샷**(현행 **v0.6**) | 수정 금지 |
| `adr/` | ADR-001 + **decision-log**(D01~D86) | living |
| `components/` | 상세 스펙 + 파이프라인 6종 설계(입출력·동작·결정 의도) | living |
| `mvp/` | 컴포넌트 카탈로그·MVP 범위(IN/OUT)·수용 기준 | living |
| `prompts/` | LLM 프롬프트 정의서(입력계약 12요소·JSON 스키마) | living |
| `reference/` | 법안 속성·Triage·임베딩 벤치·Spring 마이그레이션 | living |
| `onboarding.md` | 이 문서 | living |

## 작업 규약 (꼭 알아둘 것)
- **동결 스냅샷 불변** — 설계 변경은 새 버전 파일 + `ARCHITECTURE.md` 색인·Changelog 갱신. 결정은 decision-log에 D번호로.
- **GitHub 트래킹** — 라벨 `area:*`/`type:*`/`priority:*`, 마일스톤 M1~M4. 완료 작업도 이슈로 남기고 close(이력). 커밋은 의미 단위, **푸시는 사용자 확인 후**, 푸시 전 `.env`·`config.yaml` 미추적 확인.
- **작업은 이 레포에서만** — 문서는 **2곳 동기화(byte-identical)**: ① 이 레포 `docs/`(단일 소스, 편집은 여기서만) ② Obsidian 볼트(`…\프로젝트\입법 영향 분석\`, 아카이빙 미러). 문서 수정 후 `cp`+`diff`. (`law-impact-analysis`는 2026-07-21 제외)
- **비밀 관리** — 실제 키는 루트 `.env`(gitignore)에만. `config.yaml`·`application.yml`은 `${ENV}` 참조. `*.example`엔 절대 금지.
- **문서 사이트** — `mkdocs serve` (Material·mermaid·콜아웃·위키링크 구성됨).

## 핵심 개념 30초
- **그라운딩:** 모든 주장은 조문 `source_id` 인용 필수 — 없으면 게이트 차단(환각 통제).
- **해소 4상태:** RESOLVED/AMBIGUOUS/NOT_FOUND_YET/UNVERIFIED — 신뢰 출처 미확인이면 분석 안 함(fail-closed). 미등록≠허위(안내 다름).
- **2계층 분석:** Layer A(법안 사실·LawFacts, 페르소나 무관·캐시) → Layer B(세그먼트별 영향·대응안).
- **RAG 두 용도:** 분석용(`law` ns, 현행법) + 탐색용(`bill` ns, 법안 요약 — 모호 질의→후보). 적재·검색 **동일 임베딩 모델**.
- **출처 3종(내용 기준):** assembly=의원발의(ServiceKey+**AGE 필수**) / moleg=정부입법예고(**OC**) / law=현행법령(**OC**) — 법제처가 뒤 둘을 다 운영하니 이름에 속지 말 것.

## 빠른 명령어
```bash
# 백엔드 (core)
cd core && ./gradlew test              # 단위 + 통합(Testcontainers — Docker 필요)
# 웹 (web)
cd web && pnpm test                    # vitest
cd web && pnpm e2e                     # Playwright(모의 API)
# 로컬 인프라·문서
docker compose up -d db                # pgvector Postgres
../.venv/Scripts/python.exe -m mkdocs serve
gh issue list                          # 열린 일
```
