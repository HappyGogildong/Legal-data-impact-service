---
title: 시작하기 — 온보딩 가이드
status: Living
date: 2026-06-28
tags: [onboarding, start-here, guide]
---

# 🚀 시작하기 — 어떤 문서부터 볼까

새로 합류한 팀원을 위한 **읽기 순서·문서 지도**. 5분 안에 프로젝트를 이해하고, 필요한 문서로 바로 가도록 돕는다.

## 이 프로젝트 한 줄
**LIA(입법 영향 분석기)** — 아직 시행 전인(발의·심사·공포 단계) 법안이 *나에게 어떤 변화를 주는지, 무엇을 해야 하는지*를 일반 시민 언어로 알려주는 **웹앱**. Python(수집·해석)·Java Spring(도메인·API)·TypeScript(웹) 폴리글랏.

## 권장 읽기 순서
1. **이 문서** (지금)
2. [[ARCHITECTURE|아키텍처 색인]] → 최신 스냅샷 [[v0.5-bill-discovery|v0.5]] — 전체 그림·데이터 흐름·다이어그램
3. [[decision-log|결정 로그]] (D01~D33) — "왜 이렇게 됐나"를 한눈에
4. [[components-io-and-scope|MVP 컴포넌트·범위]] — 무엇을 어디까지 만드나
5. [[component-specs|컴포넌트 상세 스펙]] — 개발용 I/O·계약(REST·세그먼트)·정합성
6. [[analysis-prompt-spec|프롬프트 정의서]] — LLM 호출 입력계약·응답 JSON·인용검증
7. 참조: [[bill-attributes|법안 속성]] · [[triage-policy|Triage 정책]] · [[embedding-benchmark|임베딩 벤치]]

## 역할별 빠른 경로
| 역할 | 먼저 볼 것 |
|---|---|
| **Spring 백엔드** | component-specs §1(도메인)·§3(REST 계약)·§4(#8~#13), prompts §4(응답 스키마), mvp |
| **Python 파이프라인** | v0.5 §3, component-specs #1~#5·#11, bill-attributes, embedding-benchmark |
| **프론트엔드(TS)** | component-specs #13, prompts §4(ImpactResult 스키마), mvp §2(흐름) |
| **기획·PM** | v0.5 §1·§4·§5, decision-log, mvp §4(범위) |

## 문서 지도 (폴더별)
| 위치 | 무엇 | 성격 |
|---|---|---|
| `ARCHITECTURE.md` | 버전 색인 + ADR 매핑 + Changelog | living 색인 |
| `architecture/` | 버전별 **동결 스냅샷**(현행 v0.5) | 수정 금지 |
| `adr/` | ADR-001 + **decision-log**(전체 결정 인덱스) | living |
| `mvp/` | 컴포넌트 카탈로그·MVP 범위 | living |
| `components/` | 상세 스펙·계약·정합성 검증 | living |
| `prompts/` | LLM 프롬프트 정의서 | living |
| `reference/` | 법안 속성·Triage·임베딩 벤치 | living |

## 문서 규약 (꼭 알아둘 것)
- **`architecture/`는 동결 스냅샷.** 설계가 바뀌면 *이전 파일을 고치지 않고* 새 버전(vX.Y)을 추가하고 `ARCHITECTURE.md` 색인·Changelog를 갱신. 최신본만 "현행".
- **결정은 `adr/decision-log.md`에.** 새 결정은 D번호로 추가. 스냅샷 밖의 결정(임베딩 벤더 등)도 여기서 추적.
- **3곳 동기화(byte-identical):** ① workspace `Legislative Impact Analyzer/docs` ② Obsidian 볼트 ③ 코드 레포 `law-impact-analysis/docs`. **git PR/커밋은 하지 않음**(Obsidian 자동 동기화, workspace는 Notion 공유).
- **문서 사이트:** `mkdocs serve`로 렌더(Material 테마, mermaid·Obsidian 콜아웃·위키링크 플러그인 설정됨). 설치: `pip install mkdocs-material mkdocs-callouts mkdocs-roamlinks-plugin`.

## 지금 상태 / 다음 작업
- **설계 단계 정리됨** — MVP 범위 확정: 출처 3종(열린국회·법제처·국가법령정보), 커맨드 4종(요약·diff·내영향·대응안), 현행법 diff 포함, 페르소나 6 세그먼트, 추론=Opus 4.8, 임베딩=외부 API(벤더 미확정).
- **다음:** 데이터 수집 파이프라인 최소 구현 → 골드셋 추출(신구조문대비표) → **OpenAI vs Upstage 임베딩 벤치**([[embedding-benchmark]]) → 벤더 확정 → 수직 슬라이스.

## 핵심 개념 30초
- **2계층 분석:** 법안 사실(Layer A, 페르소나 무관·캐시) → 개인화 해석(Layer B).
- **그라운딩:** 모든 주장은 조문 `source_id` 인용 필수, 없으면 차단(환각 통제).
- **해소 4상태:** RESOLVED/AMBIGUOUS/NOT_FOUND_YET/UNVERIFIED — 신뢰 출처 확인 안 되면 분석 안 함(fail-closed).
- **RAG 두 용도:** 분석용(현행법) + 탐색용(법안 요약, 모호 질의 → 후보).
